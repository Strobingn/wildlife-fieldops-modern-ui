package com.strobingn.wildlifefieldops.data.observation

import com.strobingn.wildlifefieldops.ai.species.SpeciesRecognition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationEventMapperTest {

    @Test
    fun roundTripPreservesInferenceAndConfirm() {
        val suggestion = SpeciesRecognition.suggest(mapOf("raccoon" to 0.8f, "skunk" to 0.15f))!!
        val inference = SpeciesRecognition.recordInference(
            entityId = "obs-map",
            suggestion = suggestion,
            observedAt = 9_000L,
            deviceId = "dev",
            operatorId = "tech",
            frameHash = "frame-map",
            modelHash = "hash-map",
            mediaUri = "file:///tmp/obs.jpg",
            captureQuality = 0.7f,
            geometryTrust = 0.4f,
        )
        val commit = SpeciesRecognition.confirm(inference, "raccoon", confirmedAt = 9_100L)

        val inferenceBack = ObservationEventMapper.toDomain(ObservationEventMapper.toRecord(inference))
        val confirmBack = ObservationEventMapper.toDomain(ObservationEventMapper.toRecord(commit.verificationEvent))

        assertEquals(inference, inferenceBack)
        assertEquals(commit.verificationEvent, confirmBack)
        assertEquals(HumanVerificationState.UNREVIEWED, inferenceBack.humanVerificationState)
        assertEquals(HumanVerificationState.CONFIRMED, confirmBack.humanVerificationState)
        assertEquals(inference.eventId, confirmBack.supersedesEventId)
    }

    @Test
    fun emptyJsonDistributionDecodesEmpty() {
        assertTrue(ObservationEventMapper.decodeDistribution("").isEmpty())
        assertTrue(ObservationEventMapper.decodeDistribution("not-json").isEmpty())
    }

    @Test
    fun operationalHintStaysEmptyUntilHumanEvent() {
        val suggestion = SpeciesRecognition.suggest(mapOf("bat" to 0.6f))!!
        val inference = SpeciesRecognition.recordInference(
            entityId = "obs-hint",
            suggestion = suggestion,
            observedAt = 1L,
            deviceId = "d",
            operatorId = "o",
            frameHash = "f",
            modelHash = "m",
            captureQuality = 0.5f,
            geometryTrust = 0f,
        )
        assertNull(SpeciesRecognition.operationalLabel("obs-hint", listOf(inference), nowMs = 1L))
        val commit = SpeciesRecognition.confirm(inference, "bat", confirmedAt = 2L)
        assertEquals(
            "bat",
            SpeciesRecognition.operationalLabel(
                "obs-hint",
                listOf(inference, commit.verificationEvent),
                nowMs = 2L,
            )
        )
    }
}
