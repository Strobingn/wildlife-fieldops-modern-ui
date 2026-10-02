package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.ai.fieldops.OperatorWins
import kotlinx.coroutines.Dispatchers
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
}

/**
 * Dictation → structured job fields. Heuristic fill is instant; cloud/local
 * refine is optional and must never block Save.
 */
internal object JobIntakeParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    const val REFINE_TIMEOUT_MS = 8_000L
    private const val TAG = "DictationFill"

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
            localDisplayName = localDisplayName,
            skipLocalIfCloud = cloudConfigured
        )
        val merged = when {
            heuristic != null && refined.draft != null ->
                merge(heuristic, refined.draft, editedFields = emptySet())
            refined.draft != null -> refined.draft
            heuristic != null -> heuristic
            else -> null
        }
        return if (merged != null) {
            JobIntakeResult(
                draft = merged,
                sourceLabel = refined.sourceLabel.ifBlank {
                    if (heuristic != null) "⚙️ Local heuristic (no generative model)" else ""
                }
            )
        } else {
            JobIntakeResult(error = refined.error ?: notConfiguredMessage)
        }
    }

    fun heuristicFill(transcript: String): JobIntakeDraft? {
        val text = transcript.trim()
        if (text.isBlank()) return null
        val lower = text.lowercase()
        val typeGuess = when {
            "bat" in lower -> "Bat Exclusion"
            "raccoon" in lower -> "Raccoon Removal"
            "squirrel" in lower -> "Squirrel Removal"
            "skunk" in lower -> "Skunk Removal"
            "snake" in lower -> "Snake Removal"
            "trap" in lower -> "Trapping"
            "exclu" in lower -> "Exclusion"
            "inspect" in lower -> "Inspection"
            "repair" in lower -> "Repair"
            "clean" in lower -> "Cleanup"
            else -> "Inspection"
        }
        val priority = when {
            "urgent" in lower || "emergency" in lower || "asap" in lower -> "URGENT"
            "high priority" in lower || "high-priority" in lower -> "HIGH"
            "low priority" in lower -> "LOW"
            else -> "MEDIUM"
        }
        val address = Regex("(?i)(?:at|address(?: is)?|located at)\\s+([^.\\n]{8,80})")
            .find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val customer = Regex("(?i)(?:customer|client|homeowner|for)\\s+([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)?)")
            .find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val title = buildString {
            append(typeGuess)
            if (customer.isNotBlank()) append(" — ").append(customer)
            else if (address.isNotBlank()) append(" — ").append(address.take(40))
        }
        return JobIntakeDraft(
            title = title,
            customerName = customer,
            address = address,
            type = typeGuess,
            priority = priority,
            description = text.take(800),
            notes = ""
        )
    }

    fun canSave(draft: JobIntakeDraft?): Boolean {
        val d = draft ?: return false
        return d.title.isNotBlank() || d.customerName.isNotBlank() || d.address.isNotBlank()
    }

    fun merge(
        current: JobIntakeDraft,
        incoming: JobIntakeDraft,
        editedFields: Set<String>
    ): JobIntakeDraft {
        fun pick(key: String, existing: String, suggested: String): String =
            OperatorWins.suggest(existing, suggested, manual = key in editedFields)
        return current.copy(
            title = pick(IntakeField.TITLE, current.title, incoming.title),
            customerName = pick(IntakeField.CUSTOMER, current.customerName, incoming.customerName),
            address = pick(IntakeField.ADDRESS, current.address, incoming.address),
            type = pick(IntakeField.TYPE, current.type, incoming.type),
            priority = pick(IntakeField.PRIORITY, current.priority, incoming.priority)
                .ifBlank { current.priority.ifBlank { "MEDIUM" } },
            description = pick(IntakeField.DESCRIPTION, current.description, incoming.description),
            notes = pick(IntakeField.NOTES, current.notes, incoming.notes)
        )
    }

    suspend fun refine(
        transcript: String,
        localReady: Boolean,
        generateLocal: suspend (system: String, user: String) -> String?,
        cloudConfigured: Boolean,
        completeCloud: (system: String, user: String) -> Pair<String?, String?>,
        providerLabel: String,
        localDisplayName: String,
        skipLocalIfCloud: Boolean = cloudConfigured,
        timeoutMs: Long = REFINE_TIMEOUT_MS
    ): JobIntakeResult = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val timed = withTimeoutOrNull(timeoutMs) {
            val system = REFINE_SYSTEM
            val user = buildString {
                appendLine("Technician dictation / notes:")
                appendLine(transcript.ifBlank { "(empty)" })
                appendLine()
                append("Parse into job JSON now.")
            }
            val useLocal = localReady && !(skipLocalIfCloud && cloudConfigured)
            if (cloudConfigured) {
                val (text, err) = completeCloud(system, user)
                if (text != null) {
                    val parsed = parseJobIntake(text)
                    if (parsed != null) {
                        return@withTimeoutOrNull JobIntakeResult(
                            draft = parsed,
                            sourceLabel = "☁️ Cloud ($providerLabel)"
                        )
                    }
                    return@withTimeoutOrNull JobIntakeResult(
                        error = "AI returned text but JSON parse failed. Edit fields manually or try again."
                    )
                }
                if (err != null) return@withTimeoutOrNull JobIntakeResult(error = err)
            }
            if (useLocal) {
                val local = generateLocal(system, user)
                if (local != null) {
                    val parsed = parseJobIntake(local)
                    if (parsed != null) {
                        return@withTimeoutOrNull JobIntakeResult(
                            draft = parsed,
                            sourceLabel = "📱 On-device ($localDisplayName)"
                        )
                    }
                }
            }
            JobIntakeResult()
        }
        val elapsed = System.currentTimeMillis() - t0
        if (timed == null) {
            logI("refine timeout ${elapsed}ms limit=${timeoutMs}ms")
            return@withContext JobIntakeResult(error = "AI refine timed out")
        }
        logI(
            "refine ${elapsed}ms source=${timed.sourceLabel.ifBlank { "none" }} skipLocal=${skipLocalIfCloud && cloudConfigured}"
        )
        timed
    }

    private fun parseJobIntake(raw: String): JobIntakeDraft? = try {
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
            draft.copy(
                title = draft.title.trim(),
                customerName = draft.customerName.trim(),
                address = draft.address.trim(),
                type = draft.type.trim(),
                priority = if (pri in allowed) pri else "MEDIUM",
                description = draft.description.trim(),
                notes = draft.notes.trim()
            )
        }
    } catch (e: Exception) {
        logW("parseJobIntake failed: ${e.message}")
        null
    }

    private val REFINE_SYSTEM = """
You are a wildlife removal dispatcher parsing a field technician voice note into a new job.
Return ONLY valid JSON with these string fields:
title, customerName, address, type, priority, description, notes
priority MUST be one of: LOW, MEDIUM, HIGH, URGENT
type should be a short service label when possible (e.g. Inspection, Removal, Exclusion, Trapping, Bat Exclusion, Raccoon Removal).
Infer a concise title if the tech did not give one. Prefer facts from the transcript; mark uncertain address pieces clearly.
Do not invent a phone number or dollar amount.
""".trimIndent()
}
