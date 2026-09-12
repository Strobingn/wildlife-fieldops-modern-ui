package com.strobingn.wildlifefieldops.data.local

import androidx.room.*
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Job>>

    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<Job>

    @Query("SELECT * FROM jobs WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: JobStatus): Flow<List<Job>>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun getById(id: String): Job?

    @Query("SELECT * FROM jobs WHERE id = :id")
    fun observeById(id: String): Flow<Job?>

    @Query("SELECT COUNT(*) FROM jobs WHERE type = :serviceType")
    suspend fun countByServiceType(serviceType: String): Int

    @Query("UPDATE jobs SET type = :newType, isSynced = 0, updatedAt = :updatedAt WHERE type = :oldType")
    suspend fun reassignServiceType(oldType: String, newType: String, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("SELECT * FROM jobs WHERE customerId = :customerId ORDER BY createdAt DESC")
    fun getByCustomer(customerId: String): Flow<List<Job>>

    @Query("SELECT * FROM jobs WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Job>

    @Query("SELECT * FROM jobs WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR customerName LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<Job>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: Job)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(jobs: List<Job>)

    @Update
    suspend fun update(job: Job)

    @Delete
    suspend fun delete(job: Job)

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM jobs")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM jobs WHERE status = :status")
    suspend fun countByStatus(status: JobStatus): Int

    @Query("UPDATE jobs SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM jobs")
    suspend fun deleteAll()
}
