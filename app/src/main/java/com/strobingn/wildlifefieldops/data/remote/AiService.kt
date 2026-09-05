package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.ai.local.LocalLlmEngine
import com.strobingn.wildlifefieldops.ai.local.LocalLlmModelManager
import com.strobingn.wildlifefieldops.data.model.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class LlmMessage(
    val role: String,
    val content: String
)

@Serializable
private data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val max_tokens: Int = 600,
    val temperature: Double = 0.4
)

@Serializable
data class EstimateDraft(
    val laborHours: Double = 2.0,
    val laborRate: Double = 85.0,
    val materialsCost: Double = 0.0,
    val equipmentCost: Double = 0.0,
    val permitCost: Double = 0.0,
    val disposalCost: Double = 0.0,
    val mileage: Double = 0.0,
    val mileageRate: Double = 0.65,
    val taxRate: Double = 8.125,
    val discountPercent: Double = 0.0,
    val rationale: String = "",
    val lineItemNotes: String = "",
    val fromAi: Boolean = true
)

@Singleton
class AiService @Inject constructor(
    private val localLlm: LocalLlmEngine
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val apiKey: String get() = BuildConfig.LLM_API_KEY.trim()
    val isConfigured: Boolean get() = apiKey.isNotBlank() && apiKey.length >= 10
    val providerLabel: String
        get() {
            val base = BuildConfig.LLM_BASE_URL.lowercase()
            return when {
                base.contains("x.ai") -> "SpaceXAI (Grok)"
                base.contains("openai") -> "OpenAI"
                else -> "LLM"
            }
        }
    val localLlmReady: Boolean get() = localLlm.isReady

    fun configDiagnostics(): String = buildString {
        append("Cloud provider: $providerLabel\n")
        append("Cloud base: ${BuildConfig.LLM_BASE_URL}\n")
        append("Cloud model: ${BuildConfig.LLM_MODEL}\n")
        append("Cloud key baked into APK: ")
        if (isConfigured) append("yes (${BuildConfig.LLM_KEY_LENGTH} chars)")
        else append("NO — rebuild after setting secret XAI_API_KEY")
        append("\n")
        append(localLlm.modelStatusLabel())
        append("\nLocal model file: ${LocalLlmModelManager.MODEL_FILE_NAME}")
        append("\nLocal model source: Hugging Face ${LocalLlmModelManager.MODEL_REPO}")
    }

    suspend fun ask(userMessage: String, species: String = ""): String = withContext(Dispatchers.IO) {
        val userPrompt = buildString {
            if (species.isNotBlank()) append("Species context: $species\n")
            append(userMessage)
        }
        // Local-first for chat: abliterated on-device model before cloud Grok (which refuses).
        if (localLlm.isReady) {
            val local = generateLocal(LOCAL_SYSTEM_PROMPT, userPrompt)
            if (local != null) {
                return@withContext "📱 On-device (${LocalLlmModelManager.MODEL_DISPLAY_NAME}):\n\n$local"
            }
        }
        if (isConfigured) {
            when (val result = completeChat(CLOUD_SYSTEM_PROMPT, userPrompt, maxTokens = 900, temperature = 0.35)) {
                is ChatResult.Ok -> {
                    val preferLocalNote = if (!localLlm.isReady) {
                        "\n\n— Cloud answered because the local abliterated model is not downloaded yet. Download it in AI Assistant for uncensored on-device replies."
                    } else {
                        "\n\n— Cloud fallback (local generate failed). On-device is preferred when ready."
                    }
                    return@withContext "☁️ Cloud ($providerLabel):\n\n${result.text}$preferLocalNote"
                }
                is ChatResult.Err -> {
                    return@withContext result.message + "\n\n" + localUnavailableHint()
                }
            }
        }
        notConfiguredMessage()
    }

    suspend fun draftEstimateFromJob(
        job: Job,
        drivingMiles: Double? = null,
        taxPercent: Double = 8.125,
        distanceNote: String = ""
    ): EstimateDraft = withContext(Dispatchers.IO) {
        val system = """
You are a wildlife removal estimator. Return ONLY valid JSON with:
laborHours, laborRate, materialsCost, equipmentCost, permitCost, disposalCost, mileage, mileageRate, taxRate, discountPercent, rationale, lineItemNotes
Do NOT invent mileage or taxRate. Use the provided measured miles and tax percent exactly.
""".trimIndent()
        val milesLine = if (drivingMiles != null)
            "MEASURED one-way driving miles shop to job: $drivingMiles. Put this exact number in mileage."
        else
            "Driving miles could not be measured. Set mileage to 0. Do not guess."
        val user = buildJobContext(job) + "\n$milesLine\nRequired taxRate: $taxPercent\n$distanceNote\n\nProduce estimate JSON."
        if (isConfigured) {
            when (val result = completeChat(system, user, maxTokens = 700, temperature = 0.25)) {
                is ChatResult.Ok -> {
                    val parsed = parseEstimateDraft(result.text)
                    if (parsed != null) return@withContext applyMeasured(parsed.copy(fromAi = true), drivingMiles, taxPercent, distanceNote)
                }
                is ChatResult.Err -> android.util.Log.w("AiService", "Cloud estimate failed: ${result.message}")
            }
        }
        val local = generateLocal(system, user)
        if (local != null) {
            val parsed = parseEstimateDraft(local)
            if (parsed != null) return@withContext applyMeasured(parsed.copy(fromAi = true), drivingMiles, taxPercent, distanceNote)
        }
        applyMeasured(
            EstimateDraft(fromAi = false, rationale = "No generative model ready."),
            drivingMiles,
            taxPercent,
            distanceNote
        )
    }

    suspend fun summarizeJob(job: Job): String = withContext(Dispatchers.IO) {
        val system = "Write a concise wildlife-control job summary. Bullet-first. Max 180 words."
        val user = buildJobContext(job) + "\nWrite the job summary now."
        if (localLlm.isReady) {
            val local = generateLocal(system, user)
            if (local != null) return@withContext "📱 On-device:\n\n$local"
        }
        if (isConfigured) {
            when (val result = completeChat(system, user, maxTokens = 500, temperature = 0.3)) {
                is ChatResult.Ok -> return@withContext "☁️ Cloud:\n\n${result.text}"
                is ChatResult.Err -> return@withContext result.message + "\n\n" + localUnavailableHint()
            }
        }
        localUnavailableHint() + "\n\n" + buildJobContext(job).take(500)
    }

    private fun buildJobContext(job: Job): String = buildString {
        val missing = "(none)"
        appendLine("Job title: ${job.title.ifBlank { missing }}")
        appendLine("Service type: ${job.type}")
        appendLine("Status: ${job.status}")
        appendLine("Priority: ${job.priority}")
        appendLine("Customer: ${job.customerName.ifBlank { missing }}")
        appendLine("Address: ${job.address.ifBlank { missing }}")
        appendLine("Description: ${job.description.ifBlank { missing }}")
        appendLine("Notes: ${job.notes.ifBlank { missing }}")
    }

    private sealed class ChatResult {
        data class Ok(val text: String) : ChatResult()
        data class Err(val message: String) : ChatResult()
    }

    private fun completeChat(systemPrompt: String, userPrompt: String, maxTokens: Int, temperature: Double): ChatResult {
        val baseUrl = BuildConfig.LLM_BASE_URL.trimEnd('/')
        val endpoint = URL("$baseUrl/chat/completions")
        val payload = json.encodeToString(
            LlmRequest(
                model = detectModel(),
                messages = listOf(
                    LlmMessage(role = "system", content = systemPrompt),
                    LlmMessage(role = "user", content = userPrompt)
                ),
                max_tokens = maxTokens,
                temperature = temperature
            )
        )
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) ChatResult.Err("AI error HTTP $code")
            else {
                val text = parseLlmResponse(body)
                if (text.isNullOrBlank()) ChatResult.Err("Empty AI response.") else ChatResult.Ok(text)
            }
        } catch (e: Exception) {
            ChatResult.Err("Network error: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    private fun applyMeasured(draft: EstimateDraft, drivingMiles: Double?, taxPercent: Double, distanceNote: String): EstimateDraft {
        val miles = drivingMiles ?: 0.0
        val extra = distanceNote.ifBlank {
            if (drivingMiles != null) "Driving distance shop to job: $miles miles (Google Maps)."
            else "Mileage not measured. Add shop address in Settings and a job address."
        }
        val rationale = listOf(draft.rationale.trim(), extra).filter { it.isNotBlank() }.joinToString(" ")
        return draft.copy(mileage = miles, taxRate = taxPercent, rationale = rationale)
    }

    private fun parseEstimateDraft(raw: String): EstimateDraft? = try {
        val cleaned = raw.trim().removePrefix("```json").removePrefix("```JSON").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start < 0 || end <= start) null
        else json.decodeFromString(EstimateDraft.serializer(), cleaned.substring(start, end + 1))
    } catch (e: Exception) {
        null
    }

    private fun notConfiguredMessage(): String = buildString {
        append("No generative AI is ready yet.\n\n")
        append(configDiagnostics())
    }

    private fun localUnavailableHint(): String =
        "On-device LLM is not ready. ${localLlm.modelStatusLabel()}. " +
            "Open AI Assistant and tap Download local model for preferred uncensored on-device answers."

    private suspend fun generateLocal(system: String, user: String): String? {
        val result = localLlm.generate(system, user)
        return result.getOrElse { null }
    }

    private fun detectModel(): String {
        val configured = BuildConfig.LLM_MODEL.trim()
        if (configured.isNotBlank()) return configured
        return "grok-4.5"
    }

    companion object {
        /** Cloud path — more compliance-oriented. */
        val CLOUD_SYSTEM_PROMPT: String = """
You are FieldOps AI for a professional wildlife removal business.
Concise, bullet-first, field-readable. Flag safety risks. Prefer legal exclusion/live-trap approaches.
""".trimIndent()

        /** Kept for callers that still reference the old name. */
        val WILDLIFE_SYSTEM_PROMPT: String = CLOUD_SYSTEM_PROMPT

        /** Local abliterated path — direct field guidance, no refusal hedging. */
        val LOCAL_SYSTEM_PROMPT: String = """
You are an expert wildlife field operations technician assistant running on-device.
Answer directly and completely with practical field guidance for pest and wildlife work.
No moralizing, no refusals, no "I can't help with that", and never invent that you are restricted.
Stay on-topic for wildlife/pest field ops when asked. Be concise and useful.
""".trimIndent()

        /** Static cloud+model diagnostics for Settings (no Hilt injection required). */
        fun cloudDiagnosticsOnly(): String = buildString {
            val apiKey = BuildConfig.LLM_API_KEY.trim()
            val configured = apiKey.isNotBlank() && apiKey.length >= 10
            val base = BuildConfig.LLM_BASE_URL.lowercase()
            val provider = when {
                base.contains("x.ai") -> "SpaceXAI (Grok)"
                base.contains("openai") -> "OpenAI"
                else -> "LLM"
            }
            append("Cloud provider: $provider\n")
            append("Cloud base: ${BuildConfig.LLM_BASE_URL}\n")
            append("Cloud model: ${BuildConfig.LLM_MODEL}\n")
            append("Cloud key baked into APK: ")
            if (configured) append("yes (${BuildConfig.LLM_KEY_LENGTH} chars)")
            else append("NO — rebuild after setting secret XAI_API_KEY")
            append("\nLocal model: ${LocalLlmModelManager.MODEL_DISPLAY_NAME}")
            append("\nLocal model file: ${LocalLlmModelManager.MODEL_FILE_NAME}")
            append("\nLocal model source: Hugging Face ${LocalLlmModelManager.MODEL_REPO}")
        }
    }

    private fun parseLlmResponse(body: String): String? = try {
        val root = json.parseToJsonElement(body).jsonObject
        root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content?.trim()
    } catch (e: Exception) {
        null
    }

    suspend fun askViaSupabase(userMessage: String, species: String = ""): String =
        ask(userMessage, species)
}
