package com.strobingn.wildlifefieldops.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: ObservationEventRecord): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(events: List<ObservationEventRecord>)

    @Query("SELECT * FROM observation_events WHERE entityId = :entityId ORDER BY observedAt ASC")
    suspend fun getForEntity(entityId: String): List<ObservationEventRecord>

    @Query("SELECT * FROM observation_events WHERE entityId = :entityId ORDER BY observedAt ASC")
    fun observeForEntity(entityId: String): Flow<List<ObservationEventRecord>>

    @Query("SELECT * FROM observation_events ORDER BY observedAt DESC")
    fun observeAll(): Flow<List<ObservationEventRecord>>

    @Query("SELECT * FROM observation_events WHERE isSynced = 0 ORDER BY observedAt ASC")
    suspend fun getUnsynced(): List<ObservationEventRecord>

    @Query("SELECT COUNT(*) FROM observation_events WHERE isSynced = 0")
    suspend fun countUnsynced(): Int

    @Query(
        "UPDATE observation_events SET isSynced = 1, syncedAt = :syncedAt, syncError = NULL WHERE eventId = :eventId"
    )
    suspend fun markSynced(eventId: String, syncedAt: Long)

    @Query("UPDATE observation_events SET syncError = :error WHERE eventId = :eventId")
    suspend fun markSyncError(eventId: String, error: String?)

    @Query("SELECT COUNT(*) FROM observation_events")
    suspend fun count(): Int
}
