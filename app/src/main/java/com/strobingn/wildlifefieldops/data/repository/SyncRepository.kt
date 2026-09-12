package com.strobingn.wildlifefieldops.data.repository

import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.InspectionDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.remote.RemoteCustomerDto
import com.strobingn.wildlifefieldops.data.remote.RemoteInspectionDto
import com.strobingn.wildlifefieldops.data.remote.RemoteJobDto
import com.strobingn.wildlifefieldops.data.remote.SupabaseService
import com.strobingn.wildlifefieldops.data.remote.toLocal
import com.strobingn.wildlifefieldops.data.remote.toRemoteDto
import com.strobingn.wildlifefieldops.data.remote.toRemoteDtoOrNull
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
    val pulledJobs: Int = 0,
    val pulledCustomers: Int = 0
)

@Singleton
class SyncRepository @Inject constructor(
    private val supabaseService: SupabaseService,
    private val jobDao: JobDao,
    private val customerDao: CustomerDao,
    private val inspectionDao: InspectionDao
) {
    fun isCloudConfigured(): Boolean = supabaseService.isConfigured

    suspend fun syncAll(): SyncResult = withContext(Dispatchers.IO) {
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

    private suspend fun doSync(): SyncResult {
        val client = supabaseService.client
            ?: return SyncResult(
                success = false,
                message = "Cloud not configured. Rebuild the APK with Supabase secrets (VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY)."
            )

        var pushedJobs = 0
        var pushedCustomers = 0
        var pushedInspections = 0
        var pulledJobs = 0
        var pulledCustomers = 0
        val warnings = mutableListOf<String>()

        try {
            val unsyncedCustomers = customerDao.getUnsynced()
            if (unsyncedCustomers.isNotEmpty()) {
                val dtos = unsyncedCustomers.mapNotNull { c ->
                    runCatching { c.toRemoteDto() }
                        .onFailure { android.util.Log.w("SyncRepository", "Skip customer ${c.id}: ${it.message}") }
                        .getOrNull()
                }
                if (dtos.isNotEmpty()) {
                    client.from("customers").upsert(dtos)
                    unsyncedCustomers.forEach { customerDao.markSynced(it.id) }
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
                val dtos = unsyncedJobs.mapNotNull { j ->
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
                val dtos = mutableListOf<RemoteInspectionDto>()
                val okIds = mutableListOf<String>()
                unsyncedInspections.forEach { insp ->
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

        val base = "Synced. Pushed: $pushedJobs jobs, $pushedCustomers customers, $pushedInspections inspections. " +
            "Pulled: $pulledJobs jobs, $pulledCustomers customers."
        val message = if (warnings.isEmpty()) base else "$base Warnings: ${warnings.joinToString("; ")}"
        return SyncResult(
            success = true,
            message = message,
            pushedJobs = pushedJobs,
            pushedCustomers = pushedCustomers,
            pushedInspections = pushedInspections,
            pulledJobs = pulledJobs,
            pulledCustomers = pulledCustomers
        )
    }

    private suspend fun mergeJobs(remote: List<RemoteJobDto>): Int {
        if (remote.isEmpty()) return 0
        val localById = jobDao.getAllOnce().associateBy { it.id }
        val incoming = mutableListOf<Job>()
        remote.forEach { dto ->
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
        val localById = customerDao.getAllOnce().associateBy { it.id }
        val incoming = mutableListOf<Customer>()
        remote.forEach { dto ->
            val existing = localById[dto.id]
            if (existing != null && !existing.isSynced) return@forEach
            val mapped = runCatching { dto.toLocal(existing) }.getOrNull() ?: return@forEach
            incoming += mapped
        }
        if (incoming.isNotEmpty()) customerDao.insertAll(incoming)
        return incoming.size
    }
}
