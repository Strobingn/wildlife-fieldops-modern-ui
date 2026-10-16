package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.observation.ObservationPhotoPaths
import com.strobingn.wildlifefieldops.data.observation.ObservationPhotoUploader
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

data class JobPhotoUpload(
    val storagePath: String,
    val publicUrl: String
)

/**
 * Uploads Live Capture / job-gallery stills to the public `job-photos` bucket.
 * Local files are never deleted. Paths are deterministic so retries upsert.
 */
@Singleton
class JobPhotoUploader @Inject constructor(
    private val observationPhotoUploader: ObservationPhotoUploader
) {
    suspend fun upload(client: SupabaseClient, photo: Photo): JobPhotoUpload {
        val sources = JobPhotoPaths.sourceCandidates(photo.localPath, photo.filePath)
        val local = sources.firstOrNull().orEmpty()
        val bytes = observationPhotoUploader.readFirstAvailable(sources)
            ?: error("Local job photo missing: $local")
        require(bytes.isNotEmpty()) { "Local job photo is empty: $local" }
        require(bytes.size <= JobPhotoPaths.MAX_BYTES) {
            "Job photo exceeds 50MB: $local"
        }
        val mime = ObservationPhotoPaths.mimeType(local)
        require(ObservationPhotoPaths.isAllowedMime(mime)) { "Unsupported photo type: $mime" }
        val path = JobPhotoPaths.objectPath(photo.jobId, photo.id, local)
        return uploadWithRetry(client, path, bytes)
    }

    private suspend fun uploadWithRetry(
        client: SupabaseClient,
        path: String,
        bytes: ByteArray,
        attempts: Int = 3
    ): JobPhotoUpload {
        var last: Throwable? = null
        repeat(attempts) { index ->
            try {
                val bucket = client.storage.from(JobPhotoPaths.BUCKET)
                bucket.upload(path, bytes, upsert = true)
                return JobPhotoUpload(
                    storagePath = path,
                    publicUrl = bucket.publicUrl(path)
                )
            } catch (t: Throwable) {
                last = t
                if (!SyncErrorFormatter.isTransient(t) || index == attempts - 1) throw t
                // Back off so a flaky link is not hit three times within a second.
                delay(RETRY_BACKOFF_MS * (index + 1))
            }
        }
        throw last ?: error("Job photo upload failed: $path")
    }

    private companion object {
        const val RETRY_BACKOFF_MS = 1_500L
    }
}
