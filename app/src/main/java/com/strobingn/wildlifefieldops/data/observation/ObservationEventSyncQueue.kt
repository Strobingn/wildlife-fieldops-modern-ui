package com.strobingn.wildlifefieldops.data.observation

import com.strobingn.wildlifefieldops.data.local.ObservationEventRecord

/**
 * Pure queue rules for unsynced immutable [ObservationEvent] rows.
 * Room is the offline source of truth; [com.strobingn.wildlifefieldops.data.repository.SyncRepository]
 * inserts whatever this helper marks as ready. DerivedAssessment is never queued.
 */
object ObservationEventSyncQueue {

    fun queuedForPush(records: List<ObservationEventRecord>): List<ObservationEventRecord> =
        records
            .filter { !it.isSynced && it.eventId.isNotBlank() && it.entityId.isNotBlank() }
            .sortedWith(compareBy<ObservationEventRecord> { it.observedAt }.thenBy { it.eventId })

    fun afterSuccessfulPush(
        records: List<ObservationEventRecord>,
        pushedIds: Set<String>,
        syncedAt: Long
    ): List<ObservationEventRecord> =
        records.map { row ->
            if (row.eventId in pushedIds) row.copy(isSynced = true, syncedAt = syncedAt) else row
        }

    fun remainingUnsynced(records: List<ObservationEventRecord>): Int =
        records.count { !it.isSynced }
}
