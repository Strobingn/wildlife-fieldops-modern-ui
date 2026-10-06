package com.strobingn.wildlifefieldops.pricing

/**
 * AI-generated estimate text (rationale, line-item notes) must not state a tax
 * rate: the draft is written before the county rate resolves or a manual rate
 * lock applies, so a rate in the prose can disagree with the totals (City
 * Island printed "Tax 8.125%" over an 8.875% Bronx total). The totals always
 * carry the real rate, so generated text simply leaves it out. Typed notes
 * never pass through here.
 */
object GeneratedNoteText {
    private val TAX_RATE = Regex(
        """(?i)(?:\b(?:nys?\s+|county\s+|combined\s+)?(?:sales\s+)?tax(?:\s*rate)?\s*(?:of|at|@|is|:|=)?\s*\d{1,2}(?:\.\d+)?\s*(?:%|percent\b))""" +
            """|(?:\b\d{1,2}(?:\.\d+)?\s*(?:%|percent\b)\s*(?:nys?\s+|county\s+)?(?:sales\s+)?tax(?:\s*rate)?\b)"""
    )
    private val DOUBLE_PUNCT = Regex("""\s*([.;,])(?:\s*[.;,])+""")
    private val LEADING_PUNCT = Regex("""^\s*[.;,]\s*""")
    private val SPACES = Regex("""[ \t]{2,}""")

    fun withoutTaxRate(text: String): String {
        if (!TAX_RATE.containsMatchIn(text)) return text
        return text.lines().joinToString("\n") { line ->
            if (!TAX_RATE.containsMatchIn(line)) {
                line
            } else {
                TAX_RATE.replace(line, "")
                    .replace(DOUBLE_PUNCT, "$1")
                    .replace(LEADING_PUNCT, "")
                    .replace(SPACES, " ")
                    .replace(" .", ".")
                    .trim()
            }
        }.trim()
    }
}
