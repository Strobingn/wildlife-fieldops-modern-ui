package com.strobingn.wildlifefieldops.ai.local

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads and caches the on-device **abliterated** GGUF model for llama.cpp.
 *
 * Default model (NOT a stock aligned Instruct build):
 * - Upstream weights: huihui-ai/Qwen2.5-1.5B-Instruct-abliterated
 * - Quant host: mradermacher/Qwen2.5-1.5B-Instruct-abliterated-GGUF
 * - File: Qwen2.5-1.5B-Instruct-abliterated.Q4_K_M.gguf (~986 MB, public / not gated)
 *
 * Abliteration removes refusal / safety-alignment directions so the local assistant
 * generates freely for field ops instead of stock Instruct refusals.
 */
@Singleton
class LocalLlmModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed class ModelState {
        data object Missing : ModelState()
        data class Downloading(val bytesRead: Long, val totalBytes: Long) : ModelState() {
            val progress: Float
                get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
        }
        data class Ready(val path: String) : ModelState()
        data class Error(val message: String) : ModelState()
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.Missing)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val modelsDir: File
        get() = File(context.filesDir, "local_llm").also { it.mkdirs() }

    fun modelFile(): File = File(modelsDir, MODEL_FILE_NAME)

    fun isModelReady(): Boolean {
        val file = modelFile()
        val ready = file.exists() && file.length() >= MIN_VALID_BYTES
        if (ready && _state.value !is ModelState.Ready) {
            _state.value = ModelState.Ready(file.absolutePath)
        }
        return ready
    }

    fun refreshState() {
        val file = modelFile()
        // Drop leftover MediaPipe .task from earlier builds if present.
        File(modelsDir, LEGACY_MEDIAPIPE_TASK).takeIf { it.exists() }?.delete()
        _state.value = when {
            file.exists() && file.length() >= MIN_VALID_BYTES -> ModelState.Ready(file.absolutePath)
            else -> ModelState.Missing
        }
    }

    suspend fun ensureModel(forceRedownload: Boolean = false): Result<File> = withContext(Dispatchers.IO) {
        val dest = modelFile()
        if (!forceRedownload && dest.exists() && dest.length() >= MIN_VALID_BYTES) {
            _state.value = ModelState.Ready(dest.absolutePath)
            return@withContext Result.success(dest)
        }

        val partial = File(modelsDir, "$MODEL_FILE_NAME.partial")
        try {
            if (forceRedownload) {
                dest.delete()
                partial.delete()
            }

            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 60_000
                readTimeout = 120_000
                setRequestProperty("User-Agent", "WildlifeFieldOps-Android/2.2")
                setRequestProperty("Accept", "application/octet-stream")
            }

            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(200)
                val msg = "Abliterated GGUF download failed (HTTP $code). $err".trim()
                _state.value = ModelState.Error(msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            val total = connection.contentLengthLong.takeIf { it > 0 } ?: EXPECTED_BYTES
            var read = 0L
            _state.value = ModelState.Downloading(0L, total)

            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(1024 * 256)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        read += n
                        _state.value = ModelState.Downloading(read, total)
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            if (read < MIN_VALID_BYTES) {
                partial.delete()
                val msg = "Downloaded file too small ($read bytes) — incomplete download."
                _state.value = ModelState.Error(msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            if (dest.exists()) dest.delete()
            if (!partial.renameTo(dest)) {
                partial.copyTo(dest, overwrite = true)
                partial.delete()
            }

            _state.value = ModelState.Ready(dest.absolutePath)
            Log.i(TAG, "Abliterated GGUF ready: ${dest.absolutePath} (${dest.length()} bytes)")
            Result.success(dest)
        } catch (e: Exception) {
            Log.e(TAG, "ensureModel failed", e)
            partial.delete()
            val msg = e.message ?: "Model download failed"
            _state.value = ModelState.Error(msg)
            Result.failure(e)
        }
    }

    fun deleteModel() {
        modelFile().delete()
        File(modelsDir, "$MODEL_FILE_NAME.partial").delete()
        _state.value = ModelState.Missing
    }

    fun statusLabel(): String = when (val s = _state.value) {
        is ModelState.Missing -> "Local LLM: abliterated GGUF not downloaded"
        is ModelState.Downloading -> {
            val pct = (s.progress * 100).toInt()
            val mb = s.bytesRead / (1024 * 1024)
            val totalMb = s.totalBytes / (1024 * 1024)
            "Local LLM: downloading $pct% ($mb / $totalMb MB)"
        }
        is ModelState.Ready -> "Local LLM: ready (${MODEL_DISPLAY_NAME})"
        is ModelState.Error -> "Local LLM: error — ${s.message}"
    }

    companion object {
        private const val TAG = "LocalLlmModelManager"

        /** Abliterated upstream (huihui-ai) + mradermacher GGUF quant. */
        const val MODEL_BASE = "huihui-ai/Qwen2.5-1.5B-Instruct-abliterated"
        const val MODEL_REPO = "mradermacher/Qwen2.5-1.5B-Instruct-abliterated-GGUF"
        const val MODEL_FILE_NAME = "Qwen2.5-1.5B-Instruct-abliterated.Q4_K_M.gguf"
        const val MODEL_QUANT = "Q4_K_M"
        const val MODEL_DISPLAY_NAME =
            "Qwen2.5-1.5B-Instruct-abliterated (Q4_K_M GGUF)"
        const val MODEL_URL =
            "https://huggingface.co/mradermacher/Qwen2.5-1.5B-Instruct-abliterated-GGUF/resolve/main/Qwen2.5-1.5B-Instruct-abliterated.Q4_K_M.gguf"

        /** Published Content-Length from Hugging Face CDN. */
        const val EXPECTED_BYTES = 986_049_088L
        const val MIN_VALID_BYTES = 50L * 1024L * 1024L

        private const val LEGACY_MEDIAPIPE_TASK =
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    }
}
