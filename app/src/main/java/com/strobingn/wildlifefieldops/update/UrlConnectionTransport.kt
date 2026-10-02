package com.strobingn.wildlifefieldops.update

import com.strobingn.wildlifefieldops.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class UrlConnectionTransport @javax.inject.Inject constructor() : AppUpdateTransport {
    private val userAgent: String = "WildlifeFieldOps-Android/${BuildConfig.VERSION_NAME}"

    override fun getText(url: String): AppUpdateHttpResponse {
        val connection = open(url, accept = "application/json,text/plain,*/*", noCache = true)
        try {
            connection.connect()
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return AppUpdateHttpResponse(
                code = code,
                body = body,
                rateLimitRemaining = connection.getHeaderField("X-RateLimit-Remaining")
            )
        } finally {
            connection.disconnect()
        }
    }

    override fun download(url: String, dest: File, onProgress: (read: Long, total: Long) -> Unit) {
        dest.parentFile?.mkdirs()
        val partial = File(dest.parentFile, "${dest.name}.part")
        if (partial.exists()) partial.delete()
        val connection = open(url, accept = "application/vnd.android.package-archive,application/octet-stream,*/*")
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw java.io.IOException(
                    AppUpdateManifestParser.httpErrorMessage(
                        code,
                        err,
                        connection.getHeaderField("X-RateLimit-Remaining")
                    )
                )
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: -1L
            var read = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        read += n
                        onProgress(read, total)
                    }
                    output.flush()
                }
            }
            if (dest.exists()) dest.delete()
            if (!partial.renameTo(dest)) {
                partial.copyTo(dest, overwrite = true)
                partial.delete()
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, accept: String, noCache: Boolean = false): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", accept)
            if (noCache) {
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
            }
        }
    }
}
