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
 *
 * Speech recognition rarely adds punctuation or capitals, so nothing here relies on either:
 * values end at the first word that is clearly part of something else (a species, an entry
 * point, a label like "phone", a connector like "in the").
 */
object DictationFieldExtractor {

    private val STREET_SUFFIX =
        "street|st|avenue|ave|road|rd|drive|dr|lane|ln|court|ct|boulevard|blvd|way|place|pl|" +
            "circle|cir|highway|hwy|route|rt|trail|trl|terrace|ter|parkway|pkwy"

    /** Words that end a name or town because they start the next thought. */
    private val STOP_WORDS = setOf(
        "at", "on", "in", "lives", "live", "called", "calls", "has", "have", "had", "reports",
        "reported", "said", "says", "phone", "number", "address", "and", "who", "is", "was", "are",
        "with", "from", "the", "a", "an", "found", "heard", "hears", "sees", "saw", "wants",
        "would", "for", "of", "to", "service", "severity", "species", "email", "located",
        "property", "there", "their", "they", "he", "she", "it", "complained", "complains",
        "noticed", "notices", "mentioned", "thinks", "believes", "needs", "cell", "mobile",
        "customer", "client", "homeowner", "owner", "name", "inspection", "estimate", "visit",
        "no", "not", "some", "something", "around", "near", "by", "about", "over", "under", "into"
    )

    /** Field-work vocabulary; none of these words can be part of a name or a town. */
    private val VOCAB_PREFIXES = listOf(
        "raccoon", "squirrel", "skunk", "opossum", "possum", "groundhog", "woodchuck", "chipmunk",
        "mouse", "mice", "rodent", "snake", "pigeon", "bird", "wasp", "bee", "hornet", "coyote",
        "fox", "deer", "beaver", "muskrat", "weasel",
        "attic", "roof", "soffit", "fascia", "chimney", "vent", "basement", "crawl", "deck",
        "garage", "shed", "gable", "louver", "damper", "foundation", "siding", "gutter",
        "noise", "sound", "droppings", "damage", "chew", "gnaw", "insulation", "nest", "urine",
        "guano", "smell", "odor", "hole", "gap", "entry", "trap", "bait", "exclusion", "wire"
    )
    private val VOCAB_EXACT = setOf("bat", "bats", "rat", "rats", "den", "wall", "walls", "floor")

    private fun isVocab(word: String): Boolean {
        val w = word.lowercase().trim('.', ',', ';')
        return w in VOCAB_EXACT || VOCAB_PREFIXES.any { w.startsWith(it) }
    }

    /** Whole-word vocabulary match, so a street like "Beech" or "Fisher" is not mistaken for a thought. */
    private fun isVocabStrict(word: String): Boolean {
        val w = word.lowercase().trim('.', ',', ';')
        return w in VOCAB_EXACT || VOCAB_PREFIXES.any { w == it || w == it + "s" || w == it + "es" }
    }

    private fun endsTheThought(word: String): Boolean {
        val w = word.lowercase().trim('.', ',', ';')
        return w.isEmpty() || w in STOP_WORDS || w.any { it.isDigit() } || isVocab(w)
    }

    private val PHONE = Regex("""(?<!\d)\(?(\d{3})\)?[\s.\-]?(\d{3})[\s.\-]?(\d{4})(?!\d)""")

    private val PHONE_LABEL = Regex(
        """(?:(?:phone|cell|mobile|contact)(?:\s+number)?|number)\s*(?:is|:)?\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val STREET_CORE =
        """\d{1,6}((?:\s+[A-Za-z0-9'.\-]+){1,4}?)\s+(?:$STREET_SUFFIX)\b\.?(?:\s+\d{1,4}[A-Za-z]?\b)?"""

    private val ADDRESS_LABELED = Regex(
        """\b(?:(?:service|job|property|home)\s+)?address\s*(?:is|:)?\s+($STREET_CORE)""",
        RegexOption.IGNORE_CASE
    )

    private val ADDRESS_STREET = Regex("""\b($STREET_CORE)""", RegexOption.IGNORE_CASE)

    /** "located at", "lives at", "at", "on" right before an address belong to it. */
    private val ADDRESS_LEAD_IN = Regex(
        """(?:(?:located|lives|live|living|property)\s+)?(?:at|on)\s+$""",
        RegexOption.IGNORE_CASE
    )

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

    private val WORD = Regex("""\S+""")

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

    /**
     * Removes the spoken [values] (and the "customer is" / "address is" cue right before each)
     * from [text]. With real sentence breaks, whole sentences that repeat a value are dropped;
     * dictation is usually one unpunctuated run, so then only the value and its cue go.
     */
    fun scrubSentences(text: String, values: List<String>): String {
        val needles = values.map { it.trim() }.filter { it.length >= 4 }
        if (needles.isEmpty() || text.isBlank()) return text.trim()
        val sentences = text.split(Regex("""(?<=[.!?\n])\s+""")).filter { it.isNotBlank() }
        if (sentences.size > 1) {
            return sentences.filter { s -> needles.none { s.contains(it, ignoreCase = true) } }
                .joinToString(" ")
                .trim()
        }
        var out = text
        for (needle in needles) {
            val cue = """(?:(?:customer|client|homeowner|owner)(?:'s)?(?:\s+name)?|name|""" +
                """(?:service\s+|job\s+|property\s+|home\s+)?address|phone(?:\s+number)?|cell|number)\s*(?:is|:)?\s*"""
            out = Regex("""(?:$cue)?${Regex.escape(needle)}""", RegexOption.IGNORE_CASE).replace(out, " ")
        }
        return out.replace(Regex("""\s+"""), " ").trim()
    }

    private fun findAddress(text: String): Pair<String, IntRange?> {
        ADDRESS_LABELED.find(text)?.let { m ->
            return buildAddress(text, m.groups[1]!!.range)
        }
        for (m in ADDRESS_STREET.findAll(text)) {
            val middle = m.groups[2]?.value.orEmpty().trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
            // "3 holes near the drive" is not an address: street names are short and plain.
            val plain = middle.none { w ->
                val bare = w.lowercase().trim('.', ',', ';')
                bare in STOP_WORDS || isVocabStrict(bare)
            }
            if (middle.size in 1..3 && plain) {
                return buildAddress(text, m.groups[1]!!.range)
            }
        }
        return "" to null
    }

    private fun buildAddress(text: String, core: IntRange): Pair<String, IntRange?> {
        var end = core.last
        var cursor = end + 1
        // Optional town: a comma, or up to two plain words straight after the street.
        val tail = text.substring(cursor)
        val lead = Regex("""^\s*,""").find(tail)?.value?.length ?: 0
        val townWords = mutableListOf<String>()
        for (m in WORD.findAll(tail.substring(lead))) {
            val word = m.value
            if (townWords.size == 2 || endsTheThought(word)) break
            townWords += word.trim(',', '.')
            end = cursor + lead + m.range.last
            if (word.endsWith(",") || word.endsWith(".")) break
        }
        // Drop a trailing comma from what we keep as the value.
        val raw = text.substring(core.first, end + 1)
        var start = core.first
        ADDRESS_LEAD_IN.find(text.substring(0, core.first))?.let { start = it.range.first }
        // Keep the label ("address is") out of the removed span only if it is there; include it.
        val labelStart = Regex("""(?:(?:service|job|property|home)\s+)?address\s*(?:is|:)?\s*$""", RegexOption.IGNORE_CASE)
            .find(text.substring(0, core.first))
        if (labelStart != null) start = labelStart.range.first
        return tidyAddress(raw) to (start..end)
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
                if (addressRange != null && m.range.first in addressRange) continue
                val group = m.groups[1] ?: continue
                val words = WORD.findAll(group.value).toList()
                val kept = mutableListOf<MatchResult>()
                for (w in words) {
                    if (kept.size == 3 || endsTheThought(w.value)) break
                    kept += w
                }
                if (kept.isEmpty()) continue
                val name = kept.joinToString(" ") { titleCase(it.value) }
                val end = group.range.first + kept.last().range.last
                return name to (m.range.first..end)
            }
        }
        return "" to null
    }

    private fun titleCase(word: String): String {
        val sb = StringBuilder()
        var up = true
        for (c in word) {
            sb.append(if (up) c.uppercaseChar() else c.lowercaseChar())
            up = c == '-' || (c == '\'' && sb.length == 2)
        }
        return sb.toString()
    }

    private fun findSeverity(text: String): Pair<String, IntRange?> {
        val labeled = SEVERITY_LABELED.find(text)
        val m = labeled ?: SEVERITY_DAMAGE.find(text) ?: return "" to null
        val level = when (m.groupValues[1].lowercase()) {
            "none" -> "NONE"
            "low" -> "LOW"
            "moderate", "medium" -> "MODERATE"
            "high", "severe" -> "HIGH"
            "critical" -> "CRITICAL"
            else -> ""
        }
        // "moderate damage" is part of the finding itself; only strip an explicit "severity is X".
        return level to labeled?.range
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
            .trim()
    }
}
