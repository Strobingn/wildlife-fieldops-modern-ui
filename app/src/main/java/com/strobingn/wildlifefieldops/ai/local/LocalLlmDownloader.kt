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
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class LocalLlmDownloadProgress(
    val modelId: String = "",
    val bytesRead: Long = 0L,
    val totalBytes: Long = 0L,
    val running: Boolean = false,
    val error: String? = null
) {
    val fraction: Float
        get() = if (totalBytes <= 0L) 0f else (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
}

@Singleton
class LocalLlmDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _progress = MutableStateFlow(LocalLlmDownloadProgress())
    val progress: StateFlow<LocalLlmDownloadProgress> = _progress.asStateFlow()

    fun modelsDir(): File = File(context.filesDir, "local-llm").apply { mkdirs() }

    fun modelFile(spec: LocalLlmSpec): File = File(modelsDir(), spec.fileName)

    fun isDownloaded(spec: LocalLlmSpec): Boolean {
        val file = modelFile(spec)
        if (!file.exists()) return false
        val minAcceptable = (spec.expectedBytes * 0.98).toLong()
        return file.length() >= minAcceptable
    }

    fun delete(spec: LocalLlmSpec) {
        modelFile(spec).delete()
        File(modelsDir(), spec.fileName + ".part").delete()
    }

    suspend fun download(spec: LocalLlmSpec): Result<File> = withContext(Dispatchers.IO) {
        val dest = modelFile(spec)
        if (isDownloaded(spec)) {
            _progress.value = LocalLlmDownloadProgress(
                modelId = spec.id,
                bytesRead = dest.length(),
                totalBytes = spec.expectedBytes,
                running = false
            )
            return@withContext Result.success(dest)
        }

        val part = File(modelsDir(), spec.fileName + ".part")
        var existing = if (part.exists()) part.length() else 0L
        _progress.value = LocalLlmDownloadProgress(
            modelId = spec.id,
            bytesRead = existing,
            totalBytes = spec.expectedBytes,
            running = true
        )

        try {
            val connection = open(spec.downloadUrl, existing)
            val code = connection.responseCode
            if (code == 416) {
                connection.disconnect()
                if (part.exists()) part.renameTo(dest)
                return@withContext if (isDownloaded(spec)) Result.success(dest)
                else Result.failure(IllegalStateException("Incomplete download (HTTP 416)"))
            }
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                val msg = when (code) {
                    401, 403 -> "Download blocked (HTTP $code). Model may require Hugging Face access."
                    404 -> "Model file not found (HTTP 404)."
                    else -> "Download failed HTTP $code ${err.take(120)}"
                }
                _progress.value = _progress.value.copy(running = false, error = msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            val totalFromHeader = connection.getHeaderField("Content-Range")
                ?.substringAfter("/")
                ?.toLongOrNull()
                ?: (existing + connection.contentLengthLong.coerceAtLeast(0L))
            val total = if (totalFromHeader > 0L) totalFromHeader else spec.expectedBytes
            if (code == 200) {
                existing = 0L
                if (part.exists()) part.delete()
            }

            RandomAccessFile(part, "rw").use { raf ->
                raf.seek(existing)
                connection.inputStream.use { input ->
                    val buf = ByteArray(256 * 1024)
                    var read = existing
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        raf.write(buf, 0, n)
                        read += n
                        _progress.value = LocalLlmDownloadProgress(
                            modelId = spec.id,
                            bytesRead = read,
                            totalBytes = total,
                            running = true
                        )
                    }
                }
            }
            connection.disconnect()

            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
            if (!isDownloaded(spec)) {
                val msg = "Downloaded file incomplete (${dest.length()} bytes, expected ${spec.expectedBytes})."
                _progress.value = _progress.value.copy(running = false, error = msg)
                return@withContext Result.failure(IllegalStateException(msg))
            }
            _progress.value = LocalLlmDownloadProgress(
                modelId = spec.id,
                bytesRead = dest.length(),
                totalBytes = spec.expectedBytes,
                running = false
            )
            Result.success(dest)
        } catch (t: Throwable) {
            Log.e(TAG, "download failed", t)
            val msg = t.message ?: t.javaClass.simpleName
            _progress.value = _progress.value.copy(running = false, error = msg)
            Result.failure(t)
        }
    }

    private fun open(url: String, resumeFrom: Long): HttpURLConnection {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("User-Agent", "WildlifeFieldOps/2.2 (Android; LiteRT-LM)")
            setRequestProperty("Accept", "*/*")
            if (resumeFrom > 0L) setRequestProperty("Range", "bytes=$resumeFrom-")
        }
        connection.connect()
        return connection
    }

    companion object {
        private const val TAG = "LocalLlmDownloader"
    }
}
