package com.strobingn.wildlifefieldops.ai.asr

/**
 * Appends a voice observation block onto a job's notes field.
 * Each capture is appended (not replaced) so prior notes stay intact.
 */
object VoiceObservationNoteStamp {
    const val MARKER = "--- Voice observation ---"

    fun appendToJobNotes(
        existingNotes: String,
        transcript: String,
        audioSha256: String,
        audioUri: String,
        committedAt: Long,
    ): String {
        val body = buildString {
            append(MARKER)
            append('\n')
            append(transcript.trim())
            append('\n')
            append("audio=")
            append(audioSha256)
            append(" uri=")
            append(audioUri)
            append(" at=")
            append(committedAt)
        }
        val prefix = existingNotes.trim()
        return if (prefix.isEmpty()) body else "$prefix\n\n$body"
    }
}
