package com.strobingn.wildlifefieldops.pricing

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Dollar arithmetic with 2-decimal HALF_UP rounding.
 *
 * Room / Compose still store [Double] dollars for compatibility with existing
 * rows and Supabase `numeric(12,2)`, but every sum, tax, and override comparison
 * goes through [BigDecimal] so 0.1-style binary drift cannot accumulate.
 */
object Money {
    private val TWO = 2
    private val MODE = RoundingMode.HALF_UP
    private val HUNDRED = BigDecimal(100)

    fun of(dollars: Double): BigDecimal {
        if (dollars.isNaN() || dollars.isInfinite()) return BigDecimal.ZERO.setScale(TWO, MODE)
        return BigDecimal.valueOf(dollars).setScale(TWO, MODE)
    }

    fun parse(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == "." || trimmed == "-" || trimmed == "-.") return null
        return trimmed.toBigDecimalOrNull()?.setScale(TWO, MODE)?.toDouble()
    }

    fun round(dollars: Double): Double = of(dollars).toDouble()

    /** Quantity (hours, miles, qty) × unit price, rounded to cents. */
    fun times(quantity: Double, unitPrice: Double): Double {
        val qty = quantity.toBigDecimalSafe()
        val price = of(unitPrice)
        return qty.multiply(price).setScale(TWO, MODE).toDouble()
    }

    fun plus(vararg amounts: Double): Double {
        var acc = BigDecimal.ZERO
        amounts.forEach { acc = acc.add(of(it)) }
        return acc.setScale(TWO, MODE).toDouble()
    }

    fun minus(left: Double, right: Double): Double =
        of(left).subtract(of(right)).setScale(TWO, MODE).toDouble()

    /** [percent] is a human tax/discount rate such as 8.125, not 0.08125. */
    fun percentOf(base: Double, percent: Double): Double {
        val rate = percent.toBigDecimalSafe()
        return of(base).multiply(rate).divide(HUNDRED, TWO, MODE).toDouble()
    }

    fun equals(a: Double, b: Double): Boolean = of(a).compareTo(of(b)) == 0

    fun format(dollars: Double): String =
        String.format(Locale.US, "%.2f", round(dollars))

    /**
     * USD label. Built with concatenation so Kotlin `$` string templates cannot
     * swallow the next identifier (a recurring PDF/template pitfall).
     */
    fun formatUsd(dollars: Double): String = "$" + format(dollars)

    fun formatSignedUsd(dollars: Double): String {
        val rounded = round(dollars)
        return if (rounded > 0.0) "+" + formatUsd(rounded) else formatUsd(rounded)
    }

    private fun Double.toBigDecimalSafe(): BigDecimal {
        if (isNaN() || isInfinite()) return BigDecimal.ZERO
        return BigDecimal.valueOf(this)
    }
}

/** Calculated amount plus optional operator lock. */
data class MoneyField(
    val calculated: Double,
    val override: Double? = null
) {
    val isOverridden: Boolean get() = override != null
    val effective: Double get() = Money.round(override ?: calculated)
    val difference: Double get() = Money.minus(effective, calculated)
}
