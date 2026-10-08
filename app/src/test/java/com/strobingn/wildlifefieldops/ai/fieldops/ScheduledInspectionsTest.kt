package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.remote.toLocal
import com.strobingn.wildlifefieldops.data.remote.toRemoteDto
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.ui.screens.OpenHomeJobs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ScheduledInspectionsTest {

    private val now = 1_000_000L

    @Test
    fun listHoldsOnlyInspectionsUpcomingFirstThenPastMostRecentFirst() {
        val jobs = listOf(
            Job(id = "job", status = JobStatus.SCHEDULED, scheduledDate = now + 10),
            Job(id = "later", status = JobStatus.INSPECTION, scheduledDate = now + 500),
            Job(id = "soon", status = JobStatus.INSPECTION, scheduledDate = now + 100),
            Job(id = "undated", status = JobStatus.INSPECTION, scheduledDate = null),
            Job(id = "old", status = JobStatus.INSPECTION, scheduledDate = now - 900),
            Job(id = "yesterday", status = JobStatus.INSPECTION, scheduledDate = now - 100)
        )
        assertEquals(
            listOf("soon", "later", "undated", "yesterday", "old"),
            ScheduledInspections.list(jobs, now).map { it.id }
        )
    }

    @Test
    fun approvingMakesTheSameRowAScheduledJobAndDecliningCancelsIt() {
        val inspection = Job(id = "i", status = JobStatus.INSPECTION)
        assertTrue(ScheduledInspections.canApprove(inspection))
        assertFalse(ScheduledInspections.canApprove(inspection.copy(status = JobStatus.SCHEDULED)))
        assertEquals(JobStatus.SCHEDULED, ScheduledInspections.APPROVED_STATUS)
        assertEquals(JobStatus.CANCELLED, ScheduledInspections.DECLINED_STATUS)
        assertTrue(JobStatus.INSPECTION.isInspectionOnly())
        assertFalse(JobStatus.SCHEDULED.isInspectionOnly())
    }

    @Test
    fun inspectionsStayOutOfJobListsUntilTheInspectionChipIsPicked() {
        assertFalse(OpenHomeJobs.matchesJobList(JobStatus.INSPECTION, null, openOnly = false))
        assertFalse(OpenHomeJobs.matchesJobList(JobStatus.INSPECTION, null, openOnly = true))
        assertFalse(OpenHomeJobs.matchesJobList(JobStatus.INSPECTION, JobStatus.SCHEDULED, openOnly = false))
        assertTrue(OpenHomeJobs.matchesJobList(JobStatus.INSPECTION, JobStatus.INSPECTION, openOnly = false))
        assertFalse(OpenHomeJobs.matchesJobList(JobStatus.SCHEDULED, JobStatus.INSPECTION, openOnly = false))
        assertTrue(OpenHomeJobs.matchesJobList(JobStatus.SCHEDULED, null, openOnly = false))

        val home = OpenHomeJobs.list(
            listOf(
                Job(id = "insp", status = JobStatus.INSPECTION, scheduledDate = 1L),
                Job(id = "job", status = JobStatus.SCHEDULED, scheduledDate = 2L)
            )
        )
        assertEquals(listOf("job"), home.map { it.id })
    }

    @Test
    fun statusPipelineNeverSuggestsPastAnInspection() {
        val inspection = Job(status = JobStatus.INSPECTION, scheduledDate = 50L, pricing = JobPricing(laborHours = 2.0))
        assertNull(JobStatusPipeline.suggest(inspection))
        assertEquals(0, JobStatusPipeline.indexOf(JobStatus.INSPECTION))
    }

    @Test
    fun inspectionSurvivesACloudRoundTrip() {
        val stamped = Job(status = JobStatus.INSPECTION, pricing = JobStatusPipeline.stamp(JobPricing(), JobStatus.INSPECTION))
        val remote = stamped.toRemoteDto()
        assertEquals("Scheduled", remote.status)
        assertEquals(JobStatus.INSPECTION, remote.toLocal(existing = Job(status = JobStatus.LEAD)).status)
        assertEquals(JobStatus.INSPECTION, remote.toLocal(existing = null).status)

        // Older row without the stamp: a "Scheduled" pull keeps the local inspection flag.
        val unstamped = Job(status = JobStatus.INSPECTION).toRemoteDto()
        assertEquals(JobStatus.INSPECTION, unstamped.toLocal(existing = Job(status = JobStatus.INSPECTION)).status)
    }

    @Test
    fun appointmentTextIsReadableAndBlankWithoutATime() {
        val utc = TimeZone.getTimeZone("UTC")
        // 2026-10-14T13:30:00Z is a Wednesday.
        assertEquals("Wed Oct 14 at 1:30 PM", ScheduledInspections.appointmentText(1_791_984_600_000L, utc))
        assertEquals("", ScheduledInspections.appointmentText(null, utc))
        assertEquals("", ScheduledInspections.appointmentText(0L, utc))
    }

    @Test
    fun inspectionReminderTextNamesTheAppointmentAndSaysNoWorkYet() {
        val msg = CustomerMessageDraft.draft(
            CustomerMessageKind.INSPECTION_REMINDER,
            customerName = "Pat",
            jobTitle = "Attic noise",
            address = "12 Oak St",
            appointment = "Wed Oct 14 at 1:30 PM"
        )
        assertTrue(msg.body.contains("Hi Pat"))
        assertTrue(msg.body.contains("inspection at 12 Oak St on Wed Oct 14 at 1:30 PM"))
        assertTrue(msg.body.contains("before any work is done"))
        assertTrue(msg.subject.startsWith("Inspection reminder"))

        val undated = CustomerMessageDraft.draft(CustomerMessageKind.INSPECTION_REMINDER, "Pat", "Attic", "12 Oak St")
        assertTrue(undated.body.contains("inspection at 12 Oak St."))
    }
}
