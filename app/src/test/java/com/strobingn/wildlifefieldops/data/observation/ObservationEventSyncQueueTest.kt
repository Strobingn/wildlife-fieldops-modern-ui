package com.strobingn.wildlifefieldops.data.observation

import com.strobingn.wildlifefieldops.data.local.ObservationEventRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservationEventSyncQueueTest {

    private fun record(
        eventId: String,
        entityId: String = "entity-a",
        synced: Boolean = false,
        observedAt: Long = 1_000L
    ) = ObservationEventRecord(
        eventId = eventId,
        entityId = entityId,
        observedAt = observedAt,
        uploadedAt = observedAt,
        deviceId = "dev",
        operatorId = "op",
        modelId = "wildlife_evidence_v1",
        modelHash = "hash",
        backendTag = "tflite",
        quantizerTag = "none",
        frameHash = "frame",
        cropHash = "crop",
        mediaUri = "/tmp/$eventId.jpg",
        labelDistributionJson = """{"raccoon":0.8}""",
        captureQuality = 0.7f,
        geometryTrust = 0.4f,
        humanVerification = "UNREVIEWED",
        supersedesEventId = null,
        isSynced = synced,
        syncedAt = if (synced) observedAt + 10 else null
    )

    @Test
    fun queuedForPushSkipsSyncedAndBlankIds() {
        val records = listOf(
            record("a", synced = true, observedAt = 1),
            record("", observedAt = 2),
            record("c", entityId = "", observedAt = 3),
            record("d", observedAt = 30),
            record("e", observedAt = 10)
        )
        val queued = ObservationEventSyncQueue.queuedForPush(records)
        assertEquals(listOf("e", "d"), queued.map { it.eventId })
    }

    @Test
    fun afterSuccessfulPushFlipsOnlyPushedIds() {
        val records = listOf(record("a"), record("b"), record("c", synced = true))
        val next = ObservationEventSyncQueue.afterSuccessfulPush(records, setOf("a"), syncedAt = 99L)
        assertTrue(next.first { it.eventId == "a" }.isSynced)
        assertEquals(99L, next.first { it.eventId == "a" }.syncedAt)
        assertFalse(next.first { it.eventId == "b" }.isSynced)
        assertTrue(next.first { it.eventId == "c" }.isSynced)
        assertEquals(1, ObservationEventSyncQueue.remainingUnsynced(next))
    }
}
