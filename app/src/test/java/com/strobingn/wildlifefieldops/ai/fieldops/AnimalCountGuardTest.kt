package com.strobingn.wildlifefieldops.ai.fieldops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimalCountGuardTest {

    @Test
    fun overlappingBoxesAcrossFramesAreOneAnimal() {
        val boxes = listOf(
            box(frame = 0, left = 0f, top = 0f, right = 10f, bottom = 10f),
            box(frame = 1, left = 1f, top = 1f, right = 11f, bottom = 11f),
        )
        val count = AnimalCountGuard.estimate(boxes)
        assertEquals(1, count.maxBoxCount)
        assertEquals(1, count.trackCount)
        assertEquals(1, count.point)
        assertFalse(count.uncertain)
        assertEquals(count, AnimalCountGuard.sourceOfTruth(count, "I think there are 20 bats"))
    }

    @Test
    fun twoBoxesInOneFrameStayTwoTracks() {
        val boxes = listOf(
            box(frame = 0, left = 0f, top = 0f, right = 10f, bottom = 10f),
            box(frame = 0, left = 30f, top = 30f, right = 40f, bottom = 40f, label = "bat"),
        )
        val count = AnimalCountGuard.estimate(boxes)
        assertEquals(2, count.maxBoxCount)
        assertEquals(2, count.trackCount)
        assertEquals(2, count.low)
        assertEquals(2, count.high)
    }

    @Test
    fun fragmentedTracksPublishARangeInsteadOfAPreferredInteger() {
        val boxes = listOf(
            box(frame = 0, left = 0f, top = 0f, right = 10f, bottom = 10f),
            box(frame = 1, left = 100f, top = 100f, right = 110f, bottom = 110f),
        )
        val count = AnimalCountGuard.estimate(boxes)
        assertEquals(1, count.maxBoxCount)
        assertEquals(2, count.trackCount)
        assertEquals(1, count.low)
        assertEquals(2, count.high)
        assertNull(count.point)
        assertTrue(count.uncertain)
        val guarded = AnimalCountGuard.guardText(count, "The colony has 20 bats.")
        assertFalse(guarded.contains("20"))
        assertTrue(guarded.contains("1-2"))
        assertEquals(count, AnimalCountGuard.sourceOfTruth(count, "20"))
    }

    @Test
    fun vlmIntegerInsideTheRangeMayStayInProseButIsNotTheCount() {
        val truth = AnimalCount(
            maxBoxCount = 8,
            trackCount = 8,
            low = 8,
            high = 8,
            point = 8,
            uncertain = false,
        )
        val prose = AnimalCountGuard.guardText(truth, "Looks like a roost of 8 bats along the ridge.")
        assertTrue(prose.contains("8"))
        assertEquals(8, AnimalCountGuard.sourceOfTruth(truth, prose).point)
        val invented = AnimalCountGuard.guardText(truth, "Preferred answer is 10 bats.")
        assertFalse(invented.contains("10"))
        assertTrue(invented.contains("deterministic count 8"))
    }

    @Test
    fun lowScoreBoxesAreWithheldInsteadOfInventingZeroOrAVlmNumber() {
        val count = AnimalCountGuard.estimate(
            listOf(box(frame = 0, left = 0f, top = 0f, right = 10f, bottom = 10f, score = 0.10f)),
        )
        assertTrue(count.withheld)
        assertNull(count.point)
        val guarded = AnimalCountGuard.guardText(count, "count: 12")
        assertFalse(guarded.contains("12"))
        assertTrue(guarded.contains("withheld"))
    }

    private fun box(
        frame: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        label: String = "raccoon",
        score: Float = 0.90f,
    ) = DetectionBox(frame, left, top, right, bottom, label, score)
}
