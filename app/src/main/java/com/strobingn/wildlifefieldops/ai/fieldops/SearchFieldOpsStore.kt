package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.InspectionDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.PhotoDao
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.pricing.JobPricing
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchFieldOpsStore @Inject constructor(
    private val jobDao: JobDao,
    private val customerDao: CustomerDao,
    private val inspectionDao: InspectionDao,
    private val photoDao: PhotoDao
) {

    suspend fun search(query: String): List<SearchHit> = SmartSearch.search(
        query = query,
        jobs = jobDao.getAllOnce(),
        customers = customerDao.getAllOnce(),
        inspections = inspectionDao.getAllOnce(),
        photos = photoDao.getAllOnce()
    )

    suspend fun savePhotoTag(photo: Photo, tag: SyncedPhotoTag) {
        val jobId = photo.jobId?.takeIf { it.isNotBlank() }
        val previous = jobId?.let { id -> jobDao.getById(id)?.pricing?.photoAutoTags?.firstOrNull { it.photoId == photo.id } }
        val merged = PhotoAutoTags.persistTyped(
            photoId = photo.id,
            species = tag.species,
            damage = tag.damage,
            entry = tag.entry,
            extra = tag.extra,
            previous = previous?.copy(clearedKeys = previous.clearedKeys + tag.clearedKeys)
        )
        val description = applyTagsToDescription(photo.description, merged)
        photoDao.insert(
            photo.copy(
                description = description,
                takenAt = photo.takenAt
            )
        )
        if (jobId == null) return
        persist(jobId) { pricing ->
            pricing.copy(photoAutoTags = pricing.photoAutoTags.filterNot { it.photoId == photo.id } + merged)
        }
    }

    suspend fun savePair(jobId: String, pair: PhotoPairRecord) {
        persist(jobId) { it.copy(photoPairs = BeforeAfterPair.upsert(it.photoPairs, pair)) }
    }

    suspend fun deletePair(jobId: String, pairId: String) {
        persist(jobId) { it.copy(photoPairs = BeforeAfterPair.remove(it.photoPairs, pairId)) }
    }

    suspend fun applyChecklist(jobId: String, species: String) {
        persist(jobId) { it.copy(speciesChecklist = SpeciesChecklist.mergeMissing(it.speciesChecklist, species)) }
    }

    suspend fun saveChecklist(jobId: String, items: List<ChecklistItemRecord>) {
        persist(jobId) { it.copy(speciesChecklist = items) }
    }

    suspend fun saveShareReport(jobId: String, path: String, token: String = ShareableReport.payload(jobId)) {
        persist(jobId) {
            it.copy(
                shareReportPath = path,
                shareReportToken = token,
                shareReportAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun tagFor(photoId: String, jobId: String?): SyncedPhotoTag? {
        val id = jobId?.takeIf { it.isNotBlank() } ?: return null
        return jobDao.getById(id)?.pricing?.photoAutoTags?.firstOrNull { it.photoId == photoId }
    }

    private suspend fun persist(jobId: String, update: (JobPricing) -> JobPricing) {
        val job = jobDao.getById(jobId) ?: return
        jobDao.insert(
            JobFieldOpsCodec.mergeForSave(
                job.copy(pricing = update(job.pricing), updatedAt = System.currentTimeMillis(), isSynced = false)
            )
        )
    }

    companion object {
        fun applyTagsToDescription(current: String, tag: SyncedPhotoTag): String {
            val tagLine = tag.asDescription()
            val without = current.replace(Regex("(?m)^Tags:.*$"), "").trim()
            return if (tagLine.isBlank()) without else listOf(without, "Tags: $tagLine").filter { it.isNotBlank() }.joinToString("\n")
        }
    }
}
