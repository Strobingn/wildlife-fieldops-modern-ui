package com.strobingn.wildlifefieldops.data.observation

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ObservationPhotoUpload(
    val storagePath: String,
    val publicUrl: String
)

/**
 * Uploads local observation stills to the public `observation-photos` bucket.
 * Failures are thrown to the caller; local files are never deleted here.
 */
@Singleton
class ObservationPhotoUploader @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun uploadFieldPhoto(
        client: SupabaseClient,
        observationId: String,
        localPath: String
    ): ObservationPhotoUpload {
        val bytes = readBytes(localPath)
            ?: error("Local observation photo missing: $localPath")
        require(bytes.isNotEmpty()) { "Local observation photo is empty: $localPath" }
        require(bytes.size <= ObservationPhotoPaths.MAX_BYTES) {
            "Observation photo exceeds 50MB: $localPath"
        }
        val mime = ObservationPhotoPaths.mimeType(localPath)
        require(ObservationPhotoPaths.isAllowedMime(mime)) { "Unsupported photo type: $mime" }
        val path = ObservationPhotoPaths.fieldObservationPath(observationId, localPath)
        return upload(client, path, bytes)
    }

    suspend fun uploadEventMedia(
        client: SupabaseClient,
        eventId: String,
        mediaUri: String
    ): ObservationPhotoUpload {
        val bytes = readBytes(mediaUri)
            ?: error("Local event media missing: $mediaUri")
        require(bytes.isNotEmpty()) { "Local event media is empty: $mediaUri" }
        require(bytes.size <= ObservationPhotoPaths.MAX_BYTES) {
            "Event media exceeds 50MB: $mediaUri"
        }
        val mime = ObservationPhotoPaths.mimeType(mediaUri)
        require(ObservationPhotoPaths.isAllowedMime(mime)) { "Unsupported media type: $mime" }
        val path = ObservationPhotoPaths.eventPath(eventId, mediaUri)
        return upload(client, path, bytes)
    }

    private suspend fun upload(
        client: SupabaseClient,
        path: String,
        bytes: ByteArray
    ): ObservationPhotoUpload {
        val bucket = client.storage.from(ObservationPhotoPaths.BUCKET)
        bucket.upload(path, bytes, upsert = true)
        return ObservationPhotoUpload(
            storagePath = path,
            publicUrl = bucket.publicUrl(path)
        )
    }

    fun readBytes(pathOrUri: String): ByteArray? = readFirstAvailable(listOf(pathOrUri))

    /** First readable candidate wins. Blank paths are skipped. */
    fun readFirstAvailable(candidates: Iterable<String>): ByteArray? {
        for (candidate in candidates) {
            val bytes = readOne(candidate) ?: continue
            if (bytes.isNotEmpty()) return bytes
        }
        return null
    }

    private fun readOne(pathOrUri: String): ByteArray? {
        val trimmed = pathOrUri.trim()
        if (trimmed.isEmpty() || ObservationPhotoPaths.isRemoteUrl(trimmed)) return null
        val files = buildList {
            ObservationPhotoPaths.filesystemPath(trimmed)?.let { add(it) }
            ObservationPhotoPaths.appFileCandidate(
                trimmed,
                context.filesDir.absolutePath,
                context.cacheDir.absolutePath
            )?.let { add(it) }
        }
        for (fs in files.distinct()) {
            val file = File(fs)
            if (file.isFile && file.length() > 0L) return file.readBytes()
        }
        return runCatching {
            val uri = Uri.parse(trimmed)
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
}
