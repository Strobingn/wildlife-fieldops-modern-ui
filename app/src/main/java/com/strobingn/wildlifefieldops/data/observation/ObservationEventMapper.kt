package com.strobingn.wildlifefieldops.data.observation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.strobingn.wildlifefieldops.data.local.ObservationEventRecord

object ObservationEventMapper {
    private val gson = Gson()
    private val mapType = object : TypeToken<Map<String, Float>>() {}.type

    fun toRecord(event: ObservationEvent): ObservationEventRecord =
        ObservationEventRecord(
            eventId = event.eventId,
            entityId = event.entityId,
            observedAt = event.observedAt,
            uploadedAt = event.uploadedAt,
            deviceId = event.deviceId,
            operatorId = event.operatorId,
            modelId = event.modelId,
            modelHash = event.modelHash,
            backendTag = event.backendTag,
            quantizerTag = event.quantizerTag,
            frameHash = event.frameHash,
            cropHash = event.cropHash,
            mediaUri = event.mediaUri,
            labelDistributionJson = gson.toJson(event.labelDistribution),
            captureQuality = event.captureQuality,
            geometryTrust = event.geometryTrust,
            humanVerification = event.humanVerificationState.name,
            supersedesEventId = event.supersedesEventId,
        )

    fun toDomain(record: ObservationEventRecord): ObservationEvent =
        ObservationEvent(
            eventId = record.eventId,
            entityId = record.entityId,
            observedAt = record.observedAt,
            uploadedAt = record.uploadedAt,
            deviceId = record.deviceId,
            operatorId = record.operatorId,
            modelId = record.modelId,
            modelHash = record.modelHash,
            backendTag = record.backendTag,
            quantizerTag = record.quantizerTag,
            frameHash = record.frameHash,
            cropHash = record.cropHash,
            mediaUri = record.mediaUri,
            labelDistribution = decodeDistribution(record.labelDistributionJson),
            captureQuality = record.captureQuality,
            geometryTrust = record.geometryTrust,
            humanVerificationState = runCatching {
                HumanVerificationState.valueOf(record.humanVerification)
            }.getOrDefault(HumanVerificationState.UNREVIEWED),
            supersedesEventId = record.supersedesEventId,
        )

    fun decodeDistribution(json: String): Map<String, Float> {
        if (json.isBlank()) return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, Float>>(json, mapType)
        }.getOrNull()?.filterValues { it.isFinite() } ?: emptyMap()
    }
}
