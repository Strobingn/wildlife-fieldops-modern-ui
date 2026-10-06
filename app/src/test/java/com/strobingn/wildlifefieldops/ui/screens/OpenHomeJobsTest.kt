package com.strobingn.wildlifefieldops.ui.screens

import com.strobingn.wildlifefieldops.ai.fieldops.OpsLedger
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenHomeJobsTest {

    @Test
    fun keepsScheduledAndInProgressSoonestFirst() {
        val later = job("later", JobStatus.SCHEDULED, at = 3000L, name = "Zoe")
        val soon = job("soon", JobStatus.IN_PROGRESS, at = 1000L, name = "Pat Lee")
        val trapping = job("trap", JobStatus.TRAPPING, at = 2000L, name = "Ada")
        val done = job("done", JobStatus.COMPLETED, at = 500L, name = "Done")
        val paid = job("paid", JobStatus.PAID, at = 400L, name = "Paid")
        val cancelled = job("cancel", JobStatus.CANCELLED, at = 100L, name = "Cancel")
        val undated = job("none", JobStatus.SCHEDULED, at = null, name = "No Time")

        val open = OpenHomeJobs.list(listOf(later, done, soon, paid, trapping, cancelled, undated))

        assertEquals(listOf("soon", "trap", "later", "none"), open.map { it.id })
        assertFalse(open.any { it.customerName == "Done" })
        assertFalse(open.any { it.status == JobStatus.PAID || it.status == JobStatus.CANCELLED })
    }

    @Test
    fun homeShowsEightSoonestAndLinksWhenMoreExist() {
        val jobs = (1..10).map { index ->
            job("j$index", JobStatus.SCHEDULED, at = index * 1000L, name = "Customer $index")
        } + job("done", JobStatus.COMPLETED, at = 1L, name = "Finished")
        val slice = OpenHomeJobs.homeSlice(jobs)
        assertEquals(10, slice.total)
        assertEquals(8, slice.shown.size)
        assertEquals(listOf("j1", "j2", "j3", "j4", "j5", "j6", "j7", "j8"), slice.shown.map { it.id })
        assertEquals("See all open jobs (10)", slice.seeAllLabel)
        assertTrue(OpenHomeJobs.matchesJobList(JobStatus.SCHEDULED, null, openOnly = true))
        assertTrue(OpenHomeJobs.matchesJobList(JobStatus.TRAPPING, null, openOnly = true))
        assertFalse(OpenHomeJobs.matchesJobList(JobStatus.COMPLETED, null, openOnly = true))
        assertFalse(OpenHomeJobs.matchesJobList(JobStatus.PAID, null, openOnly = true))
    }

    @Test
    fun homeOmitsTheLinkAtTheCap() {
        val jobs = (1..8).map { index ->
            job("j$index", JobStatus.IN_PROGRESS, at = index * 1000L, name = "Customer $index")
        }
        val slice = OpenHomeJobs.homeSlice(jobs)
        assertEquals(8, slice.total)
        assertEquals(8, slice.shown.size)
        assertEquals(null, slice.seeAllLabel)
    }

    @Test
    fun pendingLeadCountsAsScheduledAndLedgerIsDropped() {
        val lead = job("lead", JobStatus.LEAD, at = 50L, name = "Lead")
        val ledger = job(OpsLedger.ID, JobStatus.SCHEDULED, at = 1L, name = "Ledger")
        val open = OpenHomeJobs.list(listOf(ledger, lead))
        assertEquals(listOf("lead"), open.map { it.id })
    }

    private fun job(id: String, status: JobStatus, at: Long?, name: String) = Job(
        id = id,
        customerName = name,
        address = "12 Oak St, Cornwall",
        status = status,
        scheduledDate = at,
        estimatedValue = 450.0
    )
}
