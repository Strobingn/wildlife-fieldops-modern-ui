package com.strobingn.wildlifefieldops.sync.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncWorkCorrelationTest {

    @Test
    fun tagsAreBoundedAndPrefixed() {
        val operation = DomainSyncOperation(
            operationId = "op-123",
            idempotencyKey = "fieldops-sync",
            state = DomainSyncState.READY,
            createdAt = 1L,
            updatedAt = 1L
        )
        val tags = SyncWorkCorrelation.tagsFor(operation)
        assertTrue(tags.contains("fieldops-sync"))
        assertTrue(tags.contains("fo-sync"))
        assertTrue(tags.contains("fo-op:op-123"))
        assertTrue(tags.contains("fo-idk:fieldops-sync"))
        tags.forEach { tag ->
            assertTrue(tag.length <= SyncWorkCorrelation.MAX_TAG_LEN)
            assertTrue(SyncWorkCorrelation.isSafeTag(tag))
        }
    }

    @Test
    fun stripsUnsafeCharactersAndLength() {
        val messy = "op/" + "a".repeat(80) + " note with spaces!"
        val token = SyncWorkCorrelation.boundToken(messy)
        assertTrue(token.length <= SyncWorkCorrelation.MAX_TOKEN_LEN)
        assertFalse(token.contains("/"))
        assertFalse(token.contains(" "))
        assertTrue(token.startsWith("op-"))
    }

    @Test
    fun sanitizeDropsGpsNotesAndSpecies() {
        val cleaned = SyncWorkCorrelation.sanitizeTags(
            listOf(
                "fo-op:abc",
                "raccoon in attic",
                "41.5123,-74.0123",
                "photo:/sdcard/x.jpg",
                "Bearer secret-token",
                FieldOpsSyncWorkNames.UNIQUE_WORK_NAME
            )
        )
        assertEquals(setOf("fo-op:abc", FieldOpsSyncWorkNames.UNIQUE_WORK_NAME), cleaned)
    }

    @Test
    fun inputDataIsBoundedIdsOnly() {
        val operation = DomainSyncOperation(
            operationId = "op-9",
            idempotencyKey = "fieldops-sync",
            state = DomainSyncState.READY,
            createdAt = 1L,
            updatedAt = 1L
        )
        val input = SyncWorkCorrelation.inputFor(operation)
        assertEquals(setOf("operationId", "idempotencyKey"), input.keys)
        assertEquals("op-9", input["operationId"])
        assertEquals("fieldops-sync", input["idempotencyKey"])
    }
}
