package com.strobingn.wildlifefieldops.pricing

/**
 * Numbers Sir types into money/quantity/rate fields: "$1,250.50", "8.875%",
 * " 300 ", "1,000". The old `toDoubleOrNull() ?: 0.0` turned any "$" or comma
 * into 0 (the deposit bug, but everywhere else too).
 */
object MoneyInput {
    fun parse(text: String): Double? {
        val cleaned = text.trim()
            .replace("$", "").replace(",", "").replace("%", "")
            .replace(" ", "").replace("USD", "", ignoreCase = true)
        if (cleaned.isBlank()) return null
        val v = cleaned.toDoubleOrNull() ?: return null
        return if (v.isNaN() || v.isInfinite()) null else v
    }
}
