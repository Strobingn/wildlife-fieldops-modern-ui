package com.strobingn.wildlifefieldops.data.observation

import com.strobingn.wildlifefieldops.data.model.FieldObservation

/**
 * Pure queue rules for offline-created map observations.
 * Room persists the rows; [com.strobingn.wildlifefieldops.data.repository.SyncRepository]
 * pushes whatever this helper marks as ready.
 */
object FieldObservationSyncQueue {

    fun isMappable(observation: FieldObservation): Boolean =
        observation.latitude.isFinite() &&
            observation.longitude.isFinite() &&
            observation.latitude in -90.0..90.0 &&
            observation.longitude in -180.0..180.0

    /** Oldest unsynced, mappable records first — the offline backlog. */
    fun queuedForPush(records: List<FieldObservation>): List<FieldObservation> =
        records
            .filter { !it.isSynced && isMappable(it) }
            .sortedBy { it.observedAt }

    fun afterSuccessfulPush(
        records: List<FieldObservation>,
        pushedIds: Set<String>
    ): List<FieldObservation> =
        records.map { row ->
            if (row.id in pushedIds) row.copy(isSynced = true, syncError = null) else row
        }

    fun remainingUnsynced(records: List<FieldObservation>): Int =
        records.count { !it.isSynced }
}
