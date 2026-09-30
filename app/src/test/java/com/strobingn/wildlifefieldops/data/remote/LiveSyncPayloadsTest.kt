package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobPriority
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveSyncPayloadsTest {

    private val middletown = Job(
        id = "11111111-1111-1111-1111-111111111111",
        title = "Middletown raccoon",
        description = "Attic exclusion after Live Capture",
        customerName = "Middletown raccoon",
        address = "12 Oak St, Middletown, NY",
        latitude = 41.4459,
        longitude = -74.4207,
        status = JobStatus.IN_PROGRESS,
        priority = JobPriority.HIGH,
        type = "Raccoon",
        notes = "AI: raccoon in chimney\n--- Repair scope (Live Capture) ---",
        estimatedValue = 450.0,
        actualCost = 0.0,
        assignedTo = "Pat",
        state = "NY",
        isSynced = false
    )

    @Test
    fun jobPayloadUsesLiveColumnNamesAndNumericCoordinates() {
        val payload = LiveSyncPayloads.job(middletown)
        val encoded = LiveSyncPayloads.json.encodeToJsonElement(LiveJobUpsert.serializer(), payload).jsonObject
        assertTrue(encoded.keys.containsAll(setOf("id", "customer_name", "customer", "title", "species", "status", "priority", "address")))
        assertFalse("phone" in encoded.keys)
        assertFalse("email" in encoded.keys)
        assertFalse("assigned_tech" in encoded.keys)
        assertFalse("organization_id" in encoded.keys)
        encoded.keys.forEach { key ->
            assertTrue("unexpected column $key", key in LiveSyncPayloads.JOB_WRITE_COLUMNS)
        }
        assertFalse(encoded.getValue("latitude").jsonPrimitive.isString)
        assertFalse(encoded.getValue("longitude").jsonPrimitive.isString)
        assertEquals(41.4459, encoded.getValue("latitude").jsonPrimitive.content.toDouble(), 1e-6)
        assertEquals(-74.4207, encoded.getValue("longitude").jsonPrimitive.content.toDouble(), 1e-6)
        assertEquals("12 Oak St, Middletown, NY", encoded.getValue("address").jsonPrimitive.content)
        assertEquals("NY", encoded.getValue("state").jsonPrimitive.content)
        assertTrue(encoded.getValue("ai_notes").jsonPrimitive.content.contains("Live Capture"))
    }

    @Test
    fun emptyAddressIsStillEncodedSoNotNullCloudColumnSucceeds() {
        val payload = LiveSyncPayloads.job(middletown.copy(address = ""))
        val encoded = LiveSyncPayloads.json.encodeToJsonElement(LiveJobUpsert.serializer(), payload).jsonObject
        assertTrue("address" in encoded.keys)
        assertEquals("", encoded.getValue("address").jsonPrimitive.content)
    }

    @Test
    fun flexibleStringSerializerIsWhyLegacyGpsJobsFailed() {
        val encoded = Json.encodeToJsonElement(FlexibleStringSerializer, "41.4459")
        assertTrue(encoded.jsonPrimitive.isString)
        assertEquals("41.4459", encoded.jsonPrimitive.content)
    }

    @Test
    fun photoPayloadLinksJobAndKeepsAiNotes() {
        val photo = Photo(
            id = "22222222-2222-2222-2222-222222222222",
            jobId = middletown.id,
            description = "AI: raccoon · src=on-device",
            category = PhotoCategory.EVIDENCE,
            localPath = "/data/user/0/app/files/live.jpg"
        )
        val dto = LiveSyncPayloads.photo(photo, "11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222.jpg", "https://example.supabase.co/storage/v1/object/public/job-photos/a.jpg")
        assertEquals(middletown.id, dto.jobId)
        assertEquals("evidence", dto.tag)
        assertTrue(dto.notes!!.contains("AI:"))
        assertTrue(dto.publicUrl!!.contains("job-photos"))
    }
}
