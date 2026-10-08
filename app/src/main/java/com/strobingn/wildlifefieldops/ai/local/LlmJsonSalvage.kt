package com.strobingn.wildlifefieldops.ai.local

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Pure helpers for reading JSON out of LLM output that may be wrapped in markdown fences,
 * prefixed with prose, or cut off mid-string by the token limit (local generation is capped).
 * No Android dependencies so they stay JVM unit-testable.
 */
object LlmJsonSalvage {

    /** Strip ```json fences and any prose around the outermost `{ ... }` (or the open tail if truncated). */
    fun extractObjectText(raw: String): String {
        var text = raw.trim()
        text = text.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trim()
        text = text.removeSuffix("```").trim()
        val start = text.indexOf('{')
        if (start < 0) return text
        val end = text.lastIndexOf('}')
        return if (end > start) text.substring(start, end + 1) else text.substring(start)
    }

    /** True when the output looks like a JSON object (complete or truncated) rather than prose. */
    fun looksLikeJson(raw: String): Boolean {
        val t = raw.trim().removePrefix("```json").removePrefix("```").trim()
        return t.startsWith("{") || Regex("\"[A-Za-z_]+\"\\s*:").containsMatchIn(t)
    }

    /**
     * Reads a string field. A value cut off by truncation (no closing quote) is returned with a
     * trailing ellipsis so the reader can tell it is incomplete.
     */
    fun extractString(json: String, key: String): String? {
        val match = Regex(
            "\"" + Regex.escape(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)(\")?",
            RegexOption.DOT_MATCHES_ALL
        ).find(json) ?: return null
        val body = unescape(match.groupValues[1])
        val closed = match.groups[2] != null
        if (body.isBlank()) return null
        return if (closed) body else body.trimEnd() + "…"
    }

    /** Reads a numeric field; ignores a number that touches the end of the text (it may be cut off). */
    fun extractNumber(json: String, key: String): Double? {
        val match = Regex(
            "\"" + Regex.escape(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)\\s*[,}\\n\\r]"
        ).find(json) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    /** Reads an array of strings; stops at `]` or at the end of a truncated text, dropping a cut-off last item. */
    fun extractStringList(json: String, key: String): List<String> {
        val open = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\\[").find(json) ?: return emptyList()
        val out = ArrayList<String>()
        var i = open.range.last + 1
        while (i < json.length) {
            val c = json[i]
            when {
                c == ']' -> return out
                c == '"' -> {
                    val sb = StringBuilder()
                    i++
                    var closed = false
                    while (i < json.length) {
                        val ch = json[i]
                        if (ch == '\\' && i + 1 < json.length) {
                            sb.append(ch).append(json[i + 1])
                            i += 2
                            continue
                        }
                        if (ch == '"') {
                            closed = true
                            i++
                            break
                        }
                        sb.append(ch)
                        i++
                    }
                    if (!closed) return out
                    val item = unescape(sb.toString()).trim()
                    if (item.isNotEmpty()) out.add(item)
                }
                else -> i++
            }
        }
        return out
    }

    internal fun unescape(s: String): String {
        if (s.indexOf('\\') < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c != '\\' || i + 1 >= s.length) {
                sb.append(c)
                i++
                continue
            }
            when (val n = s[i + 1]) {
                'n' -> { sb.append('\n'); i += 2 }
                't' -> { sb.append('\t'); i += 2 }
                'r' -> { i += 2 }
                '"' -> { sb.append('"'); i += 2 }
                '\\' -> { sb.append('\\'); i += 2 }
                '/' -> { sb.append('/'); i += 2 }
                'u' -> {
                    val hex = if (i + 6 <= s.length) s.substring(i + 2, i + 6) else null
                    val code = hex?.toIntOrNull(16)
                    if (code != null) {
                        sb.append(code.toChar())
                        i += 6
                    } else {
                        sb.append(n)
                        i += 2
                    }
                }
                else -> { sb.append(n); i += 2 }
            }
        }
        return sb.toString()
    }
}

/**
 * Runs [block] on a detached IO scope and waits at most [timeoutMs] for it.
 *
 * `withTimeout` cannot interrupt blocking calls (HttpURLConnection, llama.cpp JNI): the caller
 * would still wait for the block to finish. Here the caller stops waiting on time and gets `null`
 * (the blocking work is cancelled best-effort and abandoned). Exceptions from [block] propagate.
 */
object HardTimeout {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun <T> run(timeoutMs: Long, block: suspend () -> T): T? {
        val job = scope.async { block() }
        return try {
            withTimeoutOrNull(timeoutMs) { job.await() }
        } finally {
            if (job.isActive) job.cancel()
        }
    }
}
