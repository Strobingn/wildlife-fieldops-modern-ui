package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.data.model.JobStatus
import kotlinx.coroutines.CancellationException

/**
 * Photo upload queue decisions.
 *
 * "Job was cancelled" is the kotlinx.coroutines cancellation message
 * (`JobSupport.cancellationExceptionMessage`). Supabase/Ktor wraps the same
 * sentence as `HTTP request … failed with message: Job was cancelled`.
 * That is an interrupted upload, not [JobStatus.CANCELLED] (which syncs as
 * "Cancelled"). Storing it on the photo made the Jobs banner
 * `Photo <id prefix>: Job was cancelled` and kept a live job's backlog looking failed.
 */
object PhotoSyncQueue {
    const val KEPT_LOCAL_PREFIX = "kept-local:"

    /** Terminal skip copy. Local file and row stay; the photo leaves the upload queue. */
    const val KEPT_LOCAL_REASON =
        "kept-local: job cancelled or deleted. Photo stays on this phone."

    /** kotlinx.coroutines default, and the Ktor/Supabase wrapper around it. */
    private const val COROUTINE_CANCELLED = "job was cancelled"
    private const val HTTP_CANCELLED = "failed with message: job was cancelled"

    enum class Action {
        /** Upload to the job this photo is attached to. */
        UPLOAD,

        /** Wildlife job is cancelled or deleted. Keep the file, stop retrying. */
        SKIP_KEEP_LOCAL
    }

    fun action(jobStatus: JobStatus?, jobDeleted: Boolean): Action = when {
        jobDeleted -> Action.SKIP_KEEP_LOCAL
        jobStatus == JobStatus.CANCELLED -> Action.SKIP_KEEP_LOCAL
        else -> Action.UPLOAD
    }

    fun isKeptLocal(uploadError: String?): Boolean =
        uploadError?.startsWith(KEPT_LOCAL_PREFIX) == true

    /**
     * True when [message] is the coroutine/HTTP cancellation sentence, not a
     * domain status. A longer sync summary counts only when every listed
     * failure reason is that sentence.
     */
    fun isFalseCancellationMessage(message: String?): Boolean {
        val text = message?.trim().orEmpty()
        if (text.isEmpty()) return false
        val lower = text.lowercase()
        if (lower == COROUTINE_CANCELLED) return true
        if (lower.startsWith("$COROUTINE_CANCELLED ·") || lower.startsWith("$COROUTINE_CANCELLED.")) {
            return true
        }
        if (HTTP_CANCELLED in lower && !hasOtherFailure(lower)) return true
        val photoLine = Regex("^photo\\s+\\S+:\\s*(.+)$", RegexOption.IGNORE_CASE).find(text)
        if (photoLine != null) return isFalseCancellationMessage(photoLine.groupValues[1])
        val failedAt = text.indexOf("Failed (")
        if (failedAt >= 0) {
            val body = text.substring(failedAt).substringAfter(": ", missingDelimiterValue = "")
            if (body.isBlank()) return false
            val reasons = body.split("; ").map { it.substringAfter(" — ", it).trim() }
            return reasons.isNotEmpty() && reasons.all { isFalseCancellationMessage(it) }
        }
        if (lower.startsWith("sync failed:") && COROUTINE_CANCELLED in lower && ";" !in text) {
            return true
        }
        return false
    }

    fun isCoroutineCancellation(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { part ->
            part is CancellationException || isFalseCancellationMessage(part.message)
        }

    /** Thumbnail caption. False coroutine errors stay hidden so they are not a second banner. */
    fun galleryUploadLabel(uploadError: String?): String? = when {
        uploadError.isNullOrBlank() -> null
        isKeptLocal(uploadError) ->
            "Saved on this phone. Not uploaded because the job is cancelled or deleted."
        isFalseCancellationMessage(uploadError) -> null
        else -> "Sync failed: $uploadError"
    }

    private fun hasOtherFailure(lower: String): Boolean =
        "failed (" in lower && lower.substringAfter("failed (").contains(";")
}
