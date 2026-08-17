package com.strobingn.wildlifefieldops.ai.operations

import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AIOperationsEngineTest {
    @Test
    fun dashboardAlwaysExposesAllSixtyFiveModules() {
        val dashboard = AIOperationsEngine.analyze(emptyList())

        assertEquals(65, dashboard.advancedInsights.size)
        assertEquals(65, dashboard.advancedInsights.map { it.name }.distinct().size)
        assertTrue(dashboard.advancedInsights.all { it.score in 0..100 })
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
    }
}
