package com.strobingn.wildlifefieldops.ai.fieldops

import kotlinx.serialization.Serializable

@Serializable
data class JobMaterialUsage(
    val id: String = "",
    val itemId: String = "",
    val name: String = "",
    val quantity: Double = 1.0,
    val unitCost: Double = 0.0
) {
    val amount: Double get() = quantity * unitCost
}

object InventoryDeduct {
    fun nextOnHand(current: Double, qty: Double): Double = (current - qty).coerceAtLeast(0.0)

    fun isLow(onHand: Double, reorder: Double): Boolean = reorder > 0 && onHand <= reorder
}
