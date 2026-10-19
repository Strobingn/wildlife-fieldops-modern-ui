package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.ReminderDao
import com.strobingn.wildlifefieldops.data.local.TrapLogDao
import com.strobingn.wildlifefieldops.data.local.VisitDao
import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Reminder
import com.strobingn.wildlifefieldops.data.model.ReminderPriority
import com.strobingn.wildlifefieldops.data.model.ReminderStatus
import com.strobingn.wildlifefieldops.data.model.ReminderType
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import com.strobingn.wildlifefieldops.data.model.Visit
import com.strobingn.wildlifefieldops.location.PreciseGpsMath
import com.strobingn.wildlifefieldops.location.PreciseSpot
import com.strobingn.wildlifefieldops.pricing.SyncedTrapRecord
import com.strobingn.wildlifefieldops.pricing.hasPreciseGps
import com.strobingn.wildlifefieldops.pricing.markManual
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class FollowUpSaveResult(
    val job: Job,
    val visit: Visit?,
    val reminder: Reminder?
)

/**
 * Local trap_logs / visits / reminders are the source of truth on device.
 * Every write also embeds the rows in [Job.pricing] so AutoSync (jobs only)
 * pushes them through live `jobs.pricing` jsonb — no new PostgREST columns.
 */
@Singleton
class TrapFieldOpsStore @Inject constructor(
    private val trapLogDao: TrapLogDao,
    private val jobDao: JobDao,
    private val visitDao: VisitDao,
    private val reminderDao: ReminderDao
) {

    suspend fun saveTrap(trap: TrapLog) {
        val now = System.currentTimeMillis()
        val previous = trapLogDao.getById(trap.id)
        val saved = trap.copy(
            updatedAt = now,
            isSynced = false,
            createdAt = trap.createdAt.takeIf { it > 0L } ?: now
        )
        trapLogDao.insert(saved)
        embedTrapsOnJob(saved.jobId)
        publishPreciseSpot(previous, saved)
    }

    /**
     * The job Directions button reads the job's coordinates. A new precise
     * capture is copied there so that button navigates to the trap spot.
     * Typed coordinates and a re-save of the same spot leave the job pin alone.
     */
    private suspend fun publishPreciseSpot(previous: TrapLog?, saved: TrapLog) {
        if (saved.jobId.isBlank()) return
        val spot = saved.toPreciseSpot()
        val publish = PreciseGpsMath.shouldPublishToJob(
            previousCapturedAt = previous?.locationCapturedAt,
            previousLat = previous?.latitude,
            previousLng = previous?.longitude,
            saved = spot
        )
        if (!publish || spot == null) return
        val job = jobDao.getById(saved.jobId) ?: return
        jobDao.insert(
            job.copy(
                latitude = spot.latitude,
                longitude = spot.longitude,
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
        )
    }

    suspend fun deleteTrap(trap: TrapLog) {
        trapLogDao.delete(trap)
        if (trap.jobId.isNotBlank()) embedTrapsOnJob(trap.jobId)
    }

    suspend fun embedTrapsOnJob(jobId: String) {
        if (jobId.isBlank()) return
        val job = jobDao.getById(jobId) ?: return
        val traps = trapLogDao.getByJobOnce(jobId)
        val records = traps.map { it.toSynced() }
        val extras = JobFieldOpsCodec.extract(job.pricing).copy(
            trapRecords = records
        )
        val embedded = JobFieldOpsCodec.embed(job.pricing, extras)
        val stamped = embedded.copy(
            dataVersion = PreciseGpsMath.stampedDataVersion(
                embedded.dataVersion,
                records.any { it.hasPreciseGps() } || embedded.trapRecords.any { it.hasPreciseGps() }
            )
        )
        jobDao.insert(
            JobFieldOpsCodec.mergeForSave(
                job.copy(
                    pricing = stamped,
                    updatedAt = System.currentTimeMillis(),
                    isSynced = false
                )
            )
        )
    }

    suspend fun hydrateFromJob(job: Job) {
        val records = job.pricing.trapRecords
        if (records.isNotEmpty()) {
            val existing = trapLogDao.getByJobOnce(job.id).associateBy { it.id }
            records.forEach { rec ->
                val local = existing[rec.id]
                if (local == null || rec.updatedAt >= local.updatedAt) {
                    trapLogDao.insert(rec.toTrapLog())
                }
            }
        }
        hydrateFollowUp(job)
    }

    suspend fun saveWeatherAdvice(jobId: String, advice: String, source: String) {
        val job = jobDao.getById(jobId) ?: return
        val now = System.currentTimeMillis()
        val extras = JobFieldOpsCodec.extract(job.pricing).copy(
            weatherTrapAdvice = advice,
            weatherTrapAdviceAt = now,
            weatherTrapAdviceSource = source
        )
        jobDao.insert(
            JobFieldOpsCodec.mergeForSave(
                job.copy(
                    weatherTrapAdvice = advice,
                    pricing = JobFieldOpsCodec.embed(job.pricing.markManual(ManualField.WEATHER), extras),
                    updatedAt = now,
                    isSynced = false
                )
            )
        )
    }

    suspend fun createFollowUp(
        job: Job,
        kind: FollowUpKind?,
        dueAt: Long?,
        notes: String,
        title: String = kind?.let { FollowUpPlanner.label(it) }.orEmpty()
    ): FollowUpSaveResult {
        val now = System.currentTimeMillis()
        val latest = jobDao.getById(job.id) ?: job
        val existingVisitId = latest.pricing.followUpVisitId
        val existingReminderId = latest.pricing.followUpReminderId
        val visit: Visit?
        val reminder: Reminder?
        val visitId: String
        val reminderId: String
        if (dueAt == null) {
            if (existingVisitId.isNotBlank()) {
                visitDao.getByJobOnce(latest.id).firstOrNull { it.id == existingVisitId }?.let { visitDao.delete(it) }
            }
            if (existingReminderId.isNotBlank()) {
                reminderDao.getById(existingReminderId)?.let { reminderDao.delete(it) }
            }
            visit = null
            reminder = null
            visitId = ""
            reminderId = ""
        } else {
            visit = Visit(
                id = existingVisitId.ifBlank { UUID.randomUUID().toString() },
                jobId = latest.id,
                customerId = latest.customerId,
                customerName = latest.customerName,
                technicianName = latest.assignedTo,
                visitDate = dueAt,
                startTime = dueAt,
                notes = notes,
                workPerformed = "${kind?.let { FollowUpPlanner.label(it) } ?: "Follow-up"} follow-up",
                isCompleted = false,
                createdAt = now,
                updatedAt = now,
                isSynced = false
            )
            reminder = Reminder(
                id = existingReminderId.ifBlank { UUID.randomUUID().toString() },
                title = title.ifBlank {
                    (kind?.let { FollowUpPlanner.label(it) } ?: "Follow-up") + " — " + latest.title.ifBlank { latest.customerName }
                },
                description = notes,
                jobId = latest.id,
                customerId = latest.customerId.takeIf { it.isNotBlank() },
                customerName = latest.customerName,
                reminderType = ReminderType.FOLLOW_UP,
                priority = if (kind == FollowUpKind.TRAP_PULL) ReminderPriority.HIGH else ReminderPriority.MEDIUM,
                status = ReminderStatus.PENDING,
                dueDate = dueAt,
                notes = notes,
                createdAt = now,
                updatedAt = now,
                isSynced = false
            )
            visitDao.insert(visit)
            reminderDao.insert(reminder)
            visitId = visit.id
            reminderId = reminder.id
        }
        val extras = JobFieldOpsCodec.extract(latest.pricing).copy(
            followUpKind = kind?.name.orEmpty(),
            followUpDueAt = dueAt,
            followUpNotes = notes,
            followUpVisitId = visitId,
            followUpReminderId = reminderId
        )
        val saved = JobFieldOpsCodec.mergeForSave(
            latest.copy(
                followUpKind = kind?.name.orEmpty(),
                followUpDueAt = dueAt,
                followUpNotes = notes,
                pricing = JobFieldOpsCodec.embed(
                    latest.pricing.markManual(ManualField.FOLLOW_KIND, ManualField.FOLLOW_NOTES, ManualField.FOLLOW_DUE),
                    extras
                ),
                updatedAt = now,
                isSynced = false
            )
        )
        jobDao.insert(saved)
        return FollowUpSaveResult(saved, visit, reminder)
    }

    private suspend fun hydrateFollowUp(job: Job) {
        val extras = JobFieldOpsCodec.extract(job.pricing)
        if (extras.followUpVisitId.isNotBlank()) {
            val have = visitDao.getByJobOnce(job.id).any { it.id == extras.followUpVisitId }
            if (!have && extras.followUpDueAt != null) {
                visitDao.insert(
                    Visit(
                        id = extras.followUpVisitId,
                        jobId = job.id,
                        customerId = job.customerId,
                        customerName = job.customerName,
                        technicianName = job.assignedTo,
                        visitDate = extras.followUpDueAt,
                        startTime = extras.followUpDueAt,
                        notes = extras.followUpNotes,
                        workPerformed = extras.followUpKind.ifBlank { "Follow-up" },
                        isCompleted = false,
                        isSynced = false
                    )
                )
            }
        }
        if (extras.followUpReminderId.isNotBlank() && extras.followUpDueAt != null &&
            reminderDao.getById(extras.followUpReminderId) == null
        ) {
            reminderDao.insert(
                Reminder(
                    id = extras.followUpReminderId,
                    title = FollowUpPlanner.parseKind(extras.followUpKind)?.let { FollowUpPlanner.label(it) }
                        ?: extras.followUpKind.ifBlank { "Follow-up" },
                    description = extras.followUpNotes,
                    jobId = job.id,
                    customerId = job.customerId.takeIf { it.isNotBlank() },
                    customerName = job.customerName,
                    reminderType = ReminderType.FOLLOW_UP,
                    status = ReminderStatus.PENDING,
                    dueDate = extras.followUpDueAt,
                    notes = extras.followUpNotes,
                    isSynced = false
                )
            )
        }
    }
}

fun TrapLog.toSynced(): SyncedTrapRecord = SyncedTrapRecord(
    id = id,
    jobId = jobId,
    trapId = trapId,
    trapLocation = trapLocation,
    latitude = latitude,
    longitude = longitude,
    locationAccuracyM = locationAccuracyM,
    locationSampleCount = locationSampleCount,
    locationCapturedAt = locationCapturedAt,
    technicianName = technicianName,
    checkDate = checkDate,
    status = status.name,
    catchType = catchType.name,
    catchCount = catchCount,
    baitType = baitType,
    baitCondition = baitCondition,
    conditionNotes = conditionNotes,
    actionTaken = actionTaken,
    nextCheckDate = nextCheckDate,
    weatherConditions = weatherConditions,
    temperature = temperature,
    disposition = disposition,
    method = method,
    checkIntervalHours = checkIntervalHours,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun SyncedTrapRecord.toTrapLog(): TrapLog = TrapLog(
    id = id.ifBlank { UUID.randomUUID().toString() },
    jobId = jobId,
    trapId = trapId,
    trapLocation = trapLocation,
    latitude = latitude,
    longitude = longitude,
    locationAccuracyM = locationAccuracyM,
    locationSampleCount = locationSampleCount,
    locationCapturedAt = locationCapturedAt,
    technicianName = technicianName,
    checkDate = checkDate,
    status = runCatching { TrapStatus.valueOf(status) }.getOrDefault(TrapStatus.SET),
    catchType = runCatching { CatchType.valueOf(catchType) }.getOrDefault(CatchType.NONE),
    catchCount = catchCount,
    baitType = baitType,
    baitCondition = baitCondition,
    conditionNotes = conditionNotes,
    actionTaken = actionTaken,
    nextCheckDate = nextCheckDate,
    weatherConditions = weatherConditions,
    temperature = temperature,
    disposition = disposition,
    method = method,
    checkIntervalHours = checkIntervalHours,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isSynced = false
)

fun TrapLog.toPreciseSpot(): PreciseSpot? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    val accuracy = locationAccuracyM ?: return null
    val samples = locationSampleCount ?: return null
    val capturedAt = locationCapturedAt ?: return null
    if (!lat.isFinite() || !lng.isFinite() || !accuracy.isFinite() || samples <= 0) return null
    return PreciseSpot(
        latitude = lat,
        longitude = lng,
        accuracyMeters = accuracy,
        sampleCount = samples,
        capturedAtMillis = capturedAt
    )
}
