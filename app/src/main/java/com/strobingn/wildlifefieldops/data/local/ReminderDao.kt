package com.strobingn.wildlifefieldops.data.local

import androidx.room.*
import com.strobingn.wildlifefieldops.data.model.Reminder
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY dueDate ASC")
    fun getAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE status = 'PENDING' OR status = 'OVERDUE' ORDER BY dueDate ASC")
    fun getPending(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE jobId = :jobId ORDER BY dueDate ASC")
    fun getByJob(jobId: String): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): Reminder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: Reminder)

    @Update
    suspend fun update(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)
}
