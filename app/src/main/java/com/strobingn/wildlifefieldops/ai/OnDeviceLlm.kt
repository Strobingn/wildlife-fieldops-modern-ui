package com.strobingn.wildlifefieldops.ai

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.strobingn.wildlifefieldops.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Real on-device language model (Gemma 3 1B IT, int4).
 * File lives on the phone after one ~555MB download. Not a checklist.
 */
object OnDeviceLlm {
    private const val TAG = "OnDeviceLlm"
    const val MODEL_FILE = "gemma3-1b-it-int4.task"
    const val MODEL_URL =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task"
    private const val MIN_BYTES = 80L * 1024L * 1024L

    data class Progress(
        val phase: String = "idle",
        val bytes: Long = 0,
        val total: Long = 0,
        val error: String = ""
    )

    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress

    private val mutex = Mutex()
    private var engine: LlmInference? = null

    fun modelFile(context: Context): File = File(context.filesDir, "models/$MODEL_FILE")

    fun isReady(context: Context): Boolean =
        modelFile(context).let { it.exists() && it.length() >= MIN_BYTES }

    fun hasHfToken(): Boolean = BuildConfig.HF_TOKEN.trim().length >= 8

    suspend fun ensureReady(context: Context): Boolean {
        if (isReady(context)) return true
        return download(context)
    }

    suspend fun download(context: Context): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (isReady(context)) return@withLock true
            val dest = modelFile(context)
            dest.parentFile?.mkdirs()
            val part = File(dest.absolutePath + ".part")
            _progress.value = Progress(phase = "download", bytes = 0, total = 0)
            try {
                val url = URL(MODEL_URL)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    setRequestProperty("User-Agent", "WildlifeFieldOps/GrokAIV5")
                    val token = BuildConfig.HF_TOKEN.trim()
                    if (token.length >= 8) {
                        setRequestProperty("Authorization", "Bearer $token")
                    }
                }
                val code = conn.responseCode
                if (code == 401 || code == 403) {
                    _progress.value = Progress(
                        phase = "error",
                        error = "Gemma is license-gated. Add repo secret HF_TOKEN (Hugging Face token that accepted the Gemma license) and rebuild."
                    )
                    conn.disconnect()
                    return@withLock false
                }
                if (code !in 200..299) {
                    _progress.value = Progress(phase = "error", error = "Download HTTP $code")
                    conn.disconnect()
                    return@withLock false
                }
                val total = conn.contentLengthLong.coerceAtLeast(0L)
                conn.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(256 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            output.write(buf, 0, n)
                            read += n
                            _progress.value = Progress(phase = "download", bytes = read, total = total)
                        }
                    }
                }
                conn.disconnect()
                if (part.length() < MIN_BYTES) {
                    part.delete()
                    _progress.value = Progress(phase = "error", error = "Download too small (${part.length()} bytes)")
                    return@withLock false
                }
                if (dest.exists()) dest.delete()
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
                _progress.value = Progress(phase = "ready", bytes = dest.length(), total = dest.length())
                true
            } catch (t: Throwable) {
                Log.e(TAG, "download failed", t)
                part.delete()
                _progress.value = Progress(phase = "error", error = t.message ?: "download failed")
                false
            }
        }
    }

    suspend fun generate(context: Context, prompt: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isReady(context)) return@withLock null
            try {
                val llm = engine ?: LlmInference.createFromOptions(
                    context.applicationContext,
                    LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile(context).absolutePath)
                        .setMaxTokens(512)
                        .setMaxTopK(40)
                        .build()
                ).also { engine = it }
                val text = llm.generateResponse(prompt.trim()).orEmpty().trim()
                text.ifBlank { null }
            } catch (t: Throwable) {
                Log.e(TAG, "generate failed", t)
                engine = null
                null
            }
        }
    }
}
