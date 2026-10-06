package com.strobingn.wildlifefieldops.ui.screens

import com.strobingn.wildlifefieldops.ai.fieldops.OpsLedger
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
