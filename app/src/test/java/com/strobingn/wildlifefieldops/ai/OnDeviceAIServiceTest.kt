package com.strobingn.wildlifefieldops.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceAIServiceTest {
    @Test
    fun blankFieldNotesAndNoPhotosProduceNoneSeverity() {
        val draft = OnDeviceAIService.draftInspectionReport(
            fieldNotes = "",
            photoAnalyses = emptyList(),
            inspectionType = "ROUTINE"
        )

        assertEquals("NONE", draft.severity)
        assertEquals("offline_ai", draft.source)
        assertTrue(draft.findings.contains("no dictated field notes"))
    }

    @Test
    fun dictatedNotesWithSpeciesAndDamageRaiseSeverity() {
        val draft = OnDeviceAIService.draftInspectionReport(
            fieldNotes = "Found bat guano near the soffit vent and chew marks on the fascia.",
            photoAnalyses = listOf(
                AiAnalysisResult(species = listOf("bat"), damageTypes = listOf("entry point", "droppings"))
            ),
            inspectionType = "INITIAL"
        )

        assertEquals("HIGH", draft.severity)
        assertTrue(draft.speciesIdentified.contains("bat"))
        assertTrue(draft.damageAssessment.isNotBlank())
        assertTrue(draft.entryPoints.contains("soffit"))
        assertTrue(draft.recommendations.contains("maternity", ignoreCase = true))
    }

    @Test
    fun photoOnlyEvidenceStillProducesAModerateDraft() {
        val draft = OnDeviceAIService.draftInspectionReport(
            fieldNotes = "",
            photoAnalyses = listOf(AiAnalysisResult(species = listOf("raccoon"))),
            inspectionType = "ROUTINE"
        )

        assertEquals("MODERATE", draft.severity)
        assertEquals("raccoon", draft.speciesIdentified)
    }
}
