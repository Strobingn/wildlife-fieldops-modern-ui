package com.strobingn.wildlifefieldops.pricing

import com.strobingn.wildlifefieldops.ai.fieldops.PhotoEvidence
import com.strobingn.wildlifefieldops.data.model.Photo
import java.io.File

/**
 * Copies inspection stills onto an estimate without taking them off the inspection.
 *
 * [removedIds] are photos Sir took off this estimate. Opening the estimate again
 * does not put those back. Adding the same id by hand clears that removal.
 */
data class EstimatePhotoAttachment(
    val attachedIds: List<String>,
    val removedIds: List<String>
)

object EstimateInspectionPhotos {
    fun inspectionIds(jobInspectionIds: List<String>, extraInspectionId: String = ""): List<String> =
        buildList {
            val extra = extraInspectionId.trim()
            if (extra.isNotEmpty()) add(extra)
            jobInspectionIds.forEach { id ->
                val trimmed = id.trim()
                if (trimmed.isNotEmpty()) add(trimmed)
            }
        }.distinct()

    /** Photos whose inspection id is in [inspectionIds], oldest first. */
    fun idsBelongingToInspections(inspectionIds: Collection<String>, photos: List<Photo>): List<String> {
        val wanted = inspectionIds.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (wanted.isEmpty()) return emptyList()
        return photos
            .filter { it.inspectionId?.trim().orEmpty() in wanted }
            .sortedBy { it.takenAt }
            .map { it.id }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun merge(attachedIds: List<String>, removedIds: List<String>, incomingIds: List<String>): EstimatePhotoAttachment {
        val removed = removedIds.filter { it.isNotBlank() }.distinct()
        val blocked = removed.toSet()
        val kept = attachedIds.filter { it.isNotBlank() && it !in blocked }.distinct()
        val have = kept.toSet()
        val added = incomingIds.filter { it.isNotBlank() && it !in blocked && it !in have }
        return EstimatePhotoAttachment(attachedIds = kept + added, removedIds = removed)
    }

    fun remove(attachedIds: List<String>, removedIds: List<String>, photoId: String): EstimatePhotoAttachment {
        val id = photoId.trim()
        if (id.isEmpty()) return EstimatePhotoAttachment(attachedIds, removedIds.filter { it.isNotBlank() }.distinct())
        return EstimatePhotoAttachment(
            attachedIds = attachedIds.filterNot { it == id },
            removedIds = (removedIds + id).filter { it.isNotBlank() }.distinct()
        )
    }

    fun add(attachedIds: List<String>, removedIds: List<String>, photoId: String): EstimatePhotoAttachment {
        val id = photoId.trim()
        if (id.isEmpty()) return EstimatePhotoAttachment(attachedIds, removedIds)
        return EstimatePhotoAttachment(
            attachedIds = attachedIds.filterNot { it == id } + id,
            removedIds = removedIds.filterNot { it == id }
        )
    }

    fun ordered(attachedIds: List<String>, photos: List<Photo>): List<Photo> {
        val byId = photos.associateBy { it.id }
        return attachedIds.mapNotNull { byId[it] }
    }

    /** Local files the estimate PDF can embed. Derived previews stay off the customer copy. */
    fun printablePaths(photos: List<Photo>): List<String> =
        photos.mapNotNull { photo ->
            val path = PhotoEvidence.evidentiarySourcePath(photo) ?: return@mapNotNull null
            path.takeIf { File(it).isFile }
        }
}
