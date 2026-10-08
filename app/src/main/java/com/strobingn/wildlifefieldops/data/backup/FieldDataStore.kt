package com.strobingn.wildlifefieldops.data.backup

import androidx.room.withTransaction
import com.strobingn.wildlifefieldops.ai.fieldops.FieldDataExchange
import com.strobingn.wildlifefieldops.data.local.AppDatabase
import com.strobingn.wildlifefieldops.data.model.DeletedRecord
import java.time.Instant

object FieldDataStore {
    suspend fun exportBundle(database: AppDatabase, settings: Map<String, String>): FieldDataBundle {
        return FieldDataBundle(
            format = FieldDataExchange.FORMAT,
            exportedAt = Instant.now().toString(),
            jobs = database.jobDao().getAllOnce().map { it.toSnapshot() },
            customers = database.customerDao().getAllOnce().map { it.toSnapshot() },
            inspections = database.inspectionDao().getAllOnce().map { it.toSnapshot() },
            photos = database.photoDao().getAllOnce().map { it.toSnapshot() },
            settings = settings
        )
    }

    /**
     * Merges [bundle] into the local database in one transaction (all or nothing).
     * Rows the user deleted on this phone stay deleted, and only rows that won the merge
     * from the file are written, so untouched local rows are never rewritten from a
     * lossy snapshot.
     */
    suspend fun importBundle(database: AppDatabase, bundle: FieldDataBundle): String =
        database.withTransaction {
            val tombstones = database.deletedRecordDao()
            val incoming = FieldDataCodec.dropDeleted(
                incoming = bundle,
                deletedJobIds = tombstones.getIdsByType(DeletedRecord.TYPE_JOB).toSet(),
                deletedCustomerIds = tombstones.getIdsByType(DeletedRecord.TYPE_CUSTOMER).toSet(),
                deletedInspectionIds = tombstones.getIdsByType(DeletedRecord.TYPE_INSPECTION).toSet()
            )
            val localJobs = database.jobDao().getAllOnce().map { it.toSnapshot() }
            val jobs = FieldDataCodec.incomingWinners(
                localJobs,
                FieldDataCodec.mergeJobs(localJobs, incoming.jobs)
            ).map { it.toJob() }
            val localCustomers = database.customerDao().getAllOnce().map { it.toSnapshot() }
            val customers = FieldDataCodec.incomingWinners(
                localCustomers,
                FieldDataCodec.mergeCustomers(localCustomers, incoming.customers)
            ).map { it.toCustomer() }
            val localInspections = database.inspectionDao().getAllOnce().map { it.toSnapshot() }
            val inspections = FieldDataCodec.incomingWinners(
                localInspections,
                FieldDataCodec.mergeInspections(localInspections, incoming.inspections)
            ).map { it.toInspection() }
            val localPhotos = database.photoDao().getAllOnce().map { it.toSnapshot() }
            val photos = FieldDataCodec.incomingWinners(
                localPhotos,
                FieldDataCodec.mergePhotos(localPhotos, incoming.photos)
            ).map { it.toPhoto() }
            if (jobs.isNotEmpty()) database.jobDao().insertAll(jobs)
            if (customers.isNotEmpty()) database.customerDao().insertAll(customers)
            if (inspections.isNotEmpty()) database.inspectionDao().insertAll(inspections)
            if (photos.isNotEmpty()) database.photoDao().insertAll(photos)
            "Imported ${jobs.size} jobs, ${customers.size} customers, ${inspections.size} inspections, ${photos.size} photos. Matching ids were kept as one row. Auto sync will push them."
        }
}
