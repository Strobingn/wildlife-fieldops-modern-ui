package com.strobingn.wildlifefieldops.ai.asr

/**
 * Fail-closed gate between [VoiceCaptureOrchestrator] and a filed field note.
 *
 * ADR 0003 already prevents [DerivedTranscript] creation unless
 * [TranscriptState.VALID]. This gate adds the observation-filing rules:
 *
 *  - [VoiceCaptureOrchestrator.TranscriptionOutcome.Failure] never files a note.
 *  - Empty validated text (including silence-classified VALID) never files a note.
 *  - A blank technician edit never files a note.
 *  - A note must target a job and/or an ObservationEvent.
 *  - Audio is treated as retained regardless of the decision (the orchestrator
 *    already persisted it via [AudioStore.save]).
 */
object VoiceObservationCommitGate {

    fun evaluate(
        outcome: VoiceCaptureOrchestrator.TranscriptionOutcome,
        audio: AudioArtifact,
        jobId: String?,
        observationEventId: String?,
        editedTranscript: String? = null,
        noteId: String,
        committedAt: Long,
    ): VoiceObservationCommitDecision {
        val targetJob = jobId?.trim()?.takeIf { it.isNotEmpty() }
        val targetEvent = observationEventId?.trim()?.takeIf { it.isNotEmpty() }

        when (outcome) {
            is VoiceCaptureOrchestrator.TranscriptionOutcome.Failure -> {
                return VoiceObservationCommitDecision.Rejected(
                    reason = "Untrusted transcript — not filed: ${outcome.reason}",
                    state = outcome.state,
                    audioSha256 = audio.sha256,
                    audioRetained = true,
                )
            }
            is VoiceCaptureOrchestrator.TranscriptionOutcome.Success -> {
                if (outcome.state != TranscriptState.VALID) {
                    return VoiceObservationCommitDecision.Rejected(
                        reason = "Transcript state ${outcome.state} is not VALID — not filed",
                        state = outcome.state,
                        audioSha256 = audio.sha256,
                        audioRetained = true,
                    )
                }
                val validated = outcome.derived.validatedText.trim()
                if (validated.isEmpty()) {
                    return VoiceObservationCommitDecision.Rejected(
                        reason = "Empty transcript must not be committed as an observation note",
                        state = TranscriptState.VALID,
                        audioSha256 = audio.sha256,
                        audioRetained = true,
                    )
                }
                if (outcome.derived.audioSha256 != audio.sha256) {
                    return VoiceObservationCommitDecision.Rejected(
                        reason = "Transcript audio SHA-256 does not match retained artifact",
                        state = TranscriptState.FAILED,
                        audioSha256 = audio.sha256,
                        audioRetained = true,
                    )
                }
                val edited = (editedTranscript ?: validated).trim()
                if (edited.isEmpty()) {
                    return VoiceObservationCommitDecision.Rejected(
                        reason = "Editable transcript is blank — not filed",
                        state = TranscriptState.VALID,
                        audioSha256 = audio.sha256,
                        audioRetained = true,
                    )
                }
                if (targetJob == null && targetEvent == null) {
                    return VoiceObservationCommitDecision.Rejected(
                        reason = "No current job or ObservationEvent to file against",
                        state = TranscriptState.VALID,
                        audioSha256 = audio.sha256,
                        audioRetained = true,
                    )
                }
                return VoiceObservationCommitDecision.Ready(
                    VoiceObservationNote(
                        noteId = noteId,
                        jobId = targetJob,
                        observationEventId = targetEvent,
                        audioSha256 = audio.sha256,
                        audioUri = audio.uri,
                        durationMs = audio.durationMs,
                        validatedTranscript = validated,
                        editedTranscript = edited,
                        winningAttemptId = outcome.derived.winningAttemptId,
                        committedAt = committedAt,
                    )
                )
            }
        }
    }
}
