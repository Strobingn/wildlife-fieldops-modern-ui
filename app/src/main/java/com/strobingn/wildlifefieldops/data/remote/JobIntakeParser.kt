package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.ai.fieldops.OperatorWins
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

object IntakeField {
    const val TITLE = "title"
    const val CUSTOMER = "customerName"
    const val ADDRESS = "address"
    const val TYPE = "type"
    const val PRIORITY = "priority"
    const val DESCRIPTION = "description"
    const val NOTES = "notes"
    const val PHONE = "phone"
    const val EMAIL = "email"
    const val CITY = "city"
    const val STATE = "state"
    const val ZIP = "zip"
    const val SPECIES = "species"
    const val STATUS = "status"
}

/**
 * Dictation → structured job fields. The rule-based fill is instant; the
 * chain is cloud AI → on-device model → rule-based parser, and must never
 * block Save.
 */
internal object JobIntakeParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    /**
     * Outer limit for the whole chain. The edge function alone takes 7–9 s
     * (grok-4 via ai-assistant, see function_edge_logs), so the old 8 s cap
     * always timed out before the cloud answered.
     */
    const val REFINE_TIMEOUT_MS = 75_000L
    const val CLOUD_TIMEOUT_MS = 25_000L
    const val LOCAL_TIMEOUT_MS = 45_000L
    private const val TAG = "DictationFill"

    const val NOTICE_LOCAL =
        "Cloud AI didn't answer, so the phone's AI filled the form. Check the fields."
    const val NOTICE_RULES =
        "AI wasn't available, so the form was filled from your words without AI. Check the fields."

    val allowedStatuses = listOf("Scheduled", "In progress", "Completed")

    private fun logI(message: String) {
        runCatching { android.util.Log.i(TAG, message) }
    }

    private fun logW(message: String) {
        runCatching { android.util.Log.w("JobIntakeParser", message) }
    }

    suspend fun parse(
        transcript: String,
        localReady: Boolean,
        generateLocal: suspend (system: String, user: String) -> String?,
        cloudConfigured: Boolean,
        completeCloud: (system: String, user: String) -> Pair<String?, String?>,
        providerLabel: String,
        localDisplayName: String,
        notConfiguredMessage: String
    ): JobIntakeResult {
        val t0 = System.currentTimeMillis()
        val heuristic = heuristicFill(transcript)
        logI("heuristic ${System.currentTimeMillis() - t0}ms")
        val refined = refine(
            transcript = transcript,
            localReady = localReady,
            generateLocal = generateLocal,
            cloudConfigured = cloudConfigured,
            completeCloud = completeCloud,
            providerLabel = providerLabel,
            localDisplayName = localDisplayName
        )
        val merged = when {
            heuristic != null && refined.draft != null ->
                merge(heuristic, refined.draft, editedFields = emptySet(), replaceable = heuristic)
            refined.draft != null -> refined.draft
            heuristic != null -> heuristic
            else -> null
        }
        return if (merged != null) {
            JobIntakeResult(
                draft = merged,
                sourceLabel = refined.sourceLabel.ifBlank {
                    if (heuristic != null) "⚙️ Local heuristic (no generative model)" else ""
                },
                notice = if (refined.draft == null) NOTICE_RULES else refined.notice
            )
        } else {
            JobIntakeResult(error = refined.error ?: notConfiguredMessage)
        }
    }

    /** Rule-based fill (last link of the chain). See [DictationJobParser]. */
    fun heuristicFill(transcript: String): JobIntakeDraft? = DictationJobParser.parse(transcript)

    fun canSave(draft: JobIntakeDraft?): Boolean {
        val d = draft ?: return false
        return d.title.isNotBlank() || d.customerName.isNotBlank() || d.address.isNotBlank()
    }

    /**
     * Fill rules. A field Sir typed or cleared ([editedFields]) never changes.
     * Otherwise an empty field takes the suggestion. When [replaceable] is
     * given (the instant rule-based fill), a field still holding exactly that
     * machine guess may be improved by the AI answer.
     */
    fun merge(
        current: JobIntakeDraft,
        incoming: JobIntakeDraft,
        editedFields: Set<String>,
        replaceable: JobIntakeDraft? = null
    ): JobIntakeDraft {
        fun pick(key: String, existing: String, suggested: String, guess: String?): String {
            if (key in editedFields) return existing
            if (guess != null && suggested.isNotBlank() && existing.isNotBlank() && existing == guess) {
                return suggested.trim()
            }
            return OperatorWins.suggest(existing, suggested, manual = false)
        }
        val r = replaceable
        return current.copy(
            title = pick(IntakeField.TITLE, current.title, incoming.title, r?.title),
            customerName = pick(IntakeField.CUSTOMER, current.customerName, incoming.customerName, r?.customerName),
            address = pick(IntakeField.ADDRESS, current.address, incoming.address, r?.address),
            type = pick(IntakeField.TYPE, current.type, incoming.type, r?.type),
            priority = pick(IntakeField.PRIORITY, current.priority, incoming.priority, r?.priority)
                .ifBlank { if (IntakeField.PRIORITY in editedFields) "" else current.priority.ifBlank { "MEDIUM" } },
            description = pick(IntakeField.DESCRIPTION, current.description, incoming.description, r?.description),
            notes = pick(IntakeField.NOTES, current.notes, incoming.notes, r?.notes),
            phone = pick(IntakeField.PHONE, current.phone, incoming.phone, r?.phone),
            email = pick(IntakeField.EMAIL, current.email, incoming.email, r?.email),
            city = pick(IntakeField.CITY, current.city, incoming.city, r?.city),
            state = pick(IntakeField.STATE, current.state, incoming.state, r?.state),
            zip = pick(IntakeField.ZIP, current.zip, incoming.zip, r?.zip),
            species = pick(IntakeField.SPECIES, current.species, incoming.species, r?.species),
            status = pick(IntakeField.STATUS, current.status, incoming.status, r?.status),
            scheduleDay = current.scheduleDay.ifBlank { incoming.scheduleDay }
        )
    }

    /**
     * Cloud AI → on-device model. Returns no draft (with [JobIntakeResult.notice])
     * when neither answered, so the caller keeps the rule-based fill.
     */
    suspend fun refine(
        transcript: String,
        localReady: Boolean,
        generateLocal: suspend (system: String, user: String) -> String?,
        cloudConfigured: Boolean,
        completeCloud: (system: String, user: String) -> Pair<String?, String?>,
        providerLabel: String,
        localDisplayName: String,
        @Suppress("UNUSED_PARAMETER") skipLocalIfCloud: Boolean = cloudConfigured,
        timeoutMs: Long = REFINE_TIMEOUT_MS,
        cloudTimeoutMs: Long = CLOUD_TIMEOUT_MS,
        localTimeoutMs: Long = LOCAL_TIMEOUT_MS
    ): JobIntakeResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val system = REFINE_SYSTEM
        val user = buildString {
            appendLine("Technician dictation:")
            appendLine(transcript.ifBlank { "(empty)" })
            appendLine()
            append("Parse into job JSON now.")
        }
        val timed = withTimeoutOrNull(timeoutMs) {
            var cloudProblem: String? = null
            if (cloudConfigured) {
                // completeCloud blocks; run it detached so the timeout really releases us.
                val call = CoroutineScope(SupervisorJob() + Dispatchers.IO).async {
                    runCatching { completeCloud(system, user) }.getOrElse { null to it.message }
                }
                val answer = withTimeoutOrNull(cloudTimeoutMs) { call.await() }
                if (answer == null) {
                    call.cancel()
                    cloudProblem = "cloud timeout after ${cloudTimeoutMs}ms"
                } else {
                    val (text, err) = answer
                    val parsed = text?.let { parseJobIntake(it) }
                    if (parsed != null) {
                        return@withTimeoutOrNull JobIntakeResult(
                            draft = parsed,
                            sourceLabel = "☁️ Cloud ($providerLabel)"
                        )
                    }
                    cloudProblem = err ?: "cloud JSON parse failed"
                }
                logI("cloud failed: $cloudProblem")
            }
            if (localReady) {
                val local = runCatching {
                    withTimeoutOrNull(localTimeoutMs) { generateLocal(system, user) }
                }.getOrNull()
                val parsed = local?.let { parseJobIntake(it) }
                if (parsed != null) {
                    return@withTimeoutOrNull JobIntakeResult(
                        draft = parsed,
                        sourceLabel = "📱 On-device ($localDisplayName)",
                        notice = if (cloudConfigured) NOTICE_LOCAL else null
                    )
                }
            }
            JobIntakeResult(error = cloudProblem, notice = NOTICE_RULES)
        }
        val elapsed = System.currentTimeMillis() - t0
        if (timed == null) {
            logI("refine timeout ${elapsed}ms limit=${timeoutMs}ms")
            return@withContext JobIntakeResult(error = "AI refine timed out", notice = NOTICE_RULES)
        }
        logI("refine ${elapsed}ms source=${timed.sourceLabel.ifBlank { "none" }}")
        timed
    }

    internal fun parseJobIntake(raw: String): JobIntakeDraft? = try {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) null
        else {
            val draft = json.decodeFromString(JobIntakeDraft.serializer(), cleaned.substring(start, end + 1))
            val pri = draft.priority.trim().uppercase().replace(' ', '_')
            val allowed = setOf("LOW", "MEDIUM", "HIGH", "URGENT")
            val status = allowedStatuses.firstOrNull { it.equals(draft.status.trim(), ignoreCase = true) }.orEmpty()
            val description = draft.description.trim()
            draft.copy(
                title = draft.title.trim(),
                customerName = draft.customerName.trim(),
                address = draft.address.trim(),
                type = draft.type.trim(),
                priority = if (pri in allowed) pri else "MEDIUM",
                // A model that echoes the whole dictation back is not a scope.
                description = if (description.length > 240) description.take(240).substringBeforeLast(' ') else description,
                notes = draft.notes.trim().take(240),
                phone = draft.phone.trim(),
                email = draft.email.trim(),
                city = draft.city.trim(),
                state = draft.state.trim(),
                zip = draft.zip.trim(),
                species = draft.species.trim(),
                status = status,
                scheduleDay = draft.scheduleDay.trim().lowercase()
            )
        }
    } catch (e: Exception) {
        logW("parseJobIntake failed: ${e.message}")
        null
    }

    private val REFINE_SYSTEM = """
You are a wildlife removal dispatcher parsing a field technician voice note into a new job.
The note comes from speech recognition: it may be all lowercase with no punctuation.
Return ONLY one JSON object with these string fields:
title, customerName, phone, email, address, city, state, zip, species, type, priority, status, scheduleDay, description, notes
- address is the street line only (e.g. "22 Oak Street"); city, state (2-letter code) and zip go in their own fields.
- phone as digits with dashes (845-555-0199). Empty string when not said.
- species is the animal (Raccoon, Bat, Squirrel...). type is a short service label (Raccoon Removal, Bat Exclusion, Inspection...).
- priority MUST be one of: LOW, MEDIUM, HIGH, URGENT.
- status MUST be one of: Scheduled, In progress, Completed (default Scheduled).
- scheduleDay: today, tomorrow, a weekday name, or empty.
- description is the scope of work in a few words (e.g. "Raccoon in the attic"). Never copy the whole note into description or notes.
- notes only for extra facts that fit no other field; otherwise empty.
- title like "Raccoon Removal — Maria Lopez".
Use empty strings for anything not said. Do not invent a phone number, email or dollar amount.
""".trimIndent()
}
