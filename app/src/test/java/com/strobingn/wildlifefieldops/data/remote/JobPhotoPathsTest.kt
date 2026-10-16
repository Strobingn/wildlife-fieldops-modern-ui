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
        assertTrue(first.startsWith("public/"))
        assertTrue(first.endsWith(".jpg"))
        assertEquals("public/job-1/photo-9.jpg", first)
    }

    @Test
    fun sourceCandidatesPreferLocalPathThenFileUri() {
        assertEquals(
            listOf("/data/photos/a.jpg", "content://app/internal_files/photos/a.jpg"),
            JobPhotoPaths.sourceCandidates(
                "/data/photos/a.jpg",
                "content://app/internal_files/photos/a.jpg"
            )
        )
        assertEquals(
            listOf("/data/photos/a.jpg"),
            JobPhotoPaths.sourceCandidates("/data/photos/a.jpg", "/data/photos/a.jpg")
        )
        assertEquals(emptyList<String>(), JobPhotoPaths.sourceCandidates("  ", ""))
    }

    @Test
    fun unassignedFolderWhenJobIdMissing() {
        val path = JobPhotoPaths.objectPath(null, "p1", "a.png")
        assertEquals("public/unassigned/p1.jpg", path)
    }

    @Test
    fun inspectionPhotoWithoutJobFollowsLinkedInspectionJob() {
        assertEquals("job-1", JobPhotoPaths.resolveJobId(null, "job-1"))
        assertEquals("job-1", JobPhotoPaths.resolveJobId("", "job-1"))
        assertEquals("job-2", JobPhotoPaths.resolveJobId("job-2", "job-1"))
        assertEquals(null, JobPhotoPaths.resolveJobId(null, ""))
        assertEquals(null, JobPhotoPaths.resolveJobId(null, null))
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
