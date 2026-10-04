package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Jobs show exactly three flags. Stored enum values stay; filters match the bucket.
 */
class JobStatusFlagTest {

    @Test
    fun pickerAndFiltersAreExactlyTheThreeFlags() {
        assertEquals(
            listOf("Scheduled", "In progress", "Completed"),
            JobStatusPipeline.stages.map { JobStatusPipeline.label(it) }
        )
        assertEquals(JobStatusPipeline.flagLabels, JobStatusPipeline.stages.map { JobStatusPipeline.label(it) })
        assertEquals(3, JobStatusPipeline.stages.size)
    }

    @Test
    fun everyStoredStatusMapsToOneFlag() {
        val expected = mapOf(
            JobStatus.PENDING to "Scheduled",
            JobStatus.LEAD to "Scheduled",
            JobStatus.ESTIMATE_SENT to "Scheduled",
            JobStatus.SCHEDULED to "Scheduled",
            JobStatus.IN_PROGRESS to "In progress",
            JobStatus.TRAPPING to "In progress",
            JobStatus.EXCLUSION to "In progress",
            JobStatus.COMPLETED to "Completed",
            JobStatus.INVOICED to "Completed",
            JobStatus.PAID to "Completed",
            JobStatus.CANCELLED to "Completed",
            JobStatus.CLOSED to "Completed"
        )
        assertEquals(JobStatus.entries.toSet(), expected.keys)
        expected.forEach { (status, label) ->
            assertEquals(status.name, label, JobStatusPipeline.label(status))
            assertTrue(status.name, JobStatusPipeline.flagLabels.contains(JobStatusPipeline.label(status)))
        }
        assertTrue(JobStatusPipeline.matches(JobStatus.PENDING, JobStatus.SCHEDULED))
        assertTrue(JobStatusPipeline.matches(JobStatus.LEAD, JobStatus.ESTIMATE_SENT))
        assertTrue(JobStatusPipeline.matches(JobStatus.TRAPPING, JobStatus.IN_PROGRESS))
        assertTrue(JobStatusPipeline.matches(JobStatus.EXCLUSION, JobStatus.IN_PROGRESS))
        assertTrue(JobStatusPipeline.matches(JobStatus.PAID, JobStatus.COMPLETED))
        assertTrue(JobStatusPipeline.matches(JobStatus.INVOICED, JobStatus.COMPLETED))
        assertTrue(JobStatusPipeline.matches(JobStatus.CANCELLED, JobStatus.CLOSED))
        assertFalse(JobStatusPipeline.matches(JobStatus.SCHEDULED, JobStatus.IN_PROGRESS))
        assertFalse(JobStatusPipeline.matches(JobStatus.IN_PROGRESS, JobStatus.COMPLETED))
        assertEquals(JobStatus.SCHEDULED, JobStatusPipeline.flag(JobStatus.PENDING))
        assertEquals(JobStatus.IN_PROGRESS, JobStatusPipeline.flag(JobStatus.TRAPPING))
        assertEquals(JobStatus.COMPLETED, JobStatusPipeline.flag(JobStatus.CANCELLED))
    }

    @Test
    fun homeHasNoOverdueAndFormOffersOnlyTheThreeFlags() {
        val home = readAppSource("ui/screens/DashboardScreen.kt")
        val vm = readAppSource("ui/viewmodel/DashboardViewModel.kt")
        val form = readAppSource("ui/screens/JobFormScreen.kt")
        val jobs = readAppSource("ui/screens/JobListScreen.kt")
        val pipeline = readAppSource("ui/screens/JobBatch6Section.kt")
        val map = readAppSource("ui/screens/MapScreen.kt")
        assertFalse(home.contains("overdue", ignoreCase = true))
        assertFalse(vm.contains("overdueJobs"))
        val inspections = readAppSource("ui/screens/InspectionListScreen.kt")
        assertTrue(inspections.contains("Follow-ups"))
        assertFalse(home.contains("Follow-ups"))
        assertTrue(form.contains("JobStatusPipeline.stages"))
        assertTrue(form.contains("JobStatusPipeline.label(status)"))
        assertTrue(form.contains("statusTouched = true"))
        assertTrue(jobs.contains("JobStatusPipeline.stages"))
        assertTrue(jobs.contains("JobStatusPipeline.label"))
        assertTrue(pipeline.contains("JobStatusPipeline.stages"))
        assertTrue(pipeline.contains("JobStatusPipeline.flag(job.status)"))
        assertTrue(map.contains("JobStatusPipeline.stages"))
        assertTrue(map.contains("JobStatusPipeline.matches"))
        assertFalse(map.contains("JobStatus.entries.forEach"))
    }

    private fun readAppSource(relativeUnderJava: String): String {
        val suffix = "src/main/java/com/strobingn/wildlifefieldops/$relativeUnderJava"
        val file = listOf(File(suffix), File("app/$suffix"), File("../$suffix")).firstOrNull { it.isFile }
            ?: error("Missing $suffix (cwd=${File(".").canonicalPath})")
        return file.readText()
    }
}
