package com.strobingn.wildlifefieldops.ai.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionFewShotAndCsvTest {
    @Test
    fun emptyExamplesAddNoPromptAndNoPayloadRows() {
        assertEquals("", DetectionFewShot.promptBlock(emptyList()))
        assertTrue(DetectionFewShot.wire(emptyList()).isEmpty())
    }

    @Test
    fun promptNamesTheCorrectionAndDropsOversizedThumbnails() {
        val huge = "data:image/jpeg;base64," + "a".repeat(DetectionFewShot.THUMB_EACH)
        val small = "data:image/jpeg;base64,aaaa"
        val wired = DetectionFewShot.wire(
            listOf(
                FewShotExample("Soffit", "Open soffit gap", "NO", "Vent", huge),
                FewShotExample("Raccoon", "Masked face, ringed tail", "YES", "Dog", small),
            )
        )
        assertEquals("", wired[0].thumbnailDataUrl)
        assertEquals(small, wired[1].thumbnailDataUrl)
        val block = DetectionFewShot.promptBlock(wired)
        assertTrue(block.startsWith(DetectionFewShot.MARKER))
        assertTrue(block.contains("Soffit"))
        assertTrue(block.contains("Vent"))
        assertFalse(block.contains(huge))
    }

    @Test
    fun csvUsesUsDatesAndKeepsPhotoPaths() {
        val row = StoredDetectionExample(
            id = "id-1",
            userId = "sir",
            jobId = "job",
            photoId = "photo",
            photoLocalPath = "/data/photos/gap, left.jpg",
            photoStoragePath = "detection/id-1.jpg",
            modelLabel = "Gap",
            modelConfidence = 0.5,
            verdict = "PARTLY",
            correctedLabel = "Foundation crack",
            whatImageShows = "Crack \"under\" the sill",
            notes = "note",
            embedding = "",
            createdAt = 1_762_214_400_000L,
        )
        val csv = DetectionDatasetCsv.render(listOf(row))
        assertTrue(csv.startsWith(DetectionDatasetCsv.HEADER.joinToString(",") + "\n"))
        assertTrue(csv.contains(DetectionDatasetCsv.formatDay(row.createdAt)))
        assertTrue(csv.contains("\"/data/photos/gap, left.jpg\""))
        assertTrue(csv.contains("detection/id-1.jpg"))
        assertTrue(csv.contains("\"Crack \"\"under\"\" the sill\""))
        assertTrue(Regex("""\d{2}/\d{2}/\d{4}""").containsMatchIn(csv))
    }
}
