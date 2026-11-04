package com.strobingn.wildlifefieldops.data.detection

import android.content.Context
import android.provider.Settings
import com.strobingn.wildlifefieldops.ai.detection.CorrectionExample
import com.strobingn.wildlifefieldops.ai.detection.DetectionCorrectionMemory
import com.strobingn.wildlifefieldops.ai.detection.DetectionDatasetCsv
import com.strobingn.wildlifefieldops.ai.detection.DetectionFeedbackPolicy
import com.strobingn.wildlifefieldops.ai.detection.DetectionFewShot
import com.strobingn.wildlifefieldops.ai.detection.FeedbackBuild
import com.strobingn.wildlifefieldops.ai.detection.FeedbackDraft
import com.strobingn.wildlifefieldops.ai.detection.FewShotExample
import com.strobingn.wildlifefieldops.ai.detection.FrameAverageHash
import com.strobingn.wildlifefieldops.ai.detection.FrameAverageHashEncoder
import com.strobingn.wildlifefieldops.ai.detection.StoredDetectionExample
import com.strobingn.wildlifefieldops.data.local.AiDetectionFeedbackDao
import com.strobingn.wildlifefieldops.data.model.AiDetectionFeedback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetectionFeedbackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AiDetectionFeedbackDao,
) {
    fun observeAll(): Flow<List<AiDetectionFeedback>> = dao.observeAll()

    suspend fun recordUnlabeled(
        photoId: String,
        jobId: String,
        photoLocalPath: String,
        modelLabel: String,
        modelConfidence: Double,
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val embedding = FrameAverageHash.encode(FrameAverageHashEncoder.fromFile(photoLocalPath))
        val row = DetectionFeedbackPolicy.unlabeledRecord(
            id = UUID.randomUUID().toString(),
            userId = userId(),
            jobId = jobId,
            photoId = photoId,
            photoLocalPath = photoLocalPath,
            modelLabel = modelLabel,
            modelConfidence = modelConfidence,
            embedding = embedding,
            createdAt = now,
        )
        dao.insert(row.toEntity())
    }

    suspend fun saveFeedback(
        draft: FeedbackDraft,
        jobId: String,
        photoId: String,
        photoLocalPath: String,
        photoStoragePath: String,
        modelLabel: String,
        modelConfidence: Double,
    ): String = withContext(Dispatchers.IO) {
        val embedding = FrameAverageHash.encode(FrameAverageHashEncoder.fromFile(photoLocalPath))
        when (
            val built = DetectionFeedbackPolicy.feedbackRecord(
                id = UUID.randomUUID().toString(),
                userId = userId(),
                jobId = jobId,
                photoId = photoId,
                photoLocalPath = photoLocalPath,
                photoStoragePath = photoStoragePath,
                modelLabel = modelLabel,
                modelConfidence = modelConfidence,
                draft = draft,
                embedding = embedding,
                createdAt = System.currentTimeMillis(),
            )
        ) {
            is FeedbackBuild.Rejected -> built.reason
            is FeedbackBuild.Ok -> {
                dao.insert(built.record.toEntity())
                "Saved on this phone. It will sync with your other records."
            }
        }
    }

    suspend fun corrections(): List<CorrectionExample> = withContext(Dispatchers.IO) {
        dao.recentReviewed().mapNotNull { DetectionCorrectionMemory.fromRecord(it.toStored()) }
    }

    suspend fun fewShot(): List<FewShotExample> = withContext(Dispatchers.IO) {
        DetectionFewShot.fromRecords(dao.recentReviewed().map { it.toStored() })
    }

    suspend fun latestForPhoto(photoId: String): AiDetectionFeedback? =
        withContext(Dispatchers.IO) { dao.latestForPhoto(photoId) }

    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        DetectionDatasetCsv.render(dao.getAllOnce().map { it.toStored() })
    }

    private fun userId(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        return androidId.ifBlank { "field-tech" }
    }
}

fun StoredDetectionExample.toEntity(): AiDetectionFeedback = AiDetectionFeedback(
    id = id,
    userId = userId,
    jobId = jobId,
    photoId = photoId,
    photoLocalPath = photoLocalPath,
    photoStoragePath = photoStoragePath,
    modelLabel = modelLabel,
    modelConfidence = modelConfidence,
    verdict = verdict,
    correctedLabel = correctedLabel,
    whatImageShows = whatImageShows,
    notes = notes,
    embedding = embedding,
    createdAt = createdAt,
    isSynced = false,
    syncError = null,
)

fun AiDetectionFeedback.toStored(): StoredDetectionExample = StoredDetectionExample(
    id = id,
    userId = userId,
    jobId = jobId,
    photoId = photoId,
    photoLocalPath = photoLocalPath,
    photoStoragePath = photoStoragePath,
    modelLabel = modelLabel,
    modelConfidence = modelConfidence,
    verdict = verdict,
    correctedLabel = correctedLabel,
    whatImageShows = whatImageShows,
    notes = notes,
    embedding = embedding,
    createdAt = createdAt,
)
