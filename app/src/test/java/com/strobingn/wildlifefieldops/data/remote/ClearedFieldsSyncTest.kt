package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sir's rule: a field the user clears stays blank through save, reload, and sync.
 * The upload must carry the cleared value as "", so the server row is cleared and
 * a later pull cannot restore the old text.
 */
class ClearedFieldsSyncTest {

    /** Same settings as the Supabase client's serializer (SupabaseClient.kt). */
    private val clientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** PostgREST upsert updates exactly the columns present in the payload. */
    private fun upsert(serverRow: JsonObject, payload: JsonObject): JsonObject =
        JsonObject(serverRow + payload)

    private val jobId = "22222222-3333-4444-5555-666666666666"

    private val oldServerJob = JsonObject(
        mapOf(
            "id" to JsonPrimitive(jobId),
            "customer_name" to JsonPrimitive("Willow Properties"),
            "customer" to JsonPrimitive("Willow Properties"),
            "title" to JsonPrimitive("Raccoon in attic"),
            "species" to JsonPrimitive("Raccoon"),
            "status" to JsonPrimitive("Scheduled"),
            "priority" to JsonPrimitive("Normal"),
            "address" to JsonPrimitive("210 Willow Ave, Cornwall, NY"),
            "town" to JsonPrimitive("Cornwall"),
            "state" to JsonPrimitive("NY"),
            "notes" to JsonPrimitive("AI: old gate code 1234"),
            "ai_notes" to JsonPrimitive("AI: old gate code 1234"),
            "scope" to JsonPrimitive("Old scope: seal the soffit")
        )
    )

    private fun clearedJob() = Job(
        id = jobId,
        title = "Raccoon in attic",
        customerName = "Willow Properties",
        address = "210 Willow Ave, Cornwall, NY",
        status = JobStatus.SCHEDULED,
        type = "Raccoon",
        state = "NY",
        notes = "",
        description = "",
        isSynced = false
    )

    @Test
    fun clearedJobNotesAndDescriptionAreSentAsEmptyAndNotRestoredByPull() {
        val local = clearedJob()
        listOf(clientJson, LiveSyncPayloads.json).forEach { json ->
            val payload = json.encodeToJsonElement(LiveJobUpsert.serializer(), LiveSyncPayloads.job(local)).jsonObject
            assertEquals("", payload.getValue("notes").jsonPrimitive.content)
            assertEquals("", payload.getValue("scope").jsonPrimitive.content)
            assertEquals("", payload.getValue("ai_notes").jsonPrimitive.content)

            val serverRow = upsert(oldServerJob, payload)
            assertEquals("", serverRow.getValue("notes").jsonPrimitive.content)
            assertEquals("", serverRow.getValue("scope").jsonPrimitive.content)

            val pulled = json.decodeFromJsonElement(RemoteJobDto.serializer(), serverRow)
            // Pull onto this phone (the local row is now synced) ...
            val merged = pulled.toLocal(existing = local.copy(isSynced = true))
            assertEquals("", merged.notes)
            assertEquals("", merged.description)
            // ... and onto a fresh install with no local copy.
            val fresh = pulled.toLocal(existing = null)
            assertEquals("", fresh.notes)
            assertEquals("", fresh.description)
            assertEquals("Raccoon in attic", fresh.title)
        }
    }

    @Test
    fun typedJobTextStillUploads() {
        val payload = clientJson.encodeToJsonElement(
            LiveJobUpsert.serializer(),
            LiveSyncPayloads.job(clearedJob().copy(notes = "Gate on the left", description = "Seal soffit"))
        ).jsonObject
        assertEquals("Gate on the left", payload.getValue("notes").jsonPrimitive.content)
        assertEquals("Seal soffit", payload.getValue("scope").jsonPrimitive.content)
        // Non-AI notes leave ai_notes alone, as before.
        assertEquals(false, "ai_notes" in payload.keys)
        assertEquals("Cornwall", payload.getValue("town").jsonPrimitive.content)
    }

    @Test
    fun clearedJobAddressClearsDerivedTown() {
        val payload = clientJson.encodeToJsonElement(
            LiveJobUpsert.serializer(),
            LiveSyncPayloads.job(clearedJob().copy(address = "", state = null))
        ).jsonObject
        assertEquals("", payload.getValue("address").jsonPrimitive.content)
        assertEquals("", payload.getValue("town").jsonPrimitive.content)
        assertEquals("", payload.getValue("state").jsonPrimitive.content)
        payload.keys.forEach { key -> assert(key in LiveSyncPayloads.JOB_WRITE_COLUMNS) { "unexpected column $key" } }
    }

    @Test
    fun clearedCustomerFieldsAreSentAsEmptyAndNotRestoredByPull() {
        val id = "33333333-4444-5555-6666-777777777777"
        val oldServerCustomer = JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "name" to JsonPrimitive("Pat Lee"),
                "phone" to JsonPrimitive("8455550100"),
                "email" to JsonPrimitive("pat@example.com"),
                "address" to JsonPrimitive("12 Oak St"),
                "town" to JsonPrimitive("Middletown"),
                "state" to JsonPrimitive("NY"),
                "zip" to JsonPrimitive("10940"),
                "notes" to JsonPrimitive("Old dog warning")
            )
        )
        val local = Customer(id = id, firstName = "Pat", lastName = "Lee", isSynced = false)
        listOf(clientJson, LiveSyncPayloads.json).forEach { json ->
            val payload = json.encodeToJsonElement(LiveCustomerUpsert.serializer(), LiveSyncPayloads.customer(local)).jsonObject
            listOf("phone", "email", "address", "town", "state", "zip", "notes").forEach { key ->
                assertEquals(key, "", payload.getValue(key).jsonPrimitive.content)
            }
            val pulled = json.decodeFromJsonElement(RemoteCustomerDto.serializer(), upsert(oldServerCustomer, payload))
            listOf(pulled.toLocal(existing = local.copy(isSynced = true)), pulled.toLocal(existing = null)).forEach { c ->
                assertEquals("", c.phone)
                assertEquals("", c.email)
                assertEquals("", c.address)
                assertEquals("", c.city)
                assertEquals("", c.state)
                assertEquals("", c.zipCode)
                assertEquals("", c.notes)
                assertEquals("Pat", c.firstName)
            }
        }
    }
}
