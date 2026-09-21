package com.strobingn.wildlifefieldops.data.voice

import com.strobingn.wildlifefieldops.ai.asr.AudioArtifact
import com.strobingn.wildlifefieldops.ai.asr.InferenceBackend
import com.strobingn.wildlifefieldops.ai.asr.TranscriptState
import com.strobingn.wildlifefieldops.ai.asr.VoiceCaptureOrchestrator
import com.strobingn.wildlifefieldops.ai.asr.VoiceObservationCommitDecision
import com.strobingn.wildlifefieldops.ai.asr.VoiceObservationCommitGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CapturedUtteranceEngineTest {

    private val audio = AudioArtifact(
        uri = "/tmp/voice.wav",
        sha256 = "d".repeat(64),
        durationMs = 3_200L,
        capturedAt = 10L,
    )

    @Test
    fun emptyUtterance_isFailClosed_andDoesNotFile() {
        val store = FileBackedAudioStore()
        val orchestrator = VoiceCaptureOrchestrator(
            audioStore = store,
            transcriptionEngine = CapturedUtteranceEngine(capturedText = ""),
        )
        orchestrator.saveAudioAtomic(audio.uri, audio.sha256, audio.durationMs)
        val outcome = orchestrator.runTranscription(
            audio = audio,
            modelSha256 = CapturedUtteranceEngine.MODEL_SHA256,
            runtimeVersion = CapturedUtteranceEngine.RUNTIME_VERSION,
            deviceKey = "test/device/cpu",
            windowConfig = CapturedUtteranceEngine.WINDOW_CONFIG,
        )
        assertTrue(outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Failure)
        assertEquals(
            TranscriptState.FAILED,
            (outcome as VoiceCaptureOrchestrator.TranscriptionOutcome.Failure).state,
        )
        assertTrue(store.exists(audio.sha256))
        assertEquals(null, store.transcript(audio.sha256))

        val decision = VoiceObservationCommitGate.evaluate(
            outcome = outcome,
            audio = audio,
            jobId = "job-1",
            observationEventId = null,
            noteId = "note-empty",
            committedAt = 20L,
        )
        assertTrue(decision is VoiceObservationCommitDecision.Rejected)
        assertTrue((decision as VoiceObservationCommitDecision.Rejected).audioRetained)
    }

    @Test
    fun trustedUtterance_filesAgainstJob() {
        val store = FileBackedAudioStore()
        val orchestrator = VoiceCaptureOrchestrator(
            audioStore = store,
            transcriptionEngine = CapturedUtteranceEngine("Skunk under the porch."),
        )
        orchestrator.saveAudioAtomic(audio.uri, audio.sha256, audio.durationMs)
        val outcome = orchestrator.runTranscription(
            audio = audio,
            modelSha256 = CapturedUtteranceEngine.MODEL_SHA256,
            runtimeVersion = CapturedUtteranceEngine.RUNTIME_VERSION,
            deviceKey = "test/device/cpu",
            windowConfig = CapturedUtteranceEngine.WINDOW_CONFIG,
        )
        val decision = VoiceObservationCommitGate.evaluate(
            outcome = outcome,
            audio = audio,
            jobId = "job-1",
            observationEventId = null,
            noteId = "note-ok",
            committedAt = 21L,
        )
        val ready = decision as VoiceObservationCommitDecision.Ready
        assertEquals("Skunk under the porch.", ready.note.editedTranscript)
        assertEquals("job-1", ready.note.jobId)
        assertEquals(InferenceBackend.CPU, outcome.attempts.single().backend)
    }
}
