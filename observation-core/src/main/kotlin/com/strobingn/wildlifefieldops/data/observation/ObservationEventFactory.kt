package com.strobingn.wildlifefieldops.data.observation

import java.security.MessageDigest

/**
 * Builds immutable [ObservationEvent]s with deterministic [ObservationEvent.eventId]s.
 *
 * Inference events stay [HumanVerificationState.UNREVIEWED] and are never treated as
 * operational IDs. Technician confirm / correct appends a new event that supersedes
 * the inference (ADR 0002).
 */
object ObservationEventFactory {

    const val DEFAULT_MODEL_ID = "wildlife_evidence_v1"
    const val HUMAN_MODEL_ID = "human"
    const val HUMAN_MODEL_HASH = "human"
    const val DEFAULT_QUANTIZER = "none"

    fun eventId(
        entityId: String,
        observedAt: Long,
        frameHash: String,
        modelId: String,
        verification: HumanVerificationState = HumanVerificationState.UNREVIEWED,
        supersedesEventId: String? = null,
    ): String = sha256(
        listOf(
            entityId,
            observedAt.toString(),
            frameHash,
            modelId,
            verification.name,
            supersedesEventId.orEmpty(),
        ).joinToString("|")
    )

    fun inference(
        entityId: String,
        observedAt: Long,
        uploadedAt: Long = observedAt,
        deviceId: String,
        operatorId: String,
        modelId: String = DEFAULT_MODEL_ID,
        modelHash: String,
        backendTag: String,
        quantizerTag: String = DEFAULT_QUANTIZER,
        frameHash: String,
        cropHash: String = frameHash,
        mediaUri: String? = null,
        labelDistribution: Map<String, Float>,
        captureQuality: Float,
        geometryTrust: Float,
    ): ObservationEvent {
        val labels = sanitizeDistribution(labelDistribution)
        return ObservationEvent(
            eventId = eventId(entityId, observedAt, frameHash, modelId),
            entityId = entityId,
            observedAt = observedAt,
            uploadedAt = uploadedAt,
            deviceId = deviceId,
            operatorId = operatorId,
            modelId = modelId,
            modelHash = modelHash,
            backendTag = backendTag,
            quantizerTag = quantizerTag,
            frameHash = frameHash,
            cropHash = cropHash,
            mediaUri = mediaUri,
            labelDistribution = labels,
            captureQuality = captureQuality,
            geometryTrust = geometryTrust,
            humanVerificationState = HumanVerificationState.UNREVIEWED,
        )
    }

    /**
     * Technician agrees with the model's primary label. Operational only after this event.
     */
    fun confirm(
        inference: ObservationEvent,
        confirmedAt: Long,
        uploadedAt: Long = confirmedAt,
        operatorId: String = inference.operatorId,
    ): ObservationEvent {
        val primary = primaryLabel(inference.labelDistribution)
            ?: error("Cannot confirm an inference with an empty label distribution")
        return humanDecision(
            inference = inference,
            decidedAt = confirmedAt,
            uploadedAt = uploadedAt,
            operatorId = operatorId,
            labels = mapOf(primary to 1f),
            state = HumanVerificationState.CONFIRMED,
        )
    }

    /**
     * Technician supplies a different operational label.
     */
    fun correct(
        inference: ObservationEvent,
        correctedLabel: String,
        correctedAt: Long,
        uploadedAt: Long = correctedAt,
        operatorId: String = inference.operatorId,
    ): ObservationEvent {
        val label = correctedLabel.trim()
        require(label.isNotEmpty()) { "Corrected label must not be blank" }
        return humanDecision(
            inference = inference,
            decidedAt = correctedAt,
            uploadedAt = uploadedAt,
            operatorId = operatorId,
            labels = mapOf(label to 1f),
            state = HumanVerificationState.CORRECTED,
        )
    }

    fun primaryLabel(distribution: Map<String, Float>): String? =
        distribution.maxByOrNull { it.value }?.key?.takeIf { it.isNotBlank() }

    fun sanitizeDistribution(raw: Map<String, Float>): Map<String, Float> =
        raw
            .mapKeys { it.key.trim() }
            .filter { it.key.isNotEmpty() && it.value.isFinite() && it.value > 0f }
            .mapValues { it.value.coerceIn(0f, 1f) }

    fun hashBytes(bytes: ByteArray): String = sha256(bytes)

    fun hashUtf8(value: String): String = sha256(value)

    private fun humanDecision(
        inference: ObservationEvent,
        decidedAt: Long,
        uploadedAt: Long,
        operatorId: String,
        labels: Map<String, Float>,
        state: HumanVerificationState,
    ): ObservationEvent {
        val frameHash = "${inference.frameHash}:${state.name.lowercase()}"
        return ObservationEvent(
            eventId = eventId(
                entityId = inference.entityId,
                observedAt = decidedAt,
                frameHash = frameHash,
                modelId = HUMAN_MODEL_ID,
                verification = state,
                supersedesEventId = inference.eventId,
            ),
            entityId = inference.entityId,
            observedAt = decidedAt,
            uploadedAt = uploadedAt,
            deviceId = inference.deviceId,
            operatorId = operatorId,
            modelId = HUMAN_MODEL_ID,
            modelHash = HUMAN_MODEL_HASH,
            backendTag = "human",
            quantizerTag = DEFAULT_QUANTIZER,
            frameHash = frameHash,
            cropHash = inference.cropHash,
            mediaUri = inference.mediaUri,
            labelDistribution = labels,
            captureQuality = 1f,
            geometryTrust = inference.geometryTrust,
            humanVerificationState = state,
            supersedesEventId = inference.eventId,
        )
    }

    private fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
