package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.data.observation.ObservationPhotoPaths

/**
 * Deterministic `job-photos` object keys. Retry-safe: the same photo id always
 * maps to the same storage path so upserts do not duplicate objects.
 */
object JobPhotoPaths {
    const val BUCKET = "job-photos"
    const val MAX_BYTES = 50L * 1024 * 1024
    /** Live anon INSERT on job-photos requires first folder `public` and `.jpg`. */
    const val ANON_FOLDER = "public"

    /** localPath first, then filePath. Duplicates dropped. */
    fun sourceCandidates(localPath: String, filePath: String): List<String> =
        listOf(localPath, filePath).map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /**
     * Job a photo belongs to on the server. An inspection photo (category INSPECTION) is
     * saved with an inspectionId and often no jobId; when that inspection is linked to a job,
     * the photo follows it so it does not upload as "unassigned". The photo's own jobId wins.
     */
    fun resolveJobId(photoJobId: String?, inspectionJobId: String?): String? =
        photoJobId?.takeIf { it.isNotBlank() } ?: inspectionJobId?.takeIf { it.isNotBlank() }

    fun objectPath(jobId: String?, photoId: String, localPath: String): String {
        val folder = jobId?.takeIf { it.isNotBlank() }?.let { ObservationPhotoPaths.sanitizeSegment(it) }
            ?: "unassigned"
        val name = ObservationPhotoPaths.sanitizeSegment(photoId.ifBlank { "photo" })
        // Extension is always jpg: live policy is `storage.extension(name) = 'jpg'`.
        return "$ANON_FOLDER/$folder/$name.jpg"
    }
}
