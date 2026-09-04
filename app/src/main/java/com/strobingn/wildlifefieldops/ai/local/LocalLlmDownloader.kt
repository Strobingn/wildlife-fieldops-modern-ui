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

    fun hasBundledAsset(spec: LocalLlmSpec): Boolean {
        return try {
            context.assets.open(spec.assetPath).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isOnDisk(spec: LocalLlmSpec): Boolean {
        val file = modelFile(spec)
        if (!file.exists()) return false
        val minBytes = minOf((spec.expectedBytes * 0.90).toLong(), spec.expectedBytes - 8_000_000L)
        val floor = 400_000_000L
        return file.length() >= maxOf(floor, minBytes.coerceAtLeast(floor))
    }

    fun isDownloaded(spec: LocalLlmSpec): Boolean = isOnDisk(spec)

    fun delete(spec: LocalLlmSpec) {
        modelFile(spec).delete()
        File(modelsDir(), spec.fileName + ".part").delete()
    }

    /** Copy the APK-baked default model into filesDir so LiteRT-LM can mmap a real path. */
    suspend fun extractBundledIfNeeded(spec: LocalLlmSpec): Result<File> = withContext(Dispatchers.IO) {
        val dest = modelFile(spec)
        if (isOnDisk(spec)) return@withContext Result.success(dest)
        if (!hasBundledAsset(spec)) {
            return@withContext Result.failure(IllegalStateException("No baked asset for ${spec.fileName}"))
        }
        _progress.value = LocalLlmDownloadProgress(
            modelId = spec.id,
            bytesRead = 0L,
            totalBytes = spec.expectedBytes,
            running = true
        )
        try {
            context.assets.open(spec.assetPath).use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(256 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (read % (8L * 1024L * 1024L) < buf.size) {
                            _progress.value = LocalLlmDownloadProgress(
                                modelId = spec.id,
                                bytesRead = read,
                                totalBytes = spec.expectedBytes,
                                running = true
                            )
                        }
                    }
                }
            }
            _progress.value = LocalLlmDownloadProgress(
                modelId = spec.id,
                bytesRead = dest.length(),
                totalBytes = dest.length(),
                running = false
            )
            Result.success(dest)
        } catch (t: Throwable) {
            dest.delete()
            Log.e(TAG, "extract bundled failed", t)
            _progress.value = _progress.value.copy(running = false, error = t.message)
            Result.failure(t)
        }
    }

    suspend fun ensureAvailable(spec: LocalLlmSpec): Result<File> {
        if (isOnDisk(spec)) return Result.success(modelFile(spec))
        if (hasBundledAsset(spec)) return extractBundledIfNeeded(spec)
        return download(spec)
    }

    suspend fun download(spec: LocalLlmSpec): Result<File> = withContext(Dispatchers.IO) {
        val dest = modelFile(spec)
        if (isOnDisk(spec)) {
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
                return@withContext if (isOnDisk(spec)) Result.success(dest)
                else Result.failure(IllegalStateException("Incomplete download (HTTP 416)"))
            }
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                connection.disconnect()
                val msg = when (code) {
                    401, 403 -> "Download blocked (HTTP $code). Add repo secret HF_TOKEN with access to this model, then rebuild."
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
            if (!isOnDisk(spec)) {
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
