package com.strobingn.wildlifefieldops.data.backup

import com.strobingn.wildlifefieldops.ai.fieldops.FieldDataExchange
import com.strobingn.wildlifefieldops.data.local.AppDatabase
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

    suspend fun importBundle(database: AppDatabase, incoming: FieldDataBundle): String {
        val jobs = FieldDataCodec.mergeJobs(
            database.jobDao().getAllOnce().map { it.toSnapshot() },
            incoming.jobs
        ).map { it.toJob() }
        val customers = FieldDataCodec.mergeCustomers(
            database.customerDao().getAllOnce().map { it.toSnapshot() },
            incoming.customers
        ).map { it.toCustomer() }
        val inspections = FieldDataCodec.mergeInspections(
            database.inspectionDao().getAllOnce().map { it.toSnapshot() },
            incoming.inspections
        ).map { it.toInspection() }
        val photos = FieldDataCodec.mergePhotos(
            database.photoDao().getAllOnce().map { it.toSnapshot() },
            incoming.photos
        ).map { it.toPhoto() }
        if (jobs.isNotEmpty()) database.jobDao().insertAll(jobs)
        if (customers.isNotEmpty()) database.customerDao().insertAll(customers)
        if (inspections.isNotEmpty()) database.inspectionDao().insertAll(inspections)
        if (photos.isNotEmpty()) database.photoDao().insertAll(photos)
        return "Imported ${jobs.size} jobs, ${customers.size} customers, ${inspections.size} inspections, ${photos.size} photos. Matching ids were kept as one row. Auto sync will push them."
    }
}
