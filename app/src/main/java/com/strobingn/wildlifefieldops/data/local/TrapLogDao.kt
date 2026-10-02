package com.strobingn.wildlifefieldops.data.local

import androidx.room.*
import com.strobingn.wildlifefieldops.data.model.TrapLog
import kotlinx.coroutines.flow.Flow

@Dao
interface TrapLogDao {
    @Query("SELECT * FROM trap_logs ORDER BY checkDate DESC")
    fun getAll(): Flow<List<TrapLog>>

    @Query("SELECT * FROM trap_logs WHERE jobId = :jobId ORDER BY checkDate DESC")
    fun getByJob(jobId: String): Flow<List<TrapLog>>

    @Query("SELECT * FROM trap_logs ORDER BY checkDate DESC")
    suspend fun getAllOnce(): List<TrapLog>

    @Query("SELECT * FROM trap_logs WHERE jobId = :jobId ORDER BY checkDate DESC")
    suspend fun getByJobOnce(jobId: String): List<TrapLog>

    @Query("SELECT * FROM trap_logs WHERE id = :id")
    suspend fun getById(id: String): TrapLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trapLog: TrapLog)

    @Update
    suspend fun update(trapLog: TrapLog)

    @Delete
    suspend fun delete(trapLog: TrapLog)
}
