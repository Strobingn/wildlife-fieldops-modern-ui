package com.strobingn.wildlifefieldops.ui.viewmodel

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingStringWritesTest {

    @Test
    fun flushWritesTheLastKeystrokeWithoutWaiting() = runBlocking {
        val saved = mutableListOf<Map<String, String>>()
        val writes = PendingStringWrites(this, delayMs = 60_000L) { saved += it }
        writes.schedule("phone", "845")
        writes.schedule("phone", "845-555-0142")
        writes.schedule("email", "a@b.co")
        assertTrue(saved.isEmpty())
        assertTrue(writes.hasPending())
        writes.flushBlocking()
        assertEquals(1, saved.size)
        assertEquals("845-555-0142", saved.first()["phone"])
        assertEquals("a@b.co", saved.first()["email"])
        assertTrue(!writes.hasPending())
    }

    @Test
    fun clearedValueIsFlushedAsBlank() = runBlocking {
        val saved = mutableListOf<Map<String, String>>()
        val writes = PendingStringWrites(this, delayMs = 60_000L) { saved += it }
        writes.schedule("name", "Wildlife Whisperer LLC")
        writes.schedule("name", "")
        writes.flushBlocking()
        assertEquals("", saved.first()["name"])
    }
}
