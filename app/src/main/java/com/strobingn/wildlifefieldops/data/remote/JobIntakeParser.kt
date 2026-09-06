package com.strobingn.wildlifefieldops.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Dictation → structured job fields. Used by [AiService.parseJobFromDictation].
 */
internal object JobIntakeParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun parse(
        transcript: String,
        localReady: Boolean,
        generateLocal: suspend (system: String, user: String) -> String?,
        cloudConfigured: Boolean,
        completeCloud: (system: String, user: String) -> Pair<String?, String?>,
        providerLabel: String,
        localDisplayName: String,
        notConfiguredMessage: String
    ): JobIntakeResult = withContext(Dispatchers.IO) {
        val system = """
You are a wildlife removal dispatcher parsing a field technician voice note into a new job.
Return ONLY valid JSON with these string fields:
title, customerName, address, type, priority, description, notes
priority MUST be one of: LOW, MEDIUM, HIGH, URGENT
type should be a short service label when possible (e.g. Inspection, Removal, Exclusion, Trapping, Bat Exclusion, Raccoon Removal).
Infer a concise title if the tech did not give one. Prefer facts from the transcript; mark uncertain address pieces clearly.
Do not invent a phone number or dollar amount.
""".trimIndent()
        val user = buildString {
            appendLine("Technician dictation / notes:")
            appendLine(transcript.ifBlank { "(empty)" })
            appendLine()
            append("Parse into job JSON now.")
        }

        if (localReady) {
            val local = generateLocal(system, user)
            if (local != null) {
                val parsed = parseJobIntake(local)
                if (parsed != null) {
                    return@withContext JobIntakeResult(
                        draft = parsed,
                        sourceLabel = "📱 On-device ($localDisplayName)"
                    )
                }
            }
        }
        if (cloudConfigured) {
            val (text, err) = completeCloud(system, user)
            if (text != null) {
                val parsed = parseJobIntake(text)
                if (parsed != null) {
                    return@withContext JobIntakeResult(
                        draft = parsed,
                        sourceLabel = "☁️ Cloud ($providerLabel)"
                    )
                }
                return@withContext JobIntakeResult(
                    error = "AI returned text but JSON parse failed. Edit fields manually or try again."
                )
            }
            if (err != null) return@withContext JobIntakeResult(error = err)
        }
        val heuristic = heuristicJobIntake(transcript)
        if (heuristic != null) {
            return@withContext JobIntakeResult(
                draft = heuristic,
                sourceLabel = "⚙️ Local heuristic (no generative model)"
            )
        }
        JobIntakeResult(error = notConfiguredMessage)
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
        android.util.Log.w("JobIntakeParser", "parseJobIntake failed: ${e.message}")
        null
    }

    private fun heuristicJobIntake(transcript: String): JobIntakeDraft? {
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
            notes = "Parsed from voice dictation"
        )
    }
}
