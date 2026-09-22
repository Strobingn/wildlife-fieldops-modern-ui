package com.strobingn.wildlifefieldops.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only Room row for an immutable [com.strobingn.wildlifefieldops.data.observation.ObservationEvent].
 * Inserts use IGNORE so a repeated content hash is a no-op (ADR 0002).
 */
@Entity(
    tableName = "observation_events",
    indices = [
        Index(value = ["entityId", "observedAt"]),
        Index(value = ["humanVerification"]),
    ],
)
data class ObservationEventRecord(
    @PrimaryKey val eventId: String,
    val entityId: String,
    val observedAt: Long,
    val uploadedAt: Long,
    val deviceId: String,
    val operatorId: String,
    val modelId: String,
    val modelHash: String,
    val backendTag: String,
    val quantizerTag: String,
    val frameHash: String,
    val cropHash: String,
    val mediaUri: String?,
    val labelDistributionJson: String,
    val captureQuality: Float,
    val geometryTrust: Float,
    val humanVerification: String,
    val supersedesEventId: String?,
    /** Local sync bookkeeping only — not part of the immutable evidence payload. */
    val isSynced: Boolean = false,
    val syncedAt: Long? = null,
)
