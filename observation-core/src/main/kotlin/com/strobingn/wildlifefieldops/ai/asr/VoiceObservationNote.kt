package com.strobingn.wildlifefieldops.ai.asr

/**
 * Filed voice observation: a validated transcript plus the retained audio,
 * attached to a job and/or an [com.strobingn.wildlifefieldops.data.observation.ObservationEvent].
 *
 * Created only through [VoiceObservationCommitGate]. Empty or untrusted
 * transcripts never become a note.
 */
data class VoiceObservationNote(
    val noteId: String,
    val jobId: String?,
    val observationEventId: String?,
    val audioSha256: String,
    val audioUri: String,
    val durationMs: Long,
    /** ASR-validated text from [DerivedTranscript]. Never blank. */
    val validatedTranscript: String,
    /** Technician-editable copy shown in review. Never blank. */
    val editedTranscript: String,
    val winningAttemptId: String,
    val committedAt: Long,
) {
    init {
        require(noteId.isNotBlank()) { "VoiceObservationNote.noteId must not be blank" }
        require(!jobId.isNullOrBlank() || !observationEventId.isNullOrBlank()) {
            "Voice observation must be filed against a job or ObservationEvent"
        }
        require(audioSha256.matches(SHA256_HEX)) {
            "VoiceObservationNote.audioSha256 must be a 64-char hex digest"
        }
        require(audioUri.isNotBlank()) { "VoiceObservationNote.audioUri must not be blank" }
        require(durationMs > 0) { "VoiceObservationNote.durationMs must be > 0" }
        require(validatedTranscript.isNotBlank()) {
            "VoiceObservationNote.validatedTranscript must not be blank"
        }
        require(editedTranscript.isNotBlank()) {
            "VoiceObservationNote.editedTranscript must not be blank"
        }
        require(winningAttemptId.isNotBlank()) {
            "VoiceObservationNote.winningAttemptId must not be blank"
        }
    }

    companion object {
        private val SHA256_HEX = Regex("[0-9a-fA-F]{64}")
    }
}

/** Outcome of the observation-note commit gate (layer above [TranscriptState]). */
sealed class VoiceObservationCommitDecision {
    data class Ready(val note: VoiceObservationNote) : VoiceObservationCommitDecision()

    data class Rejected(
        val reason: String,
        val state: TranscriptState,
        val audioSha256: String?,
        val audioRetained: Boolean = true,
    ) : VoiceObservationCommitDecision()
}
