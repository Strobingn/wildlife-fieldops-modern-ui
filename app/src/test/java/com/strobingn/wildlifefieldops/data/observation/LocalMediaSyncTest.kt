package com.strobingn.wildlifefieldops.data.observation

import com.strobingn.wildlifefieldops.data.repository.syncFailureDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalMediaSyncTest {

    @Test
    fun missingLocalFileStaysQueued() {
        assertEquals(
            LocalMediaSync.Plan.MISSING,
            LocalMediaSync.plan("/internal_files/photos/IMG.jpg", bytesAvailable = false)
        )
        assertEquals(
            LocalMediaSync.Plan.UPLOAD,
            LocalMediaSync.plan("content://app/internal_files/photos/IMG.jpg", bytesAvailable = true)
        )
    }

    @Test
    fun remoteOrEmptyPathSyncsMetadataOnly() {
        assertEquals(LocalMediaSync.Plan.ABSENT, LocalMediaSync.plan("", bytesAvailable = false))
        assertEquals(
            LocalMediaSync.Plan.ABSENT,
            LocalMediaSync.plan("https://cdn.example/a.jpg", bytesAvailable = false)
        )
        assertEquals(LocalMediaSync.Plan.ABSENT, LocalMediaSync.plan(null, bytesAvailable = true))
    }

    @Test
    fun statusBarShowsPhotoFailureFromLastSync() {
        val message = "Synced. Pushed: 0 jobs, 0 photos. Failed (1): photo attic — Local job photo missing: /internal_files/photos/IMG.jpg"
        assertEquals(
            "Photo abc: Local job photo missing",
            syncFailureDetail("Photo abc: Local job photo missing", lastOk = false, lastMessage = message)
        )
        assertEquals(
            "Failed (1): photo attic — Local job photo missing: /internal_files/photos/IMG.jpg",
            syncFailureDetail(null, lastOk = false, lastMessage = message)
        )
        assertNull(syncFailureDetail(null, lastOk = true, lastMessage = message))
    }
}
