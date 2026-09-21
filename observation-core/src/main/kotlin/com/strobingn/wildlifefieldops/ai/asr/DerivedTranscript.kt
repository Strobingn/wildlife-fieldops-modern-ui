package com.strobingn.wildlifefieldops.ai.asr

/**
 * Committed result of a successful voice-capture transcription pipeline.
 *
 * ## Invariants
 *  - Created ONLY when the final [TranscriptState] is [TranscriptState.VALID].
 *  - The [AudioArtifact] identified by [audioSha256] MUST be persisted in the
 *    [AudioStore] before this object can be created.  The [VoiceCaptureOrchestrator]
 *    enforces this at runtime; the [create] factory enforces structural consistency.
 *  - [validatedText] is the trimmed, validated text.  It may be empty ONLY when
 *    the audio was independently classified as silence.
 *  - All [TranscriptAttempt]s that contributed to this result are preserved in
 *    [allAttempts] for audit and provenance purposes.
 *
 * Use [create] to construct instances; the primary constructor is private to
 * enforce the invariants above.
 */
data class DerivedTranscript private constructor(
    /** SHA-256 of the [AudioArtifact] this transcript was derived from. */
    val audioSha256: String,

    /**
     * Validated, trimmed text suitable for use as an observation note.
     * Empty only when audio was classified as silence.
     */
    val validatedText: String,

    /** [TranscriptAttempt.attemptId] of the attempt that produced [validatedText]. */
    val winningAttemptId: String,

    /**
     * All [TranscriptAttempt]s made for this audio, in chronological order
     * (accelerator attempt first, CPU fallback last when applicable).
     */
    val allAttempts: List<TranscriptAttempt>,

    /** Epoch-ms when this transcript was committed to the store. */
    val committedAt: Long,
) {
    companion object {
        /**
         * Create and validate a [DerivedTranscript].
         *
         * @param audio            The persisted source [AudioArtifact].
         * @param validatedText    The validated text (may be empty only for silence).
         * @param winningAttemptId [TranscriptAttempt.attemptId] of the winning run.
         * @param allAttempts      All attempts, ordered chronologically.
         * @param committedAt      Epoch-ms (defaults to [System.currentTimeMillis]).
         * @throws IllegalArgumentException if any invariant is violated.
         */
        fun create(
            audio: AudioArtifact,
            validatedText: String,
            winningAttemptId: String,
            allAttempts: List<TranscriptAttempt>,
            committedAt: Long = System.currentTimeMillis(),
        ): DerivedTranscript {
            // Allow empty string (silence) but reject non-empty whitespace-only strings.
            require(validatedText.isEmpty() || validatedText.isNotBlank()) {
                "DerivedTranscript.validatedText must not be whitespace-only; " +
                    "use empty string only for silence-classified audio"
            }
            require(allAttempts.isNotEmpty()) {
                "DerivedTranscript requires at least one TranscriptAttempt"
            }
            require(allAttempts.any { it.attemptId == winningAttemptId }) {
                "winningAttemptId '$winningAttemptId' not found in allAttempts " +
                    "(ids: ${allAttempts.map { it.attemptId }})"
            }
            require(allAttempts.all { it.audioSha256 == audio.sha256 }) {
                "All TranscriptAttempts must reference audioSha256='${audio.sha256}'"
            }
            return DerivedTranscript(
                audioSha256 = audio.sha256,
                validatedText = validatedText,
                winningAttemptId = winningAttemptId,
                allAttempts = allAttempts.toList(),
                committedAt = committedAt,
            )
        }
    }
}
