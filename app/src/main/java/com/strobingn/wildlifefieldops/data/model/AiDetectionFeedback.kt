package com.strobingn.wildlifefieldops.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One detection example. [verdict] is UNLABELED until Sir answers
 * Yes, No, or Partly. Rows sync to public.ai_detection_feedback.
 */
@Entity(tableName = "ai_detection_feedback")
data class AiDetectionFeedback(
    @PrimaryKey
    val id: String,
    val userId: String = "",
    val jobId: String = "",
    val photoId: String = "",
    val photoLocalPath: String = "",
    val photoStoragePath: String = "",
    val modelLabel: String = "",
    val modelConfidence: Double = 0.0,
    val verdict: String = "UNLABELED",
    val correctedLabel: String = "",
    val whatImageShows: String = "",
    val notes: String = "",
    val embedding: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = false,
    val syncError: String? = null,
)
