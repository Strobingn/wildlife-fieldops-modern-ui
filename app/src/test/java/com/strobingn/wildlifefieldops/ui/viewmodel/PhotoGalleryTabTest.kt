package com.strobingn.wildlifefieldops.ui.viewmodel

import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoGalleryTabTest {

    @Test
    fun allTabShowsEverything() {
        assertTrue(photoMatchesGalleryTab(Photo(), null))
        assertTrue(photoMatchesGalleryTab(Photo(category = PhotoCategory.DOCUMENT), null))
    }

    @Test
    fun inspectionPhotoWithoutJobShowsOnInspectionsTab() {
        val photo = Photo(category = PhotoCategory.INSPECTION, jobId = null, inspectionId = "insp-1")
        assertTrue(photoMatchesGalleryTab(photo, PhotoCategory.INSPECTION))
        assertFalse(photoMatchesGalleryTab(photo, PhotoCategory.JOB_SITE))
    }

    @Test
    fun photoAttachedToInspectionShowsOnInspectionsTabEvenWithOtherCategory() {
        val photo = Photo(category = PhotoCategory.EVIDENCE, inspectionId = "insp-1")
        assertTrue(photoMatchesGalleryTab(photo, PhotoCategory.INSPECTION))
        assertTrue(photoMatchesGalleryTab(photo, PhotoCategory.EVIDENCE))
    }

    @Test
    fun plainJobPhotoStaysOffInspectionsTab() {
        val photo = Photo(category = PhotoCategory.JOB_SITE, jobId = "job-1", inspectionId = "")
        assertFalse(photoMatchesGalleryTab(photo, PhotoCategory.INSPECTION))
        assertTrue(photoMatchesGalleryTab(photo, PhotoCategory.JOB_SITE))
    }
}
