package com.strobingn.wildlifefieldops.ai.operations

import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AIOperationsEngineTest {
    @Test
    fun dashboardAlwaysExposesAllEightyFiveModules() {
        val dashboard = AIOperationsEngine.analyze(emptyList())

        assertEquals(85, dashboard.advancedInsights.size)
        assertEquals(85, dashboard.advancedInsights.map { it.name }.distinct().size)
        assertTrue(dashboard.advancedInsights.all { it.score in 0..100 })
    }

    @Test
    fun catalogExposesFortyUniqueLaunchableTools() {
        assertEquals(40, IndividualAIToolCatalog.tools.size)
        assertEquals(40, IndividualAIToolCatalog.tools.map { it.id }.distinct().size)
        assertEquals(40, IndividualAIToolCatalog.tools.map { it.title }.distinct().size)
        val insightNames = AIOperationsEngine.analyze(emptyList()).advancedInsights.map { it.name }.toSet()
        assertTrue(IndividualAIToolCatalog.tools.all { it.insightName in insightNames })
        assertTrue(IndividualAIToolCatalog.tools.all { it.checklist.isNotEmpty() })
    }

    @Test
    fun newModulesUseRecordedJobSignals() {
        val now = System.currentTimeMillis()
        val job = Job(
            title = "Bat entry at roof vent",
            description = "Guano in attic; ladder and respirator needed",
            customerName = "Hudson Valley Customer",
            address = "1 Test Road",
            type = "Bat exclusion",
            estimatedValue = 1_200.0,
            actualCost = 400.0,
            status = JobStatus.INVOICED,
            scheduledDate = now + 86_400_000L,
            notes = "Call back for follow-up; locked gate and warranty details"
        )

        val insights = AIOperationsEngine.analyze(listOf(job)).advancedInsights.associateBy { it.name }

        assertTrue(insights.getValue("Tomorrow readiness check").signal.startsWith("1 open appointment"))
        assertTrue(insights.getValue("Zoonotic exposure warning").signal.startsWith("1 job"))
        assertTrue(insights.getValue("Equipment cue extractor").signal.startsWith("1 job"))
        assertTrue(insights.getValue("Property access blocker").signal.startsWith("1 job"))
        assertTrue(insights.getValue("Blank-title data hygiene").signal.startsWith("0 jobs"))
        assertTrue(insights.getValue("Multi-species complexity flag").signal.startsWith("0 jobs"))
        assertTrue(insights.getValue("Commercial-site complexity flag").signal.startsWith("0 jobs"))
    }

    @Test
    fun newSchedulingAndHygieneModulesUseRecordedJobSignals() {
        val now = System.currentTimeMillis()
        val staleLead = Job(
            title = "",
            customerName = "SHOUTING CUSTOMER",
            customerId = "cust-1",
            address = "200 Main Street, Suite 400",
            status = JobStatus.PENDING,
            createdAt = now - 3 * 86_400_000L,
            latitude = 0.0,
            longitude = 0.0
        )
        val duplicateOpenJob = Job(
            customerId = "cust-1",
            status = JobStatus.IN_PROGRESS,
            estimatedValue = 300.0
        )

        val insights = AIOperationsEngine.analyze(listOf(staleLead, duplicateOpenJob)).advancedInsights.associateBy { it.name }

        assertTrue(insights.getValue("New lead aging alert").signal.startsWith("1 of"))
        assertTrue(insights.getValue("Blank-title data hygiene").signal.startsWith("1 job"))
        assertTrue(insights.getValue("Coordinate integrity checker").signal.startsWith("1 job"))
        assertTrue(insights.getValue("Customer name formatting auditor").signal.startsWith("1 customer name"))
        assertTrue(insights.getValue("Commercial-site complexity flag").signal.startsWith("1 job"))
        assertTrue(insights.getValue("Concurrent-job customer alert").signal.startsWith("1 customer"))
    }
}
