package com.strobingn.wildlifefieldops.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JobPhotoPathsTest {

    @Test
    fun pathIsDeterministicForRetries() {
        val first = JobPhotoPaths.objectPath("job-1", "photo-9", "/tmp/still.JPEG")
        val second = JobPhotoPaths.objectPath("job-1", "photo-9", "content://media/still.JPEG")
        assertEquals(first, second)
        assertEquals("job-photos", JobPhotoPaths.BUCKET)
        assertTrue(first.startsWith("job-1/"))
        assertTrue(first.endsWith(".jpg"))
    }

    @Test
    fun unassignedFolderWhenJobIdMissing() {
        val path = JobPhotoPaths.objectPath(null, "p1", "a.png")
        assertTrue(path.startsWith("unassigned/"))
        assertTrue(path.endsWith(".png"))
    }

    @Test
    fun backlogSummaryCountsPendingWithoutClearing() {
        val snap = com.strobingn.wildlifefieldops.data.repository.SyncBacklogSnapshot(
            pendingJobs = 3,
            pendingCustomers = 0,
            pendingInspections = 1,
            pendingObservations = 0,
            pendingEvents = 2,
            pendingPhotos = 4,
            failedJobs = 1,
            failedPhotos = 1,
            recentFailures = listOf("Job Middletown raccoon: column mismatch")
        )
        assertEquals(10, snap.pendingTotal)
        assertTrue(snap.hasFailures)
        assertTrue(snap.summaryLine().contains("3 jobs"))
        assertTrue(snap.summaryLine().contains("4 photos"))
        assertTrue(snap.summaryLine().contains("not cleared"))
    }
}
