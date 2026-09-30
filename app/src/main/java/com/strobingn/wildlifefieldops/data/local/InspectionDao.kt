package com.strobingn.wildlifefieldops.data.local

import androidx.room.*
import com.strobingn.wildlifefieldops.data.model.Inspection
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {
    @Query("SELECT * FROM inspections ORDER BY inspectionDate DESC")
    fun getAll(): Flow<List<Inspection>>

    @Query("SELECT * FROM inspections WHERE id = :id")
    suspend fun getById(id: String): Inspection?

    @Query("SELECT * FROM inspections WHERE jobId = :jobId ORDER BY inspectionDate DESC")
    fun getByJob(jobId: String): Flow<List<Inspection>>

    @Query("SELECT * FROM inspections WHERE customerId = :customerId ORDER BY inspectionDate DESC")
    fun getByCustomer(customerId: String): Flow<List<Inspection>>

    @Query("SELECT * FROM inspections WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Inspection>

    @Query("SELECT COUNT(*) FROM inspections WHERE isSynced = 0")
    suspend fun countUnsynced(): Int

    @Query("SELECT * FROM inspections WHERE followUpRequired = 1 AND followUpDate <= :currentTime")
    suspend fun getPendingFollowUps(currentTime: Long): List<Inspection>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(inspection: Inspection)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(inspections: List<Inspection>)

    @Update
    suspend fun update(inspection: Inspection)

    @Delete
    suspend fun delete(inspection: Inspection)

    @Query("SELECT COUNT(*) FROM inspections")
    suspend fun count(): Int

    @Query("UPDATE inspections SET isSynced = 1, syncError = NULL WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE inspections SET syncError = :error WHERE id = :id")
    suspend fun markSyncError(id: String, error: String?)

    @Query("DELETE FROM inspections")
    suspend fun deleteAll()
}
