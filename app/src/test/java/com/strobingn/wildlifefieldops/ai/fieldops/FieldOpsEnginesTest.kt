package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldOpsEnginesTest {

    @Test
    fun operatorWinsKeepsTypedText() {
        assertEquals("Sir wrote this", OperatorWins.text("Sir wrote this", "AI guess", replace = false))
        assertEquals("AI guess", OperatorWins.text("", "AI guess", replace = false))
        assertEquals("AI guess", OperatorWins.text("Sir wrote this", "AI guess", replace = true))
    }

    @Test
    fun runtimePrefersCloudThenDeviceThenHeuristic() {
        assertEquals(AiRuntimeMode.CLOUD, AiRuntimeStatus.resolve(true, true).mode)
        assertEquals(AiRuntimeMode.ON_DEVICE, AiRuntimeStatus.resolve(false, true).mode)
        assertEquals(AiRuntimeMode.HEURISTIC, AiRuntimeStatus.resolve(false, false).mode)
        assertEquals(AiRuntimeMode.CLOUD, AiRuntimeStatus.parse("grok"))
        assertEquals("on_device", AiRuntimeStatus.wireName(AiRuntimeMode.ON_DEVICE))
    }

    @Test
    fun inspectionNarrativeUsesPhotoTagsAndDoesNotInventUnknownSpecies() {
        val draft = InspectionNarrativeEngine.draft(
            InspectionEvidence(
                jobAddress = "12 Oak St, Cornwall, NY",
                jobType = "Inspection",
                jobNotes = "Customer heard scratching in soffit.",
                photoTags = listOf("squirrel", "soffit", "chew"),
                photoNotes = listOf("gap at fascia")
            )
        )
        assertTrue(draft.findings.contains("Cornwall") || draft.findings.contains("12 Oak"))
        assertTrue(draft.speciesIdentified.contains("squirrel"))
        assertTrue(draft.entryPoints.contains("soffit") || draft.entryPoints.contains("fascia"))
        assertTrue(draft.recommendations.contains("one-way") || draft.recommendations.contains("vent"))
        assertEquals(AiRuntimeMode.HEURISTIC, draft.source)
    }


    @Test
    fun dictationFillsFindingsNotNotes() {
        val draft = InspectionNarrativeEngine.fromDictation(
            transcript = "Raccoon in the attic, soffit gap on the north side, insulation torn up and droppings",
            jobAddress = "4 River Rd, Newburgh, NY"
        )
        assertTrue(draft.findings.contains("soffit gap"))
        assertTrue(draft.speciesIdentified.contains("raccoon"))
        assertTrue(draft.entryPoints.contains("soffit"))
        assertTrue(draft.damageAssessment.contains("insulation") || draft.damageAssessment.contains("droppings"))
        assertEquals("", draft.notes)
    }

    @Test
    fun inspectionApplyDoesNotOverwriteTypedFindings() {
        val current = InspectionNarrativeDraft(findings = "I already typed this")
        val suggested = InspectionNarrativeDraft(findings = "AI paragraph")
        val merged = InspectionNarrativeEngine.apply(current, suggested, replace = false)
        assertEquals("I already typed this", merged.findings)
        assertEquals("AI paragraph", InspectionNarrativeEngine.apply(current, suggested, replace = true).findings)
    }

    @Test
    fun estimateLinesForRaccoonIncludeTrapAndFlashing() {
        val lines = EstimateLineSuggester.suggest(
            EstimateLineContext(species = "raccoon", notes = "chimney and soffit chew")
        )
        assertTrue(lines.size >= 3)
        assertTrue(lines.any { it.description.contains("trap", ignoreCase = true) })
        assertTrue(lines.any { it.unit == "lf" && it.quantity > 0 })
        assertTrue(lines.all { it.unitPrice > 0 })
    }

    @Test
    fun failedOrEmptyAiNeverWipesTypedLines() {
        val typed = listOf(InvoiceLineItem(description = "Hand-entered flashing", quantity = 3.0, unitPrice = 22.0))
        val emptyAi = EstimateLineSuggester.merge(typed, emptyList(), replace = false)
        assertEquals(1, emptyAi.size)
        assertEquals("Hand-entered flashing", emptyAi[0].description)
        assertEquals("Sir wrote this", OperatorWins.text("Sir wrote this", "", replace = false))
        assertEquals("Sir wrote this", OperatorWins.text("Sir wrote this", "", replace = true))
    }

    @Test
    fun estimateMergeKeepsOperatorLines() {
        val existing = listOf(InvoiceLineItem(description = "My custom flashing", quantity = 2.0, unitPrice = 40.0))
        val suggested = EstimateLineSuggester.suggest(EstimateLineContext(species = "bat"))
        val merged = EstimateLineSuggester.merge(existing, suggested, replace = false)
        assertTrue(merged.any { it.description == "My custom flashing" })
        assertTrue(merged.size > 1)
    }

    @Test
    fun nextStepTrapCheckWithinADay() {
        val now = 1_700_000_000_000L
        val draft = JobNextStepEngine.suggest(
            NextStepInput(status = "IN_PROGRESS", species = "raccoon", notes = "trap set at deck", now = now)
        )
        assertTrue(draft.text.contains("trap", ignoreCase = true))
        assertEquals(now + 86_400_000L, draft.dueAt)
    }

    @Test
    fun nextStepPaidClosesFile() {
        val draft = JobNextStepEngine.suggest(NextStepInput(paid = true, status = "PAID"))
        assertTrue(draft.text.contains("paid", ignoreCase = true))
        assertEquals(null, draft.dueAt)
    }

    @Test
    fun speciesLegalFlagsRabiesVector() {
        val card = SpeciesJobLegal.card("raccoon", savedNotes = "")
        assertEquals("HIGH", card.risk)
        assertTrue(card.decNotes.any { it.contains("Rabies", ignoreCase = true) })
        assertTrue(card.displayNotes.contains("DEC") || card.catalogNotes.any { it.contains("DEC") })
        val edited = SpeciesJobLegal.card("raccoon", savedNotes = "Sir's rewrite")
        assertEquals("Sir's rewrite", edited.displayNotes)
    }

    @Test
    fun speciesLegalFlagsProtectedBats() {
        val card = SpeciesJobLegal.card("little brown bat")
        assertTrue(card.decNotes.any { it.contains("Protected") || it.contains("maternity") })
        assertTrue(card.catalogNotes.isNotEmpty())
        assertEquals("", card.displayNotes)
    }
}
