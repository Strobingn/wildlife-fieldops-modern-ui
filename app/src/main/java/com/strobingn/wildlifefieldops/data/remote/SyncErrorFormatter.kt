package com.strobingn.wildlifefieldops.data.remote

/**
 * Turns PostgREST / Storage / network throwables into a short operator-readable reason.
 */
object SyncErrorFormatter {
    fun reason(error: Throwable): String {
        val text = buildString {
            generateSequence(error) { it.cause }.forEach { part ->
                val message = part.message?.trim().orEmpty()
                if (message.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(message)
                }
            }
        }.ifBlank { error.javaClass.simpleName }
        val lower = text.lowercase()
        return when {
            "pgrst204" in lower || "could not find the" in lower && "column" in lower ->
                "Cloud table is missing a column the app sent. $text"
            "23502" in lower || "null value in column" in lower ->
                "Cloud rejected a required empty field. $text"
            "23503" in lower || "foreign key" in lower ->
                "Related row is not on the server yet (job/customer). Will retry. $text"
            hasCode(lower, "23505") || "duplicate" in lower || hasCode(lower, "409") ->
                "Already on server. $text"
            "42501" in lower || "permission denied" in lower || "row-level security" in lower || hasCode(lower, "rls") ->
                "Cloud permission denied (RLS/grant). $text"
            "jwtexpired" in lower || "invalid jwt" in lower ->
                "Cloud key rejected. Rebuild the APK with current Supabase secrets. $text"
            "unable to resolve host" in lower || "failed to connect" in lower || "timeout" in lower ->
                "Network error. $text"
            "bucket" in lower && ("not found" in lower || "not exist" in lower) ->
                "Storage bucket missing. $text"
            else -> text.take(400)
        }
    }

    fun isDuplicate(error: Throwable): Boolean {
        val text = buildString {
            generateSequence(error) { it.cause }.forEach { append(it.message.orEmpty()).append(' ') }
        }.lowercase()
        return "duplicate" in text ||
            "already exists" in text ||
            hasCode(text, "23505") ||
            hasCode(text, "409")
    }

    fun isTransient(error: Throwable): Boolean {
        val text = buildString {
            generateSequence(error) { it.cause }.forEach { append(it.message.orEmpty()).append(' ') }
        }.lowercase()
        return "timeout" in text ||
            "unable to resolve host" in text ||
            "failed to connect" in text ||
            "connection reset" in text ||
            hasCode(text, "503") ||
            hasCode(text, "502") ||
            hasCode(text, "504") ||
            hasCode(text, "429")
    }

    /**
     * True when [code] stands alone in lower-cased [text]. A bare substring test matched ids
     * and paths (a UUID containing "409" made an unrelated failure look like a duplicate, so
     * an event that was never inserted was marked synced).
     */
    internal fun hasCode(text: String, code: String): Boolean =
        Regex("(?<![0-9a-z])" + Regex.escape(code) + "(?![0-9a-z])").containsMatchIn(text)
}
