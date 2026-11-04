package com.strobingn.wildlifefieldops.ai.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectionCorrectionMemoryTest {
    private val darkLeft = IntArray(FrameAverageHash.LENGTH) { index -> if (index < 32) 20 else 220 }
    private val hash = FrameAverageHash.fromGrid(darkLeft)

    @Test
    fun sameModelLabelWithoutAnEmbeddingUsesTheCorrection() {
        val preference = DetectionCorrectionMemory.prefer(
            modelLabel = "Vent",
            embedding = null,
            examples = listOf(
                example("Vent", "Soffit", emptyList(), createdAt = 1),
                example("Gap", "Foundation crack", emptyList(), createdAt = 5),
            ),
        )
        assertEquals("Soffit", preference!!.label)
        assertTrue(preference.learned)
    }

    @Test
    fun similarEmbeddingPrefersTheCorrection() {
        val preference = DetectionCorrectionMemory.prefer(
            modelLabel = "vent",
            embedding = hash,
            examples = listOf(example("Vent", "Soffit", hash, createdAt = 2)),
        )
        assertEquals("Soffit", preference!!.label)
        assertTrue(preference.learned)
    }

    @Test
    fun dissimilarEmbeddingDoesNotReuseTheCorrection() {
        val other = FrameAverageHash.fromGrid(IntArray(FrameAverageHash.LENGTH) { index -> if (index % 2 == 0) 0 else 255 })
        val preference = DetectionCorrectionMemory.prefer(
            modelLabel = "Vent",
            embedding = hash,
            examples = listOf(example("Vent", "Soffit", other, createdAt = 3)),
        )
        assertNull(preference)
        assertTrue(FrameAverageHash.cosine(hash, other) < DetectionCorrectionMemory.SIMILARITY)
    }

    @Test
    fun newestMatchingCorrectionWins() {
        val preference = DetectionCorrectionMemory.prefer(
            modelLabel = "Gap",
            embedding = emptyList(),
            examples = listOf(
                example("Gap", "Foundation crack", emptyList(), createdAt = 1),
                example("Gap", "Soffit", emptyList(), createdAt = 9),
            ),
        )
        assertEquals("Soffit", preference!!.label)
    }

    @Test
    fun confirmedYesThatMatchesTheModelIsNotAnnouncedAsLearning() {
        val preference = DetectionCorrectionMemory.prefer(
            modelLabel = "Raccoon",
            embedding = null,
            examples = listOf(example("Raccoon", "Raccoon", emptyList(), createdAt = 4, verdict = "YES")),
        )
        assertEquals("Raccoon", preference!!.label)
        assertFalse(preference.learned)
    }

    @Test
    fun unlabeledRowsAreNotCorrections() {
        val row = DetectionFeedbackPolicy.unlabeledRecord(
            id = "u",
            userId = "sir",
            jobId = "",
            photoId = "p",
            photoLocalPath = "/tmp/a.jpg",
            modelLabel = "Vent",
            modelConfidence = 0.5,
            embedding = FrameAverageHash.encode(hash),
            createdAt = 1,
        )
        assertNull(DetectionCorrectionMemory.fromRecord(row))
    }

    @Test
    fun identicalHashesMatchAndEncodeRoundTrips() {
        val encoded = FrameAverageHash.encode(hash)
        val decoded = FrameAverageHash.decode(encoded)
        assertEquals(FrameAverageHash.LENGTH, decoded.size)
        assertEquals(1f, FrameAverageHash.cosine(hash, decoded), 0.001f)
    }

    private fun example(
        model: String,
        preferred: String,
        embedding: List<Float>,
        createdAt: Long,
        verdict: String = "NO",
    ) = CorrectionExample(model, preferred, embedding, createdAt, verdict)
}
