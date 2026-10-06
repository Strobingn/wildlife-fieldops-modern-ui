package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.local.InspectionDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.PhotoDao
import com.strobingn.wildlifefieldops.data.local.TrapLogDao
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPricing
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DecNwcoLogStore @Inject constructor(
    private val jobDao: JobDao,
    private val trapLogDao: TrapLogDao,
    private val inspectionDao: InspectionDao,
    private val photoDao: PhotoDao
) {

    suspend fun compiledRows(): List<NwcoLogRecord> {
        val jobs = jobDao.getAllOnce()
        val auto = DecNwcoLog.autoFill(
            NwcoAutoInput(
                jobs = jobs,
                traps = trapLogDao.getAllOnce(),
                inspections = inspectionDao.getAllOnce(),
                photos = photoDao.getAllOnce()
            )
        )
        val saved = jobs.flatMap { it.pricing.decNwcoRows }
        return DecNwcoLog.merge(auto, saved)
    }

    suspend fun operator(): NwcoOperatorProfile =
        jobDao.getById(OpsLedger.ID)?.pricing?.decNwcoOperator ?: NwcoOperatorProfile()

    suspend fun saveOperator(profile: NwcoOperatorProfile) {
        persistLedger { it.copy(decNwcoOperator = profile) }
    }

    /**
     * Sir tapped "Add to DEC log" after a catch. If the log already lists this trap,
     * nothing is added. Otherwise a new entry is created with only its empty cells prefilled.
     * @return true when a new entry was added.
     */
    suspend fun addCatchEntry(trap: com.strobingn.wildlifefieldops.data.model.TrapLog): Boolean {
        val listed = compiledRows().any { !it.deleted && it.trapId == trap.id }
        if (listed) return false
        val job = trap.jobId.takeIf { it.isNotBlank() }?.let { jobDao.getById(it) }
        saveRow(DecCatchPrefill.newEntry(job, trap))
        return true
    }

    suspend fun saveRow(row: NwcoLogRecord) {
        val target = targetJob(row)
        persistPricing(target) { pricing ->
            pricing.copy(decNwcoRows = pricing.decNwcoRows.filterNot { it.id == row.id || it.sourceKey == row.sourceKey } + row)
        }
    }

    suspend fun deleteRow(row: NwcoLogRecord) {
        val target = targetJob(row)
        val marked = row.copy(deleted = true)
        persistPricing(target) { pricing ->
            pricing.copy(decNwcoRows = pricing.decNwcoRows.filterNot { it.id == row.id || it.sourceKey == row.sourceKey } + marked)
        }
    }

    private suspend fun targetJob(row: NwcoLogRecord): Job {
        val fromJob = row.jobId.takeIf { it.isNotBlank() && !OpsLedger.isLedgerId(it) }?.let { jobDao.getById(it) }
        return fromJob ?: ensureLedger()
    }

    private suspend fun ensureLedger(): Job {
        val existing = jobDao.getById(OpsLedger.ID)
        if (existing != null) return existing
        val created = OpsLedger.newJob()
        jobDao.insert(JobFieldOpsCodec.mergeForSave(created))
        return jobDao.getById(OpsLedger.ID) ?: created
    }

    private suspend fun persistLedger(update: (JobPricing) -> JobPricing) {
        persistPricing(ensureLedger(), update)
    }

    private suspend fun persistPricing(job: Job, update: (JobPricing) -> JobPricing) {
        val now = System.currentTimeMillis()
        jobDao.insert(
            JobFieldOpsCodec.mergeForSave(
                job.copy(pricing = update(job.pricing), updatedAt = now, isSynced = false)
            )
        )
    }
}
