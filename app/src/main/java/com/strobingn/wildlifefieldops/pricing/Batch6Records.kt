package com.strobingn.wildlifefieldops.pricing

import kotlinx.serialization.Serializable

/** Estimate or service-contract signature. Blank ink plus a typed name is the fallback. */
@Serializable
data class CustomerSignatureRecord(
    val document: String = "estimate",
    val signerName: String = "",
    val signedAt: Long = 0L,
    val pngBase64: String = "",
    val typedOnly: Boolean = false
)

@Serializable
data class JobPaymentRecord(
    val id: String = "",
    val method: String = PaymentMethod.CASH,
    val checkNumber: String = "",
    val amount: Double = 0.0,
    val paidAt: Long = 0L,
    /** Free text when method is Other, or any note Sir types. */
    val note: String = ""
)

object PaymentMethod {
    const val CASH = "CASH"
    const val CHECK = "CHECK"
    const val CARD = "CARD"
    const val VENMO = "VENMO"
    const val ZELLE = "ZELLE"
    const val OTHER = "OTHER"
    val all = listOf(CASH, CHECK, CARD, VENMO, ZELLE, OTHER)

    fun label(code: String): String = when (code) {
        CASH -> "Cash"
        CHECK -> "Check"
        CARD -> "Card"
        VENMO -> "Venmo"
        ZELLE -> "Zelle"
        OTHER -> "Other"
        else -> code.ifBlank { "Other" }
    }
}

@Serializable
data class ExclusionPointRecord(
    val id: String = "",
    val location: String = "",
    val size: String = "",
    val material: String = "",
    val photoPath: String = "",
    val description: String = "",
    val quantity: Double = 1.0,
    val unitPrice: Double = 0.0,
    val totalOverride: Double? = null,
    /** True after Sir types or clears the seal-up line. Suggestion must not refill a blank. */
    val descriptionManual: Boolean = false,
    val photoManual: Boolean = false
)

/** One trap check's place in a day's manual route. */
@Serializable
data class TrapRouteSlot(
    val trapId: String = "",
    val day: String = "",
    val index: Int = 0
)
