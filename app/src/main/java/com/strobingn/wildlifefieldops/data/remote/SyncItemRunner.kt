package com.strobingn.wildlifefieldops.data.remote

import javax.inject.Inject

/**
 * Per-item sync outcome. [markSynced] runs only after [block] returns without
 * throwing (the caller treats that as HTTP 2xx / storage ACK). Failures keep
 * the local row unsynced and store a readable reason.
 */
class SyncItemRunner @Inject constructor() {
    private val format: (Throwable) -> String = SyncErrorFormatter::reason
    suspend fun <T> run(
        markSynced: suspend () -> Unit,
        markError: suspend (String) -> Unit,
        block: suspend () -> T
    ): SyncItemOutcome<T> {
        return try {
            val value = block()
            markSynced()
            SyncItemOutcome.Ok(value)
        } catch (t: Throwable) {
            val reason = format(t)
            markError(reason)
            SyncItemOutcome.Failed(reason)
        }
    }
}

sealed class SyncItemOutcome<out T> {
    data class Ok<T>(val value: T) : SyncItemOutcome<T>()
    data class Failed(val reason: String) : SyncItemOutcome<Nothing>()
}

data class SyncItemFailure(
    val entityType: String,
    val id: String,
    val label: String,
    val reason: String
)
