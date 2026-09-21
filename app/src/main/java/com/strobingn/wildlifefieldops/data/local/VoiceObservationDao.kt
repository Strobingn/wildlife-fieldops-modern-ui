package com.strobingn.wildlifefieldops.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.strobingn.wildlifefieldops.data.model.VoiceObservation
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceObservationDao {
    @Query("SELECT * FROM voice_observations ORDER BY observedAt DESC")
    fun getAll(): Flow<List<VoiceObservation>>

    @Query("SELECT * FROM voice_observations WHERE jobId = :jobId ORDER BY observedAt DESC")
    fun getByJob(jobId: String): Flow<List<VoiceObservation>>

    @Query("SELECT * FROM voice_observations WHERE observationEventId = :eventId ORDER BY observedAt DESC")
    fun getByObservationEvent(eventId: String): Flow<List<VoiceObservation>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(observation: VoiceObservation)
}
