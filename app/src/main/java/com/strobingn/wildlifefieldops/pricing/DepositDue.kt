package com.strobingn.wildlifefieldops.pricing

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Half the job total, due before work starts. Shown on estimates and invoices
 * only when the total is strictly over [THRESHOLD].
 */
object DepositDue {
    /** Material red. Prints dark enough to stay readable in black and white. */
    const val COLOR = "#C62828"

    /** Body copy on money documents is 9.5pt. This is bold and a step larger. */
    const val TEXT_SIZE = 11.5f

    private val THRESHOLD = BigDecimal("1000.00")
    private val HALF = BigDecimal(2)
    private const val CENTS = 2

    /** 50% of [total], rounded to cents half-up. */
    fun amount(total: Double): Double =
        Money.of(total).divide(HALF, CENTS, RoundingMode.HALF_UP).toDouble()

    /** True when the rounded total is strictly greater than $1,000.00. */
    fun shows(total: Double): Boolean = Money.of(total).compareTo(THRESHOLD) > 0

    fun notice(total: Double): String? {
        if (!shows(total)) return null
        return "50% deposit due before work starts: " + Money.formatUsd(amount(total))
    }
}
