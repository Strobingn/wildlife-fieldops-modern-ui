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
        try {
            return@withContext mutex.withLock {
                val opened = openUnlocked(path)
                if (opened.isFailure) {
                    return@withLock Result.failure(opened.exceptionOrNull()!!)
                }
                val loaded = model
                    ?: return@withLock Result.failure(IllegalStateException("Local LLM failed to initialize"))

                val result = Llama.complete(
                    loaded,
                    prompt = userPrompt.trim(),
                    systemPrompt = systemPrompt.trim(),
                    maxTokens = maxTokens.coerceIn(64, 1024)
                )
                val text = result.text.trim()
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
                temperature = 0.4f,
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
    }
}
