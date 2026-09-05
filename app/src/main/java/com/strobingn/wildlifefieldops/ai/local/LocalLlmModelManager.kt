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
 * Catalog entry for a downloadable on-device abliterated GGUF (mradermacher quants of huihui-ai).
 * Exact filenames + byte sizes verified from Hugging Face API (LFS).
 */
data class LocalLlmOption(
    val id: String,
    val displayName: String,
    val shortLabel: String,
    val upstreamRepo: String,
    val quantRepo: String,
    val fileName: String,
    val expectedBytes: Long,
    val minValidBytes: Long,
    val approxSizeLabel: String,
    val isDefault: Boolean = false
) {
    val url: String
        get() = "https://huggingface.co/$quantRepo/resolve/main/$fileName"
}

/**
 * Downloads and caches on-device **abliterated** GGUF models for llama.cpp.
 *
 * Default: Qwen2.5-3B-Instruct-abliterated Q4_K_M (mradermacher)
 * Optional: Qwen2.5-7B-Instruct-abliterated-v3 Q4_K_M (mradermacher)
 *
 * Selection is persisted; switching unloads via [LocalLlmEngine] and uses the chosen GGUF.
 * Legacy 0.8B / 1.5B / MediaPipe files are deleted on refresh.
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

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _selectedId = MutableStateFlow(loadSelectedId())
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    private val _state = MutableStateFlow<ModelState>(ModelState.Missing)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val modelsDir: File
        get() = File(context.filesDir, "local_llm").also { it.mkdirs() }

    val selected: LocalLlmOption
        get() = optionById(_selectedId.value) ?: DEFAULT_OPTION

    /** Active display name (selected model). */
    val activeDisplayName: String get() = selected.displayName
    val activeFileName: String get() = selected.fileName
    val activeRepo: String get() = selected.quantRepo
    val activeApproxSize: String get() = selected.approxSizeLabel

    fun options(): List<LocalLlmOption> = OPTIONS

    fun optionById(id: String): LocalLlmOption? = OPTIONS.firstOrNull { it.id == id }

    fun modelFile(option: LocalLlmOption = selected): File = File(modelsDir, option.fileName)

    fun isModelReady(option: LocalLlmOption = selected): Boolean {
        val file = modelFile(option)
        val ready = file.exists() && file.length() >= option.minValidBytes
        if (option.id == selected.id && ready && _state.value !is ModelState.Ready) {
            _state.value = ModelState.Ready(file.absolutePath)
        }
        return ready
    }

    fun isOptionDownloaded(option: LocalLlmOption): Boolean {
        val file = modelFile(option)
        return file.exists() && file.length() >= option.minValidBytes
    }

    /**
     * Persist selection. Caller should unload the previous llama model before/after switch.
     * @return true if the selected id changed
     */
    fun selectModel(id: String): Boolean {
        val option = optionById(id) ?: return false
        val changed = option.id != _selectedId.value
        if (changed) {
            prefs.edit().putString(KEY_SELECTED_ID, option.id).apply()
            _selectedId.value = option.id
        }
        refreshState()
        return changed
    }

    fun refreshState() {
        deleteLegacyFiles()
        val file = modelFile()
        val opt = selected
        _state.value = when {
            file.exists() && file.length() >= opt.minValidBytes -> ModelState.Ready(file.absolutePath)
            file.exists() && file.length() > 0L && file.length() < opt.minValidBytes -> {
                // Wrong-size / incomplete (e.g. leftover tiny legacy misnamed) — remove
                Log.w(TAG, "Deleting undersized ${file.name} (${file.length()} bytes)")
                file.delete()
                ModelState.Missing
            }
            else -> ModelState.Missing
        }
    }

    private fun deleteLegacyFiles() {
        for (name in LEGACY_FILE_NAMES) {
            File(modelsDir, name).takeIf { it.exists() }?.let {
                Log.i(TAG, "Deleting legacy local LLM file: ${it.name} (${it.length()} bytes)")
                it.delete()
            }
            File(modelsDir, "$name.partial").takeIf { it.exists() }?.delete()
        }
        // Also drop any leftover 0.8B-sized GGUF if somehow renamed oddly
        modelsDir.listFiles()?.forEach { f ->
            if (!f.isFile) return@forEach
            val n = f.name
            if (n.contains("0.8B", ignoreCase = true) ||
                n.contains("Qwen3.5-0.8", ignoreCase = true) ||
                n.contains("Huihui-Qwen3.5", ignoreCase = true)
            ) {
                Log.i(TAG, "Deleting legacy 0.8B artifact: $n (${f.length()} bytes)")
                f.delete()
            }
        }
    }

    suspend fun ensureModel(
        forceRedownload: Boolean = false,
        option: LocalLlmOption = selected
    ): Result<File> = withContext(Dispatchers.IO) {
        // Ensure selection matches the option we are ensuring
        if (option.id != selected.id) {
            selectModel(option.id)
        }
        val dest = modelFile(option)
        if (!forceRedownload && dest.exists() && dest.length() >= option.minValidBytes) {
            _state.value = ModelState.Ready(dest.absolutePath)
            return@withContext Result.success(dest)
        }

        val partial = File(modelsDir, "${option.fileName}.partial")
        try {
            if (forceRedownload) {
                dest.delete()
                partial.delete()
            }

            val connection = (URL(option.url).openConnection() as HttpURLConnection).apply {
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

            val total = connection.contentLengthLong.takeIf { it > 0 } ?: option.expectedBytes
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

            if (read < option.minValidBytes) {
                partial.delete()
                val msg = "Downloaded file too small ($read bytes) — incomplete download."
                _state.value = ModelState.Error(msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            // Soft check against exact HF size when known
            if (option.expectedBytes > 0 &&
                kotlin.math.abs(read - option.expectedBytes) > option.expectedBytes / 50
            ) {
                Log.w(
                    TAG,
                    "Size mismatch for ${option.fileName}: got $read, expected ${option.expectedBytes}"
                )
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

    fun deleteModel(option: LocalLlmOption = selected) {
        modelFile(option).delete()
        File(modelsDir, "${option.fileName}.partial").delete()
        if (option.id == selected.id) {
            _state.value = ModelState.Missing
        }
    }

    fun statusLabel(): String = when (val s = _state.value) {
        is ModelState.Missing -> "Local LLM: ${selected.shortLabel} not downloaded"
        is ModelState.Downloading -> {
            val pct = (s.progress * 100).toInt()
            val mb = s.bytesRead / (1024 * 1024)
            val totalMb = s.totalBytes / (1024 * 1024)
            "Local LLM: downloading ${selected.shortLabel} $pct% ($mb / $totalMb MB)"
        }
        is ModelState.Ready -> "Local LLM: ready (${selected.displayName})"
        is ModelState.Error -> "Local LLM: error — ${s.message}"
    }

    private fun loadSelectedId(): String {
        val saved = prefs.getString(KEY_SELECTED_ID, null)
        return if (saved != null && OPTIONS.any { it.id == saved }) saved else DEFAULT_OPTION.id
    }

    companion object {
        private const val TAG = "LocalLlmModelManager"
        private const val PREFS_NAME = "local_llm_prefs"
        private const val KEY_SELECTED_ID = "selected_model_id"

        /** Default on-device model: Qwen2.5-3B Instruct abliterated Q4_K_M (mradermacher). */
        val QWEN25_3B = LocalLlmOption(
            id = "qwen25-3b-abliterated-q4km",
            displayName = "Qwen2.5-3B-Instruct-abliterated (Q4_K_M)",
            shortLabel = "Qwen2.5-3B",
            upstreamRepo = "huihui-ai/Qwen2.5-3B-Instruct-abliterated",
            quantRepo = "mradermacher/Qwen2.5-3B-Instruct-abliterated-GGUF",
            // Exact HF filename (~2.1 GB); verified via HF tree API LFS size.
            fileName = "Qwen2.5-3B-Instruct-Abliterated.Q4_K_M.gguf",
            expectedBytes = 2_104_933_600L,
            minValidBytes = 1_500L * 1024L * 1024L,
            approxSizeLabel = "~2.1 GB",
            isDefault = true
        )

        /** Optional larger model: Qwen2.5-7B Instruct abliterated v3 Q4_K_M (mradermacher). */
        val QWEN25_7B_V3 = LocalLlmOption(
            id = "qwen25-7b-abliterated-v3-q4km",
            displayName = "Qwen2.5-7B-Instruct-abliterated-v3 (Q4_K_M)",
            shortLabel = "Qwen2.5-7B v3",
            upstreamRepo = "huihui-ai/Qwen2.5-7B-Instruct-abliterated-v3",
            quantRepo = "mradermacher/Qwen2.5-7B-Instruct-abliterated-v3-GGUF",
            fileName = "Qwen2.5-7B-Instruct-abliterated-v3.Q4_K_M.gguf",
            expectedBytes = 4_683_074_560L,
            minValidBytes = 3_500L * 1024L * 1024L,
            approxSizeLabel = "~4.7 GB",
            isDefault = false
        )

        val OPTIONS: List<LocalLlmOption> = listOf(QWEN25_3B, QWEN25_7B_V3)
        val DEFAULT_OPTION: LocalLlmOption = QWEN25_3B

        // Backward-compatible aliases → default 3B (prefer instance active* for selected model).
        const val MODEL_BASE = "huihui-ai/Qwen2.5-3B-Instruct-abliterated"
        const val MODEL_REPO = "mradermacher/Qwen2.5-3B-Instruct-abliterated-GGUF"
        const val MODEL_FILE_NAME = "Qwen2.5-3B-Instruct-Abliterated.Q4_K_M.gguf"
        const val MODEL_QUANT = "Q4_K_M"
        const val MODEL_DISPLAY_NAME = "Qwen2.5-3B-Instruct-abliterated (Q4_K_M)"
        const val MODEL_URL =
            "https://huggingface.co/mradermacher/Qwen2.5-3B-Instruct-abliterated-GGUF/resolve/main/Qwen2.5-3B-Instruct-Abliterated.Q4_K_M.gguf"
        const val EXPECTED_BYTES = 2_104_933_600L
        const val MIN_VALID_BYTES = 1_500L * 1024L * 1024L

        private val LEGACY_FILE_NAMES = listOf(
            "Huihui-Qwen3.5-0.8B-abliterated.Q4_K_M.gguf",
            "Qwen2.5-1.5B-Instruct-abliterated.Q4_K_M.gguf",
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            "Qwen2.5-3B-Instruct-abliterated.Q4_K_M.gguf" // lowercase twin; we use Abliterated capital A
        )
    }
}
