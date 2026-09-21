package com.strobingn.wildlifefieldops.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Map-visible field observation (photo + GPS + note) created on or off network.
 * Stays in Room until [isSynced] is flipped by [com.strobingn.wildlifefieldops.data.repository.SyncRepository].
 *
 * Distinct from the immutable species-ID [com.strobingn.wildlifefieldops.data.observation.ObservationEvent]
 * log (roadmap item 2). [speciesHint] is an optional hook for that pipeline.
 */
@Entity(tableName = "field_observations")
data class FieldObservation(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val notes: String = "",
    val latitude: Double,
    val longitude: Double,
    val photoLocalPath: String = "",
    val photoId: String? = null,
    val jobId: String? = null,
    val speciesHint: String = "",
    val accuracyMeters: Float? = null,
    val observedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val syncError: String? = null
)
