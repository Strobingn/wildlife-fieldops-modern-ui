package com.strobingn.wildlifefieldops.ai.local

import android.content.Context
import android.util.Log
import com.strobingn.wildlifefieldops.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catalog entry for a downloadable on-device abliterated GGUF.
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
    /** Prefer ?download=true so HF serves the binary (not a HTML interstitial). */
    val url: String
        get() = "https://huggingface.co/$quantRepo/resolve/main/$fileName?download=true"
}

/**
 * Downloads and caches on-device **abliterated** GGUF models for llama.cpp.
 *
 * Default: Qwen2.5-3B-Instruct-abliterated Q4_K_M (mradermacher)
 * Also: 1.5B (easier on flaky networks), Llama-3.2-3B, optional 7B v3.
 *
 * Downloads resume via HTTP Range on `.partial` files (stalls / short timeouts
 * no longer wipe progress). Optional HF_TOKEN raises Hub rate limits.
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

    val activeDisplayName: String get() = selected.displayName
    val activeFileName: String get() = selected.fileName
    val activeRepo: String get() = selected.quantRepo
    val activeApproxSize: String get() = selected.approxSizeLabel

    fun options(): List<LocalLlmOption> = OPTIONS

    fun optionById(id: String): LocalLlmOption? = OPTIONS.firstOrNull { it.id == id }

    fun modelFile(option: LocalLlmOption = selected): File = File(modelsDir, option.fileName)

    fun isModelReady(option: LocalLlmOption = selected): Boolean {
        val file = modelFile(option)
        val ready = file.exists() && file.length() >= option.minValidBytes && looksLikeGguf(file)
        if (option.id == selected.id && ready && _state.value !is ModelState.Ready) {
            _state.value = ModelState.Ready(file.absolutePath)
        }
        return ready
    }

    fun isOptionDownloaded(option: LocalLlmOption): Boolean {
        val file = modelFile(option)
        return file.exists() && file.length() >= option.minValidBytes && looksLikeGguf(file)
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

    /** Optional runtime HF token (overrides empty BuildConfig). */
    fun setHuggingFaceToken(token: String?) {
        val t = token?.trim().orEmpty()
        if (t.isEmpty()) {
            prefs.edit().remove(KEY_HF_TOKEN).apply()
        } else {
            prefs.edit().putString(KEY_HF_TOKEN, t).apply()
        }
    }

    fun refreshState() {
        deleteLegacyFiles()
        val file = modelFile()
        val opt = selected
        _state.value = when {
            file.exists() && file.length() >= opt.minValidBytes && looksLikeGguf(file) ->
                ModelState.Ready(file.absolutePath)
            file.exists() && file.length() > 0L &&
                (file.length() < opt.minValidBytes || !looksLikeGguf(file)) -> {
                Log.w(TAG, "Deleting undersized/corrupt ${file.name} (${file.length()} bytes)")
                file.delete()
                ModelState.Missing
            }
            else -> ModelState.Missing
        }
    }

    private fun deleteLegacyFiles() {
        val keepNames = OPTIONS.map { it.fileName }.toSet()
        for (name in LEGACY_FILE_NAMES) {
            if (name in keepNames) continue
            File(modelsDir, name).takeIf { it.exists() }?.let {
                Log.i(TAG, "Deleting legacy local LLM file: ${it.name} (${it.length()} bytes)")
                it.delete()
            }
            File(modelsDir, "$name.partial").takeIf { it.exists() }?.delete()
        }
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
        if (option.id != selected.id) {
            selectModel(option.id)
        }
        val dest = modelFile(option)
        if (!forceRedownload && dest.exists() && dest.length() >= option.minValidBytes && looksLikeGguf(dest)) {
            _state.value = ModelState.Ready(dest.absolutePath)
            return@withContext Result.success(dest)
        }

        val partial = File(modelsDir, "${option.fileName}.partial")
        if (forceRedownload) {
            dest.delete()
            partial.delete()
        }

        var lastError: Exception? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                downloadResumable(option, dest, partial)
                _state.value = ModelState.Ready(dest.absolutePath)
                Log.i(TAG, "Abliterated GGUF ready: ${dest.absolutePath} (${dest.length()} bytes)")
                return@withContext Result.success(dest)
            } catch (e: Exception) {
                lastError = e
                Log.e(TAG, "ensureModel attempt ${attempt + 1}/$MAX_ATTEMPTS failed (keeping partial)", e)
                val msg = humanizeDownloadError(e, partial.length())
                _state.value = ModelState.Error(msg)
                if (attempt < MAX_ATTEMPTS - 1) {
                    delay(1_500L * (attempt + 1))
                }
            }
        }

        val fail = lastError ?: IllegalStateException("Model download failed")
        val msg = humanizeDownloadError(fail, partial.length())
        _state.value = ModelState.Error(msg)
        Result.failure(IllegalStateException(msg, fail))
    }

    private fun downloadResumable(option: LocalLlmOption, dest: File, partial: File) {
        var existing = when {
            partial.exists() -> partial.length()
            else -> 0L
        }
        // Corrupt tiny leftovers (HTML error pages) — restart
        if (existing in 1 until 64) {
            Log.w(TAG, "Discarding tiny partial (${existing}B)")
            partial.delete()
            existing = 0L
        } else if (existing >= 4 && !looksLikeGguf(partial)) {
            Log.w(TAG, "Discarding non-GGUF partial (${existing}B)")
            partial.delete()
            existing = 0L
        }

        val connection = openHfConnection(option.url, existing)
        try {
            connection.connect()
            var code = connection.responseCode
            // Some CDNs ignore Range and return 200 — restart from 0
            if (code == 200 && existing > 0L) {
                Log.i(TAG, "Server returned 200 for ranged request; restarting from 0")
                connection.disconnect()
                partial.delete()
                existing = 0L
                val fresh = openHfConnection(option.url, 0L)
                try {
                    fresh.connect()
                    code = fresh.responseCode
                    if (code !in 200..299) {
                        throw httpFailure(fresh, code)
                    }
                    streamToPartial(fresh, option, partial, existingBytes = 0L)
                } finally {
                    fresh.disconnect()
                }
                finalizePartial(option, dest, partial)
                return
            }

            if (code == HttpURLConnection.HTTP_PARTIAL || code in 200..299) {
                val resumeFrom = if (code == HttpURLConnection.HTTP_PARTIAL) existing else 0L
                if (code != HttpURLConnection.HTTP_PARTIAL && existing > 0L) {
                    // Unexpected 2xx without partial — treat as full body
                    partial.delete()
                }
                streamToPartial(connection, option, partial, existingBytes = resumeFrom)
                finalizePartial(option, dest, partial)
                return
            }

            throw httpFailure(connection, code)
        } finally {
            connection.disconnect()
        }
    }

    private fun openHfConnection(url: String, existingBytes: Long): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("User-Agent", "WildlifeFieldOps-Android/2.2")
            setRequestProperty("Accept", "application/octet-stream,*/*")
            hfTokenOrNull()?.let { token ->
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (existingBytes > 0L) {
                setRequestProperty("Range", "bytes=$existingBytes-")
            }
        }
    }

    private fun hfTokenOrNull(): String? {
        val fromPrefs = prefs.getString(KEY_HF_TOKEN, null)?.trim().orEmpty()
        if (fromPrefs.isNotEmpty()) return fromPrefs
        val fromBuild = BuildConfig.HF_TOKEN.trim()
        return fromBuild.takeIf { it.isNotEmpty() }
    }

    private fun streamToPartial(
        connection: HttpURLConnection,
        option: LocalLlmOption,
        partial: File,
        existingBytes: Long
    ) {
        val headerTotal = parseTotalBytes(connection, existingBytes)
        val total = headerTotal.takeIf { it > 0 } ?: option.expectedBytes
        var read = existingBytes
        _state.value = ModelState.Downloading(read, total)

        val append = existingBytes > 0L && partial.exists()
        connection.inputStream.use { input ->
            FileOutputStream(partial, append).use { output ->
                val buffer = ByteArray(256 * 1024)
                var firstChunk = !append
                while (true) {
                    val n = try {
                        input.read(buffer)
                    } catch (e: SocketTimeoutException) {
                        // Keep partial; outer retry resumes
                        throw IOException(
                            "Download stalled after ${read / (1024 * 1024)} MB " +
                                "(will resume on retry). ${e.message ?: ""}".trim(),
                            e
                        )
                    }
                    if (n <= 0) break
                    if (firstChunk) {
                        firstChunk = false
                        if (n >= 4 && !isGgufMagic(buffer, n)) {
                            throw IOException(
                                "Server did not return a GGUF file (got HTML/error). " +
                                    "Check network or set HF_TOKEN for Hugging Face."
                            )
                        }
                    }
                    output.write(buffer, 0, n)
                    read += n
                    _state.value = ModelState.Downloading(read, total)
                }
                output.flush()
            }
        }
    }

    private fun parseTotalBytes(connection: HttpURLConnection, existingBytes: Long): Long {
        val linked = connection.getHeaderField("X-Linked-Size")?.toLongOrNull()
        if (linked != null && linked > 0) return linked
        val range = connection.getHeaderField("Content-Range") // bytes a-b/total
        if (range != null) {
            val slash = range.substringAfterLast('/', missingDelimiterValue = "")
            slash.toLongOrNull()?.takeIf { it > 0 }?.let { return it }
        }
        val len = connection.contentLengthLong
        return when {
            len > 0 && existingBytes > 0 &&
                connection.responseCode == HttpURLConnection.HTTP_PARTIAL -> existingBytes + len
            len > 0 -> len
            else -> -1L
        }
    }

    private fun finalizePartial(option: LocalLlmOption, dest: File, partial: File) {
        val size = partial.length()
        if (size < option.minValidBytes) {
            throw IOException(
                "Downloaded file too small ($size bytes, need ≥ ${option.minValidBytes}). " +
                    "Partial kept — tap Retry to resume."
            )
        }
        if (!looksLikeGguf(partial)) {
            partial.delete()
            throw IOException("Downloaded file is not a valid GGUF — deleted. Try again.")
        }
        if (option.expectedBytes > 0 &&
            kotlin.math.abs(size - option.expectedBytes) > option.expectedBytes / 20
        ) {
            Log.w(TAG, "Size mismatch for ${option.fileName}: got $size, expected ${option.expectedBytes}")
        }
        if (dest.exists()) dest.delete()
        if (!partial.renameTo(dest)) {
            partial.copyTo(dest, overwrite = true)
            partial.delete()
        }
    }

    private fun httpFailure(connection: HttpURLConnection, code: Int): IOException {
        val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(240)
        return IOException("Abliterated GGUF download failed (HTTP $code). $err".trim())
    }

    private fun humanizeDownloadError(e: Exception, partialBytes: Long): String {
        val mb = partialBytes / (1024 * 1024)
        val base = e.message ?: "Model download failed"
        return if (partialBytes > 64L) {
            "$base (saved ${mb} MB — tap Retry to resume)"
        } else {
            base
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
        private const val KEY_HF_TOKEN = "hf_token"
        private const val MAX_ATTEMPTS = 4
        private const val CONNECT_TIMEOUT_MS = 60_000
        /** Per-read idle timeout; long enough for slow mobile, short enough to resume. */
        private const val READ_TIMEOUT_MS = 180_000

        val QWEN25_1_5B = LocalLlmOption(
            id = "qwen25-1.5b-abliterated-q4km",
            displayName = "Qwen2.5-1.5B-Instruct-abliterated (Q4_K_M)",
            shortLabel = "Qwen2.5-1.5B",
            upstreamRepo = "huihui-ai/Qwen2.5-1.5B-Instruct-abliterated",
            quantRepo = "mradermacher/Qwen2.5-1.5B-Instruct-abliterated-GGUF",
            fileName = "Qwen2.5-1.5B-Instruct-abliterated.Q4_K_M.gguf",
            expectedBytes = 986_049_088L,
            minValidBytes = 700L * 1024L * 1024L,
            approxSizeLabel = "~0.9 GB",
            isDefault = false
        )

        /** Default on-device model: Qwen2.5-3B Instruct abliterated Q4_K_M (mradermacher). */
        val QWEN25_3B = LocalLlmOption(
            id = "qwen25-3b-abliterated-q4km",
            displayName = "Qwen2.5-3B-Instruct-abliterated (Q4_K_M)",
            shortLabel = "Qwen2.5-3B",
            upstreamRepo = "huihui-ai/Qwen2.5-3B-Instruct-abliterated",
            quantRepo = "mradermacher/Qwen2.5-3B-Instruct-abliterated-GGUF",
            fileName = "Qwen2.5-3B-Instruct-Abliterated.Q4_K_M.gguf",
            expectedBytes = 2_104_933_600L,
            minValidBytes = 1_500L * 1024L * 1024L,
            approxSizeLabel = "~2.1 GB",
            isDefault = true
        )

        val LLAMA32_3B = LocalLlmOption(
            id = "llama32-3b-abliterated-q4km",
            displayName = "Llama-3.2-3B-Instruct-abliterated (Q4_K_M)",
            shortLabel = "Llama-3.2-3B",
            upstreamRepo = "huihui-ai/Llama-3.2-3B-Instruct-abliterated",
            quantRepo = "MaziyarPanahi/Llama-3.2-3B-Instruct-abliterated-GGUF",
            fileName = "Llama-3.2-3B-Instruct-abliterated.Q4_K_M.gguf",
            expectedBytes = 2_241_004_288L,
            minValidBytes = 1_600L * 1024L * 1024L,
            approxSizeLabel = "~2.2 GB",
            isDefault = false
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

        val OPTIONS: List<LocalLlmOption> = listOf(
            QWEN25_1_5B,
            QWEN25_3B,
            LLAMA32_3B,
            QWEN25_7B_V3
        )
        val DEFAULT_OPTION: LocalLlmOption = QWEN25_3B

        const val MODEL_BASE = "huihui-ai/Qwen2.5-3B-Instruct-abliterated"
        const val MODEL_REPO = "mradermacher/Qwen2.5-3B-Instruct-abliterated-GGUF"
        const val MODEL_FILE_NAME = "Qwen2.5-3B-Instruct-Abliterated.Q4_K_M.gguf"
        const val MODEL_QUANT = "Q4_K_M"
        const val MODEL_DISPLAY_NAME = "Qwen2.5-3B-Instruct-abliterated (Q4_K_M)"
        const val MODEL_URL =
            "https://huggingface.co/mradermacher/Qwen2.5-3B-Instruct-abliterated-GGUF/resolve/main/Qwen2.5-3B-Instruct-Abliterated.Q4_K_M.gguf?download=true"
        const val EXPECTED_BYTES = 2_104_933_600L
        const val MIN_VALID_BYTES = 1_500L * 1024L * 1024L

        private val LEGACY_FILE_NAMES = listOf(
            "Huihui-Qwen3.5-0.8B-abliterated.Q4_K_M.gguf",
            "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            // lowercase twin of default 3B; we keep Abliterated capital-A as the catalog file
            "Qwen2.5-3B-Instruct-abliterated.Q4_K_M.gguf"
        )

        private fun isGgufMagic(buf: ByteArray, len: Int): Boolean {
            if (len < 4) return false
            return buf[0] == 'G'.code.toByte() &&
                buf[1] == 'G'.code.toByte() &&
                buf[2] == 'U'.code.toByte() &&
                buf[3] == 'F'.code.toByte()
        }

        private fun looksLikeGguf(file: File): Boolean {
            if (!file.exists() || file.length() < 4L) return false
            return try {
                RandomAccessFile(file, "r").use { raf ->
                    val magic = ByteArray(4)
                    raf.readFully(magic)
                    isGgufMagic(magic, 4)
                }
            } catch (_: Exception) {
                false
            }
        }
    }
}
