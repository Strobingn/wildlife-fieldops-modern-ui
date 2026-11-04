package com.strobingn.wildlifefieldops.data.local

import android.app.Application
import androidx.room.Room
import com.strobingn.wildlifefieldops.ai.detection.DetectionFeedbackPolicy
import com.strobingn.wildlifefieldops.ai.detection.DetectionLabels
import com.strobingn.wildlifefieldops.ai.detection.FeedbackBuild
import com.strobingn.wildlifefieldops.ai.detection.FeedbackDraft
import com.strobingn.wildlifefieldops.data.detection.toEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AiDetectionFeedbackDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun close() = db.close()

    @Test
    fun savesUnlabeledExampleAndACorrectionForTheSamePhoto() = runBlocking {
        val dao = db.aiDetectionFeedbackDao()
        val unlabeled = DetectionFeedbackPolicy.unlabeledRecord(
            id = "11111111-1111-4111-8111-111111111111",
            userId = "sir-device",
            jobId = "job-1",
            photoId = "photo-1",
            photoLocalPath = "/data/photos/LIVE_entry.jpg",
            modelLabel = "Vent",
            modelConfidence = 0.44,
            embedding = "",
            createdAt = 50L,
        )
        dao.insert(unlabeled.toEntity())

        val feedback = DetectionFeedbackPolicy.feedbackRecord(
            id = "22222222-2222-4222-8222-222222222222",
            userId = "sir-device",
            jobId = "job-1",
            photoId = "photo-1",
            photoLocalPath = "/data/photos/LIVE_entry.jpg",
            photoStoragePath = "",
            modelLabel = "Vent",
            modelConfidence = 0.44,
            draft = FeedbackDraft(
                verdict = "NO",
                whatImageShows = "Soffit gap at the roof return",
                pickerLabel = "Soffit",
            ),
            embedding = "",
            createdAt = 60L,
        ) as FeedbackBuild.Ok
        dao.insert(feedback.record.toEntity())

        val stored = dao.getAllOnce()
        assertEquals(2, stored.size)
        assertEquals(DetectionLabels.VERDICT_UNLABELED, stored.first { it.id.startsWith("1111") }.verdict)
        val correction = stored.first { it.verdict == "NO" }
        assertEquals("Soffit", correction.correctedLabel)
        assertEquals("/data/photos/LIVE_entry.jpg", correction.photoLocalPath)
        assertFalse(correction.isSynced)
        assertEquals(2, dao.countUnsynced())
        assertEquals(listOf(unlabeled.id, feedback.record.id), dao.getUnsynced().map { it.id })

        dao.markSynced(correction.id)
        assertEquals(1, dao.countUnsynced())
        assertTrue(dao.recentReviewed().any { it.correctedLabel == "Soffit" })
        assertEquals("Vent", dao.latestForPhoto("photo-1")?.modelLabel)
    }
}
