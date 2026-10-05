package com.strobingn.wildlifefieldops.sync.work

import java.util.concurrent.atomic.AtomicInteger

/**
 * Marks Room writes that came from a sync pass (pull merge, sync ACK, upload ACK).
 *
 * [androidx.room.InvalidationTracker] notifies observers asynchronously after the
 * transaction. [marking] drains those notifications before the depth drops so
 * AutoSync can ignore them. A genuine local edit is a separate write outside
 * [marking] and still schedules sync.
 */
class SyncWriteGate(
    private val drainInvalidations: () -> Unit = {}
) {
    private val depth = AtomicInteger(0)

    fun isSyncWrite(): Boolean = depth.get() > 0

    suspend fun <T> marking(block: suspend () -> T): T {
        depth.incrementAndGet()
        try {
            return block()
        } finally {
            // Observers run inside drain, while depth is still > 0.
            runCatching { drainInvalidations() }
            depth.decrementAndGet()
        }
    }

    companion object {
        /** Pull/ACK invalidations must not enqueue another sync. */
        fun shouldEnqueueForInvalidation(syncWriteInProgress: Boolean): Boolean =
            !syncWriteInProgress
    }
}

/** Fired when a local edit landed during a sync pass and still needs a push. */
fun interface LocalEditSignal {
    fun notifyLocalChange()
}
