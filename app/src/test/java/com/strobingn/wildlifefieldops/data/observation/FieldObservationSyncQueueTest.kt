package com.strobingn.wildlifefieldops.data.observation

import com.strobingn.wildlifefieldops.data.model.FieldObservation
import com.strobingn.wildlifefieldops.data.remote.toRemoteDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

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

    @Test
    fun remoteDtoWritesStorageFieldsWithoutDroppingMetadata() {
        val row = obs("obs-1", observedAt = 1_700_000_000_000L)
        val dto = row.toRemoteDto(
            photoStoragePath = "field/obs-1/obs-1.jpg",
            photoPublicUrl = "https://example.supabase.co/storage/v1/object/public/observation-photos/field/obs-1/obs-1.jpg"
        )
        assertEquals("obs-1", dto.id)
        assertEquals("raccoon attic", dto.notes)
        assertEquals(41.5, dto.latitude)
        assertEquals(-74.0, dto.longitude)
        assertEquals("/tmp/obs-1.jpg", dto.photoPath)
        assertEquals("field/obs-1/obs-1.jpg", dto.photoStoragePath)
        assertTrue(dto.photoPublicUrl!!.contains("observation-photos"))
        assertEquals(Instant.ofEpochMilli(row.observedAt).toString(), dto.observedAt)
    }
}
