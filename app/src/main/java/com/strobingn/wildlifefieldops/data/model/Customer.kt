package com.strobingn.wildlifefieldops.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class CustomerType {
    RESIDENTIAL, COMMERCIAL, INDUSTRIAL, GOVERNMENT, AGRICULTURAL
}

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val firstName: String = "",
    val lastName: String = "",
    val companyName: String = "",
    val email: String = "",
    val phone: String = "",
    val alternatePhone: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val customerType: CustomerType = CustomerType.RESIDENTIAL,
    val notes: String = "",
    val billingAddress: String = "",
    val billingContact: String = "",
    val paymentTerms: String = "Net 30",
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val syncError: String? = null
) {
    val fullName: String
        get() = if (companyName.isNotBlank()) {
            "$firstName $lastName ($companyName)"
        } else {
            "$firstName $lastName"
        }
}
