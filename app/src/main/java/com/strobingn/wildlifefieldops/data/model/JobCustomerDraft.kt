package com.strobingn.wildlifefieldops.data.model

/**
 * Customer fields shown and edited on the unified Job screen.
 * Preferred contact is stored on [Customer.billingContact] (Phone / Text / Email)
 * so Room stays on schema 11.
 */
enum class PreferredContact(val label: String) {
    PHONE("Phone"),
    TEXT("Text"),
    EMAIL("Email");

    companion object {
        fun fromStored(value: String): PreferredContact =
            entries.firstOrNull {
                it.label.equals(value.trim(), ignoreCase = true) ||
                    it.name.equals(value.trim(), ignoreCase = true)
            } ?: PHONE
    }
}

data class JobCustomerDraft(
    val customerId: String = "",
    val name: String = "",
    val companyName: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val billingAddress: String = "",
    val notes: String = "",
    val preferredContact: PreferredContact = PreferredContact.PHONE
) {
    fun displayName(): String {
        val person = name.trim()
        val company = companyName.trim()
        return when {
            person.isNotBlank() && company.isNotBlank() -> "$person ($company)"
            person.isNotBlank() -> person
            else -> company
        }
    }

    fun composedServiceAddress(): String {
        val street = address.trim()
        val cityPart = city.trim()
        val region = listOf(state.trim(), zipCode.trim()).filter { it.isNotEmpty() }.joinToString(" ")
        val cityLine = listOf(cityPart, region).filter { it.isNotEmpty() }.joinToString(", ")
        return listOf(street, cityLine).filter { it.isNotEmpty() }.joinToString(", ")
    }

    fun hasAnyInfo(): Boolean =
        customerId.isNotBlank() ||
            name.isNotBlank() ||
            companyName.isNotBlank() ||
            phone.isNotBlank() ||
            email.isNotBlank() ||
            address.isNotBlank() ||
            city.isNotBlank() ||
            state.isNotBlank() ||
            zipCode.isNotBlank() ||
            billingAddress.isNotBlank() ||
            notes.isNotBlank()

    fun isLinked(): Boolean = customerId.isNotBlank()

    companion object {
        fun parsePersonName(fullName: String): Pair<String, String> {
            val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> "" to ""
                parts.size == 1 -> parts[0] to ""
                else -> parts.dropLast(1).joinToString(" ") to parts.last()
            }
        }

        fun fromCustomer(customer: Customer): JobCustomerDraft = JobCustomerDraft(
            customerId = customer.id,
            name = customer.personName.ifBlank { customer.companyName },
            companyName = customer.companyName,
            phone = customer.phone,
            email = customer.email,
            address = customer.address,
            city = customer.city,
            state = customer.state,
            zipCode = customer.zipCode,
            billingAddress = customer.billingAddress,
            notes = customer.notes,
            preferredContact = PreferredContact.fromStored(customer.billingContact)
        )

        fun fromJobDenormalized(job: Job): JobCustomerDraft = JobCustomerDraft(
            customerId = job.customerId,
            name = job.customerName,
            address = job.address,
            state = job.state.orEmpty()
        )
    }
}
