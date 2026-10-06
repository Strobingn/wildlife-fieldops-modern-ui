package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.ai.fieldops.DuplicateCustomer
import com.strobingn.wildlifefieldops.ai.fieldops.OperatorWins
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.JobCustomerDraft
import com.strobingn.wildlifefieldops.data.model.JobPriority
import kotlin.math.abs

/**
 * Offline parse of a customer text. Heuristic only: no network, no model.
 * [fillEmpty] is the rule for later AI suggestions and for paste onto a form
 * Sir has already started typing.
 */
object TextMessageImport {
    const val NAME = "name"
    const val PHONE = "phone"
    const val EMAIL = "email"
    const val STREET = "street"
    const val CITY = "city"
    const val STATE = "state"
    const val ZIP = "zip"
    const val ANIMAL = "animal"
    const val PROBLEM = "problem"
    const val TITLE = "title"
    const val TYPE = "type"
    const val JOB_NOTES = "jobNotes"
    const val CUSTOMER_NOTES = "customerNotes"
    const val FIRST = "firstName"
    const val LAST = "lastName"

    data class Fields(
        val name: String = "",
        val phone: String = "",
        val email: String = "",
        val street: String = "",
        val city: String = "",
        val state: String = "",
        val zip: String = "",
        val animal: String = "",
        val problem: String = "",
        val jobTitle: String = "",
        val serviceType: String = "",
        val notes: String = "",
        val priority: String = "",
        val sourceText: String = ""
    )

    data class JobSnapshot(
        val title: String = "",
        val description: String = "",
        val notes: String = "",
        val species: String = "",
        val serviceType: String = "",
        val serviceTouched: Boolean = false,
        val priority: JobPriority = JobPriority.MEDIUM,
        val priorityTouched: Boolean = false,
        val customer: JobCustomerDraft = JobCustomerDraft(),
        val manual: Set<String> = emptySet(),
        val linked: Boolean = false
    )

    data class CustomerSnapshot(
        val firstName: String = "",
        val lastName: String = "",
        val phone: String = "",
        val email: String = "",
        val address: String = "",
        val city: String = "",
        val state: String = "",
        val zip: String = "",
        val notes: String = "",
        val manual: Set<String> = emptySet()
    )

    fun parse(raw: String, senderPhone: String? = null): Fields {
        val original = raw.replace('\u00A0', ' ').replace(Regex("[\\t\\r]+"), " ").trim()
        if (original.isBlank() && senderPhone.isNullOrBlank()) return Fields()

        val email = EMAIL_REGEX.find(original)?.value.orEmpty()
        val bodyPhones = PHONE_REGEX.findAll(original).map { it.value }.toList()
        val phone = bodyPhones.firstOrNull()?.let(::formatPhone).orEmpty()
            .ifBlank { formatPhone(senderPhone.orEmpty()) }

        var working = original
        if (email.isNotBlank()) working = working.replace(email, " ")
        bodyPhones.forEach { working = working.replace(it, " ") }

        val zipMatch = ZIP_REGEX.find(working)
        val zip = zipMatch?.groupValues?.getOrNull(1).orEmpty()
        val stateFound = findState(working, zipMatch?.range?.first)
        val streetMatch = STREET_REGEX.find(working)
        val street = streetMatch?.value?.let(::tidyStreet).orEmpty()
        val city = tidyCity(
            extractCity(
                text = working,
                street = streetMatch?.range,
                state = stateFound?.second
            )
        )
        val state = stateFound?.first.orEmpty()
        val name = findName(original)
        val animalHit = findAnimal(original)
        val problem = findProblem(original, animalHit?.first.orEmpty(), email, bodyPhones, streetMatch?.value)
        val serviceType = animalHit?.third
            ?: JobIntakeParser.heuristicFill(original)?.type?.takeIf { animalHit == null && it != "Inspection" }
            .orEmpty()
        val animal = animalHit?.second.orEmpty()
        val priority = when {
            URGENT_REGEX.containsMatchIn(original) -> "URGENT"
            HIGH_REGEX.containsMatchIn(original) -> "HIGH"
            else -> ""
        }
        val title = buildTitle(serviceType, name, problem, street)
        return Fields(
            name = name,
            phone = phone,
            email = email,
            street = street,
            city = city,
            state = state,
            zip = zip,
            animal = animal,
            problem = problem,
            jobTitle = title,
            serviceType = serviceType,
            notes = "",
            priority = priority,
            sourceText = original
        )
    }

    fun joinBodies(parts: List<String>): String =
        parts.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    /**
     * Copy [incoming] into blank fields only. Keys in [manual] stay as they
     * are, including when Sir cleared them.
     */
    fun fillEmpty(current: Fields, incoming: Fields, manual: Set<String> = emptySet()): Fields {
        fun pick(key: String, existing: String, suggested: String): String =
            OperatorWins.suggest(existing, suggested, manual = key in manual)
        return current.copy(
            name = pick(NAME, current.name, incoming.name),
            phone = pick(PHONE, current.phone, incoming.phone),
            email = pick(EMAIL, current.email, incoming.email),
            street = pick(STREET, current.street, incoming.street),
            city = pick(CITY, current.city, incoming.city),
            state = pick(STATE, current.state, incoming.state),
            zip = pick(ZIP, current.zip, incoming.zip),
            animal = pick(ANIMAL, current.animal, incoming.animal),
            problem = pick(PROBLEM, current.problem, incoming.problem),
            jobTitle = pick(TITLE, current.jobTitle, incoming.jobTitle),
            serviceType = pick(TYPE, current.serviceType, incoming.serviceType),
            notes = pick(JOB_NOTES, current.notes, incoming.notes),
            priority = pick("priority", current.priority, incoming.priority),
            sourceText = current.sourceText.ifBlank { incoming.sourceText }
        )
    }

    fun fromIntake(draft: JobIntakeDraft?): Fields {
        if (draft == null) return Fields()
        val address = parse(draft.address)
        return Fields(
            name = draft.customerName.trim(),
            street = address.street.ifBlank { draft.address.trim() },
            city = address.city,
            state = address.state,
            zip = address.zip,
            problem = draft.description.trim(),
            jobTitle = draft.title.trim(),
            serviceType = draft.type.trim(),
            notes = draft.notes.trim(),
            animal = address.animal,
            priority = draft.priority.trim().takeIf { it.equals("URGENT", true) || it.equals("HIGH", true) }.orEmpty()
        )
    }

    fun applyToJob(current: JobSnapshot, incoming: Fields): JobSnapshot {
        fun pick(key: String, existing: String, suggested: String): String =
            OperatorWins.suggest(existing, suggested, manual = key in current.manual)
        val keepCustomer = current.linked || current.customer.customerId.isNotBlank()
        val customer = if (keepCustomer) {
            current.customer
        } else {
            current.customer.copy(
                name = pick(NAME, current.customer.name, incoming.name),
                phone = pick(PHONE, current.customer.phone, incoming.phone),
                email = pick(EMAIL, current.customer.email, incoming.email),
                address = pick(STREET, current.customer.address, incoming.street),
                city = pick(CITY, current.customer.city, incoming.city),
                state = pick(STATE, current.customer.state, incoming.state),
                zipCode = pick(ZIP, current.customer.zipCode, incoming.zip),
                notes = pick(CUSTOMER_NOTES, current.customer.notes, incoming.notes)
            )
        }
        val service = when {
            current.serviceTouched -> current.serviceType
            incoming.serviceType.isBlank() -> current.serviceType
            current.serviceType.isBlank() || current.serviceType.equals("Inspection", true) -> incoming.serviceType
            else -> current.serviceType
        }
        val priority = when {
            current.priorityTouched || incoming.priority.isBlank() -> current.priority
            incoming.priority.equals("URGENT", true) -> JobPriority.URGENT
            incoming.priority.equals("HIGH", true) -> JobPriority.HIGH
            else -> current.priority
        }
        return current.copy(
            title = pick(TITLE, current.title, incoming.jobTitle),
            description = pick(PROBLEM, current.description, incoming.problem),
            notes = pick(JOB_NOTES, current.notes, incoming.notes),
            species = pick(ANIMAL, current.species, incoming.animal),
            serviceType = service,
            priority = priority,
            customer = customer
        )
    }

    fun applyToCustomer(current: CustomerSnapshot, incoming: Fields): CustomerSnapshot {
        fun pick(key: String, existing: String, suggested: String): String =
            OperatorWins.suggest(existing, suggested, manual = key in current.manual)
        val (first, last) = JobCustomerDraft.parsePersonName(incoming.name)
        val firstName = if (FIRST in current.manual || LAST in current.manual) {
            current.firstName
        } else {
            pick(FIRST, current.firstName, first)
        }
        val lastName = if (FIRST in current.manual || LAST in current.manual) {
            current.lastName
        } else {
            pick(LAST, current.lastName, last)
        }
        return current.copy(
            firstName = firstName,
            lastName = lastName,
            phone = pick(PHONE, current.phone, incoming.phone),
            email = pick(EMAIL, current.email, incoming.email),
            address = pick(STREET, current.address, incoming.street),
            city = pick(CITY, current.city, incoming.city),
            state = pick(STATE, current.state, incoming.state),
            zip = pick(ZIP, current.zip, incoming.zip),
            notes = pick(CUSTOMER_NOTES, current.notes, incoming.problem)
        )
    }

    fun markEdited(manual: Set<String>, key: String): Set<String> = manual + key

    fun markCustomerEdits(manual: Set<String>, before: JobCustomerDraft, after: JobCustomerDraft): Set<String> {
        var next = manual
        if (before.name != after.name) next = next + NAME
        if (before.phone != after.phone) next = next + PHONE
        if (before.email != after.email) next = next + EMAIL
        if (before.address != after.address) next = next + STREET
        if (before.city != after.city) next = next + CITY
        if (before.state != after.state) next = next + STATE
        if (before.zipCode != after.zipCode) next = next + ZIP
        if (before.notes != after.notes) next = next + CUSTOMER_NOTES
        return next
    }

    fun matchingCustomers(customers: List<Customer>, fields: Fields): List<Customer> {
        val phone = digits(fields.phone).let { if (it.length == 11 && it.startsWith("1")) it.drop(1) else it }
        val street = normalizeStreet(fields.street)
        val zip = fields.zip.trim().take(5)
        if (phone.length < 10 && street.length < 6) return emptyList()
        return customers.filter { customer ->
            if (!customer.isActive) return@filter false
            val samePhone = phone.length >= 10 && digits(customer.phone).let { raw ->
                val ten = if (raw.length == 11 && raw.startsWith("1")) raw.drop(1) else raw
                ten.length >= 10 && ten.takeLast(10) == phone.takeLast(10)
            }
            val theirs = normalizeStreet(customer.address)
            val sameStreet = street.length >= 6 && theirs.length >= 6 &&
                (theirs == street || theirs.startsWith(street) || street.startsWith(theirs))
            val zipOk = zip.isBlank() || customer.zipCode.isBlank() ||
                customer.zipCode.trim().startsWith(zip)
            samePhone || (sameStreet && zipOk)
        }
    }

    fun formatPhone(raw: String): String {
        val digits = digits(raw).let { if (it.length == 11 && it.startsWith("1")) it.drop(1) else it }
        if (digits.length != 10) return ""
        return "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6)}"
    }

    fun looksLikePhone(raw: String): Boolean = formatPhone(raw).isNotBlank()

    private fun digits(raw: String): String = DuplicateCustomer.digits(raw)

    private fun buildTitle(serviceType: String, name: String, problem: String, street: String): String {
        val service = serviceType.ifBlank {
            if (problem.isNotBlank() || street.isNotBlank() || name.isNotBlank()) "Inspection" else ""
        }
        if (service.isBlank()) return ""
        return if (name.isNotBlank()) "$service — $name" else service
    }

    private fun findName(text: String): String {
        val patterns = listOf(FROM_NAME, NAME_INTRO)
        for (pattern in patterns) {
            val match = pattern.find(text) ?: continue
            val trimmed = trimName(match.groupValues.getOrNull(1).orEmpty())
            if (trimmed.isNotBlank()) return trimmed
        }
        return ""
    }

    private fun trimName(captured: String): String {
        val words = captured.trim().split(Regex("\\s+"))
            .map { it.trim('.', ',', '!', ':', ';') }
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return ""
        val cut = words.indexOfFirst { it.lowercase().trim('.') in NAME_STOP }
        val kept = (if (cut >= 0) words.take(cut) else words).filter { it.any(Char::isLetter) }
        if (kept.isEmpty() || kept.size > 4) return ""
        if (kept.first().lowercase() in NAME_STOP) return ""
        if (kept.any { it.any(Char::isDigit) }) return ""
        return titleCaseName(kept.joinToString(" "))
    }

    private fun findAnimal(text: String): Triple<String, String, String>? {
        val lower = text.lowercase()
        return ANIMALS.firstOrNull { (key, _) ->
            Regex("""\b${Regex.escape(key)}s?\b""").containsMatchIn(lower)
        }?.let { (key, pair) -> Triple(key, pair.first, pair.second) }
    }

    private fun findProblem(
        original: String,
        animalKey: String,
        email: String,
        phones: List<String>,
        street: String?
    ): String {
        val chunks = original.split(Regex("[\\n.!;]+|,(?=\\s)"))
            .map { it.trim().trim(',', ' ') }
            .filter { it.length >= 4 }
        val hit = if (animalKey.isNotBlank()) {
            chunks.filter { it.lowercase().contains(animalKey) }.minByOrNull { it.length }
        } else {
            null
        }
        val fallback = if (animalKey.isBlank()) {
            chunks.lastOrNull { chunk ->
                chunk.length >= 12 && chunk.lowercase() !in GREETINGS
            }
        } else {
            null
        }
        val chosenText = hit ?: fallback ?: return ""
        if (chosenText.isBlank()) return ""
        var cleaned = chosenText
        if (email.isNotBlank()) cleaned = cleaned.replace(email, " ")
        phones.forEach { phone -> cleaned = cleaned.replace(phone, " ") }
        val streetText = street?.takeIf { it.isNotBlank() }
        if (streetText != null) cleaned = cleaned.replace(streetText, " ")
        cleaned = cleaned.replace(Regex("\\s+"), " ").trim(' ', ',', '.', ';', ':')
        if (cleaned.length < 4) return ""
        if (PHONE_REGEX.containsMatchIn(cleaned) && animalKey.isBlank()) return ""
        return cleaned.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }.take(240)
    }

    private fun extractCity(text: String, street: IntRange?, state: IntRange?): String {
        if (state == null) return ""
        val raw = if (street != null && state.first > street.last) {
            text.substring(street.last + 1, state.first)
        } else {
            val before = text.substring(0, state.first)
            before.trim().trim(',').split(Regex("\\s+")).takeLast(3).joinToString(" ")
        }
        return raw
    }

    private fun findState(text: String, zipAt: Int?): Pair<String, IntRange>? {
        val hits = mutableListOf<Pair<String, IntRange>>()
        STATE_NAMES.forEach { (name, code) ->
            Regex("""\b${Regex.escape(name)}\b""").findAll(text.lowercase()).forEach { match ->
                hits += code to match.range
            }
        }
        Regex("""(?i)\b([A-Za-z]{2})\s+\d{5}\b""").findAll(text).forEach { match ->
            val code = match.groupValues[1].uppercase()
            if (code in STATE_CODES) hits += code to match.groups[1]!!.range
        }
        if (hits.isEmpty()) return null
        if (zipAt != null) {
            return hits.minByOrNull { abs(it.second.last - zipAt) }
        }
        return hits.maxByOrNull { it.second.first }
    }

    private fun tidyStreet(raw: String): String =
        raw.trim().trimEnd('.', ',').split(Regex("\\s+")).joinToString(" ") { word ->
            val lower = word.lowercase().trimEnd('.')
            STREET_DISPLAY[lower] ?: titleWord(word)
        }

    private fun tidyCity(raw: String): String {
        val cleaned = raw
            .replace(Regex("""(?i)\b(at|in|address|is|the)\b"""), " ")
            .replace(Regex("""[^A-Za-z\s.'\-]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim(' ', ',', '.')
        if (cleaned.length < 2) return ""
        val lower = cleaned.lowercase()
        if (lower in STREET_DISPLAY || lower in NAME_STOP) return ""
        return titleCaseName(cleaned)
    }

    private fun titleCaseName(raw: String): String =
        raw.trim().split(Regex("\\s+")).joinToString(" ") { word ->
            word.split(Regex("(?<=['’])")).joinToString("") { part -> titleWord(part) }
        }

    private fun titleWord(word: String): String =
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private fun normalizeStreet(raw: String): String {
        val n = DuplicateCustomer.normalize(raw).replace(".", "").replace(",", "")
        return n.split(" ").joinToString(" ") { STREET_NORM[it] ?: it }
    }

    private val EMAIL_REGEX = Regex("""[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}""", RegexOption.IGNORE_CASE)
    private val PHONE_REGEX = Regex(
        """(?:\+?1[\s.\-]?)?\(?\d{3}\)?[\s.\-]?\d{3}[\s.\-]?\d{4}"""
    )
    private val ZIP_REGEX = Regex("""\b(\d{5})(?:-\d{4})?\b""")
    private val STREET_REGEX = Regex(
        """(?i)\b\d{1,6}\s+(?:[A-Za-z0-9#'./\-]+\s+){0,5}(?:street|st|avenue|ave|road|rd|drive|dr|lane|ln|boulevard|blvd|court|ct|way|place|pl|terrace|ter|circle|cir|highway|hwy|parkway|pkwy|trail|trl|pike|alley|aly)\.?(?:\s+(?:apt|apartment|unit|ste|suite|#)\s*[A-Za-z0-9\-]+)?"""
    )
    private val NAME_INTRO = Regex(
        """(?i)(?:^|[\s,;])(?:(?:hi|hey|hello|yo|dear)[\s,!]*)?(?:this is|i'm|i am|it's|its|my name is|name is|name:|name)\s+([A-Za-z][A-Za-z'`’\-]*(?:\s+[A-Za-z][A-Za-z'`’\-]*){0,3})"""
    )
    private val FROM_NAME = Regex(
        """(?i)(?:^|\n)\s*from:\s*([A-Za-z][A-Za-z'`’\-]+(?:\s+[A-Za-z][A-Za-z'`’\-]+){0,2})"""
    )
    private val GREETINGS = setOf("hi", "hey", "hello", "yo", "thanks", "thank you", "ok", "okay")
    private val URGENT_REGEX = Regex("""(?i)\b(urgent|emergency|asap)\b""")
    private val HIGH_REGEX = Regex("""(?i)\bhigh priority\b""")

    private val NAME_STOP = setOf(
        "the", "and", "in", "at", "on", "my", "our", "attic", "please", "call", "phone", "phn", "address",
        "from", "raccoon", "raccoons", "racoon", "racoons", "squirrel", "squirrels", "skunk",
        "skunks", "snake", "snakes", "bat", "bats", "mouse", "mice", "rat", "rats", "opossum",
        "possum", "groundhog", "woodchuck", "chipmunk", "beaver", "pigeon", "woodpecker",
        "bird", "birds", "wasp", "hornet", "bees", "bee", "with", "about", "need", "needs"
    )

    private val STREET_DISPLAY = mapOf(
        "street" to "Street", "st" to "St",
        "avenue" to "Avenue", "ave" to "Ave",
        "road" to "Road", "rd" to "Rd",
        "drive" to "Drive", "dr" to "Dr",
        "lane" to "Lane", "ln" to "Ln",
        "boulevard" to "Boulevard", "blvd" to "Blvd",
        "court" to "Court", "ct" to "Ct",
        "way" to "Way", "place" to "Place", "pl" to "Pl",
        "terrace" to "Terrace", "ter" to "Ter",
        "circle" to "Circle", "cir" to "Cir",
        "highway" to "Highway", "hwy" to "Hwy",
        "parkway" to "Parkway", "pkwy" to "Pkwy",
        "trail" to "Trail", "trl" to "Trl",
        "pike" to "Pike", "alley" to "Alley", "aly" to "Aly",
        "apt" to "Apt", "apartment" to "Apt", "unit" to "Unit", "suite" to "Suite", "ste" to "Ste"
    )

    private val STREET_NORM = mapOf(
        "street" to "st", "st" to "st",
        "avenue" to "ave", "ave" to "ave",
        "road" to "rd", "rd" to "rd",
        "drive" to "dr", "dr" to "dr",
        "lane" to "ln", "ln" to "ln",
        "boulevard" to "blvd", "blvd" to "blvd",
        "court" to "ct", "ct" to "ct",
        "place" to "pl", "pl" to "pl",
        "terrace" to "ter", "ter" to "ter",
        "circle" to "cir", "cir" to "cir",
        "highway" to "hwy", "hwy" to "hwy",
        "parkway" to "pkwy", "pkwy" to "pkwy"
    )

    /** Longer keys first so "feral cat" wins over a shorter token. */
    private val ANIMALS = listOf(
        "feral cat" to ("Feral cat" to "Removal"),
        "woodpecker" to ("Woodpecker" to "Bird Control"),
        "yellowjacket" to ("Yellowjacket" to "Removal"),
        "groundhog" to ("Groundhog" to "Removal"),
        "woodchuck" to ("Woodchuck" to "Removal"),
        "chipmunk" to ("Chipmunk" to "Removal"),
        "opossum" to ("Opossum" to "Removal"),
        "raccoon" to ("Raccoon" to "Raccoon Removal"),
        "racoon" to ("Raccoon" to "Raccoon Removal"),
        "squirrel" to ("Squirrel" to "Squirrel Removal"),
        "skunk" to ("Skunk" to "Skunk Removal"),
        "snake" to ("Snake" to "Snake Removal"),
        "pigeon" to ("Pigeon" to "Bird Control"),
        "possum" to ("Opossum" to "Removal"),
        "hornet" to ("Hornet" to "Removal"),
        "beaver" to ("Beaver" to "Removal"),
        "coyote" to ("Coyote" to "Removal"),
        "mouse" to ("Mouse" to "Removal"),
        "birds" to ("Bird" to "Bird Control"),
        "bird" to ("Bird" to "Bird Control"),
        "bats" to ("Bat" to "Bat Exclusion"),
        "bat" to ("Bat" to "Bat Exclusion"),
        "mice" to ("Mouse" to "Removal"),
        "rats" to ("Rat" to "Removal"),
        "rat" to ("Rat" to "Removal"),
        "mole" to ("Mole" to "Removal"),
        "vole" to ("Vole" to "Removal"),
        "wasp" to ("Wasp" to "Removal"),
        "bees" to ("Bee" to "Removal"),
        "bee" to ("Bee" to "Removal"),
        "fox" to ("Fox" to "Removal")
    )

    private val STATE_NAMES = listOf(
        "new york" to "NY",
        "new jersey" to "NJ",
        "pennsylvania" to "PA",
        "connecticut" to "CT",
        "massachusetts" to "MA",
        "vermont" to "VT",
        "new hampshire" to "NH",
        "rhode island" to "RI"
    )

    private val STATE_CODES = setOf(
        "AL", "AK", "AZ", "AR", "CA", "CO", "CT", "DE", "FL", "GA",
        "HI", "ID", "IL", "IN", "IA", "KS", "KY", "LA", "ME", "MD",
        "MA", "MI", "MN", "MS", "MO", "MT", "NE", "NV", "NH", "NJ",
        "NM", "NY", "NC", "ND", "OH", "OK", "OR", "PA", "RI", "SC",
        "SD", "TN", "TX", "UT", "VT", "VA", "WA", "WV", "WI", "WY", "DC"
    )
}
