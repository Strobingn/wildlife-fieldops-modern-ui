package com.strobingn.wildlifefieldops.data.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationPhotoPathsTest {

    @Test
    fun localCandidatesExcludeRemoteUrls() {
        assertTrue(ObservationPhotoPaths.isLocalCandidate("/data/photos/OBS.jpg"))
        assertTrue(ObservationPhotoPaths.isLocalCandidate("file:///data/photos/OBS.jpg"))
        assertTrue(ObservationPhotoPaths.isLocalCandidate("content://media/external/images/1"))
        assertFalse(ObservationPhotoPaths.isLocalCandidate("https://example.supabase.co/storage/v1/object/public/observation-photos/a.jpg"))
        assertFalse(ObservationPhotoPaths.isLocalCandidate(""))
        assertFalse(ObservationPhotoPaths.isLocalCandidate(null))
    }

    @Test
    fun filesystemPathStripsFileScheme() {
        assertEquals("/tmp/obs.jpg", ObservationPhotoPaths.filesystemPath("file:///tmp/obs.jpg"))
        assertEquals("/tmp/obs.jpg", ObservationPhotoPaths.filesystemPath("/tmp/obs.jpg"))
        assertNull(ObservationPhotoPaths.filesystemPath("content://media/1"))
        assertNull(ObservationPhotoPaths.filesystemPath("https://cdn.example/a.jpg"))
    }

    @Test
    fun storagePathsAreDeterministicAndSanitized() {
        val field = ObservationPhotoPaths.fieldObservationPath("obs id/1", "/tmp/photos/OBS_1.PNG")
        assertEquals("field/obs_id_1/obs_id_1.png", field)
        assertEquals(field, ObservationPhotoPaths.fieldObservationPath("obs id/1", "ignored.PNG"))

        val event = ObservationPhotoPaths.eventPath("evt-99", "file:///tmp/LIVE.webp")
        assertEquals("events/evt-99/evt-99.webp", event)
        assertEquals("image/webp", ObservationPhotoPaths.mimeType("file:///tmp/LIVE.webp"))
        assertTrue(ObservationPhotoPaths.isAllowedMime("image/heic"))
    }

    @Test
    fun unknownExtensionsDefaultToJpeg() {
        assertEquals("jpg", ObservationPhotoPaths.extension("/tmp/frame.bin"))
        assertEquals("image/jpeg", ObservationPhotoPaths.mimeType("/tmp/frame.bin"))
    }
}
