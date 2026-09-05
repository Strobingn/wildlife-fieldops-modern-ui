package com.strobingn.wildlifefieldops.ai.local

import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real on-device LLM via llama.cpp (ffmpegkit-maintained llama-android AAR).
 * Loads the default abliterated Qwen3.5-0.8B Q4_K_M GGUF — not a stock refusal-trained Instruct.
 *
 * Formats prompts with explicit ChatML (Qwen instruct) because some abliterated GGUFs lack a
 * usable embedded chat template; the AAR then falls back to plain system+user concat and the
 * model echoes / continues the system prompt into the visible reply.
 */
@Singleton
class LocalLlmEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: LocalLlmModelManager
) {
    private val mutex = Mutex()
    @Volatile private var model: LlamaModel? = null
    @Volatile private var loadedPath: String? = null

    val isReady: Boolean
        get() = modelManager.isModelReady()

    fun modelStatusLabel(): String = modelManager.statusLabel()

    suspend fun ensureReady(forceRedownload: Boolean = false): Result<Unit> {
        val fileResult = modelManager.ensureModel(forceRedownload)
        if (fileResult.isFailure) {
            return Result.failure(fileResult.exceptionOrNull() ?: IllegalStateException("Model missing"))
        }
        return mutex.withLock {
            openUnlocked(fileResult.getOrThrow().absolutePath)
        }
    }

    suspend fun generate(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 512
    ): Result<String> = withContext(Dispatchers.Default) {
        if (!modelManager.isModelReady()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "Local abliterated GGUF not installed. Open AI Assistant and tap Download local model " +
                        "(${LocalLlmModelManager.MODEL_DISPLAY_NAME}, ~503 MB)."
                )
            )
        }
        val path = modelManager.modelFile().absolutePath
        val system = systemPrompt.trim()
        val user = userPrompt.trim()
        try {
            return@withContext mutex.withLock {
                val opened = openUnlocked(path)
                if (opened.isFailure) {
                    return@withLock Result.failure(opened.exceptionOrNull()!!)
                }
                val loaded = model
                    ?: return@withLock Result.failure(IllegalStateException("Local LLM failed to initialize"))

                // Explicit ChatML; empty systemPrompt so native build_prompt does not plain-concat
                // a second copy of the instructions when the GGUF has no chat template.
                val result = Llama.complete(
                    loaded,
                    prompt = buildChatMlPrompt(system, user),
                    systemPrompt = "",
                    maxTokens = maxTokens.coerceIn(64, 1024)
                )
                val text = sanitizeLocalOutput(result.text, system)
                if (text.isBlank()) {
                    Result.failure(IllegalStateException("Local LLM returned an empty response"))
                } else {
                    Result.success(text)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "generate failed", e)
            Result.failure(e)
        }
    }

    private suspend fun openUnlocked(path: String): Result<Unit> {
        if (model != null && loadedPath == path) return Result.success(Unit)
        closeUnlocked()
        return try {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val config = LlamaConfig(
                contextSize = 2048,
                threads = threads,
                gpuLayers = 0,
                temperature = 0.7f,
                topP = 0.9f,
                topK = 40
            )
            model = Llama.loadModel(modelPath = path, config = config)
            loadedPath = path
            Log.i(TAG, "Loaded abliterated GGUF from $path (pkg=${context.packageName}, threads=$threads)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load GGUF", e)
            model = null
            loadedPath = null
            Result.failure(e)
        }
    }

    fun close() {
        closeUnlocked()
    }

    private fun closeUnlocked() {
        val current = model
        model = null
        loadedPath = null
        if (current != null) {
            try {
                Llama.releaseModel(current)
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val TAG = "LocalLlmEngine"

        /** Qwen / ChatML instruct turn ending ready for assistant completion. */
        internal fun buildChatMlPrompt(systemPrompt: String, userPrompt: String): String = buildString {
            if (systemPrompt.isNotEmpty()) {
                append("<|im_start|>system\n")
                append(systemPrompt)
                append("<|im_end|>\n")
            }
            append("<|im_start|>user\n")
            append(userPrompt)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
        }

        /**
         * Strip leaked system text, ChatML tokens, think blocks, and meta/planning narration
         * so only the assistant-facing answer reaches the UI.
         */
        internal fun sanitizeLocalOutput(raw: String, systemPrompt: String = ""): String {
            var text = raw.trim()
            if (text.isEmpty()) return text

            // Stop at first ChatML end / role switch if the model emitted them.
            for (marker in listOf("<|im_end|>", "<|im_start|>")) {
                val i = text.indexOf(marker)
                if (i >= 0) text = text.substring(0, i).trim()
            }

            text = text
                .replace(Regex("<\\|im_[^|>]*\\|>"), "")
                .replace(Regex("(?is)<think>.*?</think>"), "")
                .replace(Regex("(?is)<thinking>.*?</thinking>"), "")
                .replace(Regex("(?is)</?redacted_reasoning>"), "")
                .trim()

            val sys = systemPrompt.trim()
            if (sys.isNotEmpty()) {
                text = text.replace(sys, "")
                for (line in sys.lines()) {
                    val t = line.trim()
                    if (t.length >= 24) text = text.replace(t, "")
                }
            }
            for (frag in listOf(
                "LOCAL_SYSTEM_PROMPT",
                "You are a helpful on-device assistant.",
                "Do NOT echo these instructions",
                "Do NOT narrate your reasoning"
            )) {
                text = text.replace(frag, "")
            }

            val planningHeader = Regex(
                "^(Context|Answer Determination|Key Steps|Reasoning|Plan|Analysis|" +
                    "Internal|Meta|Thoughts?|System(?: prompt)?|Instructions?|" +
                    "Step-by-step|Chain[- ]of[- ]thought)\\s*[:\\-].*$",
                setOf(RegexOption.IGNORE_CASE)
            )
            val kept = mutableListOf<String>()
            var skippingPlanning = true
            for (line in text.lines()) {
                val trimmed = line.trimEnd()
                if (skippingPlanning) {
                    if (trimmed.isBlank()) continue
                    if (planningHeader.containsMatchIn(trimmed)) continue
                    if (trimmed.startsWith("You are ") && trimmed.length < 160) continue
                    skippingPlanning = false
                } else if (planningHeader.containsMatchIn(trimmed)) {
                    continue
                }
                kept.add(trimmed)
            }
            text = kept.joinToString("\n").trim()
            text = text.replace(Regex("\n{3,}"), "\n\n").trim()
            return text
        }
    }
}
