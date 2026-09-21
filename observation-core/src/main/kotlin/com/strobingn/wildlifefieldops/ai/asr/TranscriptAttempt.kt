package com.strobingn.wildlifefieldops.ai.asr

/**
 * Immutable record of one ASR transcription attempt for a single [AudioArtifact].
 *
 * An attempt captures the full provenance of a single model run: which backend,
 * which model, which runtime, which device/SoC, and what the model returned.
 * Both successful and failed attempts are preserved and attached to any resulting
 * [DerivedTranscript].
 *
 * The known defect LiteRT-LM #3684: Qwen3-ASR can init on GPU, set [exitOk] = true,
 * and return an empty [rawText].  This situation is detected by the validator and
 * classified as [TranscriptState.RETRYABLE_FAILURE], triggering one bounded CPU fallback.
 */
data class TranscriptAttempt(
    /** Unique identifier for this attempt (UUID or deterministic content hash). */
    val attemptId: String,

    /** SHA-256 of the [AudioArtifact] this attempt targeted. */
    val audioSha256: String,

    /** Inference backend used for this run. */
    val backend: InferenceBackend,

    /** SHA-256 of the ASR model flatbuffer used for this run. */
    val modelSha256: String,

    /** LiteRT-LM / runtime version string, e.g. "litert-lm-1.0.1". */
    val runtimeVersion: String,

    /**
     * Device / SoC identifier: a stable string uniquely describing the hardware,
     * e.g. "google/Pixel_8_Pro/tensor_g3" or "samsung/Galaxy_S24/exynos_2400".
     * Corresponds to the `device/SoC` component of the backend policy key.
     */
    val deviceKey: String,

    /**
     * Window configuration passed to the ASR model, serialised as an opaque string
     * (e.g. "chunkMs=2000,overlapMs=200" or a compact JSON blob).
     * Corresponds to the `window_config` component of the backend policy key.
     */
    val windowConfig: String,

    /** Epoch-ms when transcription started. */
    val startedAt: Long,

    /** Epoch-ms when transcription finished (success or failure). */
    val finishedAt: Long,

    /**
     * Whether the backend process exited cleanly (exit code 0 or equivalent
     * in-process success indicator).
     *
     * A clean exit ([exitOk] = true) with an empty [rawText] on non-silent audio
     * is the symptom of LiteRT-LM issue #3684 and is treated as [TranscriptState.RETRYABLE_FAILURE].
     */
    val exitOk: Boolean,

    /**
     * Raw text returned by the ASR model, or null if the process crashed before
     * producing any output.  An empty string is meaningful and distinct from null.
     */
    val rawText: String?,

    /** Human-readable error message when the attempt failed; null on success. */
    val error: String?,
)

/**
 * Inference backend for ASR model execution.
 *
 * The backend allowlist (see [BackendPolicy]) determines which backends may be
 * used for a given (model × runtime × device × window) combination.
 * CPU is always available as a bounded fallback.
 */
enum class InferenceBackend {
    /** Neural-processing unit: on-device dedicated AI accelerator. */
    NPU,

    /** GPU via compute shader or vendor ML API (e.g. OpenCL, Vulkan compute). */
    GPU,

    /**
     * CPU — always available; used as the single bounded fallback when
     * an accelerator attempt returns a retryable failure.
     */
    CPU,
}
