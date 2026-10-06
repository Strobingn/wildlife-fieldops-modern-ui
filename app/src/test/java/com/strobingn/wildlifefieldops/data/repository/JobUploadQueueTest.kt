package com.strobingn.wildlifefieldops.data.repository

import com.strobingn.wildlifefieldops.ai.fieldops.OpsLedger
import com.strobingn.wildlifefieldops.data.model.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobUploadQueueTest {
    private val real = Job(id = "8f14e45f-ceea-467a-9b2c-3d1f0e6a7b10", title = "Attic raccoon")
    private val realFailed = Job(id = "0b6c9f3e-1d2a-4f5b-8c7d-6e5f4a3b2c1d", title = "Bat exclusion", syncError = "timeout")
    private val ledgerFailed = OpsLedger.newJob().copy(
        syncError = "invalid input syntax for type uuid: \"fieldops-ops-ledger\""
    )

    @Test
    fun opsLedgerAndNonUuidIdsNeverUpload() {
        assertFalse(JobUploadQueue.isUploadable(OpsLedger.ID))
        assertFalse(JobUploadQueue.isUploadable("job-1"))
        assertFalse(JobUploadQueue.isUploadable(""))
        assertTrue(JobUploadQueue.isUploadable(real))
        assertEquals(listOf(real, realFailed), JobUploadQueue.pending(listOf(real, ledgerFailed, realFailed)))
    }

    @Test
    fun failedCountClearsOnceTheLedgerIsSkipped() {
        assertEquals(emptyList<Job>(), JobUploadQueue.failed(listOf(real, ledgerFailed)))
        assertEquals(listOf(realFailed), JobUploadQueue.failed(listOf(real, ledgerFailed, realFailed)))
    }

    @Test
    fun ledgerRowItselfIsNotChanged() {
        val before = ledgerFailed.copy()
        JobUploadQueue.pending(listOf(ledgerFailed))
        JobUploadQueue.failed(listOf(ledgerFailed))
        assertEquals(before, ledgerFailed)
    }

    @Test
    fun backlogLineNoLongerReportsTheLedger() {
        val unsynced = listOf(ledgerFailed)
        val snapshot = SyncBacklogSnapshot(
            pendingJobs = JobUploadQueue.pending(unsynced).size,
            pendingCustomers = 0, pendingInspections = 0, pendingObservations = 0,
            pendingEvents = 0, pendingPhotos = 0,
            failedJobs = JobUploadQueue.failed(unsynced).size,
            failedPhotos = 0,
            recentFailures = emptyList()
        )
        assertEquals(0, snapshot.pendingTotal)
        assertFalse(snapshot.hasFailures)
        assertEquals("Nothing waiting to sync.", snapshot.summaryLine())
    }
}
