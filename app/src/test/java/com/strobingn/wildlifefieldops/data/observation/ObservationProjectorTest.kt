package com.strobingn.wildlifefieldops.data.observation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the immutable ObservationEvent / DerivedAssessment projection system.
 *
 * All tests use JUnit4 (consistent with existing app test suite).
 * No Android or Room dependencies are needed — the projector is pure Kotlin/JVM.
 */
class ObservationProjectorTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Test-fixture helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun modelEvent(
        eventId: String,
        entityId: String = "site-001",
        observedAt: Long = 1_000L,
        uploadedAt: Long = 1_500L,
        labelDistribution: Map<String, Float> = mapOf("raccoon" to 0.9f, "opossum" to 0.1f),
        captureQuality: Float = 0.8f,
        modelId: String = "wildlife_evidence_v3",
        modelHash: String = "abc123",
    ) = ObservationEvent(
        eventId = eventId,
        entityId = entityId,
        observedAt = observedAt,
        uploadedAt = uploadedAt,
        deviceId = "dev-1",
        operatorId = "op-1",
        modelId = modelId,
        modelHash = modelHash,
        backendTag = "tflite",
        quantizerTag = "int8",
        frameHash = "fh-$eventId",
        cropHash = "ch-$eventId",
        labelDistribution = labelDistribution,
        captureQuality = captureQuality,
        geometryTrust = 0.9f,
        humanVerificationState = HumanVerificationState.UNREVIEWED,
    )

    private fun humanCorrectedEvent(
        eventId: String,
        entityId: String = "site-001",
        correctedLabel: String,
        supersedesEventId: String,
    ) = ObservationEvent(
        eventId = eventId,
        entityId = entityId,
        observedAt = 2_000L,
        uploadedAt = 2_100L,
        deviceId = "dev-1",
        operatorId = "op-1",
        modelId = "human",
        modelHash = "human",
        backendTag = "human",
        quantizerTag = "none",
        frameHash = "fh-human",
        cropHash = "ch-human",
        labelDistribution = mapOf(correctedLabel to 1.0f),
        captureQuality = 1.0f,
        geometryTrust = 0.0f,
        humanVerificationState = HumanVerificationState.CORRECTED,
        supersedesEventId = supersedesEventId,
    )

    private fun humanConfirmedEvent(
        eventId: String,
        entityId: String = "site-001",
        confirmedLabel: String,
    ) = ObservationEvent(
        eventId = eventId,
        entityId = entityId,
        observedAt = 2_000L,
        uploadedAt = 2_100L,
        deviceId = "dev-1",
        operatorId = "op-1",
        modelId = "human",
        modelHash = "human",
        backendTag = "human",
        quantizerTag = "none",
        frameHash = "fh-human-c",
        cropHash = "ch-human-c",
        labelDistribution = mapOf(confirmedLabel to 1.0f),
        captureQuality = 1.0f,
        geometryTrust = 0.0f,
        humanVerificationState = HumanVerificationState.CONFIRMED,
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Commutativity: same evidence, different delivery order → same result
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun sameEvidenceDifferentOrderProducesSameAssessment() {
        val e1 = modelEvent("evt-a", labelDistribution = mapOf("fox" to 0.7f, "coyote" to 0.3f), captureQuality = 0.9f)
        val e2 = modelEvent("evt-b", labelDistribution = mapOf("fox" to 0.6f, "coyote" to 0.4f), captureQuality = 0.7f)
        val e3 = modelEvent("evt-c", labelDistribution = mapOf("fox" to 0.8f, "coyote" to 0.2f), captureQuality = 0.85f)

        val permutations = listOf(
            listOf(e1, e2, e3),
            listOf(e3, e1, e2),
            listOf(e2, e3, e1),
            listOf(e3, e2, e1),
            listOf(e1, e3, e2),
            listOf(e2, e1, e3),
        )

        val nowMs = 99_000L
        val results = permutations.map { ObservationProjector.project("site-001", it, nowMs) }

        val first = results.first()
        for (assessment in results) {
            assertEquals(
                "primaryLabel must be identical regardless of event delivery order",
                first.primaryLabel,
                assessment.primaryLabel,
            )
            assertEquals(
                "uncertainty must be identical regardless of event delivery order",
                first.uncertainty,
                assessment.uncertainty,
            )
            assertEquals(
                "evidenceEventIds must be identical regardless of event delivery order",
                first.evidenceEventIds,
                assessment.evidenceEventIds,
            )
        }
    }

    @Test
    fun twoEventPermutationsConverge() {
        val e1 = modelEvent("alpha", labelDistribution = mapOf("bear" to 0.9f, "deer" to 0.1f), captureQuality = 0.5f)
        val e2 = modelEvent("beta",  labelDistribution = mapOf("bear" to 0.6f, "deer" to 0.4f), captureQuality = 0.8f)

        val fwd = ObservationProjector.project("site-001", listOf(e1, e2), 1L)
        val rev = ObservationProjector.project("site-001", listOf(e2, e1), 1L)

        assertEquals(fwd.primaryLabel, rev.primaryLabel)
        assertEquals(fwd.uncertainty,  rev.uncertainty)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Human-verified evidence cannot be overturned by lower-trust model events
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun humanCorrectionPreservedAgainstHighConfidenceModelEvent() {
        val modelEvt = modelEvent(
            "evt-model",
            labelDistribution = mapOf("raccoon" to 0.99f, "opossum" to 0.01f),
            captureQuality = 1.0f,
        )
        val correction = humanCorrectedEvent(
            eventId = "evt-human",
            correctedLabel = "opossum",
            supersedesEventId = "evt-model",
        )

        val assessment = ObservationProjector.project("site-001", listOf(modelEvt, correction))

        assertEquals("opossum", assessment.primaryLabel)
        assertEquals("opossum", assessment.humanVerifiedLabel)
        assertEquals(AssessmentStatus.HUMAN_OVERRIDE, assessment.status)
    }

    @Test
    fun humanConfirmationPersistsEvenAfterLateReorderingModelEvents() {
        val earlyModel = modelEvent("evt-1", observedAt = 1_000L,
            labelDistribution = mapOf("mink" to 0.8f, "weasel" to 0.2f))
        val confirmation = humanConfirmedEvent("evt-human", confirmedLabel = "mink")
        val lateModel = modelEvent("evt-late", observedAt = 500L,
            labelDistribution = mapOf("weasel" to 0.95f, "mink" to 0.05f),
            captureQuality = 0.95f)

        val assessment = ObservationProjector.project("site-001",
            listOf(confirmation, lateModel, earlyModel))

        assertEquals("mink", assessment.humanVerifiedLabel)
        assertEquals(AssessmentStatus.HUMAN_OVERRIDE, assessment.status)
    }

    @Test
    fun humanCorrectionToRareClassPreservedAgainstManyCommonClassVotes() {
        val commonVotes = (1..5).map { i ->
            modelEvent("evt-common-$i",
                labelDistribution = mapOf("deer" to 0.95f, "lynx" to 0.05f),
                captureQuality = 0.9f)
        }
        val rareCorrectionEvt = humanCorrectedEvent(
            eventId = "evt-rare",
            correctedLabel = "lynx",
            supersedesEventId = "evt-common-1",
        )

        val assessment = ObservationProjector.project(
            "site-001",
            commonVotes + rareCorrectionEvt,
        )

        assertEquals("Rare-class human correction must not be overturned by majority model vote",
            "lynx", assessment.primaryLabel)
        assertEquals(AssessmentStatus.HUMAN_OVERRIDE, assessment.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Idempotency: duplicate events are harmless
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun duplicateEventIsIgnoredIdempotently() {
        val evt = modelEvent("evt-x")

        val single    = ObservationProjector.project("site-001", listOf(evt),            1L)
        val duplicate = ObservationProjector.project("site-001", listOf(evt, evt),       1L)
        val triplicate = ObservationProjector.project("site-001", listOf(evt, evt, evt), 1L)

        assertEquals(single.primaryLabel, duplicate.primaryLabel)
        assertEquals(single.primaryLabel, triplicate.primaryLabel)
        assertEquals(single.uncertainty,  duplicate.uncertainty)
        assertEquals(single.uncertainty,  triplicate.uncertainty)
        assertEquals(
            "evidenceEventIds must not grow on duplicate upload",
            single.evidenceEventIds,
            duplicate.evidenceEventIds,
        )
        assertEquals(single.evidenceEventIds, triplicate.evidenceEventIds)
    }

    @Test
    fun reuploadOfHumanCorrectionIsIdempotent() {
        val model = modelEvent("evt-model")
        val correction = humanCorrectedEvent("evt-corr", correctedLabel = "bear", supersedesEventId = "evt-model")

        val once  = ObservationProjector.project("site-001", listOf(model, correction), 1L)
        val twice = ObservationProjector.project("site-001", listOf(model, correction, correction), 1L)

        assertEquals(once.primaryLabel,        twice.primaryLabel)
        assertEquals(once.humanVerifiedLabel,  twice.humanVerifiedLabel)
        assertEquals(once.evidenceEventIds,    twice.evidenceEventIds)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Last-write-wins diverges under reorder; conservative projector converges
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Demonstrates that a naive last-write-wins strategy produces DIFFERENT
     * outcomes depending on event order, while [ObservationProjector] is stable.
     *
     * LWW is the REJECTED strategy (see ADR 0002, section 7).
     */
    @Test
    fun lwwDivergesWhereConservativeProjectorConverges() {
        val early = modelEvent("evt-early", observedAt = 1_000L,
            labelDistribution = mapOf("otter" to 0.85f, "muskrat" to 0.15f),
            captureQuality = 0.9f)
        val late = modelEvent("evt-late", observedAt = 2_000L,
            labelDistribution = mapOf("muskrat" to 0.75f, "otter" to 0.25f),
            captureQuality = 0.4f)

        // Simulate last-write-wins by taking the event with the highest uploadedAt.
        // In order [early, late] the "last written" is late → muskrat
        // In order [late, early] it would be early → otter
        // We model this by simply picking the last element of the list.
        fun lwwProject(events: List<ObservationEvent>): String {
            val last = events.maxByOrNull { it.observedAt }!!
            return last.labelDistribution.maxByOrNull { it.value }!!.key
        }

        val lwwNet1 = listOf(early, late).last().labelDistribution.maxByOrNull { it.value }!!.key
        val lwwNet2 = listOf(late, early).last().labelDistribution.maxByOrNull { it.value }!!.key
        // lwwNet1 = muskrat, lwwNet2 = otter — different!
        assertTrue("LWW diverges when network order differs",
            lwwNet1 != lwwNet2)

        // Conservative projector is stable across all permutations.
        val conservative1 = ObservationProjector.project("site-001", listOf(early, late), 1L)
        val conservative2 = ObservationProjector.project("site-001", listOf(late, early), 1L)

        assertEquals("Conservative projector must converge regardless of input order",
            conservative1.primaryLabel, conservative2.primaryLabel)
        assertEquals(conservative1.uncertainty, conservative2.uncertainty)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Clock-skew and model-version-mix trigger NEEDS_REVIEW
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun clockSkewFlagsNeedsReview() {
        val skewedEvent = modelEvent(
            "evt-skew",
            observedAt = 1_000L,
            uploadedAt = 1_000L + (25L * 60 * 60 * 1_000), // 25-hour delta
        )

        val assessment = ObservationProjector.project("site-001", listOf(skewedEvent))

        assertEquals(AssessmentStatus.NEEDS_REVIEW, assessment.status)
    }

    @Test
    fun modelVersionMixFlagsNeedsReview() {
        val v1 = modelEvent("evt-v1", modelId = "wildlife_evidence_v1", modelHash = "hash1")
        val v2 = modelEvent("evt-v2", modelId = "wildlife_evidence_v2", modelHash = "hash2")

        val assessment = ObservationProjector.project("site-001", listOf(v1, v2))

        assertEquals(AssessmentStatus.NEEDS_REVIEW, assessment.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. DISPUTED events surface the correct status
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun disputedEventWithConflictingLabelSurfacesDisputedStatus() {
        val model = modelEvent("evt-model",
            labelDistribution = mapOf("coyote" to 0.9f, "fox" to 0.1f))
        val disputed = ObservationEvent(
            eventId = "evt-disputed",
            entityId = "site-001",
            observedAt = 1_500L,
            uploadedAt = 1_600L,
            deviceId = "dev-1",
            operatorId = "op-1",
            modelId = "wildlife_evidence_v3",
            modelHash = "abc123",
            backendTag = "tflite",
            quantizerTag = "int8",
            frameHash = "fh-disputed",
            cropHash = "ch-disputed",
            labelDistribution = mapOf("fox" to 0.8f, "coyote" to 0.2f),
            captureQuality = 0.7f,
            geometryTrust = 0.5f,
            humanVerificationState = HumanVerificationState.DISPUTED,
        )

        val assessment = ObservationProjector.project("site-001", listOf(model, disputed))

        assertEquals(AssessmentStatus.DISPUTED, assessment.status)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Zero-quality events do not pollute aggregation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun zeroQualityEventExcludedFromWeightedAggregation() {
        val good = modelEvent("evt-good",
            labelDistribution = mapOf("deer" to 0.9f, "elk" to 0.1f),
            captureQuality = 0.8f)
        val blurry = modelEvent("evt-blurry",
            labelDistribution = mapOf("elk" to 0.99f, "deer" to 0.01f),
            captureQuality = 0.0f)

        val assessment = ObservationProjector.project("site-001", listOf(good, blurry))

        // Zero-quality event must not flip the result to elk.
        assertEquals("deer", assessment.primaryLabel)
        // But the event is still recorded in evidence.
        assertTrue(assessment.evidenceEventIds.contains("evt-blurry"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Single-event baseline sanity checks
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun singleUnreviewedEventProducesStableAssessment() {
        val evt = modelEvent("evt-single",
            labelDistribution = mapOf("skunk" to 0.75f, "weasel" to 0.25f))

        val assessment = ObservationProjector.project("site-001", listOf(evt))

        assertEquals("skunk", assessment.primaryLabel)
        assertEquals(AssessmentStatus.STABLE, assessment.status)
        assertNull(assessment.humanVerifiedLabel)
        assertEquals(setOf("evt-single"), assessment.evidenceEventIds)
    }

    @Test
    fun projectionVersionIsExposed() {
        val evt = modelEvent("evt-v")
        val assessment = ObservationProjector.project("site-001", listOf(evt))
        assertTrue(assessment.projectionVersion > 0)
    }

    @Test
    fun lastProjectedAtIsRecorded() {
        val evt = modelEvent("evt-ts")
        val nowMs = 42_000L
        val assessment = ObservationProjector.project("site-001", listOf(evt), nowMs)
        assertEquals(nowMs, assessment.lastProjectedAt)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Out-of-order late events still converge
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun outOfOrderReconnectConverges() {
        val session1 = listOf(
            modelEvent("s1-a", observedAt = 100L, labelDistribution = mapOf("bear" to 0.8f, "wolf" to 0.2f), captureQuality = 0.9f),
            modelEvent("s1-b", observedAt = 200L, labelDistribution = mapOf("bear" to 0.85f, "wolf" to 0.15f), captureQuality = 0.85f),
        )
        val session2 = listOf(
            modelEvent("s2-a", observedAt = 50L, labelDistribution = mapOf("bear" to 0.7f, "wolf" to 0.3f), captureQuality = 0.75f),
            modelEvent("s2-b", observedAt = 150L, labelDistribution = mapOf("wolf" to 0.6f, "bear" to 0.4f), captureQuality = 0.3f),
        )

        val fullSet = session1 + session2
        // Simulate out-of-order arrival: session2 arrives before session1 in batch 2.
        val outOfOrder = session2 + session1

        val result1 = ObservationProjector.project("site-001", fullSet,   1L)
        val result2 = ObservationProjector.project("site-001", outOfOrder, 1L)

        assertEquals(result1.primaryLabel, result2.primaryLabel)
        assertEquals(result1.evidenceEventIds, result2.evidenceEventIds)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. Evidence from other entities is ignored
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun eventsForOtherEntityAreIgnored() {
        val mine  = modelEvent("evt-mine",  entityId = "site-001",
            labelDistribution = mapOf("fox" to 0.9f))
        val other = modelEvent("evt-other", entityId = "site-002",
            labelDistribution = mapOf("bear" to 0.99f))

        val assessment = ObservationProjector.project("site-001", listOf(mine, other))

        assertTrue(!assessment.evidenceEventIds.contains("evt-other"))
        assertEquals("fox", assessment.primaryLabel)
    }
}
