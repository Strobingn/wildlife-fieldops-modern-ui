package com.strobingn.wildlifefieldops.data.repository

import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.DeletedRecordDao
import com.strobingn.wildlifefieldops.data.local.FieldObservationDao
import com.strobingn.wildlifefieldops.data.local.InspectionDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.ObservationEventDao
import com.strobingn.wildlifefieldops.data.local.PhotoDao
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.DeletedRecord
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.observation.FieldObservationSyncQueue
import com.strobingn.wildlifefieldops.data.observation.ObservationEventMapper
import com.strobingn.wildlifefieldops.data.observation.ObservationEventSyncQueue
import com.strobingn.wildlifefieldops.data.observation.ObservationPhotoPaths
import com.strobingn.wildlifefieldops.data.observation.ObservationPhotoUploader
import com.strobingn.wildlifefieldops.data.remote.JobPhotoUploader
import com.strobingn.wildlifefieldops.data.remote.LiveSyncPayloads
import com.strobingn.wildlifefieldops.data.remote.RemoteCustomerDto
import com.strobingn.wildlifefieldops.data.remote.RemoteJobDto
import com.strobingn.wildlifefieldops.data.remote.RemoteObservationEventDto
import com.strobingn.wildlifefieldops.data.remote.SupabaseService
import com.strobingn.wildlifefieldops.data.remote.SyncErrorFormatter
import com.strobingn.wildlifefieldops.data.remote.SyncItemFailure
import com.strobingn.wildlifefieldops.data.remote.SyncItemOutcome
import com.strobingn.wildlifefieldops.data.remote.SyncItemRunner
import com.strobingn.wildlifefieldops.ai.fieldops.TrapFieldOpsStore
import com.strobingn.wildlifefieldops.data.remote.toLocal
import com.strobingn.wildlifefieldops.sync.work.FieldOpsSyncGateway
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class SyncResult(
    val success: Boolean,
    val message: String,
    val pushedJobs: Int = 0,
    val pushedCustomers: Int = 0,
    val pushedInspections: Int = 0,
    val pushedObservations: Int = 0,
    val pushedEvents: Int = 0,
    val uploadedPhotos: Int = 0,
    val pulledJobs: Int = 0,
    val pulledCustomers: Int = 0,
    val failedItems: List<SyncItemFailure> = emptyList(),
    val pendingRemaining: Int = 0
)

@Singleton
class SyncRepository @Inject constructor(
    private val supabaseService: SupabaseService,
    private val jobDao: JobDao,
    private val customerDao: CustomerDao,
    private val inspectionDao: InspectionDao,
    private val fieldObservationDao: FieldObservationDao,
    private val observationEventDao: ObservationEventDao,
    private val photoDao: PhotoDao,
    private val observationPhotoUploader: ObservationPhotoUploader,
    private val jobPhotoUploader: JobPhotoUploader,
    private val deletedRecordDao: DeletedRecordDao,
    private val itemRunner: SyncItemRunner,
    private val trapFieldOpsStore: TrapFieldOpsStore
) : FieldOpsSyncGateway {
    override fun isCloudConfigured(): Boolean = supabaseService.isConfigured

    override suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        try {
            doSync()
        } catch (t: Throwable) {
            android.util.Log.e("SyncRepository", "Sync crashed", t)
            SyncResult(
                success = false,
                message = "Sync failed: ${SyncErrorFormatter.reason(t)}. Local data was not deleted."
            )
        }
    }

    /**
     * Best-effort immediate remote delete. Offline / failure is OK — tombstone stays
     * unsynced and [doSync] will retry.
     */
    suspend fun tryRemoteDelete(entityType: String, id: String) = withContext(Dispatchers.IO) {
        val client = supabaseService.client ?: return@withContext
        val table = when (entityType) {
            DeletedRecord.TYPE_JOB -> "jobs"
            DeletedRecord.TYPE_CUSTOMER -> "customers"
            DeletedRecord.TYPE_INSPECTION -> "inspections"
            else -> return@withContext
        }
        runCatching {
            client.from(table).delete {
                filter {
                    eq("id", id)
                }
            }
            deletedRecordDao.markSynced(id, entityType)
        }.onFailure {
            android.util.Log.w("SyncRepository", "Immediate remote delete failed for $entityType/$id", it)
        }
    }

    private suspend fun doSync(): SyncResult {
        val client = supabaseService.client
            ?: return SyncResult(
                success = false,
                message = "Cloud not configured. Rebuild the APK with Supabase secrets (VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY)."
            )

        var pushedJobs = 0
        var pushedCustomers = 0
        var pushedInspections = 0
        var pushedObservations = 0
        var pushedEvents = 0
        var uploadedPhotos = 0
        var pulledJobs = 0
        var pulledCustomers = 0
        val failures = mutableListOf<SyncItemFailure>()

        pushDeletions(client, DeletedRecord.TYPE_JOB, "jobs", failures)
        pushDeletions(client, DeletedRecord.TYPE_CUSTOMER, "customers", failures)
        pushDeletions(client, DeletedRecord.TYPE_INSPECTION, "inspections", failures)

        val deletedCustomerIds = deletedRecordDao.getIdsByType(DeletedRecord.TYPE_CUSTOMER).toSet()
        customerDao.getUnsynced().forEach { customer ->
            if (customer.id in deletedCustomerIds) return@forEach
            val outcome = itemRunner.run(
                markSynced = { customerDao.markSynced(customer.id) },
                markError = { customerDao.markSyncError(customer.id, it) }
            ) {
                client.from("customers").upsert(LiveSyncPayloads.customer(customer))
            }
            when (outcome) {
                is SyncItemOutcome.Ok -> pushedCustomers += 1
                is SyncItemOutcome.Failed -> failures += SyncItemFailure(
                    "customer", customer.id, customer.fullName, outcome.reason
                )
            }
        }

        val deletedJobIds = deletedRecordDao.getIdsByType(DeletedRecord.TYPE_JOB).toSet()
        jobDao.getUnsynced().forEach { job ->
            if (job.id in deletedJobIds) return@forEach
            val outcome = itemRunner.run(
                markSynced = { jobDao.markSynced(job.id) },
                markError = { jobDao.markSyncError(job.id, it) }
            ) {
                client.from("jobs").upsert(LiveSyncPayloads.job(job))
            }
            when (outcome) {
                is SyncItemOutcome.Ok -> pushedJobs += 1
                is SyncItemOutcome.Failed -> {
                    android.util.Log.e("SyncRepository", "Job push failed ${job.id}: ${outcome.reason}")
                    failures += SyncItemFailure("job", job.id, job.title.ifBlank { job.customerName }, outcome.reason)
                }
            }
        }

        val deletedInspectionIds = deletedRecordDao.getIdsByType(DeletedRecord.TYPE_INSPECTION).toSet()
        inspectionDao.getUnsynced().forEach { insp ->
            if (insp.id in deletedInspectionIds) return@forEach
            if (insp.jobId.isNotBlank() && !com.strobingn.wildlifefieldops.data.remote.SyncIds.isUuid(insp.jobId)) {
                val reason = "Inspection job_id is not a UUID; cloud inspections.job_id is uuid"
                inspectionDao.markSyncError(insp.id, reason)
                failures += SyncItemFailure("inspection", insp.id, insp.customerName, reason)
                return@forEach
            }
            val outcome = itemRunner.run(
                markSynced = { inspectionDao.markSynced(insp.id) },
                markError = { inspectionDao.markSyncError(insp.id, it) }
            ) {
                client.from("inspections").upsert(LiveSyncPayloads.inspection(insp))
            }
            when (outcome) {
                is SyncItemOutcome.Ok -> pushedInspections += 1
                is SyncItemOutcome.Failed -> failures += SyncItemFailure(
                    "inspection", insp.id, insp.customerName.ifBlank { insp.id }, outcome.reason
                )
            }
        }

        try {
            val remoteCustomers = client.from("customers").select().decodeList<RemoteCustomerDto>()
            pulledCustomers = mergeCustomers(remoteCustomers)
        } catch (e: Exception) {
            android.util.Log.w("SyncRepository", "Customer pull failed", e)
            failures += SyncItemFailure("customer", "*", "pull", SyncErrorFormatter.reason(e))
        }

        try {
            val remoteJobs = client.from("jobs").select().decodeList<RemoteJobDto>()
            pulledJobs = mergeJobs(remoteJobs)
        } catch (e: Exception) {
            android.util.Log.w("SyncRepository", "Job pull failed", e)
            failures += SyncItemFailure("job", "*", "pull", SyncErrorFormatter.reason(e))
        }

        photoDao.getUnuploaded().forEach { photo ->
            val outcome = itemRunner.run(
                markSynced = { /* ACK inside the block after a description-stable write */ },
                markError = { photoDao.markUploadError(photo.id, it) }
            ) {
                val uploaded = jobPhotoUploader.upload(client, photo)
                val latest = photoDao.getById(photo.id) ?: photo
                try {
                    client.from("photos").upsert(
                        LiveSyncPayloads.photo(latest, uploaded.storagePath, uploaded.publicUrl)
                    )
                } catch (e: Exception) {
                    android.util.Log.w("SyncRepository", "photos row failed for ${photo.id}", e)
                    throw e
                }
                runCatching {
                    client.from("job_photos").upsert(
                        LiveSyncPayloads.jobPhotoLink(latest, uploaded.storagePath, uploaded.publicUrl)
                    )
                }.onFailure {
                    android.util.Log.w("SyncRepository", "job_photos link skipped for ${photo.id}", it)
                }
                val acked = photoDao.markUploadedIfDescription(
                    latest.id,
                    uploaded.publicUrl,
                    latest.description
                )
                if (acked == 0) {
                    android.util.Log.i(
                        "SyncRepository",
                        "Photo ${photo.id} notes changed during upload; leaving unsynced for AI retry"
                    )
                }
                acked
            }
            when (outcome) {
                is SyncItemOutcome.Ok -> {
                    if (outcome.value > 0) uploadedPhotos += 1
                }
                is SyncItemOutcome.Failed -> {
                    android.util.Log.e("SyncRepository", "Photo upload failed ${photo.id}: ${outcome.reason}")
                    failures += SyncItemFailure(
                        "photo",
                        photo.id,
                        photo.description.take(40).ifBlank { photo.id },
                        outcome.reason
                    )
                }
            }
        }

        FieldObservationSyncQueue.queuedForPush(fieldObservationDao.getUnsynced()).forEach { observation ->
            val outcome = itemRunner.run(
                markSynced = { fieldObservationDao.markSynced(observation.id) },
                markError = { fieldObservationDao.markSyncError(observation.id, it) }
            ) {
                var storagePath: String? = null
                var publicUrl: String? = null
                val localPhoto = observation.photoLocalPath.trim()
                if (ObservationPhotoPaths.isLocalCandidate(localPhoto) &&
                    observationPhotoUploader.readBytes(localPhoto) != null
                ) {
                    val uploaded = observationPhotoUploader.uploadFieldPhoto(
                        client = client,
                        observationId = observation.id,
                        localPath = localPhoto
                    )
                    storagePath = uploaded.storagePath
                    publicUrl = uploaded.publicUrl
                    uploadedPhotos += 1
                } else if (ObservationPhotoPaths.isLocalCandidate(localPhoto)) {
                    android.util.Log.w(
                        "SyncRepository",
                        "Field observation ${observation.id} photo missing locally; syncing metadata only"
                    )
                }
                client.from("field_observations").upsert(
                    LiveSyncPayloads.fieldObservation(
                        observation,
                        photoStoragePath = storagePath,
                        photoPublicUrl = publicUrl
                    )
                )
            }
            when (outcome) {
                is SyncItemOutcome.Ok -> pushedObservations += 1
                is SyncItemOutcome.Failed -> failures += SyncItemFailure(
                    "observation", observation.id, observation.notes.take(40), outcome.reason
                )
            }
        }

        val now = System.currentTimeMillis()
        ObservationEventSyncQueue.queuedForPush(observationEventDao.getUnsynced()).forEach { record ->
            val outcome = itemRunner.run(
                markSynced = { observationEventDao.markSynced(record.eventId, now) },
                markError = { observationEventDao.markSyncError(record.eventId, it) }
            ) {
                var storagePath: String? = null
                val mediaUri = record.mediaUri?.trim().orEmpty()
                if (ObservationPhotoPaths.isLocalCandidate(mediaUri) &&
                    observationPhotoUploader.readBytes(mediaUri) != null
                ) {
                    val uploaded = observationPhotoUploader.uploadEventMedia(
                        client = client,
                        eventId = record.eventId,
                        mediaUri = mediaUri
                    )
                    storagePath = uploaded.storagePath
                    uploadedPhotos += 1
                } else if (ObservationPhotoPaths.isLocalCandidate(mediaUri)) {
                    android.util.Log.w(
                        "SyncRepository",
                        "ObservationEvent ${record.eventId} media missing locally; inserting row without storage path"
                    )
                }
                val dto = ObservationEventMapper.toRemoteDto(record, mediaStoragePath = storagePath)
                insertObservationEventIgnoreDuplicate(client, dto)
            }
            when (outcome) {
                is SyncItemOutcome.Ok -> pushedEvents += 1
                is SyncItemOutcome.Failed -> failures += SyncItemFailure(
                    "event", record.eventId, record.entityId, outcome.reason
                )
            }
        }

        val pendingRemaining = jobDao.countUnsynced() +
            customerDao.countUnsynced() +
            inspectionDao.countUnsynced() +
            fieldObservationDao.countUnsynced() +
            observationEventDao.countUnsynced() +
            photoDao.countUnuploaded()

        val base = "Synced. Pushed: $pushedJobs jobs, $pushedCustomers customers, $pushedInspections inspections, " +
            "$pushedObservations observations, $pushedEvents events, $uploadedPhotos photos. " +
            "Pulled: $pulledJobs jobs, $pulledCustomers customers. Still pending locally: $pendingRemaining."
        val failureText = if (failures.isEmpty()) {
            ""
        } else {
            " Failed (${failures.size}): " + failures.take(8).joinToString("; ") {
                "${it.entityType} ${it.label.ifBlank { it.id }} — ${it.reason}"
            }
        }
        val ok = failures.isEmpty()
        if (!ok) {
            android.util.Log.e("SyncRepository", "Sync finished with failures:$failureText")
        }
        return SyncResult(
            success = ok,
            message = if (ok) base else "$base$failureText",
            pushedJobs = pushedJobs,
            pushedCustomers = pushedCustomers,
            pushedInspections = pushedInspections,
            pushedObservations = pushedObservations,
            pushedEvents = pushedEvents,
            uploadedPhotos = uploadedPhotos,
            pulledJobs = pulledJobs,
            pulledCustomers = pulledCustomers,
            failedItems = failures,
            pendingRemaining = pendingRemaining
        )
    }

    private suspend fun insertObservationEventIgnoreDuplicate(
        client: SupabaseClient,
        dto: RemoteObservationEventDto
    ) {
        try {
            client.from("observation_events").insert(dto)
        } catch (e: Exception) {
            if (SyncErrorFormatter.isDuplicate(e)) {
                android.util.Log.i(
                    "SyncRepository",
                    "ObservationEvent ${dto.eventId} already on server; treating as synced"
                )
            } else {
                throw e
            }
        }
    }

    private suspend fun pushDeletions(
        client: SupabaseClient,
        entityType: String,
        table: String,
        failures: MutableList<SyncItemFailure>
    ) {
        try {
            val unsynced = deletedRecordDao.getUnsyncedByType(entityType)
            for (tombstone in unsynced) {
                try {
                    client.from(table).delete {
                        filter {
                            eq("id", tombstone.id)
                        }
                    }
                    deletedRecordDao.markSynced(tombstone.id, entityType)
                } catch (e: Exception) {
                    val reason = SyncErrorFormatter.reason(e)
                    android.util.Log.w(
                        "SyncRepository",
                        "Remote delete failed for $entityType/${tombstone.id}",
                        e
                    )
                    failures += SyncItemFailure(entityType, tombstone.id, "delete", reason)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "Deletion push failed for $entityType", e)
            failures += SyncItemFailure(entityType, "*", "deletions", SyncErrorFormatter.reason(e))
        }
    }

    private suspend fun mergeJobs(remote: List<RemoteJobDto>): Int {
        if (remote.isEmpty()) return 0
        val deletedIds = deletedRecordDao.getIdsByType(DeletedRecord.TYPE_JOB).toSet()
        val localById = jobDao.getAllOnce().associateBy { it.id }
        val incoming = mutableListOf<Job>()
        remote.forEach { dto ->
            if (dto.id in deletedIds) return@forEach
            val existing = localById[dto.id]
            if (existing != null && !existing.isSynced) return@forEach
            val mapped = runCatching { dto.toLocal(existing) }.getOrNull() ?: return@forEach
            incoming += mapped
        }
        if (incoming.isNotEmpty()) {
            jobDao.insertAll(incoming)
            incoming.forEach { trapFieldOpsStore.hydrateFromJob(it) }
        }
        return incoming.size
    }

    private suspend fun mergeCustomers(remote: List<RemoteCustomerDto>): Int {
        if (remote.isEmpty()) return 0
        val deletedIds = deletedRecordDao.getIdsByType(DeletedRecord.TYPE_CUSTOMER).toSet()
        val localById = customerDao.getAllOnce().associateBy { it.id }
        val incoming = mutableListOf<Customer>()
        remote.forEach { dto ->
            if (dto.id in deletedIds) return@forEach
            val existing = localById[dto.id]
            if (existing != null && !existing.isSynced) return@forEach
            val mapped = runCatching { dto.toLocal(existing) }.getOrNull() ?: return@forEach
            incoming += mapped
        }
        if (incoming.isNotEmpty()) customerDao.insertAll(incoming)
        return incoming.size
    }
}
