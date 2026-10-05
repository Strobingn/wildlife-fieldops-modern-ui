package com.strobingn.wildlifefieldops.data.repository

import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.FieldObservationDao
import com.strobingn.wildlifefieldops.data.local.InspectionDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.ObservationEventDao
import com.strobingn.wildlifefieldops.data.local.PhotoDao
import javax.inject.Inject
import javax.inject.Singleton

/** Prefer the on-device backlog line; otherwise the failure clause from the last sync. */
fun syncFailureDetail(backlogLine: String?, lastOk: Boolean?, lastMessage: String?): String? {
    if (!backlogLine.isNullOrBlank()) return backlogLine
    if (lastOk != false || lastMessage.isNullOrBlank()) return null
    val idx = lastMessage.indexOf("Failed (")
    return if (idx >= 0) lastMessage.substring(idx) else lastMessage
}

data class SyncBacklogSnapshot(
    val pendingJobs: Int,
    val pendingCustomers: Int,
    val pendingInspections: Int,
    val pendingObservations: Int,
    val pendingEvents: Int,
    val pendingPhotos: Int,
    val failedJobs: Int,
    val failedPhotos: Int,
    val recentFailures: List<String>
) {
    val pendingTotal: Int
        get() = pendingJobs + pendingCustomers + pendingInspections +
            pendingObservations + pendingEvents + pendingPhotos

    val hasFailures: Boolean
        get() = failedJobs > 0 || failedPhotos > 0 || recentFailures.isNotEmpty()

    fun summaryLine(): String {
        if (pendingTotal == 0 && !hasFailures) return "Nothing waiting to sync."
        return buildString {
            append("On this phone (not cleared by an update): ")
            append("$pendingJobs jobs, $pendingCustomers customers, $pendingInspections inspections, ")
            append("$pendingObservations map pins, $pendingEvents ML events, $pendingPhotos photos")
            if (hasFailures) {
                append(". Failed: $failedJobs jobs, $failedPhotos photos")
            }
            append('.')
        }
    }
}

@Singleton
class SyncBacklogRepository @Inject constructor(
    private val jobDao: JobDao,
    private val customerDao: CustomerDao,
    private val inspectionDao: InspectionDao,
    private val fieldObservationDao: FieldObservationDao,
    private val observationEventDao: ObservationEventDao,
    private val photoDao: PhotoDao
) {
    suspend fun snapshot(): SyncBacklogSnapshot {
        val failedJobs = jobDao.getUnsynced().filter { !it.syncError.isNullOrBlank() }
        val failedPhotos = photoDao.getUnuploaded().filter { !it.uploadError.isNullOrBlank() }
        val failedObservations = fieldObservationDao.getUnsynced().filter { !it.syncError.isNullOrBlank() }
        val failedEvents = observationEventDao.getUnsynced().filter { !it.syncError.isNullOrBlank() }
        val recent = buildList {
            failedJobs.take(5).forEach { add("Job ${it.title.ifBlank { it.id }}: ${it.syncError}") }
            failedPhotos.take(5).forEach { add("Photo ${it.id.take(8)}: ${it.uploadError}") }
            failedObservations.take(3).forEach { add("Observation ${it.id.take(8)}: ${it.syncError}") }
            failedEvents.take(3).forEach { add("Event ${it.eventId.take(8)}: ${it.syncError}") }
        }
        return SyncBacklogSnapshot(
            pendingJobs = jobDao.countUnsynced(),
            pendingCustomers = customerDao.countUnsynced(),
            pendingInspections = inspectionDao.countUnsynced(),
            pendingObservations = fieldObservationDao.countUnsynced(),
            pendingEvents = observationEventDao.countUnsynced(),
            pendingPhotos = photoDao.countUnuploaded(),
            failedJobs = failedJobs.size,
            failedPhotos = failedPhotos.size,
            recentFailures = recent
        )
    }
}
