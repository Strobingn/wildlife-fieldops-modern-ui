package com.strobingn.wildlifefieldops.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.strobingn.wildlifefieldops.data.model.FieldObservation
import kotlinx.coroutines.flow.Flow

@Dao
interface FieldObservationDao {
    @Query("SELECT * FROM field_observations ORDER BY observedAt DESC")
    fun getAll(): Flow<List<FieldObservation>>

    @Query("SELECT * FROM field_observations ORDER BY observedAt DESC")
    suspend fun getAllOnce(): List<FieldObservation>

    @Query("SELECT * FROM field_observations WHERE isSynced = 0 ORDER BY observedAt ASC")
    suspend fun getUnsynced(): List<FieldObservation>

    @Query("SELECT * FROM field_observations WHERE id = :id")
    suspend fun getById(id: String): FieldObservation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(observation: FieldObservation)

    @Update
    suspend fun update(observation: FieldObservation)

    @Query("UPDATE field_observations SET isSynced = 1, syncError = NULL WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE field_observations SET syncError = :error WHERE id = :id")
    suspend fun markSyncError(id: String, error: String?)

    @Query("SELECT COUNT(*) FROM field_observations")
    suspend fun count(): Int
}
