package com.strobingn.wildlifefieldops.ai.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionFeedbackTest {
    @Test
    fun noAndPartlyRequireWhatTheImageShows() {
        val no = FeedbackDraft(verdict = "NO", pickerLabel = "Soffit")
        val partly = FeedbackDraft(verdict = "partly", whatImageShows = "  ")
        assertEquals(DetectionFeedbackPolicy.NEED_WHAT, DetectionFeedbackPolicy.validate(no))
        assertEquals(DetectionFeedbackPolicy.NEED_WHAT, DetectionFeedbackPolicy.validate(partly))
        val yes = FeedbackDraft(verdict = "YES")
        assertNull(DetectionFeedbackPolicy.validate(yes))
    }

    @Test
    fun freeTextOverridesThePickerAndSavesTheRecord() {
        val draft = FeedbackDraft(
            verdict = "NO",
            whatImageShows = "Open soffit at the roof return",
            pickerLabel = "Vent",
            freeTextLabel = "Soffit",
            notes = "Left corner",
        )
        val built = DetectionFeedbackPolicy.feedbackRecord(
            id = "fb-1",
            userId = "sir",
            jobId = "job-1",
            photoId = "photo-1",
            photoLocalPath = "/data/photos/LIVE.jpg",
            photoStoragePath = "",
            modelLabel = "Vent",
            modelConfidence = 0.62,
            draft = draft,
            embedding = "1,0",
            createdAt = 1_700_000_000_000L,
        ) as FeedbackBuild.Ok
        assertEquals("Soffit", built.record.correctedLabel)
        assertEquals("NO", built.record.verdict)
        assertEquals("Open soffit at the roof return", built.record.whatImageShows)
        assertEquals("/data/photos/LIVE.jpg", built.record.photoLocalPath)
        assertEquals("Left corner", built.record.notes)
        assertFalse(built.record.verdict == DetectionLabels.VERDICT_UNLABELED)
    }

    @Test
    fun yesConfirmsTheModelLabelWhenSirDoesNotTypeOne() {
        val built = DetectionFeedbackPolicy.feedbackRecord(
            id = "fb-2",
            userId = "sir",
            jobId = "",
            photoId = "photo-2",
            photoLocalPath = "/data/photos/a.jpg",
            photoStoragePath = "",
            modelLabel = "Raccoon",
            modelConfidence = 0.8,
            draft = FeedbackDraft(verdict = "YES"),
            embedding = "",
            createdAt = 10L,
        ) as FeedbackBuild.Ok
        assertEquals("Raccoon", built.record.correctedLabel)
    }

    @Test
    fun yesKeepsTheLearnedLabelWhenSirDoesNotPickAnotherType() {
        val built = DetectionFeedbackPolicy.feedbackRecord(
            id = "fb-learned",
            userId = "sir",
            jobId = "",
            photoId = "photo-3",
            photoLocalPath = "/data/photos/c.jpg",
            photoStoragePath = "",
            modelLabel = "Raccoon",
            modelConfidence = 0.8,
            draft = FeedbackDraft(verdict = "YES", shownLabel = "Soffit"),
            embedding = "",
            createdAt = 11L,
        ) as FeedbackBuild.Ok
        assertEquals("Soffit", built.record.correctedLabel)
    }

    @Test
    fun aTypedTypeOverridesTheLearnedLabel() {
        val built = DetectionFeedbackPolicy.feedbackRecord(
            id = "fb-override",
            userId = "sir",
            jobId = "",
            photoId = "photo-4",
            photoLocalPath = "/data/photos/d.jpg",
            photoStoragePath = "",
            modelLabel = "Raccoon",
            modelConfidence = 0.8,
            draft = FeedbackDraft(verdict = "YES", pickerLabel = "Chimney", shownLabel = "Soffit"),
            embedding = "",
            createdAt = 12L,
        ) as FeedbackBuild.Ok
        assertEquals("Chimney", built.record.correctedLabel)
    }

    @Test
    fun unlabeledExampleKeepsTheModelOutput() {
        val row = DetectionFeedbackPolicy.unlabeledRecord(
            id = "ex-1",
            userId = "sir",
            jobId = "job-9",
            photoId = "photo-9",
            photoLocalPath = "/data/photos/b.jpg",
            modelLabel = "Gap",
            modelConfidence = 0.4,
            embedding = "",
            createdAt = 20L,
        )
        assertEquals(DetectionLabels.VERDICT_UNLABELED, row.verdict)
        assertEquals("Gap", row.modelLabel)
        assertEquals("", row.correctedLabel)
    }

    @Test
    fun primaryOutputPrefersTheHigherScore() {
        val output = DetectionFeedbackPolicy.primaryOutput(
            entryTypes = listOf("Vent"),
            species = listOf("Raccoon"),
            speciesScores = mapOf("Raccoon" to 0.91f),
            confidence = 0.4f,
            suggestionLabel = null,
            suggestionConfidence = null,
        )
        assertEquals("Raccoon", output.label)
        assertEquals(0.91, output.confidence, 0.001)
    }
}
