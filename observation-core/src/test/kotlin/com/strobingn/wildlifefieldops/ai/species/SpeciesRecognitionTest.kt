package com.strobingn.wildlifefieldops.ai.species

import com.strobingn.wildlifefieldops.data.observation.HumanVerificationState
import com.strobingn.wildlifefieldops.data.observation.ObservationEventFactory
import com.strobingn.wildlifefieldops.data.observation.ObservationProjector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeciesRecognitionTest {

    @Test
    fun suggestionIncludesConfidenceAndSafetyNotes() {
        val suggestion = SpeciesRecognition.suggest(
            hits = mapOf("raccoon" to 0.82f, "opossum" to 0.11f),
            backendTag = "tflite",
        )
        assertNotNull(suggestion)
        assertEquals("raccoon", suggestion!!.primaryLabel)
        assertEquals(82, suggestion.confidencePercent)
        assertEquals("tflite", suggestion.backendTag)
        assertTrue(suggestion.safety.notes.any { it.contains("Rabies", ignoreCase = true) })
        assertTrue(suggestion.safety.notes.any { it.contains("not treat this ID as operational", ignoreCase = true) })
        assertEquals(SpeciesSafetyNotes.SafetyCard.Risk.HIGH, suggestion.safety.handlingRisk)
    }

    @Test
    fun lowConfidenceHitsAreNotSuggested() {
        assertNull(
            SpeciesRecognition.suggest(mapOf("raccoon" to 0.05f))
        )
    }

    @Test
    fun unreviewedInferenceIsNotOperational() {
        val suggestion = SpeciesRecognition.suggest(mapOf("bat" to 0.77f))!!
        val inference = SpeciesRecognition.recordInference(
            entityId = "obs-1",
            suggestion = suggestion,
            observedAt = 1_000L,
            deviceId = "dev",
            operatorId = "tech",
            frameHash = "frame-a",
            modelHash = "model-a",
            captureQuality = 0.8f,
            geometryTrust = 0.4f,
        )
        assertEquals(HumanVerificationState.UNREVIEWED, inference.humanVerificationState)
        assertFalse(SpeciesRecognition.isOperational(inference))
        assertNull(SpeciesRecognition.operationalLabel("obs-1", listOf(inference), nowMs = 1_000L))
        val assessment = ObservationProjector.project("obs-1", listOf(inference), nowMs = 1_000L)
        assertEquals("bat", assessment.primaryLabel)
        assertNull(assessment.humanVerifiedLabel)
        assertNull(SpeciesRecognition.operationalLabel(assessment))
    }

    @Test
    fun confirmMatchingSuggestionWritesConfirmedOperationalId() {
        val suggestion = SpeciesRecognition.suggest(mapOf("skunk" to 0.64f, "raccoon" to 0.2f))!!
        val inference = SpeciesRecognition.recordInference(
            entityId = "obs-2",
            suggestion = suggestion,
            observedAt = 2_000L,
            deviceId = "dev",
            operatorId = "tech",
            frameHash = "frame-b",
            modelHash = "model-b",
            captureQuality = 0.7f,
            geometryTrust = 0.5f,
        )
        val commit = SpeciesRecognition.confirm(
            inference = inference,
            technicianLabel = "Skunk",
            confirmedAt = 2_500L,
        )
        assertEquals(HumanVerificationState.CONFIRMED, commit.verification)
        assertEquals("skunk", commit.operationalLabel.lowercase())
        assertEquals(inference.eventId, commit.verificationEvent.supersedesEventId)
        assertTrue(SpeciesRecognition.isOperational(commit.verificationEvent))
        assertEquals(
            "skunk",
            SpeciesRecognition.operationalLabel(
                "obs-2",
                listOf(inference, commit.verificationEvent),
                nowMs = 2_500L,
            )?.lowercase()
        )
    }

    @Test
    fun technicianOverrideWritesCorrectedOperationalId() {
        val suggestion = SpeciesRecognition.suggest(mapOf("squirrel" to 0.91f))!!
        val inference = SpeciesRecognition.recordInference(
            entityId = "obs-3",
            suggestion = suggestion,
            observedAt = 3_000L,
            deviceId = "dev",
            operatorId = "tech",
            frameHash = "frame-c",
            modelHash = "model-c",
            captureQuality = 0.9f,
            geometryTrust = 0.2f,
        )
        val commit = SpeciesRecognition.confirm(
            inference = inference,
            technicianLabel = "flying squirrel",
            confirmedAt = 3_100L,
        )
        assertEquals(HumanVerificationState.CORRECTED, commit.verification)
        assertEquals("flying squirrel", commit.operationalLabel)
        assertEquals(
            "flying squirrel",
            SpeciesRecognition.operationalLabel(
                "obs-3",
                listOf(inference, commit.verificationEvent),
                nowMs = 3_100L,
            )
        )
    }

    @Test
    fun laterHighConfidenceModelEventDoesNotReplaceConfirmedId() {
        val suggestion = SpeciesRecognition.suggest(mapOf("raccoon" to 0.55f))!!
        val inference = SpeciesRecognition.recordInference(
            entityId = "site-9",
            suggestion = suggestion,
            observedAt = 4_000L,
            deviceId = "dev",
            operatorId = "tech",
            frameHash = "frame-d",
            modelHash = "model-d",
            captureQuality = 0.6f,
            geometryTrust = 0.3f,
        )
        val commit = SpeciesRecognition.confirm(inference, "raccoon", confirmedAt = 4_100L)
        val laterModel = ObservationEventFactory.inference(
            entityId = "site-9",
            observedAt = 5_000L,
            deviceId = "dev",
            operatorId = "tech",
            modelHash = "model-e",
            backendTag = "tflite",
            frameHash = "frame-e",
            labelDistribution = mapOf("opossum" to 0.99f),
            captureQuality = 1f,
            geometryTrust = 0.9f,
        )
        assertEquals(
            "raccoon",
            SpeciesRecognition.operationalLabel(
                "site-9",
                listOf(laterModel, commit.verificationEvent, inference),
                nowMs = 5_000L,
            )
        )
    }

    @Test
    fun eventIdsAreDeterministicAndInferenceDiffersFromConfirm() {
        val suggestion = SpeciesRecognition.suggest(mapOf("groundhog" to 0.7f))!!
        val a = SpeciesRecognition.recordInference(
            entityId = "e",
            suggestion = suggestion,
            observedAt = 10L,
            deviceId = "d",
            operatorId = "o",
            frameHash = "f",
            modelHash = "m",
            captureQuality = 0.5f,
            geometryTrust = 0f,
        )
        val b = SpeciesRecognition.recordInference(
            entityId = "e",
            suggestion = suggestion,
            observedAt = 10L,
            deviceId = "d",
            operatorId = "o",
            frameHash = "f",
            modelHash = "m",
            captureQuality = 0.5f,
            geometryTrust = 0f,
        )
        assertEquals(a.eventId, b.eventId)
        val confirmed = SpeciesRecognition.confirm(a, "groundhog", confirmedAt = 11L)
        assertNotEquals(a.eventId, confirmed.verificationEvent.eventId)
    }

    @Test
    fun birdSafetyNotesFlagProtectedNests() {
        val card = SpeciesSafetyNotes.forLabel("pigeon")
        assertEquals(SpeciesSafetyNotes.SafetyCard.Risk.PROTECTED, card.handlingRisk)
        assertTrue(card.notes.any { it.contains("nest", ignoreCase = true) })
    }
}
