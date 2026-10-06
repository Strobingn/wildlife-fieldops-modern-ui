package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.data.model.FieldObservation
import com.strobingn.wildlifefieldops.data.model.Inspection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Inspections and map pins are push-only, so the server row is the copy other
 * devices and the web app see. A cleared field must reach it as "", not be left out.
 */
class ClearedInspectionObservationSyncTest {

    /** Same settings as the Supabase client's serializer (SupabaseClient.kt). */
    private val clientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private fun upsert(serverRow: JsonObject, payload: JsonObject): JsonObject = JsonObject(serverRow + payload)

    @Test
    fun clearedInspectionNotesAreSentAsEmptyAndClearTheServerRow() {
        val id = "44444444-5555-6666-7777-888888888888"
        val server = JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "inspection_type" to JsonPrimitive("INITIAL"),
                "notes" to JsonPrimitive("Old inspection notes")
            )
        )
        val local = Inspection(id = id, notes = "")
        listOf(clientJson, LiveSyncPayloads.json).forEach { json ->
            val payload = json.encodeToJsonElement(LiveInspectionUpsert.serializer(), LiveSyncPayloads.inspection(local)).jsonObject
            assertEquals("", payload.getValue("notes").jsonPrimitive.content)
            assertEquals("", upsert(server, payload).getValue("notes").jsonPrimitive.content)
        }
        val typed = clientJson.encodeToJsonElement(
            LiveInspectionUpsert.serializer(),
            LiveSyncPayloads.inspection(local.copy(notes = "Bat guano in soffit"))
        ).jsonObject
        assertEquals("Bat guano in soffit", typed.getValue("notes").jsonPrimitive.content)
    }

    @Test
    fun clearedMapPinNotesAndSpeciesAreSentAsEmptyAndClearTheServerRow() {
        val id = "55555555-6666-7777-8888-999999999999"
        val server = JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "notes" to JsonPrimitive("Old pin note"),
                "species_hint" to JsonPrimitive("raccoon")
            )
        )
        val local = FieldObservation(id = id, notes = "", latitude = 41.40, longitude = -74.03, speciesHint = "")
        listOf(clientJson, LiveSyncPayloads.json).forEach { json ->
            val payload = json.encodeToJsonElement(
                LiveFieldObservationUpsert.serializer(),
                LiveSyncPayloads.fieldObservation(local)
            ).jsonObject
            assertEquals("", payload.getValue("notes").jsonPrimitive.content)
            assertEquals("", payload.getValue("species_hint").jsonPrimitive.content)
            val row = upsert(server, payload)
            assertEquals("", row.getValue("notes").jsonPrimitive.content)
            assertEquals("", row.getValue("species_hint").jsonPrimitive.content)
            // Photo columns are untouched by this fix: no photo, no photo keys.
            assertEquals(false, "photo_path" in payload.keys)
            assertEquals(false, "photo_storage_path" in payload.keys)
        }
    }
}
