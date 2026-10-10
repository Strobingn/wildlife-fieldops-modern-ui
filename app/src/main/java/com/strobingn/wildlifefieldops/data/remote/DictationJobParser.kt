package com.strobingn.wildlifefieldops.data.remote

import java.util.Locale

/**
 * Rule-based reader for a spoken job ("new job for maria lopez at 22 oak street
 * newburgh new york 12550 phone 845 555 0199 raccoon in the attic ...").
 * Works on raw speech-recognizer text: any case, little or no punctuation.
 * Last link of the fill chain (cloud AI -> on-device model -> this), and the
 * instant first fill shown before any AI answers. Each fact goes to its own
 * field; the transcript itself is never copied into description or notes.
 */
internal object DictationJobParser {

    private val STATES = linkedMapOf(
        "new york" to "NY", "new jersey" to "NJ", "connecticut" to "CT", "pennsylvania" to "PA",
        "massachusetts" to "MA", "vermont" to "VT", "new hampshire" to "NH", "rhode island" to "RI",
        "maine" to "ME", "delaware" to "DE", "maryland" to "MD", "virginia" to "VA", "ohio" to "OH",
        "florida" to "FL", "texas" to "TX", "california" to "CA"
    )
    private val STATE_CODES = STATES.values.toSet()

    private val SPECIES = linkedMapOf(
        "raccoon" to "Raccoon", "squirrel" to "Squirrel", "bats" to "Bat", "bat" to "Bat",
        "skunk" to "Skunk", "groundhog" to "Groundhog", "woodchuck" to "Groundhog",
        "opossum" to "Opossum", "possum" to "Opossum", "mice" to "Mouse", "mouse" to "Mouse",
        "rats" to "Rat", "rat" to "Rat", "snake" to "Snake", "pigeon" to "Pigeon", "bird" to "Bird",
        "chipmunk" to "Chipmunk", "fox" to "Fox", "coyote" to "Coyote", "beaver" to "Beaver",
        "mole" to "Mole", "vole" to "Vole", "bees" to "Bee", "wasp" to "Wasp", "deer" to "Deer"
    )

    private const val SUFFIX =
        "street|st|avenue|ave|road|rd|drive|dr|lane|ln|court|ct|place|pl|boulevard|blvd|way|" +
            "terrace|ter|circle|cir|highway|hwy|turnpike|tpke|parkway|pkwy|trail|trl|run|pike|square|sq|loop"

    private val PHONE = Regex("(?<!\\d)(?:\\+?1[\\s.-]*)?\\(?(\\d{3})\\)?[\\s.-]*(\\d{3})[\\s.-]*(\\d{4})(?!\\d)")
    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val SPOKEN_EMAIL = Regex("(?i)\\b([a-z0-9._-]+)\\s+at\\s+([a-z0-9-]+)\\s+dot\\s+(com|net|org|edu|us)\\b")
    private val ZIP = Regex("(?<![\\d-])(\\d{5})(?:-\\d{4})?(?![\\d-])")
    private val STREET = Regex(
        "(?i)\\b(\\d{1,6}[a-z]?(?:\\s+[a-z0-9'.]+){1,4}?\\s+(?:$SUFFIX))\\b\\.?"
    )
    private val ROUTE = Regex("(?i)\\b(\\d{1,6}\\s+(?:state\\s+)?route\\s+\\d{1,4}[a-z]?)\\b")
    private val NAME_LEAD = Regex(
        "(?i)\\b(?:for|customer(?:'s)?(?:\\s+name)?(?:\\s+is)?|client(?:\\s+is)?|homeowner(?:\\s+is)?|name\\s+is|caller(?:\\s+is)?)\\s+" +
            "([a-z][a-z'.-]*(?:\\s+[a-z][a-z'.-]*){0,3})"
    )
    private val NAME_STOP = setOf(
        "at", "in", "on", "phone", "number", "cell", "with", "who", "has", "have", "the", "a", "an", "and",
        "address", "located", "lives", "living", "from", "tomorrow", "today", "schedule", "scheduled",
        "high", "low", "urgent", "priority", "email", "zip", "about", "of", "to", "is", "needs", "need",
        "job", "new", "service", "call", "called", "there", "theres", "there's", "his", "her", "their"
    )
    private val NAME_REJECT_FIRST = setOf(
        "tomorrow", "today", "a", "an", "the", "monday", "tuesday", "wednesday", "thursday", "friday",
        "saturday", "sunday", "next", "this", "some", "removal", "inspection", "trapping", "exclusion"
    ) + SPECIES.keys
    private val WEEKDAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

    fun parse(transcript: String): JobIntakeDraft? {
        val original = transcript.trim()
        if (original.isBlank()) return null
        var rest = " " + original.replace(Regex("\\s+"), " ") + " "

        fun cut(range: IntRange) {
            rest = rest.removeRange(range).let { it.substring(0, range.first) + " , " + it.substring(range.first) }
        }

        // Email first ("maria at gmail dot com" would otherwise look like "at <address>").
        var email = EMAIL.find(rest)?.also { cut(it.range) }?.value.orEmpty()
        if (email.isBlank()) {
            SPOKEN_EMAIL.find(rest)?.let { m ->
                email = "${m.groupValues[1]}@${m.groupValues[2]}.${m.groupValues[3]}".lowercase(Locale.US)
                cut(m.range)
            }
        }
        // Phone before ZIP so "12550 phone 845 555 0199" stays two facts.
        val phoneMatch = PHONE.find(rest)
        val phone = phoneMatch?.let { "${it.groupValues[1]}-${it.groupValues[2]}-${it.groupValues[3]}" }.orEmpty()
        phoneMatch?.let { cut(it.range) }

        val streetMatch = STREET.find(rest) ?: ROUTE.find(rest)
        val street = streetMatch?.groupValues?.get(1)?.let { titleCase(it) }.orEmpty()
        val zipMatch = ZIP.find(rest, startIndex = streetMatch?.range?.last ?: 0) ?: ZIP.find(rest)
        val zip = zipMatch?.groupValues?.get(1).orEmpty()

        // Town and state: the words between the street and the ZIP (or the next stop word).
        var city = ""
        var state = ""
        if (streetMatch != null) {
            val tailEnd = zipMatch?.range?.first?.takeIf { it > streetMatch.range.last } ?: rest.length
            val between = rest.substring(streetMatch.range.last + 1, tailEnd)
                .replace(Regex("[,.;]"), " ").trim().lowercase(Locale.US)
            var words = between.split(" ").filter { it.isNotBlank() }
            if (zipMatch == null || zipMatch.range.first < streetMatch.range.last) {
                words = words.takeWhile { it !in NAME_STOP && it !in SPECIES.keys }
            }
            val joined = words.joinToString(" ")
            val stateName = STATES.keys.firstOrNull { joined.endsWith(it) || joined.contains(" $it") || joined == it }
            if (stateName != null) {
                state = STATES.getValue(stateName)
                val idx = joined.lastIndexOf(stateName)
                words = joined.substring(0, idx).trim().split(" ").filter { it.isNotBlank() }
            } else if (words.isNotEmpty() && words.last().uppercase(Locale.US) in STATE_CODES) {
                state = words.last().uppercase(Locale.US)
                words = words.dropLast(1)
            }
            city = titleCase(words.takeLast(3).joinToString(" "))
        }
        if (state.isBlank()) {
            STATES.entries.firstOrNull { Regex("(?i)\\b${it.key}\\b").containsMatchIn(rest) && zip.isNotBlank() }
                ?.let { state = it.value }
        }

        val customer = findName(rest)
        val lower = rest.lowercase(Locale.US)
        val speciesKey = SPECIES.keys
            .mapNotNull { k -> Regex("\\b$k\\b").find(lower)?.let { k to it.range.first } }
            .minByOrNull { it.second }
        val species = speciesKey?.let { SPECIES.getValue(it.first) }.orEmpty()
        val scope = speciesKey?.let { scopeFrom(rest, it.second) }.orEmpty()

        val type = when (species) {
            "Bat" -> "Bat Exclusion"
            "Raccoon" -> "Raccoon Removal"
            "Squirrel" -> "Squirrel Removal"
            "Skunk" -> "Skunk Removal"
            "Snake" -> "Snake Removal"
            else -> when {
                "trap" in lower -> "Trapping"
                "exclu" in lower -> "Exclusion"
                "inspect" in lower -> "Inspection"
                "repair" in lower -> "Repair"
                "clean" in lower -> "Cleanup"
                species.isNotBlank() -> "$species Removal"
                else -> "Inspection"
            }
        }
        val priority = when {
            Regex("\\b(urgent|emergency|asap)\\b").containsMatchIn(lower) -> "URGENT"
            Regex("\\bhigh[ -]priority\\b|\\bpriority (is )?high\\b").containsMatchIn(lower) -> "HIGH"
            Regex("\\blow[ -]priority\\b|\\bpriority (is )?low\\b").containsMatchIn(lower) -> "LOW"
            else -> "MEDIUM"
        }
        val status = when {
            Regex("\\b(in progress|started|working on it)\\b").containsMatchIn(lower) -> "In progress"
            Regex("\\b(completed|finished|already done)\\b").containsMatchIn(lower) -> "Completed"
            else -> "Scheduled"
        }
        val day = when {
            Regex("\\btomorrow\\b").containsMatchIn(lower) -> "tomorrow"
            Regex("\\btoday\\b").containsMatchIn(lower) -> "today"
            else -> WEEKDAYS.firstOrNull { Regex("\\b$it\\b").containsMatchIn(lower) }.orEmpty()
        }
        val title = buildString {
            append(type)
            when {
                customer.isNotBlank() -> append(" — ").append(customer)
                street.isNotBlank() -> append(" — ").append(street)
            }
        }
        return JobIntakeDraft(
            title = title,
            customerName = customer,
            address = street,
            type = type,
            priority = priority,
            description = scope,
            notes = "",
            phone = phone,
            email = email,
            city = city,
            state = state,
            zip = zip,
            species = species,
            status = status,
            scheduleDay = day
        )
    }

    private fun findName(text: String): String {
        for (m in NAME_LEAD.findAll(text)) {
            val words = m.groupValues[1].split(" ").filter { it.isNotBlank() }
            if (words.isEmpty() || words.first().lowercase(Locale.US) in NAME_REJECT_FIRST) continue
            val kept = words.takeWhile { w ->
                val lw = w.lowercase(Locale.US).trim('.', ',')
                lw !in NAME_STOP && lw !in SPECIES.keys && lw !in WEEKDAYS
            }.take(3)
            if (kept.isNotEmpty()) return titleCase(kept.joinToString(" "))
        }
        return ""
    }

    /** "raccoon in the attic" — from the animal to the next separate fact. */
    private fun scopeFrom(text: String, start: Int): String {
        val tail = text.substring(start)
        val stop = Regex(
            "(?i)[,.;!?]|\\b(schedule|scheduled|tomorrow|today|high priority|low priority|urgent|" +
                "emergency|asap|priority|phone|call|email|customer|status|for [a-z]+ [a-z]+ at)\\b"
        ).find(tail)?.range?.first ?: tail.length
        val raw = tail.substring(0, stop).trim()
        val clipped = raw.split(" ").take(12).joinToString(" ")
        return clipped.replaceFirstChar { it.titlecase(Locale.US) }
    }

    private fun titleCase(s: String): String = s.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .joinToString(" ") { w ->
            if (w.length <= 3 && w.uppercase(Locale.US) == w && w.any { it.isLetter() }) w
            else w.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
        }

    /** Start of the spoken day at 9:00 local, or null. */
    fun scheduleMillis(day: String, now: java.util.Calendar = java.util.Calendar.getInstance()): Long? {
        val d = day.trim().lowercase(Locale.US)
        if (d.isBlank()) return null
        val cal = now.clone() as java.util.Calendar
        when (d) {
            "today" -> Unit
            "tomorrow" -> cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
            else -> {
                val idx = WEEKDAYS.indexOf(d).takeIf { it >= 0 } ?: return null
                val target = (idx + 1) % 7 + 1 // Calendar: SUNDAY=1 ... SATURDAY=7
                var add = (target - cal.get(java.util.Calendar.DAY_OF_WEEK) + 7) % 7
                if (add == 0) add = 7
                cal.add(java.util.Calendar.DAY_OF_YEAR, add)
            }
        }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
