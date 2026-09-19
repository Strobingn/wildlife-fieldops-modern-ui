package com.strobingn.wildlifefieldops.data.observation

/**
 * Immutable record of a single species-identification inference or human correction.
 *
 * Rules:
 * - Never updated or deleted after creation.
 * - [eventId] is a deterministic content hash; re-uploading the same evidence
 *   produces the same ID and is silently deduplicated.
 * - [observedAt] is the device capture clock (epoch-ms), NOT the upload timestamp.
 * - [labelDistribution] is sealed at creation; model re-scoring requires a new event.
 */
data class ObservationEvent(
    /** Deterministic content hash: SHA-256(entityId + observedAt + frameHash + modelId). */
    val eventId: String,

    /** Stable identifier for the observed site / individual. */
    val entityId: String,

    /** Device-local capture timestamp (epoch-ms). NOT the upload or server time. */
    val observedAt: Long,

    /** Wall-clock timestamp when this event was first uploaded (diagnostics only). */
    val uploadedAt: Long,

    val deviceId: String,
    val operatorId: String,

    /** Model identifier, e.g. "wildlife_evidence_v3". */
    val modelId: String,

    /** SHA-256 of the TFLite/LiteRT flatbuffer binary. */
    val modelHash: String,

    /** Inference backend: "tflite" | "mlkit" | "litert" | "human". */
    val backendTag: String,

    /** Quantization: "int8" | "float16" | "none". */
    val quantizerTag: String,

    /** Perceptual hash of the captured frame. */
    val frameHash: String,

    /** Hash of the detection-crop region (may differ from [frameHash]). */
    val cropHash: String,

    /** Content-URI or object-store key; null for ephemeral live frames. */
    val mediaUri: String? = null,

    /**
     * Model or operator label probabilities.  Values should be in [0,1] and
     * the map may sum to ≤ 1.0 (models may reserve probability mass for
     * an implicit "other" class).
     */
    val labelDistribution: Map<String, Float>,

    /** Frame sharpness score in [0, 1]. Blurry frames (< 0.2) are down-weighted. */
    val captureQuality: Float,

    /** GPS/IMU fix confidence in [0, 1]. 0.0 when location is unavailable. */
    val geometryTrust: Float,

    val humanVerificationState: HumanVerificationState,

    /**
     * For CORRECTED or CONFIRMED events: the eventId this event supersedes.
     * Null for raw model inferences.
     */
    val supersedesEventId: String? = null,
)

enum class HumanVerificationState {
    /** Raw model output; operator has not reviewed this event. */
    UNREVIEWED,

    /** Operator confirmed the primary model label is correct. */
    CONFIRMED,

    /** Operator provided a different label; see [ObservationEvent.supersedesEventId]. */
    CORRECTED,

    /** Operator flagged this evidence as ambiguous or contested. */
    DISPUTED,
}
