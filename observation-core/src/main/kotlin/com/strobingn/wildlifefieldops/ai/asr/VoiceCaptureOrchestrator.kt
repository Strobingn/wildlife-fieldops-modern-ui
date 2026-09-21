package com.strobingn.wildlifefieldops.ai.asr

/**
 * Orchestrator for the offline voice-capture transcription pipeline.
 *
 * ## Guarantees
 *  - Audio is persisted atomically (via [AudioStore.save]) before any transcription attempt.
 *  - Empty transcripts on non-silent audio are NEVER committed (fail-closed).
 *  - At most ONE CPU fallback is performed after an accelerator (GPU/NPU) failure.
 *  - There is no infinite retry loop.
 *  - [DerivedTranscript] is only produced and committed when state is [TranscriptState.VALID].
 *  - Audio is ALWAYS retained regardless of outcome (retained by [AudioStore]).
 *
 * ## Retry budget
 *  - First attempt: most-preferred backend per [BackendPolicy].
 *  - If the first attempt is [TranscriptState.RETRYABLE_FAILURE] AND the backend was not CPU:
 *    one CPU retry is performed.
 *  - If the first attempt was already CPU, or if the CPU retry also fails: [TranscriptState.FAILED].
 *
 * No Android, Room, or coroutine dependencies — pure Kotlin/JVM.
 * All I/O and inference are injected via interfaces for testability.
 *
 * See docs/adr/0003-asr-backend-policy.md.
 */
class VoiceCaptureOrchestrator(
    private val audioStore: AudioStore,
    private val transcriptionEngine: TranscriptionEngine,
    private val validator: TranscriptValidator = TranscriptValidator.Default,
    private val policy: BackendPolicy = BackendPolicy.Default,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Persist the audio artifact atomically and return the domain record.
     *
     * The returned [AudioArtifact] is the authoritative reference for subsequent
     * calls to [runTranscription].  Callers must not retain raw URIs or paths for
     * transcription decisions — only pass the returned artifact.
     *
     * @param uri       Content-URI or absolute path to the audio file.
     * @param sha256    SHA-256 hex digest of the raw audio bytes.
     * @param durationMs Duration in milliseconds; must be > 0.
     * @return The persisted [AudioArtifact].
     */
    fun saveAudioAtomic(
        uri: String,
        sha256: String,
        durationMs: Long,
    ): AudioArtifact {
        val artifact = AudioArtifact(
            uri = uri,
            sha256 = sha256,
            durationMs = durationMs,
            capturedAt = clock(),
        )
        audioStore.save(artifact)
        return artifact
    }

    /**
     * Run transcription according to the backend policy, applying at most one CPU fallback.
     *
     * ## Steps
     *  1. Verify [audio] is already persisted (model invariant).
     *  2. Resolve backend preference list via [BackendPolicy].
     *  3. Attempt the preferred (accelerator) backend.
     *  4. Validate the result:
     *     - **VALID** → commit and return [TranscriptionOutcome.Success].
     *     - **RETRYABLE_FAILURE** (first backend ≠ CPU) → attempt CPU once; validate again:
     *         - VALID → commit and return [TranscriptionOutcome.Success].
     *         - anything else → return [TranscriptionOutcome.Failure] with [TranscriptState.FAILED].
     *     - **RETRYABLE_FAILURE** (first backend = CPU) or non-retryable → [TranscriptionOutcome.Failure].
     *
     * @param audio          A persisted [AudioArtifact] returned by [saveAudioAtomic].
     * @param modelSha256    SHA-256 of the ASR model to use.
     * @param runtimeVersion Runtime version string.
     * @param deviceKey      Device/SoC identifier.
     * @param windowConfig   Window configuration string.
     * @return [TranscriptionOutcome] describing the final result and all attempts.
     * @throws IllegalArgumentException if [audio] has not been persisted.
     */
    fun runTranscription(
        audio: AudioArtifact,
        modelSha256: String,
        runtimeVersion: String,
        deviceKey: String,
        windowConfig: String,
    ): TranscriptionOutcome {
        require(audioStore.exists(audio.sha256)) {
            "AudioArtifact '${audio.sha256}' has not been persisted; call saveAudioAtomic first"
        }

        val backendOrder = policy.resolve(modelSha256, runtimeVersion, deviceKey, windowConfig)
        // backendOrder is non-empty by BackendPolicy contract (always contains CPU).

        val allAttempts = mutableListOf<TranscriptAttempt>()

        // ── First attempt (preferred/accelerator backend) ─────────────────────
        val firstBackend = backendOrder.first()
        val firstAttempt = runAttempt(
            audio = audio,
            backend = firstBackend,
            modelSha256 = modelSha256,
            runtimeVersion = runtimeVersion,
            deviceKey = deviceKey,
            windowConfig = windowConfig,
        )
        allAttempts += firstAttempt

        val firstResult = validator.validate(audio, firstAttempt)
        if (firstResult is TranscriptValidator.ValidationResult.Ok) {
            return commitSuccess(audio, firstResult.validatedText, firstAttempt.attemptId, allAttempts)
        }

        val firstFailure = firstResult as TranscriptValidator.ValidationResult.Failure

        // ── CPU fallback (one attempt only, if first backend was an accelerator) ──
        if (firstBackend != InferenceBackend.CPU) {
            val cpuAttempt = runAttempt(
                audio = audio,
                backend = InferenceBackend.CPU,
                modelSha256 = modelSha256,
                runtimeVersion = runtimeVersion,
                deviceKey = deviceKey,
                windowConfig = windowConfig,
            )
            allAttempts += cpuAttempt

            val cpuResult = validator.validate(audio, cpuAttempt)
            if (cpuResult is TranscriptValidator.ValidationResult.Ok) {
                return commitSuccess(audio, cpuResult.validatedText, cpuAttempt.attemptId, allAttempts)
            }

            val cpuFailure = cpuResult as TranscriptValidator.ValidationResult.Failure
            return TranscriptionOutcome.Failure(
                state = TranscriptState.FAILED,
                reason = "CPU fallback also failed: ${cpuFailure.reason} " +
                    "(original: ${firstFailure.reason})",
                attempts = allAttempts.toList(),
            )
        }

        // First attempt was already CPU → no further retries.
        return TranscriptionOutcome.Failure(
            state = TranscriptState.FAILED,
            reason = firstFailure.reason,
            attempts = allAttempts.toList(),
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun runAttempt(
        audio: AudioArtifact,
        backend: InferenceBackend,
        modelSha256: String,
        runtimeVersion: String,
        deviceKey: String,
        windowConfig: String,
    ): TranscriptAttempt = transcriptionEngine.transcribe(
        audio = audio,
        backend = backend,
        modelSha256 = modelSha256,
        runtimeVersion = runtimeVersion,
        deviceKey = deviceKey,
        windowConfig = windowConfig,
        startedAt = clock(),
    )

    private fun commitSuccess(
        audio: AudioArtifact,
        validatedText: String,
        winningAttemptId: String,
        allAttempts: List<TranscriptAttempt>,
    ): TranscriptionOutcome.Success {
        val derived = DerivedTranscript.create(
            audio = audio,
            validatedText = validatedText,
            winningAttemptId = winningAttemptId,
            allAttempts = allAttempts,
            committedAt = clock(),
        )
        audioStore.commitTranscript(derived)
        return TranscriptionOutcome.Success(
            state = TranscriptState.VALID,
            derived = derived,
            attempts = allAttempts.toList(),
        )
    }

    // ── Result types ──────────────────────────────────────────────────────────

    /** Outcome of a [runTranscription] call. */
    sealed class TranscriptionOutcome {
        abstract val attempts: List<TranscriptAttempt>

        /**
         * Transcription succeeded; [derived] is committed to the [AudioStore].
         * [state] is always [TranscriptState.VALID].
         */
        data class Success(
            val state: TranscriptState,
            val derived: DerivedTranscript,
            override val attempts: List<TranscriptAttempt>,
        ) : TranscriptionOutcome()

        /**
         * Transcription failed; nothing was committed.  Audio is retained.
         * [state] is always [TranscriptState.FAILED].
         */
        data class Failure(
            val state: TranscriptState,
            val reason: String,
            override val attempts: List<TranscriptAttempt>,
        ) : TranscriptionOutcome()
    }
}

// ── Injectable interfaces ─────────────────────────────────────────────────────

/**
 * Persistence interface for audio artifacts and committed transcripts.
 *
 * Implementations must ensure that [save] is durable before returning, so that
 * the audio cannot be lost if the process is killed between [save] and [commitTranscript].
 */
interface AudioStore {
    /** Persist [artifact] durably. Idempotent for the same [AudioArtifact.sha256]. */
    fun save(artifact: AudioArtifact)

    /** Return true if an artifact with [sha256] has been successfully persisted. */
    fun exists(sha256: String): Boolean

    /**
     * Commit [transcript] to durable storage.
     * Called only when the final state is [TranscriptState.VALID].
     */
    fun commitTranscript(transcript: DerivedTranscript)
}

/** Injectable interface for ASR model inference. */
interface TranscriptionEngine {
    /**
     * Run the ASR model on [audio] using [backend] and return the result.
     *
     * Implementations must set [TranscriptAttempt.finishedAt] before returning.
     * They must NOT throw on model-level failures; instead return an attempt with
     * [TranscriptAttempt.exitOk] = false and [TranscriptAttempt.error] set.
     */
    fun transcribe(
        audio: AudioArtifact,
        backend: InferenceBackend,
        modelSha256: String,
        runtimeVersion: String,
        deviceKey: String,
        windowConfig: String,
        startedAt: Long,
    ): TranscriptAttempt
}
