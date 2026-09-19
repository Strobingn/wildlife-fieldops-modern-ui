package com.strobingn.wildlifefieldops.tax

/**
 * New York State combined sales tax rates (state 4% + MCTD 0.375% where applicable +
 * county/city local portion) for counties most commonly served by Wildlife Whisperer LLC
 * (Cornwall, NY / Orange County region — Hudson Valley and surrounding areas).
 *
 * Sources:
 *   - NYS Department of Taxation and Finance publication TB-ST-825
 *     "Sales Tax Rates, Additional Taxes, and Fees"
 *     https://www.tax.ny.gov/pdf/publications/sales/tb-st-825.pdf  (retrieved June 2025)
 *   - NYS Jurisdiction/Rate lookup: https://www8.tax.ny.gov/ZSRP/getJurisdiction
 *
 * All rates are the combined rate applicable to most tangible personal property and
 * taxable services at the county level (city/special-district surcharges not included).
 * Rates are subject to change; a TODO is left below for a Settings-level override.
 *
 * TODO: Allow per-installation rate override via app Settings so operators can correct
 *       the rate without a code update if the legislature changes a local rate.
 */
object NyCountyTaxRates {

    /**
     * Map of lower-cased, trimmed county name (without "county" suffix) to combined NY
     * sales-tax rate as a percentage (e.g. 8.125 means 8.125 %).
     *
     * Counties included: the eight Hudson Valley / nearby counties most commonly served
     * by Wildlife Whisperer LLC, plus a generic NY fallback.
     */
    private val rates: Map<String, Double> = mapOf(
        // Hudson Valley core
        "orange"      to 8.125,  // Orange County: 4% state + 3.75% county + 0.375% MCTD
        "rockland"    to 8.375,  // Rockland County: 4% + 4% county - no MCTD surcharge applies at county level; combined 8.375%
        "ulster"      to 8.0,    // Ulster County: 4% state + 4% county
        "dutchess"    to 8.125,  // Dutchess County: 4% state + 3.75% county + 0.375% MCTD
        "putnam"      to 8.375,  // Putnam County: 4% state + 4% county + 0.375% MCTD
        "westchester" to 8.375,  // Westchester County: 4% state + 4% county + 0.375% MCTD
        "sullivan"    to 8.0,    // Sullivan County: 4% state + 4% county
        // Additional nearby
        "columbia"    to 8.0,    // Columbia County: 4% state + 4% county
        "greene"      to 8.0,    // Greene County: 4% state + 4% county
        "warren"      to 8.0,    // Warren County: 4% state + 4% county
        "albany"      to 8.0,    // Albany County: 4% state + 4% county
        "nassau"      to 8.625,  // Nassau County: 4% state + 4.25% county + 0.375% MCTD
        "suffolk"     to 8.625,  // Suffolk County: 4% state + 4.25% county + 0.375% MCTD
        "new york city" to 8.875 // NYC (all five boroughs): 4% + 4.5% city + 0.375% MCTD
    )

    /**
     * Fallback combined rate when the county cannot be resolved to a known entry.
     * Uses the NYS base rate + a common local add-on (8 %).
     */
    private const val NY_DEFAULT_RATE = 8.0

    /**
     * Return the combined NY sales-tax rate (as a percentage) for [county].
     *
     * Accepts county strings in any of these forms:
     *   - "Orange"
     *   - "Orange County"
     *   - "orange county, ny"
     *   - "ORANGE"
     *
     * Returns `null` when [state] is not "NY" / "New York" so the caller can choose to
     * skip auto-fill entirely for jobs outside New York State.
     */
    fun taxRatePercentForCounty(county: String, state: String = "NY"): Double? {
        if (!isNewYork(state)) return null
        val key = normalizeCountyName(county)
        return rates[key] ?: NY_DEFAULT_RATE
    }

    /**
     * Returns the display-friendly county name (title case, without "County" suffix
     * duplication) for use in the invoice UI label.
     *
     * Example: "orange county, ny" → "Orange County"
     */
    fun displayName(rawCounty: String): String {
        val key = normalizeCountyName(rawCounty)
        val titled = key.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
        return if (titled.endsWith("County", ignoreCase = true)) titled else "$titled County"
    }

    // ── internal helpers ─────────────────────────────────────────────────────────

    /**
     * Strips whitespace, lowercases, removes trailing "county" and any state suffix
     * such as ", ny" or ", new york" so the result matches a key in [rates].
     */
    internal fun normalizeCountyName(raw: String): String {
        var s = raw.trim().lowercase()
        // remove ", ny" / ", new york" / ", n.y." suffixes
        s = s.replace(Regex(",?\\s*(ny|new york|n\\.y\\.)\\s*$"), "").trim()
        // remove trailing "county" word
        s = s.replace(Regex("\\s+county\\s*$"), "").trim()
        return s
    }

    private fun isNewYork(state: String): Boolean {
        val s = state.trim().lowercase()
        return s == "ny" || s == "new york" || s == "n.y."
    }
}
