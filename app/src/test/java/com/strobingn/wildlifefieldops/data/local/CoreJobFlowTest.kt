package com.strobingn.wildlifefieldops.data.local

import android.app.Application
import androidx.room.Room
import org.robolectric.RuntimeEnvironment
import com.strobingn.wildlifefieldops.ai.fieldops.JobFieldOpsCodec
import com.strobingn.wildlifefieldops.ai.fieldops.ManualField
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckOutcome
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckRecorder
import com.strobingn.wildlifefieldops.ai.fieldops.TrapReminders
import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.DeletedRecord
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.data.remote.LiveSyncPayloads
import com.strobingn.wildlifefieldops.data.remote.toLocal
import com.strobingn.wildlifefieldops.data.remote.toRemoteDto
import com.strobingn.wildlifefieldops.data.repository.JobRepository
import com.strobingn.wildlifefieldops.pricing.markManual
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Core offline job flow against a real Room database (in memory):
 * create offline, edit, clear fields, reload, sync mapping, Completed, delete.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CoreJobFlowTest {
    private lateinit var db: AppDatabase
    private lateinit var jobs: JobRepository

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        jobs = JobRepository(db.jobDao(), db.deletedRecordDao())
    }

    @After
    fun close() = db.close()

    @Test
    fun createEditClearCompleteDelete() = runBlocking {
        // Create offline: nothing has synced, so it waits in the upload queue.
        val created = Job(
            id = "11111111-2222-3333-4444-555555555555",
            title = "Raccoon in attic",
            customerName = "Willow Properties",
            address = "210 Willow Ave, Cornwall, NY",
            status = JobStatus.SCHEDULED,
            notes = "Gate code 1234",
            description = "Noise at night",
            legalNotes = "Permit on file",
            confirmedSpecies = "Raccoon",
            isSynced = false
        )
        jobs.saveJob(JobFieldOpsCodec.mergeForSave(created))
        assertEquals(1, db.jobDao().countUnsynced())
        assertEquals("Raccoon in attic", jobs.getJobById(created.id)?.title)

        // Edit.
        val edited = jobs.getJobById(created.id)!!.copy(title = "Raccoons in attic (2)", updatedAt = 2_000L)
        jobs.updateJob(JobFieldOpsCodec.mergeForSave(edited))
        assertEquals("Raccoons in attic (2)", jobs.getJobById(created.id)?.title)

        // Clear fields the way the job form does (typed blank is manual).
        val cleared = jobs.getJobById(created.id)!!.let {
            it.copy(
                notes = "",
                description = "",
                legalNotes = "",
                confirmedSpecies = "",
                pricing = it.pricing.markManual(ManualField.LEGAL_NOTES, ManualField.SPECIES)
            )
        }
        jobs.updateJob(JobFieldOpsCodec.mergeForSave(cleared))

        // Reload: the blanks stay blank, including fields embedded in pricing.
        val reloaded = JobFieldOpsCodec.applyFromPricing(jobs.getJobById(created.id)!!)
        assertEquals("", reloaded.notes)
        assertEquals("", reloaded.description)
        assertEquals("", reloaded.legalNotes)
        assertEquals("", reloaded.confirmedSpecies)

        // Sync mapping: the upload sends the cleared values as "", and pulling the row back keeps the blanks.
        val upsert = LiveSyncPayloads.job(reloaded)
        assertEquals("", upsert.notes)
        assertEquals("", upsert.scope)
        assertEquals("", upsert.pricing.legalNotes)
        val pulled = reloaded.toRemoteDto().toLocal(existing = reloaded)
        assertEquals("", pulled.notes)
        assertEquals("", pulled.description)
        assertEquals("", pulled.legalNotes)
        assertEquals("", pulled.confirmedSpecies)
        assertEquals("Raccoons in attic (2)", pulled.title)

        // Mark Completed.
        jobs.updateJob(reloaded.copy(status = JobStatus.COMPLETED, completedDate = 3_000L))
        assertEquals(JobStatus.COMPLETED, jobs.getJobById(created.id)?.status)
        assertEquals(1, db.jobDao().countByStatus(JobStatus.COMPLETED))

        // Delete: gone locally and queued as a tombstone for sync.
        jobs.deleteJob(jobs.getJobById(created.id)!!)
        assertNull(jobs.getJobById(created.id))
        assertTrue(db.deletedRecordDao().exists(created.id, DeletedRecord.TYPE_JOB))
        assertEquals(listOf(created.id), db.deletedRecordDao().getUnsyncedByType(DeletedRecord.TYPE_JOB).map { it.id })
    }

    @Test
    fun trapCheckIntervalSurvivesRoomAndSetsNextDue() = runBlocking {
        val now = 1_760_000_000_000L
        val trap = TrapLog(id = "t1", jobId = "j1", trapId = "Cage 1", checkIntervalHours = 8, nextCheckDate = now)
        db.trapLogDao().insert(trap)
        val stored = db.trapLogDao().getById("t1")!!
        assertEquals(8, stored.checkIntervalHours)
        val checked = TrapCheckRecorder.checked(
            stored, TrapCheckOutcome.NONE, CatchType.NONE, 0, "", now,
            TrapReminders.intervalHours(stored, defaultHours = 24)
        )
        db.trapLogDao().insert(checked)
        val after = db.trapLogDao().getById("t1")!!
        assertEquals(now + 8 * TrapReminders.HOUR_MS, after.nextCheckDate)
        assertFalse(after.isSynced)
    }
}
