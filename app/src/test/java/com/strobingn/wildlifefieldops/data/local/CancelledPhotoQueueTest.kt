package com.strobingn.wildlifefieldops.data.local

import android.app.Application
import androidx.room.Room
import com.strobingn.wildlifefieldops.data.model.DeletedRecord
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.remote.PhotoSyncQueue
import com.strobingn.wildlifefieldops.data.repository.JobRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The Jobs banner "Photo ced4294a: Job was cancelled" is a stuck upload error.
 * A live job keeps that photo in the queue. Cancelling or deleting the job
 * keeps the file and drops it from the queue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CancelledPhotoQueueTest {
    private lateinit var db: AppDatabase
    private lateinit var jobs: JobRepository

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        jobs = JobRepository(db.jobDao(), db.deletedRecordDao())
    }

    @After
    fun close() = db.close()

    @Test
    fun falseCancelledErrorStaysQueuedUntilARealCancel() = runBlocking {
        val job = Job(
            id = "job-live",
            title = "Woodchuck exclusion",
            status = JobStatus.IN_PROGRESS,
            customerName = "Rosa-Lee Nasso"
        )
        jobs.saveJob(job)
        val photo = Photo(
            id = "ced4294a-1111-4222-8333-444444444444",
            jobId = job.id,
            filePath = "/data/photos/ced4294a.jpg",
            localPath = "/data/photos/ced4294a.jpg",
            isUploaded = false,
            uploadError = "Job was cancelled"
        )
        db.photoDao().insert(photo)

        val queued = db.photoDao().getUnuploaded()
        assertEquals(listOf(photo.id), queued.map { it.id })
        assertEquals(1, db.photoDao().countUnuploaded())
        assertEquals(
            PhotoSyncQueue.Action.UPLOAD,
            PhotoSyncQueue.action(db.jobDao().getById(job.id)?.status, jobDeleted = false)
        )

        db.photoDao().markUploadError(photo.id, null)
        val cleared = db.photoDao().getById(photo.id)!!
        assertNull(cleared.uploadError)
        assertFalse(cleared.isUploaded)
        assertEquals("/data/photos/ced4294a.jpg", cleared.localPath)
        assertEquals(1, db.photoDao().countUnuploaded())

        jobs.updateJob(db.jobDao().getById(job.id)!!.copy(status = JobStatus.CANCELLED, isSynced = false))
        val cancelled = db.jobDao().getById(job.id)
        assertNotNull(cancelled)
        assertEquals(JobStatus.CANCELLED, cancelled!!.status)
        assertEquals(
            PhotoSyncQueue.Action.SKIP_KEEP_LOCAL,
            PhotoSyncQueue.action(cancelled.status, jobDeleted = false)
        )
        db.photoDao().markUploadError(photo.id, PhotoSyncQueue.KEPT_LOCAL_REASON)
        assertEquals(0, db.photoDao().countUnuploaded())
        assertTrue(db.photoDao().getUnuploaded().isEmpty())
        val kept = db.photoDao().getById(photo.id)!!
        assertEquals("/data/photos/ced4294a.jpg", kept.localPath)
        assertEquals("/data/photos/ced4294a.jpg", kept.filePath)
        assertFalse(kept.isUploaded)
    }

    @Test
    fun deleteJobStillTombstonesAndKeepsThePhotoFile() = runBlocking {
        val job = Job(id = "job-gone", title = "Inspection", status = JobStatus.SCHEDULED)
        jobs.saveJob(job)
        db.photoDao().insert(
            Photo(
                id = "photo-1",
                jobId = job.id,
                localPath = "/data/photos/keep.jpg",
                filePath = "/data/photos/keep.jpg",
                isUploaded = false
            )
        )
        jobs.deleteJob(db.jobDao().getById(job.id)!!)
        assertNull(db.jobDao().getById(job.id))
        assertTrue(db.deletedRecordDao().exists(job.id, DeletedRecord.TYPE_JOB))
        val photo = db.photoDao().getById("photo-1")!!
        assertEquals("/data/photos/keep.jpg", photo.localPath)
        val deleted = db.deletedRecordDao().getIdsByType(DeletedRecord.TYPE_JOB).contains(job.id)
        assertEquals(PhotoSyncQueue.Action.SKIP_KEEP_LOCAL, PhotoSyncQueue.action(null, deleted))
        db.photoDao().markUploadError(photo.id, PhotoSyncQueue.KEPT_LOCAL_REASON)
        assertTrue(db.photoDao().getUnuploaded().isEmpty())
        assertNotNull(db.photoDao().getById("photo-1"))
    }
}
