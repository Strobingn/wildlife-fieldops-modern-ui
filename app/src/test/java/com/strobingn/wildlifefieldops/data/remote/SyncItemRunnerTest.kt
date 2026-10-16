package com.strobingn.wildlifefieldops.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncItemRunnerTest {

    @Test
    fun marksSyncedOnlyWhenBlockSucceeds() = runBlocking {
        var synced = false
        var error: String? = "stale"
        val runner = SyncItemRunner()
        val outcome = runner.run(
            markSynced = { synced = true; error = null },
            markError = { error = it }
        ) { "2xx" }
        assertTrue(outcome is SyncItemOutcome.Ok)
        assertTrue(synced)
        assertEquals(null, error)
    }

    @Test
    fun retainsItemAndStoresReasonWhenWriterThrows() = runBlocking {
        var synced = false
        var error: String? = null
        val runner = SyncItemRunner()
        val outcome = runner.run(
            markSynced = { synced = true },
            markError = { error = it }
        ) {
            error("Could not find the 'assigned_tech' column of 'jobs' in the schema cache")
        }
        assertTrue(outcome is SyncItemOutcome.Failed)
        assertFalse(synced)
        assertTrue(error!!.contains("column", ignoreCase = true))
        assertEquals(error, (outcome as SyncItemOutcome.Failed).reason)
    }

    @Test
    fun cancellationIsRethrownAndNotStoredAsRowError() = runBlocking {
        var error: String? = null
        val runner = SyncItemRunner()
        val thrown = try {
            runner.run(
                markSynced = { },
                markError = { error = it }
            ) {
                throw kotlinx.coroutines.CancellationException("worker stopped")
            }
            null
        } catch (c: kotlinx.coroutines.CancellationException) {
            c
        }
        assertTrue(thrown != null)
        assertEquals(null, error)
    }

    @Test
    fun duplicateKeyIsReadableAndDetectable() {
        val err = RuntimeException("duplicate key value violates unique constraint (23505)")
        assertTrue(SyncErrorFormatter.isDuplicate(err))
        assertTrue(SyncErrorFormatter.reason(err).contains("Already on server"))
    }
}
