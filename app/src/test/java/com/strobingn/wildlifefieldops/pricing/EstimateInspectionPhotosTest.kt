package com.strobingn.wildlifefieldops.pricing

import com.strobingn.wildlifefieldops.data.model.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EstimateInspectionPhotosTest {
    @Test
    fun inspectionPhotosAttachInCaptureOrderAndSkipOtherInspections() {
        val photos = listOf(
            photo("later", "insp-1", takenAt = 20),
            photo("other", "insp-9", takenAt = 5),
            photo("early", "insp-1", takenAt = 10),
            photo("unsaved", "insp-new", takenAt = 15)
        )
        val ids = EstimateInspectionPhotos.idsBelongingToInspections(
            EstimateInspectionPhotos.inspectionIds(listOf("insp-1"), extraInspectionId = "insp-new"),
            photos
        )
        assertEquals(listOf("early", "unsaved", "later"), ids)
    }

    @Test
    fun mergeKeepsRemovedPhotosOffAndKeepsPhotosAddedOnTheEstimate() {
        val first = EstimateInspectionPhotos.merge(
            attachedIds = emptyList(),
            removedIds = emptyList(),
            incomingIds = listOf("a", "b")
        )
        assertEquals(listOf("a", "b"), first.attachedIds)

        val removed = EstimateInspectionPhotos.remove(first.attachedIds, first.removedIds, "a")
        assertEquals(listOf("b"), removed.attachedIds)
        val again = EstimateInspectionPhotos.merge(removed.attachedIds, removed.removedIds, listOf("a", "b", "c"))
        assertEquals(listOf("b", "c"), again.attachedIds)
        assertTrue("a" in again.removedIds)

        val putBack = EstimateInspectionPhotos.add(again.attachedIds, again.removedIds, "a")
        assertEquals(listOf("b", "c", "a"), putBack.attachedIds)
        assertTrue("a" !in putBack.removedIds)
    }

    @Test
    fun printablePathsUseTheLocalFileAndSkipDerivedPreviews() {
        val file = File.createTempFile("estimate-photo", ".jpg")
        file.writeBytes(byteArrayOf(1, 2, 3))
        val derived = File(file.parentFile, "derived_skip.jpg")
        derived.writeBytes(byteArrayOf(9))
        try {
            val paths = EstimateInspectionPhotos.printablePaths(
                listOf(
                    Photo(id = "ok", localPath = file.absolutePath, inspectionId = "insp"),
                    Photo(id = "missing", localPath = "/no/such/file.jpg", inspectionId = "insp"),
                    Photo(id = "preview", localPath = derived.absolutePath, inspectionId = "insp")
                )
            )
            assertEquals(listOf(file.absolutePath), paths)
        } finally {
            file.delete()
            derived.delete()
        }
    }

    @Test
    fun inspectionReportKeepsPhotosTheEstimateOptedOut() {
        val photos = listOf(
            photo("a", "insp", takenAt = 1),
            photo("b", "insp", takenAt = 2)
        )
        val report = EstimateInspectionPhotos.inspectionReportPhotos(listOf("insp"), photos).map { it.id }
        assertEquals(listOf("a", "b"), report)
        val estimate = EstimateInspectionPhotos.merge(
            attachedIds = emptyList(),
            removedIds = listOf("a"),
            incomingIds = listOf("a", "b")
        )
        assertEquals(listOf("b"), estimate.attachedIds)
    }

    @Test
    fun insertedPhotosMergeIntoTheCurrentWorksheet() {
        val edited = JobPricing(
            laborHours = 4.0,
            laborRate = 185.0,
            totalOverride = 900.0,
            estimatePhotoIds = listOf("kept"),
            estimatePhotosRemoved = listOf("opt-out")
        )
        val merged = EstimateInspectionPhotos.withInsertedPhotos(edited, listOf("new-1"))
        assertEquals(4.0, merged.laborHours, 0.0)
        assertEquals(185.0, merged.laborRate, 0.0)
        assertEquals(900.0, merged.totalOverride)
        assertEquals(listOf("kept", "new-1"), merged.estimatePhotoIds)
        assertEquals(listOf("opt-out"), merged.estimatePhotosRemoved)
    }

    private fun photo(id: String, inspectionId: String, takenAt: Long) = Photo(
        id = id,
        inspectionId = inspectionId,
        takenAt = takenAt
    )
}
