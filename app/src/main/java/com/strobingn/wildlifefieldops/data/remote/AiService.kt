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

/** Structured estimate fields the calculator can apply. */
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
    val taxRate: Double = 8.0,
    val discountPercent: Double = 0.0,
    val rationale: String = "",
    val lineItemNotes: String = "",
    val fromAi: Boolean = true
)

// AiEdgeRequest is defined in RemoteDtos.kt (same package) — do not redeclare

@Singleton
class AiService @Inject constructor(
    private val localLlm: LocalLlmEngine
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val apiKey: String
        get() = BuildConfig.LLM_API_KEY.trim()

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && apiKey.length >= 10

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

    /** Safe diagnostics for Settings / AI screen (never exposes the key). */
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


    /**
     * SpaceXAI (xAI Grok) by default — OpenAI-compatible chat completions.
     * Env: XAI_API_KEY (preferred) or LLM_API_KEY; base https://api.x.ai/v1; model grok-4.5.
     */
    suspend fun ask(userMessage: String, species: String = ""): String = withContext(Dispatchers.IO) {
        val userPrompt = buildString {
            if (species.isNotBlank()) append("Species context: $species\n")
            append(userMessage)
        }
        // 1) Cloud LLM when key is present
        if (isConfigured) {
            when (val result = completeChat(WILDLIFE_SYSTEM_PROMPT, userPrompt, maxTokens = 900, temperature = 0.35)) {
                is ChatResult.Ok -> return@withContext result.text
                is ChatResult.Err -> {
                    android.util.Log.w("AiService", "Cloud LLM failed, trying local: ${result.message}")
                    val local = generateLocal(WILDLIFE_SYSTEM_PROMPT, userPrompt)
                    if (local != null) {
                        return@withContext "📡 Cloud unavailable (${result.message.take(80)})\n\n📱 On-device LLM:\n\n$local"
                    }
                    return@withContext result.message + "\n\n" + localUnavailableHint()
                }
            }
        }
        // 2) Real on-device LLM (never keyword stubs)
        val local = generateLocal(WILDLIFE_SYSTEM_PROMPT, userPrompt)
        if (local != null) {
            return@withContext "📱 On-device LLM (${LocalLlmModelManager.MODEL_DISPLAY_NAME}):\n\n$local"
        }
        // 3) Honest setup guidance — no fake field-knowledge lists
        notConfiguredMessage()
    }

    /**
     * Draft estimate numbers from job title, type, description, and notes.
     * Fills the Estimate Calculator fields. Falls back to heuristics offline.
     */
    suspend fun draftEstimateFromJob(job: Job): EstimateDraft = withContext(Dispatchers.IO) {
        val system = """
You are a wildlife removal estimator. Return ONLY valid JSON (no markdown fences) with these number fields:
laborHours, laborRate, materialsCost, equipmentCost, permitCost, disposalCost, mileage, mileageRate, taxRate, discountPercent
Plus string fields: rationale, lineItemNotes

Rules:
- Use realistic US nuisance wildlife pricing
- Prefer laborHours 1–8 and laborRate 75–125 unless notes say otherwise
- Materials/equipment/disposal scale with exclusion, attic cleanout, multi-entry, dead animal, etc.
- mileage is one-way miles if address distance unknown use 0–25 guess or 0
- taxRate default 8 unless notes specify
- discountPercent usually 0
- Keep rationale under 3 sentences
""".trimIndent()
        val user = buildJobContext(job) + "\n\nProduce an estimate draft JSON for this job."

        if (isConfigured) {
            when (val result = completeChat(system, user, maxTokens = 700, temperature = 0.25)) {
                is ChatResult.Ok -> {
                    val parsed = parseEstimateDraft(result.text)
                    if (parsed != null) return@withContext parsed.copy(fromAi = true)
                }
                is ChatResult.Err -> android.util.Log.w("AiService", "Cloud estimate failed: ${result.message}")
            }
        }
        val local = generateLocal(system, user)
        if (local != null) {
            val parsed = parseEstimateDraft(local)
            if (parsed != null) return@withContext parsed.copy(fromAi = true, rationale = "On-device LLM draft. ${parsed.rationale}")
            return@withContext EstimateDraft(
                rationale = "On-device LLM response unparseable.\n\n${local.take(280)}",
                lineItemNotes = "Review manually before quoting.",
                fromAi = false
            )
        }
        EstimateDraft(
            rationale = "No cloud key and local LLM model not ready. Download the on-device model in AI Assistant, or set XAI_API_KEY.",
            lineItemNotes = "Cannot invent pricing without a real LLM.",
            fromAi = false
        )
    }

    /**
     * Auto job summary for handoff, invoice notes, or office review.
     */
    suspend fun summarizeJob(job: Job): String = withContext(Dispatchers.IO) {
        val system = """
You write concise wildlife-control job summaries for field/office handoff.
Structure with short headings:
• Overview
• Customer / site
• Service type & priority
• Work notes
• Risks / safety
• Suggested next steps
Max ~180 words. Bullet-first. No fluff.
""".trimIndent()
        val user = buildJobContext(job) + "\n\nWrite the job summary now."
        if (isConfigured) {
            when (val result = completeChat(system, user, maxTokens = 500, temperature = 0.3)) {
                is ChatResult.Ok -> return@withContext result.text
                is ChatResult.Err -> {
                    val local = generateLocal(system, user)
                    if (local != null) return@withContext "📱 On-device LLM summary:\n\n$local"
                    return@withContext result.message + "\n\n" + localUnavailableHint()
                }
            }
        }
        val local = generateLocal(system, user)
        if (local != null) return@withContext "📱 On-device LLM summary:\n\n$local"
        localUnavailableHint() + "\n\nJob context available locally:\n" + buildJobContext(job).take(500)
    }

    private fun buildJobContext(job: Job): String = buildString {
        appendLine("Job title: ${job.title.ifBlank { \"(none)\" }}")
        appendLine("Service type: ${job.type}")
        appendLine("Status: ${job.status}")
        appendLine("Priority: ${job.priority}")
        appendLine("Customer: ${job.customerName.ifBlank { \"(none)\" }}")
        appendLine("Address: ${job.address.ifBlank { \"(none)\" }}")
        if (job.estimatedValue > 0) appendLine("Existing estimate value: $${job.estimatedValue}")
        if (job.actualCost > 0) appendLine("Actual cost so far: $${job.actualCost}")
        appendLine("Description: ${job.description.ifBlank { \"(none)\" }}")
        appendLine("Notes: ${job.notes.ifBlank { \"(none)\" }}")
        if (job.assignedTo.isNotBlank()) appendLine("Assigned to: ${job.assignedTo}")
    }

    private sealed class ChatResult {
        data class Ok(val text: String) : ChatResult()
        data class Err(val message: String) : ChatResult()
    }

    private fun completeChat(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
        temperature: Double
    ): ChatResult {
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
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) {
                android.util.Log.w("AiService", "LLM HTTP $code: ${body.take(400)}")
                val detail = body.take(180).replace("\n", " ")
                ChatResult.Err(
                    when (code) {
                        429 -> "⚠️ AI rate limit (HTTP 429). Wait and retry."
                        401 -> "⚠️ Invalid API key (HTTP 401).\nKey length in APK: ${BuildConfig.LLM_KEY_LENGTH}.\nRebuild after updating secret XAI_API_KEY (no spaces/newlines)."
                        404 -> "⚠️ Model/endpoint not found (HTTP 404).\nModel: ${BuildConfig.LLM_MODEL}\nBase: ${BuildConfig.LLM_BASE_URL}\n$body".take(280)
                        400 -> "⚠️ Bad request (HTTP 400). $detail"
                        else -> "⚠️ AI error (HTTP $code). $detail"
                    }
                )
            } else {
                val text = parseLlmResponse(body)
                if (text.isNullOrBlank()) ChatResult.Err("Empty AI response.")
                else ChatResult.Ok(text)
            }
        } catch (e: Exception) {
            android.util.Log.e("AiService", "LLM request failed", e)
            ChatResult.Err("Network error: ${e.message}")
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEstimateDraft(raw: String): EstimateDraft? {
        return try {
            val cleaned = raw
                .trim()
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val obj = cleaned.substring(start, end + 1)
            json.decodeFromString(EstimateDraft.serializer(), obj)
        } catch (e: Exception) {
            android.util.Log.w("AiService", "parseEstimateDraft failed", e)
            null
        }
    }

    private fun notConfiguredMessage(): String = buildString {
        append("⚠️ No generative AI is ready yet.\n\n")
        append(configDiagnostics())
        append("\n\nEnable ONE of:\n")
        append("A) On-device LLM (works offline after download)\n")
        append("   • Open AI Assistant → Download local model\n")
        append("   • Model: ${LocalLlmModelManager.MODEL_DISPLAY_NAME}\n")
        append("B) Cloud LLM\n")
        append("   • Set repo secret XAI_API_KEY and rebuild the APK\n")
        append("\nKeyword stub / canned field-knowledge lists have been removed.")
    }

    private fun localUnavailableHint(): String = buildString {
        append("On-device LLM is not ready.\n")
        append(localLlm.modelStatusLabel())
        append("\nDownload ${LocalLlmModelManager.MODEL_DISPLAY_NAME} from AI Assistant (first use ~940 MB).")
    }

    private suspend fun generateLocal(system: String, user: String): String? {
        val result = localLlm.generate(system, user)
        return result.getOrElse {
            android.util.Log.w("AiService", "Local LLM generate failed: ${it.message}")
            null
        }
    }

    private fun detectModel(): String {
        val configured = BuildConfig.LLM_MODEL.trim()
        if (configured.isNotBlank()) return configured
        val base = BuildConfig.LLM_BASE_URL.lowercase()
        return when {
            base.contains("x.ai") || base.contains("grok") -> "grok-4.5"
            base.contains("openai") -> "gpt-4o-mini"
            else -> "grok-4.5"
        }
    }

    companion object {
        val WILDLIFE_SYSTEM_PROMPT: String = """
You are FieldOps AI for a professional wildlife removal / nuisance wildlife control business
(e.g. inspections, trapping, exclusion, cleanup, repairs, follow-ups, invoicing).

You help with EVERY operational area a tech or owner needs:
1) Field work — species ID (signs, sounds, damage, droppings), trapping/bait, exclusion, one-way doors, cleanup, PPE
2) Safety & compliance — rabies vectors, histoplasmosis, MBTA, state rules (call out when laws vary by state)
3) Job management — how to structure jobs, statuses, follow-ups, multi-visit plans, scheduling routes
4) Inspections — what to document, photos, entry points, severity, recommendations
5) Estimates & pricing — ballpark ranges, line items (labor, materials, exclusion, sanitation), upsells
6) Customer communication — clear, professional scripts without scare tactics
7) Equipment & inventory — traps, sealants, PPE, when to restock
8) Business ops — daily workflow, SOPs, quality checks before leaving a site

Style:
- Concise, bullet-first, field-readable on a phone
- Actionable next steps (1–5 bullets)
- Flag safety risks clearly
- If info is missing, ask 1–2 clarifying questions max
- Prefer practical US nuisance wildlife practice; note regional variation when relevant
- Never invent licenses or claim illegal methods; prefer legal exclusion/live-trap approaches

Prefer cloud LLM when configured; otherwise use the on-device llama.cpp (abliterated GGUF).
Never answer with canned keyword tip lists — always generate.
""".trimIndent()

        fun cloudDiagnosticsOnly(): String = buildString {
            append("Cloud base: ${BuildConfig.LLM_BASE_URL}\n")
            append("Cloud model: ${BuildConfig.LLM_MODEL}\n")
            append("Cloud key length: ${BuildConfig.LLM_KEY_LENGTH}\n")
            append("On-device: llama.cpp (abliterated GGUF) + ${LocalLlmModelManager.MODEL_DISPLAY_NAME}")
        }
    }

    private fun parseLlmResponse(body: String): String? {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            root["choices"]
                ?.jsonArray
                ?.firstOrNull()
                ?.jsonObject
                ?.get("message")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.content
                ?.trim()
        } catch (e: Exception) {
            android.util.Log.w("AiService", "Parse LLM response failed", e)
            null
        }
    }

    suspend fun askViaSupabase(userMessage: String, species: String = ""): String = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        if (base.contains("your-project") || BuildConfig.SUPABASE_ANON_KEY == "your-anon-key") {
            return@withContext "⚠️ No AI backend configured. Add LLM_API_KEY to enable AI assistance."
        }
        val endpoint = URL("$base/functions/v1/ai-assistant")
        val payload = json.encodeToString(
            AiEdgeRequest(
                mode = "field_plan",
                observation = userMessage,
                species = species
            )
        )
        val connection = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 25_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
            setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
        }
        try {
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            if (code !in 200..299) {
                android.util.Log.w("AiService", "Supabase edge HTTP $code: ${body.take(300)}")
                return@withContext "Supabase edge function error (HTTP $code). Check your edge function is deployed."
            }
            formatEdgeResponse(body) ?: "Empty response from edge function."
        } catch (e: Exception) {
            android.util.Log.e("AiService", "Supabase request failed", e)
            "Network error: ${e.message}"
        } finally {
            connection.disconnect()
        }
    }

    private fun formatEdgeResponse(body: String): String? {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val data = root["data"]?.jsonObject ?: root["result"]?.jsonObject ?: root
            val summary = data["summary"]?.jsonPrimitive?.content
            if (summary.isNullOrBlank()) return null
            val steps = data["recommended_next_steps"]?.jsonArray
                ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                .orEmpty()
            val safety = data["safety_flags"]?.jsonArray
                ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                .orEmpty()
            buildString {
                append(summary.trim())
                if (steps.isNotEmpty()) {
                    append("\n\nNext steps:\n")
                    steps.forEachIndexed { i, s -> append("${i + 1}. $s\n") }
                }
                if (safety.isNotEmpty()) {
                    append("\nSafety:\n")
                    safety.forEach { append("• $it\n") }
                }
            }.trim()
        } catch (e: Exception) {
            android.util.Log.w("AiService", "Parse edge response failed", e)
            null
        }
    }
}
