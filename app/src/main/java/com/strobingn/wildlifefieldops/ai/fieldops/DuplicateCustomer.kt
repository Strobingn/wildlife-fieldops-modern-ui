package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Customer

data class DuplicateMatch(
    val left: Customer,
    val right: Customer,
    val reasons: List<String>
)

data class JobCustomerLink(
    val jobId: String,
    val customerId: String,
    val customerName: String,
    val address: String
)

data class CustomerMergeUndo(
    val keepBefore: Customer,
    val dropBefore: Customer,
    val movedJobs: List<JobCustomerLink>
)

object DuplicateCustomer {
    private val multiSpace = Regex("\\s+")

    fun digits(phone: String): String = phone.filter { it.isDigit() }

    fun normalize(text: String): String = text.trim().lowercase().replace(multiSpace, " ")

    fun findPairs(customers: List<Customer>): List<DuplicateMatch> {
        val prepared = customers.mapNotNull { customer ->
            if (!customer.isActive) null else prepare(customer)
        }
        val out = mutableListOf<DuplicateMatch>()
        for (i in prepared.indices) {
            for (j in i + 1 until prepared.size) {
                val reasons = matchReasons(prepared[i], prepared[j])
                if (reasons.isNotEmpty()) {
                    out += DuplicateMatch(prepared[i].customer, prepared[j].customer, reasons)
                }
            }
        }
        return out
    }

    fun reasons(a: Customer, b: Customer): List<String> = matchReasons(prepare(a), prepare(b))

    private fun prepare(customer: Customer): Prepared {
        val address = normalize(
            listOf(customer.address, customer.city, customer.zipCode)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        )
        return Prepared(
            customer = customer,
            phone = digits(customer.phone),
            address = address,
            name = normalize(customer.fullName)
        )
    }

    private fun matchReasons(a: Prepared, b: Prepared): List<String> {
        val why = mutableListOf<String>()
        if (a.phone.length >= 10 && a.phone == b.phone) why += "same phone"
        if (a.address.length >= 8 && a.address == b.address) why += "same address"
        if (a.name.length >= 4 && a.name == b.name) why += "same name"
        return why
    }

    private data class Prepared(
        val customer: Customer,
        val phone: String,
        val address: String,
        val name: String
    )

    fun mergeKeepLeft(keep: Customer, drop: Customer): Customer = keep.copy(
        email = keep.email.ifBlank { drop.email },
        phone = keep.phone.ifBlank { drop.phone },
        alternatePhone = keep.alternatePhone.ifBlank { drop.alternatePhone },
        address = keep.address.ifBlank { drop.address },
        city = keep.city.ifBlank { drop.city },
        state = keep.state.ifBlank { drop.state },
        zipCode = keep.zipCode.ifBlank { drop.zipCode },
        notes = listOf(keep.notes, drop.notes).filter { it.isNotBlank() }.joinToString("\n"),
        companyName = keep.companyName.ifBlank { drop.companyName },
        billingAddress = keep.billingAddress.ifBlank { drop.billingAddress },
        updatedAt = System.currentTimeMillis(),
        isSynced = false
    )
}
