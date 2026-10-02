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
    fun digits(phone: String): String = phone.filter { it.isDigit() }

    fun normalize(text: String): String = text.trim().lowercase().replace(Regex("\\s+"), " ")

    fun findPairs(customers: List<Customer>): List<DuplicateMatch> {
        val active = customers.filter { it.isActive }
        val out = mutableListOf<DuplicateMatch>()
        for (i in active.indices) {
            for (j in i + 1 until active.size) {
                val reasons = reasons(active[i], active[j])
                if (reasons.isNotEmpty()) out += DuplicateMatch(active[i], active[j], reasons)
            }
        }
        return out
    }

    fun reasons(a: Customer, b: Customer): List<String> {
        val why = mutableListOf<String>()
        val pa = digits(a.phone)
        val pb = digits(b.phone)
        if (pa.length >= 10 && pa == pb) why += "same phone"
        val addrA = normalize(listOf(a.address, a.city, a.zipCode).filter { it.isNotBlank() }.joinToString(" "))
        val addrB = normalize(listOf(b.address, b.city, b.zipCode).filter { it.isNotBlank() }.joinToString(" "))
        if (addrA.length >= 8 && addrA == addrB) why += "same address"
        val nameA = normalize(a.fullName)
        val nameB = normalize(b.fullName)
        if (nameA.length >= 4 && nameA == nameB) why += "same name"
        return why
    }

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
