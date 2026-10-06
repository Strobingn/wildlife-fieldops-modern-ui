package com.strobingn.wildlifefieldops.ui.viewmodel

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Debounced preference writes. [flushBlocking] stores the latest keystroke
 * immediately so leaving the screen does not drop the last edit.
 */
class PendingStringWrites(
    private val scope: CoroutineScope,
    private val delayMs: Long = 350L,
    private val persist: suspend (Map<String, String>) -> Unit
) {
    private val pending = LinkedHashMap<String, String>()
    private var job: Job? = null
    private var generation = 0
    private val lock = Any()

    fun schedule(key: String, value: String) {
        val gen = synchronized(lock) {
            pending[key] = value
            job?.cancel()
            generation += 1
            generation
        }
        job = scope.launch(Dispatchers.IO) {
            delay(delayMs)
            val batch = synchronized(lock) {
                if (generation != gen) {
                    emptyMap()
                } else {
                    pending.toMap().also {
                        pending.clear()
                        job = null
                    }
                }
            }
            if (batch.isNotEmpty()) persist(batch)
        }
    }

    fun flushBlocking() {
        val batch = synchronized(lock) {
            generation += 1
            job?.cancel()
            job = null
            pending.toMap().also { pending.clear() }
        }
        if (batch.isEmpty()) return
        runBlocking(Dispatchers.IO) { persist(batch) }
    }

    fun hasPending(): Boolean = synchronized(lock) { pending.isNotEmpty() }
}
