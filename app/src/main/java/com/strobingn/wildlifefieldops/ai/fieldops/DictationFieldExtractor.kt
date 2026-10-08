package com.strobingn.wildlifefieldops.ai.fieldops

/** Form fields a technician can speak aloud, pulled out of a dictation transcript. */
data class DictatedFields(
    val customerName: String = "",
    val customerPhone: String = "",
    val serviceAddress: String = "",
    /** One of NONE, LOW, MODERATE, HIGH, CRITICAL, or blank when the technician did not say one. */
    val severity: String = "",
    /** The transcript with the spoken customer / address / phone / severity phrases removed. */
    val remainder: String = ""
)

/**
 * Deterministic pull of customer name, phone, service address and severity out of dictation,
 * so they land in their own form fields instead of the findings or notes. Only fields that were
 * actually spoken are returned; nothing is guessed.
 */
object DictationFieldExtractor {

    private val STREET_SUFFIX =
        "street|st|avenue|ave|road|rd|drive|dr|lane|ln|court|ct|boulevard|blvd|way|place|pl|" +
            "circle|cir|highway|hwy|route|rt|trail|trl|terrace|ter|parkway|pkwy"

    private val NAME_STOP = setOf(
        "at", "on", "in", "lives", "live", "called", "has", "have", "had", "reports", "reported",
        "said", "says", "phone", "number", "address", "and", "who", "is", "was", "with", "from",
        "the", "a", "an", "found", "heard", "sees", "saw", "wants", "would", "for", "of", "to",
        "service", "severity", "species", "email", "calls", "called", "located", "property"
    )

    private val PHONE = Regex("""(?<!\d)\(?(\d{3})\)?[\s.\-]?(\d{3})[\s.\-]?(\d{4})(?!\d)""")

    private val PHONE_LABEL = Regex(
        """(?:(?:phone|cell|mobile|contact)(?:\s+number)?|number)\s*(?:is|:)?\s*$""",
        RegexOption.IGNORE_CASE
    )

    // number + street words + suffix (+ route number), then an optional town: comma-separated,
    // or one or two Capitalized words. Bounded so unpunctuated speech never runs on.
    private val STREET_CORE =
        """\d{1,6}(?:\s+[A-Za-z0-9'.\-]+){1,4}?\s+(?:$STREET_SUFFIX)\b\.?(?:\s+\d{1,4}[A-Za-z]?\b)?""" +
            """(?:,\s*[A-Za-z]+(?:\s+[A-Za-z]+)?|\s+(?-i:[A-Z][a-z]+(?:\s+[A-Z][a-z]+)?))?"""

    private val ADDRESS_LABELED = Regex(
        """\b(?:(?:service|job|property|home)\s+)?address\s*(?:is|:)?\s+($STREET_CORE)""",
        RegexOption.IGNORE_CASE
    )

    private val ADDRESS_STREET = Regex("""\b($STREET_CORE)""", RegexOption.IGNORE_CASE)

    private val CUSTOMER_LABELED = Regex(
        """\b(?:customer|client|homeowner|owner)(?:'s)?(?:\s+name)?\s*(?:is|:)?\s+([A-Za-z][A-Za-z'\-]*(?:\s+[A-Za-z][A-Za-z'\-]*){0,3})""",
        RegexOption.IGNORE_CASE
    )

    private val NAME_LABELED = Regex(
        """\b(?:name|inspection\s+for|estimate\s+for|visit\s+for)\s*(?:is|:)?\s+([A-Za-z][A-Za-z'\-]*(?:\s+[A-Za-z][A-Za-z'\-]*){0,3})""",
        RegexOption.IGNORE_CASE
    )

    private val SEVERITY_LABELED = Regex(
        """\bseverity\s*(?:is|:)?\s*(none|low|moderate|medium|high|critical|severe)\b""",
        RegexOption.IGNORE_CASE
    )

    private val SEVERITY_DAMAGE = Regex(
        """\b(low|moderate|high|critical|severe)\s+(?:level\s+(?:of\s+)?)?(?:damage|severity|risk)\b""",
        RegexOption.IGNORE_CASE
    )

    fun extract(transcript: String): DictatedFields {
        val text = transcript.trim()
        if (text.isEmpty()) return DictatedFields()
        val removals = mutableListOf<IntRange>()

        val (address, addressRange) = findAddress(text)
        addressRange?.let { removals += it }

        val (phone, phoneRange) = findPhone(text)
        phoneRange?.let { removals += it }

        val (name, nameRange) = findName(text, addressRange)
        nameRange?.let { removals += it }

        val (severity, severityRange) = findSeverity(text)
        severityRange?.let { removals += it }

        return DictatedFields(
            customerName = name,
            customerPhone = phone,
            serviceAddress = address,
            severity = severity,
            remainder = removeRanges(text, removals)
        )
    }

    /** Drops sentences from [text] that repeat any of [values] (case-insensitive). */
    fun scrubSentences(text: String, values: List<String>): String {
        val needles = values.map { it.trim().lowercase() }.filter { it.length >= 4 }
        if (needles.isEmpty() || text.isBlank()) return text.trim()
        return text.split(Regex("""(?<=[.!?\n])\s+"""))
            .filter { sentence ->
                val lower = sentence.lowercase()
                needles.none { lower.contains(it) }
            }
            .joinToString(" ")
            .trim()
    }

    private fun findAddress(text: String): Pair<String, IntRange?> {
        ADDRESS_LABELED.find(text)?.let { m ->
            val value = tidyAddress(m.groupValues[1])
            if (value.isNotBlank()) return value to m.range
        }
        ADDRESS_STREET.find(text)?.let { m ->
            val value = tidyAddress(m.groupValues[1])
            if (value.isNotBlank()) return value to m.range
        }
        return "" to null
    }

    private fun tidyAddress(raw: String): String =
        raw.trim().trimEnd(',', '.', ' ').split(Regex("""\s+""")).joinToString(" ") { word ->
            if (word.isNotEmpty() && word[0].isLetter()) word.replaceFirstChar { it.uppercase() } else word
        }

    private fun findPhone(text: String): Pair<String, IntRange?> {
        val m = PHONE.find(text) ?: return "" to null
        val formatted = "${m.groupValues[1]}-${m.groupValues[2]}-${m.groupValues[3]}"
        val before = text.substring(0, m.range.first)
        val label = PHONE_LABEL.find(before)
        val start = label?.range?.first ?: m.range.first
        return formatted to (start..m.range.last)
    }

    private fun findName(text: String, addressRange: IntRange?): Pair<String, IntRange?> {
        for (regex in listOf(CUSTOMER_LABELED, NAME_LABELED)) {
            for (m in regex.findAll(text)) {
                val (name, consumedWords) = trimName(m.groupValues[1])
                if (name.isEmpty()) continue
                if (addressRange != null && m.range.first in addressRange) continue
                val group = m.groups[1] ?: continue
                val end = group.range.first + wordsLength(group.value, consumedWords) - 1
                return name to (m.range.first..end)
            }
        }
        return "" to null
    }

    private fun trimName(raw: String): Pair<String, Int> {
        val words = raw.trim().split(Regex("""\s+"""))
        val kept = mutableListOf<String>()
        for (w in words) {
            if (w.lowercase() in NAME_STOP || w.any { it.isDigit() }) break
            kept += w
        }
        val name = kept.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        return name to kept.size
    }

    private fun wordsLength(raw: String, words: Int): Int {
        var idx = 0
        var count = 0
        val parts = Regex("""\S+""").findAll(raw)
        for (p in parts) {
            count++
            idx = p.range.last + 1
            if (count == words) break
        }
        return idx
    }

    private fun findSeverity(text: String): Pair<String, IntRange?> {
        val m = SEVERITY_LABELED.find(text) ?: SEVERITY_DAMAGE.find(text) ?: return "" to null
        val level = when (m.groupValues[1].lowercase()) {
            "none" -> "NONE"
            "low" -> "LOW"
            "moderate", "medium" -> "MODERATE"
            "high", "severe" -> "HIGH"
            "critical" -> "CRITICAL"
            else -> ""
        }
        // "moderate damage" is part of the finding itself; only strip an explicit "severity is X".
        val range = if (m.value.contains("severity", ignoreCase = true) &&
            SEVERITY_LABELED.find(text) != null
        ) m.range else null
        return level to range
    }

    private fun removeRanges(text: String, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) return text.trim()
        val sorted = ranges.sortedBy { it.first }
        val sb = StringBuilder()
        var cursor = 0
        for (r in sorted) {
            if (r.first < cursor) continue
            sb.append(text, cursor, r.first)
            cursor = r.last + 1
        }
        sb.append(text, cursor.coerceAtMost(text.length), text.length)
        return sb.toString()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\s+([,.;])"""), "$1")
            .replace(Regex("""^[\s,.;]+"""), "")
            .replace(Regex("""(?:\s*[,;])+\s*(?=[.])"""), "")
            .trim()
    }
}
