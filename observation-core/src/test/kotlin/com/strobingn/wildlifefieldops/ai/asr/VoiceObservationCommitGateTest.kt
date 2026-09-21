package com.strobingn.wildlifefieldops.ai.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Fail-closed observation-filing tests for roadmap item 4 (voice-first logging).
 *
 * ADR 0003 remains in force: empty or untrusted transcripts must not become
 * a job / ObservationEvent note. Audio is always treated as retained.
 */
class VoiceObservationCommitGateTest {

    private val sha256Audio = "a".repeat(64)
    private val sha256Model = "b".repeat(64)
    private val runtimeVersion = "litert-lm-1.0.1"
    private val deviceKey = "google/Pixel_8_Pro/tensor_g3"
    private val windowConfig = "chunkMs=2000,overlapMs=200"

    private val audio = AudioArtifact(
        uri = "file:///data/audio/voice-log.wav",
        sha256 = sha256Audio,
        durationMs = 4_000L,
        capturedAt = 1_000_000L,
    )

    private var fakeTime = 2_000_000L
    private val clock: () -> Long = { fakeTime++ }
    private lateinit var store: RecordingAudioStore

    @Before
    fun setup() {
        fakeTime = 2_000_000L
        store = RecordingAudioStore()
    }

    @Test
    fun validTranscript_filesAgainstJob_withAudioAndEditableText() {
        val outcome = transcribe(rawText = "Raccoon in the soffit, live trap set.")
        assertTrue(outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Success)

        val decision = VoiceObservationCommitGate.evaluate(
            outcome = outcome,
            audio = audio,
            jobId = "job-42",
            observationEventId = null,
            editedTranscript = "Raccoon in the soffit, live trap set on east wall.",
            noteId = "note-1",
            committedAt = 9_000L,
        )

        val ready = decision as VoiceObservationCommitDecision.Ready
        assertEquals("job-42", ready.note.jobId)
        assertNull(ready.note.observationEventId)
        assertEquals(sha256Audio, ready.note.audioSha256)
        assertEquals("file:///data/audio/voice-log.wav", ready.note.audioUri)
        assertEquals("Raccoon in the soffit, live trap set.", ready.note.validatedTranscript)
        assertEquals(
            "Raccoon in the soffit, live trap set on east wall.",
            ready.note.editedTranscript,
        )
        assertEquals(1, store.committedTranscripts.size)
    }

    @Test
    fun validTranscript_filesAgainstObservationEvent() {
        val outcome = transcribe(rawText = "Gray squirrel nest confirmed.")
        val decision = VoiceObservationCommitGate.evaluate(
            outcome = outcome,
            audio = audio,
            jobId = null,
            observationEventId = "evt-99",
            noteId = "note-2",
            committedAt = 9_001L,
        )
        val ready = decision as VoiceObservationCommitDecision.Ready
        assertEquals("evt-99", ready.note.observationEventId)
        assertNull(ready.note.jobId)
        assertEquals("Gray squirrel nest confirmed.", ready.note.editedTranscript)
    }

    @Test
    fun emptyTranscript_doesNotFile_evenWhenAudioRetained() {
        val outcome = transcribe(rawText = "")
        assertTrue(outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Failure)

        val decision = VoiceObservationCommitGate.evaluate(
            outcome = outcome,
            audio = audio,
            jobId = "job-42",
            observationEventId = null,
            noteId = "note-3",
            committedAt = 9_002L,
        )

        val rejected = decision as VoiceObservationCommitDecision.Rejected
        assertEquals(TranscriptState.FAILED, rejected.state)
        assertTrue(rejected.audioRetained)
        assertEquals(sha256Audio, rejected.audioSha256)
        assertTrue(rejected.reason.contains("Untrusted", ignoreCase = true))
        assertTrue(store.committedTranscripts.isEmpty())
        assertTrue(store.exists(sha256Audio))
    }

    @Test
    fun whitespaceOnlyTranscript_doesNotFile() {
        val outcome = transcribe(rawText = "   \n\t  ")
        assertTrue(outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Failure)

        val decision = VoiceObservationCommitGate.evaluate(
            outcome = outcome,
            audio = audio,
            jobId = "job-42",
            observationEventId = null,
            noteId = "note-4",
            committedAt = 9_003L,
        )
        assertTrue(decision is VoiceObservationCommitDecision.Rejected)
        assertTrue(store.committedTranscripts.isEmpty())
    }

    @Test
    fun tokenSpam_doesNotFile() {
        val spam = List(12) { "raccoon" }.joinToString(" ")
        val outcome = transcribe(rawText = spam)
        assertTrue(outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Failure)

        val decision = VoiceObservationCommitGate.evaluate(
            outcome = outcome,
            audio = audio,
            jobId = "job-42",
            observationEventId = null,
            noteId = "note-5",
            committedAt = 9_004L,
        )
        val rejected = decision as VoiceObservationCommitDecision.Rejected
        assertTrue(rejected.audioRetained)
        assertTrue(store.committedTranscripts.isEmpty())
    }

    @Test
    fun silenceValidEmpty_doesNotFileObservationNote() {
        val silentValidator = object : TranscriptValidator {
            override fun validate(
                audio: AudioArtifact,
                attempt: TranscriptAttempt,
            ): TranscriptValidator.ValidationResult =
                TranscriptValidator.ValidationResult.Ok(validatedText = "")

            override fun isSilentAudio(audio: AudioArtifact) = true
        }
        val orchestrator = VoiceCaptureOrchestrator(
            audioStore = store,
            transcriptionEngine = FixedEngine(""),
            validator = silentValidator,
            policy = CpuOnlyPolicy,
            clock = clock,
        )
        orchestrator.saveAudioAtomic(audio.uri, audio.sha256, audio.durationMs)
        val outcome = orchestrator.runTranscription(
            audio = audio,
            modelSha256 = sha256Model,
            runtimeVersion = runtimeVersion,
            deviceKey = deviceKey,
            windowConfig = windowConfig,
        )
        assertTrue(outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Success)
        assertEquals(1, store.committedTranscripts.size)

        val decision = VoiceObservationCommitGate.evaluate(
            outcome = outcome,
            audio = audio,
            jobId = "job-42",
            observationEventId = null,
            noteId = "note-6",
            committedAt = 9_005L,
        )
        val rejected = decision as VoiceObservationCommitDecision.Rejected
        assertTrue(rejected.reason.contains("Empty transcript"))
        assertTrue(rejected.audioRetained)
    }

    @Test
    fun blankEditAfterValid_doesNotFile() {
        val outcome = transcribe(rawText = "Bat in chimney.")
        val decision = VoiceObservationCommitGate.evaluate(
            outcome = outcome,
            audio = audio,
            jobId = "job-42",
            observationEventId = null,
            editedTranscript = "   ",
            noteId = "note-7",
            committedAt = 9_006L,
        )
        assertTrue(decision is VoiceObservationCommitDecision.Rejected)
        assertTrue(
            (decision as VoiceObservationCommitDecision.Rejected).reason.contains("blank"),
        )
    }

    @Test
    fun validWithoutJobOrEvent_doesNotFile() {
        val outcome = transcribe(rawText = "Opossum under deck.")
        val decision = VoiceObservationCommitGate.evaluate(
            outcome = outcome,
            audio = audio,
            jobId = "  ",
            observationEventId = null,
            noteId = "note-8",
            committedAt = 9_007L,
        )
        val rejected = decision as VoiceObservationCommitDecision.Rejected
        assertTrue(rejected.reason.contains("job") || rejected.reason.contains("ObservationEvent"))
    }

    @Test
    fun jobNotesStamp_appendsAudioAndTranscript() {
        val stamped = VoiceObservationNoteStamp.appendToJobNotes(
            existingNotes = "Arrived 08:00",
            transcript = "Squirrel in attic.",
            audioSha256 = sha256Audio,
            audioUri = audio.uri,
            committedAt = 1_700_000_000_000L,
        )
        assertTrue(stamped.startsWith("Arrived 08:00"))
        assertTrue(stamped.contains(VoiceObservationNoteStamp.MARKER))
        assertTrue(stamped.contains("Squirrel in attic."))
        assertTrue(stamped.contains(sha256Audio))
        assertTrue(stamped.contains(audio.uri))
    }

    @Test
    fun jobNotesStamp_emptyExisting_isJustTheBlock() {
        val stamped = VoiceObservationNoteStamp.appendToJobNotes(
            existingNotes = "  ",
            transcript = "First voice note",
            audioSha256 = sha256Audio,
            audioUri = audio.uri,
            committedAt = 1L,
        )
        assertFalse(stamped.startsWith("\n"))
        assertTrue(stamped.contains(VoiceObservationNoteStamp.MARKER))
        assertEquals(1, stamped.split(VoiceObservationNoteStamp.MARKER).size - 1)
    }

    private fun transcribe(rawText: String): VoiceCaptureOrchestrator.TranscriptionOutcome {
        val orchestrator = VoiceCaptureOrchestrator(
            audioStore = store,
            transcriptionEngine = FixedEngine(rawText),
            policy = CpuOnlyPolicy,
            clock = clock,
        )
        orchestrator.saveAudioAtomic(audio.uri, audio.sha256, audio.durationMs)
        return orchestrator.runTranscription(
            audio = audio,
            modelSha256 = sha256Model,
            runtimeVersion = runtimeVersion,
            deviceKey = deviceKey,
            windowConfig = windowConfig,
        )
    }

    private object CpuOnlyPolicy : BackendPolicy {
        override fun resolve(
            modelSha256: String,
            runtimeVersion: String,
            deviceKey: String,
            windowConfig: String,
        ) = listOf(InferenceBackend.CPU)
    }

    private inner class FixedEngine(private val rawText: String) : TranscriptionEngine {
        override fun transcribe(
            audio: AudioArtifact,
            backend: InferenceBackend,
            modelSha256: String,
            runtimeVersion: String,
            deviceKey: String,
            windowConfig: String,
            startedAt: Long,
        ) = TranscriptAttempt(
            attemptId = "attempt-cpu",
            audioSha256 = audio.sha256,
            backend = backend,
            modelSha256 = modelSha256,
            runtimeVersion = runtimeVersion,
            deviceKey = deviceKey,
            windowConfig = windowConfig,
            startedAt = startedAt,
            finishedAt = startedAt + 10,
            exitOk = true,
            rawText = rawText,
            error = null,
        )
    }

    private class RecordingAudioStore : AudioStore {
        private val saved = mutableMapOf<String, AudioArtifact>()
        val committedTranscripts = mutableListOf<DerivedTranscript>()

        override fun save(artifact: AudioArtifact) {
            saved[artifact.sha256] = artifact
        }

        override fun exists(sha256: String) = sha256 in saved

        override fun commitTranscript(transcript: DerivedTranscript) {
            committedTranscripts += transcript
        }
    }
}
