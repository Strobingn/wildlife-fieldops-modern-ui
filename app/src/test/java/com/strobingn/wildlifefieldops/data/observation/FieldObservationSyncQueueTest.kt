package com.strobingn.wildlifefieldops.data.observation

import com.strobingn.wildlifefieldops.data.model.FieldObservation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldObservationSyncQueueTest {

    private fun obs(
        id: String,
        synced: Boolean = false,
        lat: Double = 41.5,
        lng: Double = -74.0,
        observedAt: Long = 1_000L
    ) = FieldObservation(
        id = id,
        notes = "raccoon attic",
        latitude = lat,
        longitude = lng,
        photoLocalPath = "/tmp/$id.jpg",
        observedAt = observedAt,
        isSynced = synced
    )

    @Test
    fun queuedForPushSkipsSyncedAndInvalidCoords() {
        val records = listOf(
            obs("a", synced = true, observedAt = 1),
            obs("b", lat = 200.0, observedAt = 2),
            obs("c", observedAt = 30),
            obs("d", observedAt = 10)
        )
        val queued = FieldObservationSyncQueue.queuedForPush(records)
        assertEquals(listOf("d", "c"), queued.map { it.id })
    }

    @Test
    fun afterSuccessfulPushFlipsOnlyPushedIds() {
        val records = listOf(obs("a"), obs("b"), obs("c", synced = true))
        val next = FieldObservationSyncQueue.afterSuccessfulPush(records, setOf("a"))
        assertTrue(next.first { it.id == "a" }.isSynced)
        assertFalse(next.first { it.id == "b" }.isSynced)
        assertTrue(next.first { it.id == "c" }.isSynced)
        assertEquals(1, FieldObservationSyncQueue.remainingUnsynced(next))
    }

    @Test
    fun isMappableRejectsNaN() {
        assertFalse(
            FieldObservationSyncQueue.isMappable(
                obs("bad", lat = Double.NaN, lng = -74.0)
            )
        )
        assertTrue(FieldObservationSyncQueue.isMappable(obs("ok")))
    }
}
