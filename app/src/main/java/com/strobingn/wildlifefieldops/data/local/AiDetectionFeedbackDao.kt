package com.strobingn.wildlifefieldops.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.strobingn.wildlifefieldops.data.model.AiDetectionFeedback
import kotlinx.coroutines.flow.Flow

@Dao
interface AiDetectionFeedbackDao {
    @Query("SELECT * FROM ai_detection_feedback ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AiDetectionFeedback>>

    @Query("SELECT * FROM ai_detection_feedback ORDER BY createdAt ASC")
    suspend fun getAllOnce(): List<AiDetectionFeedback>

    @Query("SELECT * FROM ai_detection_feedback WHERE isSynced = 0 ORDER BY createdAt ASC")
    suspend fun getUnsynced(): List<AiDetectionFeedback>

    @Query(
        "SELECT * FROM ai_detection_feedback WHERE verdict IN ('YES','NO','PARTLY') " +
            "ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun recentReviewed(limit: Int = 12): List<AiDetectionFeedback>

    @Query(
        "SELECT * FROM ai_detection_feedback WHERE photoId = :photoId ORDER BY createdAt DESC LIMIT 1"
    )
    suspend fun latestForPhoto(photoId: String): AiDetectionFeedback?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(row: AiDetectionFeedback)

    @Query("UPDATE ai_detection_feedback SET photoStoragePath = :path WHERE id = :id")
    suspend fun setStoragePath(id: String, path: String)

    @Query("UPDATE ai_detection_feedback SET isSynced = 1, syncError = NULL WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE ai_detection_feedback SET syncError = :error WHERE id = :id")
    suspend fun markSyncError(id: String, error: String?)

    @Query("SELECT COUNT(*) FROM ai_detection_feedback WHERE isSynced = 0")
    suspend fun countUnsynced(): Int
}
