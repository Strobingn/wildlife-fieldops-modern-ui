package com.strobingn.wildlifefieldops.sync.work

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSyncTriggerTest {

    @Test
    fun localWriteEnqueuesAfterDebounce() = runBlocking {
        var enqueues = 0
        val trigger = AutoSyncTrigger(
            scope = this,
            enqueue = { enqueues += 1 },
            debounceMs = 30L
        )
        trigger.notifyLocalChange()
        trigger.notifyLocalChange()
        assertEquals(0, enqueues)
        delay(80L)
        assertEquals(1, enqueues)
    }

    @Test
    fun disabledSkipsLocalWrite() = runBlocking {
        var enqueues = 0
        val trigger = AutoSyncTrigger(
            scope = this,
            enqueue = { enqueues += 1 },
            isEnabled = { false },
            debounceMs = 0L
        )
        trigger.notifyLocalChange()
        delay(20L)
        assertEquals(0, enqueues)
    }

    @Test
    fun authSessionReadyEnqueuesWithoutClearing() = runBlocking {
        var enqueues = 0
        val trigger = AutoSyncTrigger(
            scope = this,
            enqueue = { enqueues += 1 },
            debounceMs = 5_000L
        )
        trigger.onAuthSessionReady()
        delay(20L)
        assertEquals(1, enqueues)
    }

    @Test
    fun reconnectEnqueuesImmediately() = runBlocking {
        var enqueues = 0
        val trigger = AutoSyncTrigger(
            scope = this,
            enqueue = { enqueues += 1 },
            debounceMs = 5_000L
        )
        trigger.onConnectivityRestored()
        delay(20L)
        assertEquals(1, enqueues)
    }

    @Test
    fun pullWriteDoesNotScheduleAnotherSync() {
        assertFalse(SyncWriteGate.shouldEnqueueForInvalidation(syncWriteInProgress = true))
        assertTrue(SyncWriteGate.shouldEnqueueForInvalidation(syncWriteInProgress = false))
    }

    @Test
    fun syncWriteGateDrainsWhileStillMarked() = runBlocking {
        val events = mutableListOf<String>()
        lateinit var gate: SyncWriteGate
        gate = SyncWriteGate(drainInvalidations = {
            events += "drain:${gate.isSyncWrite()}"
        })
        gate.marking {
            events += "write:${gate.isSyncWrite()}"
        }
        events += "after:${gate.isSyncWrite()}"
        assertEquals(listOf("write:true", "drain:true", "after:false"), events)
    }

    @Test
    fun watchedTablesCoverJobsPhotosAndObservations() {
        assertEquals(
            setOf(
                "jobs",
                "customers",
                "inspections",
                "photos",
                "field_observations",
                "observation_events",
                "deleted_records"
            ),
            AutoSyncTrigger.WATCHED_TABLES.toSet()
        )
    }
}
