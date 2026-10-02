package com.strobingn.wildlifefieldops.ai.fieldops

import java.util.Calendar

data class WarrantyPlan(
    val startAt: Long? = null,
    val termMonths: Int = 12,
    val covered: String = ""
) {
    val expiresAt: Long?
        get() = startAt?.let {
            Calendar.getInstance().apply {
                timeInMillis = it
                add(Calendar.MONTH, termMonths.coerceAtLeast(1))
            }.timeInMillis
        }

    fun isExpiring(now: Long, withinDays: Int = 30): Boolean {
        val exp = expiresAt ?: return false
        return exp >= now && exp <= now + withinDays * 86_400_000L
    }

    fun isExpired(now: Long): Boolean {
        val exp = expiresAt ?: return false
        return exp < now
    }
}

object WarrantyTracker {
    fun fromJob(startAt: Long?, termMonths: Int, covered: String): WarrantyPlan =
        WarrantyPlan(startAt = startAt, termMonths = termMonths.coerceAtLeast(1), covered = covered)
}
