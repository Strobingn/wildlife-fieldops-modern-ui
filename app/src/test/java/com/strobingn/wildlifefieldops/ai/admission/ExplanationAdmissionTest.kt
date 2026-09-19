package com.strobingn.wildlifefieldops.ai.admission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplanationAdmissionTest {

    @Test
    fun ontologyStubHasThreeConfusionSetsWithTraits() {
        assertEquals(3, TraitOntologyStub.confusionSets.size)
        assertTrue(TraitOntologyStub.confusionSets.all { it.traits.size >= 3 })
        val ids = TraitOntologyStub.confusionSets.flatMap { set -> set.traits.map { it.id } }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun faithfulTraitAdmitsAsExplanation() {
        val trials = SyntheticInterventionFactory.faithfulTrait("guano_texture")
        val decision = ExplanationAdmissionGate.evaluate(trials)

        assertEquals(AdmissionLabel.EXPLANATION, decision.label)
        assertEquals(UiClaimKind.CAUSAL_EXPLANATION, decision.allowedUiClaim)
        assertTrue(decision.traitResults.single().passed)
        assertTrue(decision.traitResults.single().agreeingKinds.size >= 2)
    }

    @Test
    fun inertClaimedTraitIsRejectedOrOverlayOnly() {
        val trials = SyntheticInterventionFactory.inertTrait("attention_blob")
        val decision = ExplanationAdmissionGate.evaluate(trials)

        assertTrue(
            decision.label == AdmissionLabel.REJECT ||
                decision.label == AdmissionLabel.REVIEW_OVERLAY,
        )
        assertFalse(decision.traitResults.single().passed)
        assertEquals(UiClaimKind.EVIDENCE_VISUALIZATION, decision.allowedUiClaim)
    }

    @Test
    fun mixedTraitsDowngradeToReviewOverlay() {
        val trials =
            SyntheticInterventionFactory.faithfulTrait("guano_texture") +
                SyntheticInterventionFactory.inertTrait("feather_debris")

        val decision = ExplanationAdmissionGate.evaluate(trials)

        assertEquals(AdmissionLabel.REVIEW_OVERLAY, decision.label)
        assertEquals(2, decision.traitResults.size)
        assertTrue(decision.traitResults.any { it.passed })
        assertTrue(decision.traitResults.any { !it.passed })
    }

    @Test
    fun claimMustWithdrawWhenEvidenceRemoved() {
        val baseline = ModelOutput(
            targetLogit = 2.0,
            topLabel = "target",
            abstained = false,
            claimedTraits = setOf("claw_marks"),
        )
        // Strong logit effect but claim text stays — fails withdrawal rule.
        val trials = InterventionKind.entries.flatMap { kind ->
            listOf(
                InterventionTrial(
                    traitId = "claw_marks",
                    kind = kind,
                    isControl = false,
                    baseline = baseline,
                    after = baseline.copy(targetLogit = 1.0),
                ),
                InterventionTrial(
                    traitId = "claw_marks",
                    kind = kind,
                    isControl = true,
                    baseline = baseline,
                    after = baseline.copy(targetLogit = 1.95),
                ),
            )
        }

        val decision = ExplanationAdmissionGate.evaluate(trials)
        assertFalse(decision.traitResults.single().passed)
        assertTrue(
            decision.traitResults.single().notes.any { it.contains("Claim withdrawal") },
        )
    }
}
