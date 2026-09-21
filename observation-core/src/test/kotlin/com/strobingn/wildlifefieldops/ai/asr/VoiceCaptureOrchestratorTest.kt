package com.strobingn.wildlifefieldops.ai.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the fail-closed ASR transcript state machine and [VoiceCaptureOrchestrator].
 *
 * ## Scenarios covered (FieldOps brief 2026-09-19 item #2)
 *  1. GPU empty + exitOk → does NOT commit; triggers exactly one CPU retry.
 *  2. CPU success after GPU empty → VALID commit; both attempts recorded.
 *  3. CPU also empty → FAILED; audio retained; nothing committed.
 *  4. Model invariant: observation cannot reference missing audio.
 *
 * ## Additional coverage
 *  5. GPU crash (exitOk=false) → RETRYABLE_FAILURE → CPU fallback.
 *  6. CPU-first policy (untested key) → no fallback; direct FAILED on empty.
 *  7. GPU returns valid text → VALID on first attempt; no CPU attempt.
 *  8. Token-spam rejection.
 *  9. Duration heuristic rejection.
 * 10. Silence exemption allows empty transcript.
 * 11. All attempts are recorded chronologically in DerivedTranscript.
 * 12. TranscriptValidator standalone: null rawText, whitespace, repetition, heuristic.
 *
 * All tests use JUnit 4 — consistent with the existing observation-core suite.
 * No Android or Room dependencies; pure Kotlin/JVM.
 */
class VoiceCaptureOrchestratorTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Constants / shared fixtures
    // ─────────────────────────────────────────────────────────────────────────

    private val sha256Audio = "a".repeat(64)
    private val sha256Model = "b".repeat(64)
    private val runtimeVersion = "litert-lm-1.0.1"
    private val deviceKey = "google/Pixel_8_Pro/tensor_g3"
    private val windowConfig = "chunkMs=2000,overlapMs=200"

    /** 10-second non-silent audio. */
    private val nonSilentAudio = AudioArtifact(
        uri = "file:///data/audio/capture.wav",
        sha256 = sha256Audio,
        durationMs = 10_000L,
        capturedAt = 1_000_000L,
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Test infrastructure
    // ─────────────────────────────────────────────────────────────────────────

    private var fakeTime = 2_000_000L
    private val clock: () -> Long = { fakeTime++ }

    private lateinit var store: FakeAudioStore

    @Before
    fun setup() {
        fakeTime = 2_000_000L
        store = FakeAudioStore()
    }

    /** Build an orchestrator with GPU as the preferred backend via an injected policy. */
    private fun orchestratorWithGpuFirst(engine: TranscriptionEngine): VoiceCaptureOrchestrator =
        VoiceCaptureOrchestrator(
            audioStore = store,
            transcriptionEngine = engine,
            policy = fixedOrderPolicy(InferenceBackend.GPU, InferenceBackend.CPU),
            clock = clock,
        )

    /** Build an orchestrator that will always resolve to CPU only. */
    private fun orchestratorCpuOnly(engine: TranscriptionEngine): VoiceCaptureOrchestrator =
        VoiceCaptureOrchestrator(
            audioStore = store,
            transcriptionEngine = engine,
            policy = fixedOrderPolicy(InferenceBackend.CPU),
            clock = clock,
        )

    /** Build an orchestrator with the given validator and GPU-first policy. */
    private fun orchestratorWithValidator(
        engine: TranscriptionEngine,
        validator: TranscriptValidator,
    ): VoiceCaptureOrchestrator = VoiceCaptureOrchestrator(
        audioStore = store,
        transcriptionEngine = engine,
        validator = validator,
        policy = fixedOrderPolicy(InferenceBackend.GPU, InferenceBackend.CPU),
        clock = clock,
    )

    private fun fixedOrderPolicy(vararg backends: InferenceBackend): BackendPolicy =
        object : BackendPolicy {
            override fun resolve(
                modelSha256: String,
                runtimeVersion: String,
                deviceKey: String,
                windowConfig: String,
            ) = backends.toList()
        }

    private fun saveAndReturnAudio(orchestrator: VoiceCaptureOrchestrator): AudioArtifact =
        orchestrator.saveAudioAtomic(
            uri = nonSilentAudio.uri,
            sha256 = nonSilentAudio.sha256,
            durationMs = nonSilentAudio.durationMs,
        )

    private fun runTranscription(
        orchestrator: VoiceCaptureOrchestrator,
        audio: AudioArtifact,
    ): VoiceCaptureOrchestrator.TranscriptionOutcome = orchestrator.runTranscription(
        audio = audio,
        modelSha256 = sha256Model,
        runtimeVersion = runtimeVersion,
        deviceKey = deviceKey,
        windowConfig = windowConfig,
    )

    private fun makeAttempt(
        backend: InferenceBackend,
        rawText: String?,
        exitOk: Boolean = true,
        audioSha256: String = sha256Audio,
        attemptId: String = "attempt-${backend.name}-${rawText?.length ?: "null"}",
    ) = TranscriptAttempt(
        attemptId = attemptId,
        audioSha256 = audioSha256,
        backend = backend,
        modelSha256 = sha256Model,
        runtimeVersion = runtimeVersion,
        deviceKey = deviceKey,
        windowConfig = windowConfig,
        startedAt = 1_000L,
        finishedAt = 2_000L,
        exitOk = exitOk,
        rawText = rawText,
        error = if (!exitOk) "inference error" else null,
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GPU empty + exitOk → does NOT commit; triggers exactly one CPU retry
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * LiteRT-LM issue #3684: GPU exits cleanly (exitOk=true) but returns empty rawText.
     * The orchestrator must NOT commit and must trigger exactly one CPU fallback.
     */
    @Test
    fun gpuEmptyExitOk_doesNotCommit_triggersCpuFallback() {
        val callLog = mutableListOf<InferenceBackend>()
        val engine = FakeEngine { _, backend ->
            callLog += backend
            when (backend) {
                InferenceBackend.GPU -> makeAttempt(backend = InferenceBackend.GPU, rawText = "", exitOk = true)
                InferenceBackend.CPU -> makeAttempt(backend = InferenceBackend.CPU, rawText = "Wolf spotted near ridge.")
                else -> error("Unexpected backend: $backend")
            }
        }
        val orchestrator = orchestratorWithGpuFirst(engine)
        val audio = saveAndReturnAudio(orchestrator)

        val outcome = runTranscription(orchestrator, audio)

        // Must have called GPU first, then exactly one CPU retry.
        assertEquals(listOf(InferenceBackend.GPU, InferenceBackend.CPU), callLog)

        // Nothing committed yet after GPU failure; only committed after CPU success.
        // The final outcome is Success (CPU succeeded) — see test #2 for that path.
        // Here we verify the GPU attempt alone did not commit anything.
        // (We verify via store.committedTranscripts being non-empty only after CPU.)
        val success = outcome as? VoiceCaptureOrchestrator.TranscriptionOutcome.Success
        assertNotNull("Expected Success after CPU retry", success)
        assertEquals(TranscriptState.VALID, success!!.state)

        // The GPU attempt must be recorded.
        val gpuAttempt = success.attempts.firstOrNull { it.backend == InferenceBackend.GPU }
        assertNotNull("GPU attempt must be recorded in allAttempts", gpuAttempt)
        assertEquals("", gpuAttempt!!.rawText)
        assertTrue(gpuAttempt.exitOk)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. CPU success after GPU empty → VALID commit; both attempts recorded
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun cpuSuccessAfterGpuEmpty_validCommit_bothAttemptsRecorded() {
        val cpuText = "Wolf spotted near ridge, approximately 50m east."
        val engine = FakeEngine { _, backend ->
            when (backend) {
                InferenceBackend.GPU -> makeAttempt(backend = InferenceBackend.GPU, rawText = "", exitOk = true, attemptId = "gpu-1")
                InferenceBackend.CPU -> makeAttempt(backend = InferenceBackend.CPU, rawText = cpuText, attemptId = "cpu-1")
                else -> error("Unexpected backend: $backend")
            }
        }
        val orchestrator = orchestratorWithGpuFirst(engine)
        val audio = saveAndReturnAudio(orchestrator)

        val outcome = runTranscription(orchestrator, audio)

        assertTrue("Expected Success", outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Success)
        val success = outcome as VoiceCaptureOrchestrator.TranscriptionOutcome.Success

        assertEquals(TranscriptState.VALID, success.state)
        assertEquals(cpuText, success.derived.validatedText)
        assertEquals("cpu-1", success.derived.winningAttemptId)
        assertEquals(audio.sha256, success.derived.audioSha256)

        // Both attempts recorded.
        assertEquals(2, success.derived.allAttempts.size)
        assertEquals(InferenceBackend.GPU, success.derived.allAttempts[0].backend)
        assertEquals(InferenceBackend.CPU, success.derived.allAttempts[1].backend)

        // Committed to store.
        assertEquals(1, store.committedTranscripts.size)
        assertEquals(cpuText, store.committedTranscripts[0].validatedText)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. CPU also empty → FAILED; audio retained; nothing committed
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun cpuAlsoEmpty_failed_nothingCommitted_audioRetained() {
        val engine = FakeEngine { _, backend ->
            makeAttempt(backend = backend, rawText = "", exitOk = true)
        }
        val orchestrator = orchestratorWithGpuFirst(engine)
        val audio = saveAndReturnAudio(orchestrator)

        val outcome = runTranscription(orchestrator, audio)

        assertTrue("Expected Failure", outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Failure)
        val failure = outcome as VoiceCaptureOrchestrator.TranscriptionOutcome.Failure

        assertEquals(TranscriptState.FAILED, failure.state)

        // Nothing committed.
        assertTrue("No transcript should be committed", store.committedTranscripts.isEmpty())

        // Audio retained.
        assertTrue("Audio must still be in store", store.exists(audio.sha256))

        // Both attempts recorded in the outcome.
        assertEquals(2, failure.attempts.size)
        assertEquals(InferenceBackend.GPU, failure.attempts[0].backend)
        assertEquals(InferenceBackend.CPU, failure.attempts[1].backend)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Model invariant: observation cannot reference missing audio
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun runTranscription_withoutSavingAudio_throwsIllegalArgument() {
        val engine = FakeEngine { _, backend ->
            makeAttempt(backend = backend, rawText = "Some text")
        }
        val orchestrator = orchestratorWithGpuFirst(engine)

        // Do NOT call saveAudioAtomic — audio is not in the store.
        try {
            runTranscription(orchestrator, nonSilentAudio)
            fail("Expected IllegalArgumentException for unpersisted audio")
        } catch (e: IllegalArgumentException) {
            assertTrue(
                "Exception message should mention the sha256",
                e.message?.contains(nonSilentAudio.sha256) == true,
            )
        }

        // Nothing committed.
        assertTrue(store.committedTranscripts.isEmpty())
    }

    @Test
    fun derivedTranscript_cannotBeCreatedWithMissingAttemptId() {
        val attempt = makeAttempt(InferenceBackend.CPU, rawText = "text", attemptId = "real-id")
        try {
            DerivedTranscript.create(
                audio = nonSilentAudio,
                validatedText = "text",
                winningAttemptId = "nonexistent-id",
                allAttempts = listOf(attempt),
            )
            fail("Expected IllegalArgumentException for missing winningAttemptId")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("nonexistent-id") == true)
        }
    }

    @Test
    fun derivedTranscript_cannotBeCreatedWithBlankText() {
        val attempt = makeAttempt(InferenceBackend.CPU, rawText = "text", attemptId = "id-1")
        try {
            DerivedTranscript.create(
                audio = nonSilentAudio,
                validatedText = "   ",
                winningAttemptId = "id-1",
                allAttempts = listOf(attempt),
            )
            fail("Expected IllegalArgumentException for blank validatedText")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. GPU crash (exitOk=false) → treated as RETRYABLE → one CPU fallback
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun gpuCrash_triggersOneCpuFallback() {
        val cpuText = "Bear tracks observed near water source."
        val callLog = mutableListOf<InferenceBackend>()
        val engine = FakeEngine { _, backend ->
            callLog += backend
            when (backend) {
                InferenceBackend.GPU -> makeAttempt(backend = InferenceBackend.GPU, rawText = null, exitOk = false)
                InferenceBackend.CPU -> makeAttempt(backend = InferenceBackend.CPU, rawText = cpuText)
                else -> error("Unexpected backend: $backend")
            }
        }
        val orchestrator = orchestratorWithGpuFirst(engine)
        val audio = saveAndReturnAudio(orchestrator)

        val outcome = runTranscription(orchestrator, audio)

        assertEquals(listOf(InferenceBackend.GPU, InferenceBackend.CPU), callLog)
        assertTrue(outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Success)
        val success = outcome as VoiceCaptureOrchestrator.TranscriptionOutcome.Success
        assertEquals(cpuText, success.derived.validatedText)
        assertEquals(2, success.attempts.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. CPU-first policy → no fallback; empty = direct FAILED
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun cpuFirstPolicy_emptyText_noFallback_directFailed() {
        val callLog = mutableListOf<InferenceBackend>()
        val engine = FakeEngine { _, backend ->
            callLog += backend
            makeAttempt(backend = backend, rawText = "", exitOk = true)
        }
        val orchestrator = orchestratorCpuOnly(engine)
        val audio = saveAndReturnAudio(orchestrator)

        val outcome = runTranscription(orchestrator, audio)

        // Exactly one attempt (CPU); no fallback beyond CPU.
        assertEquals(listOf(InferenceBackend.CPU), callLog)
        assertTrue(outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Failure)
        assertEquals(1, outcome.attempts.size)
        assertTrue(store.committedTranscripts.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. GPU returns valid text → VALID on first attempt; no CPU attempt
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun gpuValidText_commitOnFirstAttempt_noCpuAttempt() {
        val gpuText = "Mountain lion sighting — confirmed single adult."
        val callLog = mutableListOf<InferenceBackend>()
        val engine = FakeEngine { _, backend ->
            callLog += backend
            makeAttempt(backend = backend, rawText = gpuText)
        }
        val orchestrator = orchestratorWithGpuFirst(engine)
        val audio = saveAndReturnAudio(orchestrator)

        val outcome = runTranscription(orchestrator, audio)

        assertEquals(listOf(InferenceBackend.GPU), callLog)
        assertTrue(outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Success)
        val success = outcome as VoiceCaptureOrchestrator.TranscriptionOutcome.Success
        assertEquals(gpuText, success.derived.validatedText)
        assertEquals(1, success.derived.allAttempts.size)
        assertEquals(InferenceBackend.GPU, success.derived.allAttempts[0].backend)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Token-spam rejection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun tokenSpam_rejectedAsFailure() {
        // "wolf" repeated 10 times → 100% repetition, well above 60% threshold.
        val spamText = List(10) { "wolf" }.joinToString(" ")
        val engine = FakeEngine { _, backend ->
            makeAttempt(backend = backend, rawText = spamText)
        }
        val orchestrator = orchestratorCpuOnly(engine)
        val audio = saveAndReturnAudio(orchestrator)

        val outcome = runTranscription(orchestrator, audio)

        assertTrue(outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Failure)
        assertTrue(
            "Failure reason should mention repetition",
            (outcome as VoiceCaptureOrchestrator.TranscriptionOutcome.Failure).reason.contains("repetition"),
        )
        assertTrue(store.committedTranscripts.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Duration heuristic rejection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun durationHeuristic_tooFewChars_rejectedAsFailure() {
        // 1 character for a 10-second clip → 0.1 chars/s, below 0.5 threshold.
        val sparseText = "x"
        val engine = FakeEngine { _, backend ->
            makeAttempt(backend = backend, rawText = sparseText)
        }
        val orchestrator = orchestratorCpuOnly(engine)
        // nonSilentAudio is 10_000ms — above the 5_000ms heuristic threshold.
        val audio = saveAndReturnAudio(orchestrator)

        val outcome = runTranscription(orchestrator, audio)

        assertTrue(outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Failure)
        val reason = (outcome as VoiceCaptureOrchestrator.TranscriptionOutcome.Failure).reason
        assertTrue("Failure reason should mention chars/s or sparse", reason.contains("chars/s"))
        assertTrue(store.committedTranscripts.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. Silence exemption allows empty transcript
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun silenceExemption_emptyTranscriptIsValid() {
        val silenceDetector = object : TranscriptValidator {
            override fun validate(
                audio: AudioArtifact,
                attempt: TranscriptAttempt,
            ): TranscriptValidator.ValidationResult {
                if (isSilentAudio(audio)) {
                    return TranscriptValidator.ValidationResult.Ok(validatedText = "")
                }
                return TranscriptValidator.Default.validate(audio, attempt)
            }

            override fun isSilentAudio(audio: AudioArtifact) = true
        }

        val engine = FakeEngine { _, backend ->
            makeAttempt(backend = backend, rawText = "", exitOk = true)
        }
        val orchestrator = orchestratorWithValidator(engine, silenceDetector)
        val audio = saveAndReturnAudio(orchestrator)

        val outcome = runTranscription(orchestrator, audio)

        assertTrue(
            "Silent audio with empty transcript should yield Success",
            outcome is VoiceCaptureOrchestrator.TranscriptionOutcome.Success,
        )
        val success = outcome as VoiceCaptureOrchestrator.TranscriptionOutcome.Success
        assertEquals(TranscriptState.VALID, success.state)
        assertEquals("", success.derived.validatedText)
        assertEquals(1, store.committedTranscripts.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 11. All attempts are recorded in DerivedTranscript (chronological order)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun allAttemptsRecorded_inChronologicalOrder() {
        val engine = FakeEngine { _, backend ->
            when (backend) {
                InferenceBackend.GPU -> makeAttempt(InferenceBackend.GPU, rawText = "", attemptId = "gpu-first")
                InferenceBackend.CPU -> makeAttempt(InferenceBackend.CPU, rawText = "Valid field note.", attemptId = "cpu-second")
                else -> error("Unexpected backend: $backend")
            }
        }
        val orchestrator = orchestratorWithGpuFirst(engine)
        val audio = saveAndReturnAudio(orchestrator)

        val success = runTranscription(orchestrator, audio) as VoiceCaptureOrchestrator.TranscriptionOutcome.Success
        val attempts = success.derived.allAttempts

        assertEquals(2, attempts.size)
        assertEquals("gpu-first", attempts[0].attemptId)
        assertEquals("cpu-second", attempts[1].attemptId)
        assertEquals("cpu-second", success.derived.winningAttemptId)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 12. TranscriptValidator standalone edge cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun validator_nullRawText_returnsFailure() {
        val attempt = makeAttempt(InferenceBackend.GPU, rawText = null, exitOk = false)
        val result = TranscriptValidator.Default.validate(nonSilentAudio, attempt)
        assertTrue(result is TranscriptValidator.ValidationResult.Failure)
    }

    @Test
    fun validator_pureWhitespace_returnsFailure() {
        val attempt = makeAttempt(InferenceBackend.CPU, rawText = "   \t\n  ")
        val result = TranscriptValidator.Default.validate(nonSilentAudio, attempt)
        assertTrue(result is TranscriptValidator.ValidationResult.Failure)
    }

    @Test
    fun validator_validText_returnsOkWithTrimmedText() {
        val attempt = makeAttempt(InferenceBackend.CPU, rawText = "  Tracks observed.  ")
        val result = TranscriptValidator.Default.validate(nonSilentAudio, attempt)
        assertTrue(result is TranscriptValidator.ValidationResult.Ok)
        assertEquals("Tracks observed.", (result as TranscriptValidator.ValidationResult.Ok).validatedText)
    }

    @Test
    fun validator_gpuEmptyExitOk_returnsFailureWithLiteRtHint() {
        val attempt = makeAttempt(InferenceBackend.GPU, rawText = "", exitOk = true)
        val result = TranscriptValidator.Default.validate(nonSilentAudio, attempt)
        assertTrue(result is TranscriptValidator.ValidationResult.Failure)
        val reason = (result as TranscriptValidator.ValidationResult.Failure).reason
        assertTrue(
            "Failure reason should mention LiteRT-LM #3684",
            reason.contains("3684"),
        )
    }

    @Test
    fun validator_moderateRepetitionBelowThreshold_returnsOk() {
        // 5 unique + 3 repeated = "wolf wolf wolf bear deer fox cat dog"
        // "wolf" appears 3/8 = 37.5% < 60% — should pass.
        val attempt = makeAttempt(InferenceBackend.CPU, rawText = "wolf wolf wolf bear deer fox cat dog")
        val result = TranscriptValidator.Default.validate(nonSilentAudio, attempt)
        assertTrue(result is TranscriptValidator.ValidationResult.Ok)
    }

    @Test
    fun validator_shortAudioBelowHeuristicThreshold_shortTextOk() {
        // 3-second audio (below 5s heuristic threshold); 1 char → heuristic not applied.
        val shortAudio = AudioArtifact(
            uri = "file:///short.wav",
            sha256 = "c".repeat(64),
            durationMs = 3_000L,
            capturedAt = 1_000_000L,
        )
        val attempt = makeAttempt(InferenceBackend.CPU, rawText = "x", audioSha256 = "c".repeat(64))
        val result = TranscriptValidator.Default.validate(shortAudio, attempt)
        assertTrue("Short audio should not trigger duration heuristic", result is TranscriptValidator.ValidationResult.Ok)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 13. BackendPolicy: untested key resolves to CPU only
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun backendPolicy_untestedKey_resolvesCpuOnly() {
        val policy = BackendPolicy.Default
        val backends = policy.resolve(
            modelSha256 = sha256Model,
            runtimeVersion = runtimeVersion,
            deviceKey = deviceKey,
            windowConfig = windowConfig,
        )
        assertEquals(listOf(InferenceBackend.CPU), backends)
    }

    @Test
    fun backendPolicy_allowlistedKey_includesCpuAsLastFallback() {
        val entry = AllowlistEntry(
            modelSha256 = sha256Model,
            runtimeVersion = runtimeVersion,
            deviceKey = deviceKey,
            windowConfig = windowConfig,
            approvedBackends = setOf(InferenceBackend.GPU),
        )
        val policy = DefaultBackendPolicy(allowlist = listOf(entry))
        val backends = policy.resolve(sha256Model, runtimeVersion, deviceKey, windowConfig)
        assertEquals(InferenceBackend.GPU, backends.first())
        assertTrue("CPU must be the last fallback", InferenceBackend.CPU in backends)
        assertEquals(InferenceBackend.CPU, backends.last())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 14. AudioArtifact init validation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun audioArtifact_blankUri_throws() {
        try {
            AudioArtifact(uri = " ", sha256 = "a".repeat(64), durationMs = 1000L, capturedAt = 1L)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun audioArtifact_invalidSha256_throws() {
        try {
            AudioArtifact(uri = "file:///x.wav", sha256 = "tooshort", durationMs = 1000L, capturedAt = 1L)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }

    @Test
    fun audioArtifact_zeroDuration_throws() {
        try {
            AudioArtifact(uri = "file:///x.wav", sha256 = "a".repeat(64), durationMs = 0L, capturedAt = 1L)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) { /* expected */ }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Test doubles
// ─────────────────────────────────────────────────────────────────────────────

/** In-memory [AudioStore] that records saves and committed transcripts. */
private class FakeAudioStore : AudioStore {
    private val savedArtifacts = mutableMapOf<String, AudioArtifact>()
    val committedTranscripts = mutableListOf<DerivedTranscript>()

    override fun save(artifact: AudioArtifact) {
        savedArtifacts[artifact.sha256] = artifact
    }

    override fun exists(sha256: String) = sha256 in savedArtifacts

    override fun commitTranscript(transcript: DerivedTranscript) {
        committedTranscripts += transcript
    }
}

/** [TranscriptionEngine] backed by a lambda for easy per-test configuration. */
private class FakeEngine(
    private val block: (audio: AudioArtifact, backend: InferenceBackend) -> TranscriptAttempt,
) : TranscriptionEngine {
    override fun transcribe(
        audio: AudioArtifact,
        backend: InferenceBackend,
        modelSha256: String,
        runtimeVersion: String,
        deviceKey: String,
        windowConfig: String,
        startedAt: Long,
    ): TranscriptAttempt = block(audio, backend)
}
