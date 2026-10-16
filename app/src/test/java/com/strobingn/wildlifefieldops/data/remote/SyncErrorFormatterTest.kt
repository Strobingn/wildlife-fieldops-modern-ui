package com.strobingn.wildlifefieldops.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncErrorFormatterTest {

    @Test
    fun idContaining409IsNotADuplicate() {
        val err = RuntimeException("insert or update violates foreign key (job_id)=(0c8f2409-aaaa-4094-b409-409409409409)")
        assertFalse(SyncErrorFormatter.isDuplicate(err))
        assertFalse(SyncErrorFormatter.reason(err).startsWith("Already on server"))
    }

    @Test
    fun realConflictStatusIsADuplicate() {
        assertTrue(SyncErrorFormatter.isDuplicate(RuntimeException("HTTP 409 Conflict")))
        assertTrue(SyncErrorFormatter.isDuplicate(RuntimeException("{\"code\":\"23505\"}")))
    }

    @Test
    fun idContaining503IsNotTransient() {
        assertFalse(SyncErrorFormatter.isTransient(RuntimeException("bad row 7a503e10-0000-0000-0000-000000000000")))
        assertTrue(SyncErrorFormatter.isTransient(RuntimeException("Server error 503 Service Unavailable")))
        assertTrue(SyncErrorFormatter.isTransient(RuntimeException("Request timeout has expired")))
    }
}
