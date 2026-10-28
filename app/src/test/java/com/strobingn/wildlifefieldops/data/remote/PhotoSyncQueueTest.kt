package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.repository.syncFailureDetail
import com.strobingn.wildlifefieldops.data.repository.syncLooksFailed
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSyncQueueTest {

    @Test
    fun liveJobWithFalseCancelledBannerStillUploads() {
        assertEquals(
            PhotoSyncQueue.Action.UPLOAD,
            PhotoSyncQueue.action(JobStatus.IN_PROGRESS, jobDeleted = false)
        )
        assertEquals(
            PhotoSyncQueue.Action.UPLOAD,
            PhotoSyncQueue.action(JobStatus.SCHEDULED, jobDeleted = false)
        )
        assertTrue(PhotoSyncQueue.isFalseCancellationMessage("Job was cancelled"))
        assertTrue(PhotoSyncQueue.isFalseCancellationMessage("Photo ced4294a: Job was cancelled"))
        assertFalse(
            syncLooksFailed(
                hasBacklogFailures = false,
                lastOk = false,
                lastMessage = "Synced. Pushed: 0 jobs, 0 photos. Still pending locally: 8. " +
                    "Failed (1): photo ced4294a — Job was cancelled"
            )
        )
        assertNull(
            syncFailureDetail(
                "Photo ced4294a: Job was cancelled",
                lastOk = false,
                lastMessage = "Failed (1): photo ced4294a — Job was cancelled"
            )
        )
    }

    @Test
    fun httpWrapperIsTheSameFalseSignal() {
        val message = "HTTP request to https://example.supabase.co/storage/v1/object/job-photos/ced4294a.jpg " +
            "failed with message: Job was cancelled"
        assertTrue(PhotoSyncQueue.isCoroutineCancellation(RuntimeException(message)))
        assertTrue(PhotoSyncQueue.isCoroutineCancellation(CancellationException("Job was cancelled")))
        assertFalse(PhotoSyncQueue.isCoroutineCancellation(IllegalStateException("column missing")))
    }

    @Test
    fun realNetworkFailureStillCountsAsFailed() {
        assertTrue(
            syncLooksFailed(
                hasBacklogFailures = false,
                lastOk = false,
                lastMessage = "Sync failed: Network error. timeout"
            )
        )
        assertFalse(PhotoSyncQueue.isFalseCancellationMessage("timeout talking to storage"))
    }

    @Test
    fun mixedFailureIsNotCleared() {
        val message = "Synced. Failed (2): photo ced4294a — Job was cancelled; job Roof — Cloud table is missing a column"
        assertFalse(PhotoSyncQueue.isFalseCancellationMessage(message))
        assertTrue(syncLooksFailed(hasBacklogFailures = false, lastOk = false, lastMessage = message))
    }

    @Test
    fun cancelledOrDeletedJobSkipsWithoutDroppingTheDecision() {
        assertEquals(
            PhotoSyncQueue.Action.SKIP_KEEP_LOCAL,
            PhotoSyncQueue.action(JobStatus.CANCELLED, jobDeleted = false)
        )
        assertEquals(
            PhotoSyncQueue.Action.SKIP_KEEP_LOCAL,
            PhotoSyncQueue.action(null, jobDeleted = true)
        )
        // Missing local row is not proof the job was cancelled.
        assertEquals(
            PhotoSyncQueue.Action.UPLOAD,
            PhotoSyncQueue.action(null, jobDeleted = false)
        )
        assertTrue(PhotoSyncQueue.isKeptLocal(PhotoSyncQueue.KEPT_LOCAL_REASON))
        assertFalse(PhotoSyncQueue.isFalseCancellationMessage(PhotoSyncQueue.KEPT_LOCAL_REASON))
    }

    @Test
    fun cancellingAJobDoesNotDeleteIt() {
        assertFalse(JobStatus.CANCELLED.isWorkOpen())
        assertTrue(JobStatus.IN_PROGRESS.isWorkOpen())
        assertEquals("Cancelled", JobStatus.CANCELLED.toRemoteStatus())
    }
}
