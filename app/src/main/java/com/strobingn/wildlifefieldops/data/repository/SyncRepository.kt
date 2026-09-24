package com.strobingn.wildlifefieldops.data.repository

import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.DeletedRecordDao
import com.strobingn.wildlifefieldops.data.local.FieldObservationDao
import com.strobingn.wildlifefieldops.data.local.InspectionDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.ObservationEventDao
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.DeletedRecord
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.observation.FieldObservationSyncQueue
import com.strobingn.wildlifefieldops.data.observation.ObservationEventMapper
import com.strobingn.wildlifefieldops.data.observation.ObservationEventSyncQueue
import com.strobingn.wildlifefieldops.data.observation.ObservationPhotoPaths
import com.strobingn.wildlifefieldops.data.observation.ObservationPhotoUploader
import com.strobingn.wildlifefieldops.data.remote.RemoteCustomerDto
import com.strobingn.wildlifefieldops.data.remote.RemoteInspectionDto
import com.strobingn.wildlifefieldops.data.remote.RemoteJobDto
import com.strobingn.wildlifefieldops.data.remote.RemoteObservationEventDto
import com.strobingn.wildlifefieldops.data.remote.SupabaseService
import com.strobingn.wildlifefieldops.data.remote.toLocal
import com.strobingn.wildlifefieldops.data.remote.toRemoteDto
import com.strobingn.wildlifefieldops.data.remote.toRemoteDtoOrNull
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
    val pulledCustomers: Int = 0
)

@Singleton
class SyncRepository @Inject constructor(
    private val supabaseService: SupabaseService,
    private val jobDao: JobDao,
    private val customerDao: CustomerDao,
    private val inspectionDao: InspectionDao,
    private val fieldObservationDao: FieldObservationDao,
    private val observationEventDao: ObservationEventDao,
    private val observationPhotoUploader: ObservationPhotoUploader,
    private val deletedRecordDao: DeletedRecordDao
) : FieldOpsSyncGateway {
    override fun isCloudConfigured(): Boolean = supabaseService.isConfigured

    override suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
        try {
            doSync()
        } catch (t: Throwable) {
            android.util.Log.e("SyncRepository", "Sync crashed", t)
            SyncResult(
                success = false,
                message = "Sync failed: ${t.message ?: t.javaClass.simpleName}. Check connection and Supabase config."
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
        val warnings = mutableListOf<String>()

        // Push remote DELETEs for local tombstones before pull, so resurrected rows
        // are removed server-side first when possible.
        pushDeletions(client, DeletedRecord.TYPE_JOB, "jobs", warnings)
        pushDeletions(client, DeletedRecord.TYPE_CUSTOMER, "customers", warnings)
        pushDeletions(client, DeletedRecord.TYPE_INSPECTION, "inspections", warnings)

        try {
            val unsyncedCustomers = customerDao.getUnsynced()
            if (unsyncedCustomers.isNotEmpty()) {
                val deletedCustomerIds = deletedRecordDao.getIdsByType(DeletedRecord.TYPE_CUSTOMER).toSet()
                val dtos = unsyncedCustomers.mapNotNull { c ->
                    if (c.id in deletedCustomerIds) return@mapNotNull null
                    runCatching { c.toRemoteDto() }
                        .onFailure { android.util.Log.w("SyncRepository", "Skip customer ${c.id}: ${it.message}") }
                        .getOrNull()
                }
                if (dtos.isNotEmpty()) {
                    client.from("customers").upsert(dtos)
                    unsyncedCustomers.filter { it.id !in deletedCustomerIds }.forEach { customerDao.markSynced(it.id) }
                    pushedCustomers = dtos.size
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "Customer push failed", e)
            warnings += "customer push: ${e.message ?: e.javaClass.simpleName}"
        }

        try {
            val unsyncedJobs = jobDao.getUnsynced()
            if (unsyncedJobs.isNotEmpty()) {
                val deletedJobIds = deletedRecordDao.getIdsByType(DeletedRecord.TYPE_JOB).toSet()
                val dtos = unsyncedJobs.mapNotNull { j ->
                    if (j.id in deletedJobIds) return@mapNotNull null
                    runCatching { j.toRemoteDto() }
                        .onFailure { android.util.Log.w("SyncRepository", "Skip job ${j.id}: ${it.message}") }
                        .getOrNull()
                }
                if (dtos.isNotEmpty()) {
                    client.from("jobs").upsert(dtos)
                    val okIds = dtos.map { it.id }.toSet()
                    unsyncedJobs.filter { it.id in okIds }.forEach { jobDao.markSynced(it.id) }
                    pushedJobs = dtos.size
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "Job push failed", e)
            warnings += "job push: ${e.message ?: e.javaClass.simpleName}"
        }

        try {
            val unsyncedInspections = inspectionDao.getUnsynced()
            if (unsyncedInspections.isNotEmpty()) {
                val deletedInspectionIds = deletedRecordDao.getIdsByType(DeletedRecord.TYPE_INSPECTION).toSet()
                val dtos = mutableListOf<RemoteInspectionDto>()
                val okIds = mutableListOf<String>()
                unsyncedInspections.forEach { insp ->
                    if (insp.id in deletedInspectionIds) return@forEach
                    runCatching {
                        dtos += insp.toRemoteDtoOrNull()
                        okIds += insp.id
                    }.onFailure {
                        android.util.Log.w("SyncRepository", "Skip inspection ${insp.id}: ${it.message}")
                    }
                }
                if (dtos.isNotEmpty()) {
                    client.from("inspections").upsert(dtos)
                    okIds.forEach { inspectionDao.markSynced(it) }
                    pushedInspections = dtos.size
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SyncRepository", "Inspection push skipped", e)
            warnings += "inspection push: ${e.message ?: e.javaClass.simpleName}"
        }

        try {
            val remoteCustomers = client.from("customers").select().decodeList<RemoteCustomerDto>()
            pulledCustomers = mergeCustomers(remoteCustomers)
        } catch (e: Exception) {
            android.util.Log.w("SyncRepository", "Customer pull failed", e)
            warnings += "customer pull: ${e.message ?: e.javaClass.simpleName}"
        }

        try {
            val remoteJobs = client.from("jobs").select().decodeList<RemoteJobDto>()
            pulledJobs = mergeJobs(remoteJobs)
        } catch (e: Exception) {
            android.util.Log.w("SyncRepository", "Job pull failed", e)
            warnings += "job pull: ${e.message ?: e.javaClass.simpleName}"
        }

        try {
            val unsynced = FieldObservationSyncQueue.queuedForPush(fieldObservationDao.getUnsynced())
            unsynced.forEach { observation ->
                try {
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
                        warnings += "observation ${observation.id} photo missing locally"
                    }
                    client.from("field_observations").upsert(
                        observation.toRemoteDto(
                            photoStoragePath = storagePath,
                            photoPublicUrl = publicUrl
                        )
                    )
                    fieldObservationDao.markSynced(observation.id)
                    pushedObservations += 1
                } catch (e: Exception) {
                    android.util.Log.w(
                        "SyncRepository",
                        "Field observation ${observation.id} push failed; local photo kept",
                        e
                    )
                    warnings += "observation ${observation.id}: ${e.message ?: e.javaClass.simpleName}"
                    runCatching {
                        fieldObservationDao.markSyncError(
                            observation.id,
                            e.message ?: e.javaClass.simpleName
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SyncRepository", "Field observation push skipped", e)
            warnings += "observation push: ${e.message ?: e.javaClass.simpleName}"
        }

        try {
            val unsyncedEvents = ObservationEventSyncQueue.queuedForPush(observationEventDao.getUnsynced())
            val now = System.currentTimeMillis()
            unsyncedEvents.forEach { record ->
                try {
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
                        warnings += "event ${record.eventId} media missing locally"
                    }
                    val dto = ObservationEventMapper.toRemoteDto(record, mediaStoragePath = storagePath)
                    insertObservationEventIgnoreDuplicate(client, dto)
                    observationEventDao.markSynced(record.eventId, now)
                    pushedEvents += 1
                } catch (e: Exception) {
                    android.util.Log.w(
                        "SyncRepository",
                        "ObservationEvent ${record.eventId} push failed; local evidence kept",
                        e
                    )
                    warnings += "event ${record.eventId}: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SyncRepository", "ObservationEvent push skipped", e)
            warnings += "event push: ${e.message ?: e.javaClass.simpleName}"
        }

        val base = "Synced. Pushed: $pushedJobs jobs, $pushedCustomers customers, $pushedInspections inspections, " +
            "$pushedObservations observations, $pushedEvents events, $uploadedPhotos photos. " +
            "Pulled: $pulledJobs jobs, $pulledCustomers customers."
        val message = if (warnings.isEmpty()) base else "$base Warnings: ${warnings.joinToString("; ")}"
        return SyncResult(
            success = true,
            message = message,
            pushedJobs = pushedJobs,
            pushedCustomers = pushedCustomers,
            pushedInspections = pushedInspections,
            pushedObservations = pushedObservations,
            pushedEvents = pushedEvents,
            uploadedPhotos = uploadedPhotos,
            pulledJobs = pulledJobs,
            pulledCustomers = pulledCustomers
        )
    }

    private suspend fun insertObservationEventIgnoreDuplicate(
        client: SupabaseClient,
        dto: RemoteObservationEventDto
    ) {
        try {
            client.from("observation_events").insert(dto)
        } catch (e: Exception) {
            if (isDuplicateEvent(e)) {
                android.util.Log.i(
                    "SyncRepository",
                    "ObservationEvent ${dto.eventId} already on server; treating as synced"
                )
            } else {
                throw e
            }
        }
    }

    private fun isDuplicateEvent(error: Throwable): Boolean {
        val text = buildString {
            generateSequence(error) { it.cause }.forEach { append(it.message.orEmpty()).append(' ') }
        }.lowercase()
        return "duplicate" in text ||
            "already exists" in text ||
            "23505" in text ||
            "409" in text
    }

    private suspend fun pushDeletions(
        client: SupabaseClient,
        entityType: String,
        table: String,
        warnings: MutableList<String>
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
                    android.util.Log.w(
                        "SyncRepository",
                        "Remote delete failed for $entityType/${tombstone.id}",
                        e
                    )
                    warnings += "$entityType delete ${tombstone.id}: ${e.message ?: e.javaClass.simpleName}"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SyncRepository", "Deletion push failed for $entityType", e)
            warnings += "$entityType deletions: ${e.message ?: e.javaClass.simpleName}"
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
        if (incoming.isNotEmpty()) jobDao.insertAll(incoming)
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
