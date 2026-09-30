package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.data.observation.ObservationPhotoPaths

/**
 * Deterministic `job-photos` object keys. Retry-safe: the same photo id always
 * maps to the same storage path so upserts do not duplicate objects.
 */
object JobPhotoPaths {
    const val BUCKET = "job-photos"
    const val MAX_BYTES = 50L * 1024 * 1024

    fun objectPath(jobId: String?, photoId: String, localPath: String): String {
        val folder = jobId?.takeIf { it.isNotBlank() }?.let { ObservationPhotoPaths.sanitizeSegment(it) }
            ?: "unassigned"
        val name = ObservationPhotoPaths.sanitizeSegment(photoId.ifBlank { "photo" })
        return "$folder/$name.${ObservationPhotoPaths.extension(localPath)}"
    }
}
