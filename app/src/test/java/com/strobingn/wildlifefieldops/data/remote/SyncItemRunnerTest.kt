package com.strobingn.wildlifefieldops.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

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
    fun coroutineCancellationIsNotStoredAndDoesNotAck() = runBlocking {
        var synced = false
        var error: String? = "stale"
        val thrown = runCatching {
            SyncItemRunner().run(
                markSynced = { synced = true },
                markError = { error = it }
            ) {
                throw CancellationException("Job was cancelled")
            }
        }
        assertFalse(synced)
        assertTrue(error == null)
        if (thrown.isFailure) {
            assertTrue(thrown.exceptionOrNull() is CancellationException)
        } else {
            assertTrue(thrown.getOrNull() is SyncItemOutcome.Interrupted)
        }
    }

    @Test
    fun httpJobWasCancelledLeavesThePhotoQueued() = runBlocking {
        var synced = false
        var error: String? = "Job was cancelled"
        val outcome = SyncItemRunner().run(
            markSynced = { synced = true },
            markError = { error = it }
        ) {
            throw RuntimeException(
                "HTTP request to https://example.supabase.co/storage/v1/object/job-photos/a.jpg " +
                    "failed with message: Job was cancelled"
            )
        }
        assertTrue(outcome is SyncItemOutcome.Interrupted)
        assertFalse(synced)
        assertNull(error)
    }

    @Test
    fun networkFailureStaysRetryableAndRecorded() = runBlocking {
        var synced = false
        var error: String? = null
        val outcome = SyncItemRunner().run(
            markSynced = { synced = true },
            markError = { error = it }
        ) {
            throw IOException("timeout talking to storage")
        }
        assertTrue(outcome is SyncItemOutcome.Failed)
        assertFalse(synced)
        assertTrue(error!!.contains("timeout", ignoreCase = true))
    }

    @Test
    fun duplicateKeyIsReadableAndDetectable() {
        val err = RuntimeException("duplicate key value violates unique constraint (23505)")
        assertTrue(SyncErrorFormatter.isDuplicate(err))
        assertTrue(SyncErrorFormatter.reason(err).contains("Already on server"))
    }
}
