package com.strobingn.wildlifefieldops.sync.work

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debounced "something local changed, push when the network allows" trigger.
 * Android-free so enqueue-on-write and reconnect can be unit-tested.
 *
 * A missing auth session (PR #59, later) must leave [enqueue] responsible for
 * retrying — this class never clears Room queues.
 */
class AutoSyncTrigger(
    private val scope: CoroutineScope,
    private val enqueue: () -> Unit,
    private val isEnabled: () -> Boolean = { true },
    private val debounceMs: Long = 2_000L
) {
    private var debounceJob: Job? = null

    fun notifyLocalChange() {
        if (!isEnabled()) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            if (isEnabled()) enqueue()
        }
    }

    fun onConnectivityRestored() {
        if (!isEnabled()) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            enqueue()
        }
    }

    fun onAppForeground() {
        notifyLocalChange()
    }

    fun cancel() {
        debounceJob?.cancel()
        debounceJob = null
    }

    companion object {
        val WATCHED_TABLES = arrayOf(
            "jobs",
            "customers",
            "inspections",
            "photos",
            "field_observations",
            "observation_events",
            "deleted_records"
        )
    }
}

fun interface AutoSyncGate {
    fun isEnabled(): Boolean
}
