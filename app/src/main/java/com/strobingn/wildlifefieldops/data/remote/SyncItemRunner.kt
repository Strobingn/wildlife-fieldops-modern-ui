package com.strobingn.wildlifefieldops.data.remote

import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

/**
 * Per-item sync outcome. [markSynced] runs only after [block] returns without
 * throwing (the caller treats that as HTTP 2xx / storage ACK). Failures keep
 * the local row unsynced and store a readable reason.
 *
 * Coroutine cancellation ("Job was cancelled") is not a row failure. If this
 * coroutine is still active, the item stays queued with its error cleared so
 * the next pass can upload it. If this coroutine was cancelled, the exception
 * propagates and nothing is written.
 */
class SyncItemRunner @Inject constructor() {
    private val format: (Throwable) -> String = SyncErrorFormatter::reason
    suspend fun <T> run(
        markSynced: suspend () -> Unit,
        markError: suspend (String?) -> Unit,
        block: suspend () -> T
    ): SyncItemOutcome<T> {
        return try {
            val value = block()
            markSynced()
            SyncItemOutcome.Ok(value)
        } catch (t: Throwable) {
            if (PhotoSyncQueue.isCoroutineCancellation(t)) {
                coroutineContext.ensureActive()
                markError(null)
                return SyncItemOutcome.Interrupted
            }
            val reason = format(t)
            markError(reason)
            SyncItemOutcome.Failed(reason)
        }
    }
}

sealed class SyncItemOutcome<out T> {
    data class Ok<T>(val value: T) : SyncItemOutcome<T>()
    data class Failed(val reason: String) : SyncItemOutcome<Nothing>()

    /** Upload was interrupted. Row stays pending and is not a sync failure. */
    data object Interrupted : SyncItemOutcome<Nothing>()
}

data class SyncItemFailure(
    val entityType: String,
    val id: String,
    val label: String,
    val reason: String
)
