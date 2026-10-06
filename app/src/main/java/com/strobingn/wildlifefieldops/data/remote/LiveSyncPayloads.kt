package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.FieldObservation
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.observation.ObservationPhotoPaths
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PricingCalculator
import com.strobingn.wildlifefieldops.pricing.PricingJson
import com.strobingn.wildlifefieldops.pricing.hasSyncPayload
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

/**
 * Push payloads that match **live** `wildlife_app` columns (dump 2026-09-30).
 *
 * Do not add keys that are missing live — PostgREST returns 400 `PGRST204` and the
 * whole batch used to fail silently. Latitude/longitude are JSON numbers so they
 * bind to both `double precision` and `text` columns.
 */
object LiveSyncPayloads {

    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = true
        isLenient = true
    }

    /** Columns the native client is allowed to write on `public.jobs`. */
    val JOB_WRITE_COLUMNS: Set<String> = setOf(
        "id",
        "customer_name",
        "customer",
        "title",
        "scope",
        "species",
        "status",
        "priority",
        "address",
        "town",
        "state",
        "zip",
        "estimate",
        "subtotal",
        "tax_rate",
        "tax_amount",
        "grand_total",
        "pricing",
        "notes",
        "ai_notes",
        "latitude",
        "longitude",
        "scheduled_start",
        "completed_at",
        "customer_id"
    )

    fun job(job: Job): LiveJobUpsert {
        val name = job.customerName.ifBlank { job.title.ifBlank { "Customer" } }
        val jobTitle = job.title.ifBlank { name }
        val speciesGuess = when {
            job.type.isNotBlank() && job.type.length <= 40 -> job.type
            job.description.isNotBlank() && job.description.length <= 60 -> job.description
            else -> "Wildlife"
        }
        val quote = PricingCalculator.compute(job.pricing)
        val empty = job.pricing.isEmptyWorksheet()
        return LiveJobUpsert(
            id = job.id.ifBlank { UUID.randomUUID().toString() },
            customerName = name,
            customer = name,
            title = jobTitle,
            species = speciesGuess,
            customerId = job.customerId.takeIf { it.isNotBlank() && SyncIds.isUuid(it) },
            address = job.address, // empty string satisfies NOT NULL without omitting the column
            // Cleared text goes up as "" so the server copy is cleared too. A null here is
            // omitted (encodeDefaults = false), which left the old server text in place and let
            // a later pull bring it back. Town is derived from the address, so it is cleared
            // only when the address is.
            town = inferTown(job.address, job.state) ?: "".takeIf { job.address.isBlank() },
            state = job.state.orEmpty().trim(),
            zip = null,
            status = job.status.toRemoteStatus(),
            priority = job.priority.toRemotePriority(),
            notes = job.notes,
            aiNotes = job.notes.takeIf { it.contains("AI:", ignoreCase = true) || it.contains("Live Capture") }
                ?: "".takeIf { job.notes.isBlank() },
            scope = job.description,
            latitude = job.latitude,
            longitude = job.longitude,
            estimate = job.estimatedValue.takeIf { it > 0.0 },
            subtotal = if (empty) null else quote.subtotal.effective,
            taxRate = if (empty) null else job.pricing.taxRatePercent,
            taxAmount = if (empty) null else quote.taxAmount.effective,
            grandTotal = job.actualCost.takeIf { it > 0.0 },
            pricing = job.pricing,
            scheduledStart = job.scheduledDate?.let { Instant.ofEpochMilli(it).toString() },
            completedAt = job.completedDate?.let { Instant.ofEpochMilli(it).toString() }
        )
    }

    fun customer(customer: Customer): LiveCustomerUpsert = LiveCustomerUpsert(
        id = customer.id.ifBlank { UUID.randomUUID().toString() },
        name = customer.fullName.trim().ifBlank { "Customer" },
        // Always sent; a cleared field goes up as "" so a pull cannot restore the old text.
        phone = customer.phone.trim(),
        email = customer.email.trim(),
        address = customer.address.trim(),
        town = customer.city.trim(),
        state = customer.state.trim(),
        zip = customer.zipCode.trim(),
        notes = customer.notes.trim()
    )

    fun inspection(inspection: Inspection): LiveInspectionUpsert {
        val findingsJson = buildJsonObject {
            put("text", inspection.findings)
            put("recommendations", inspection.recommendations)
            put("species", inspection.speciesIdentified)
            put("entry_points", inspection.entryPoints)
            put("severity", inspection.severity.name)
            put("customer", inspection.customerName)
            put("inspector", inspection.inspectorName)
            put("weather", inspection.weatherConditions)
            put("damage", inspection.damageAssessment)
            put("ai_narrative", inspection.aiNarrativeDraft)
            put("ai_narrative_source", inspection.aiDraftSource)
            val contact = com.strobingn.wildlifefieldops.data.inspection.InspectionContact.read(inspection.aiDraftSource)
            if (contact.phone.isNotBlank()) put("phone", contact.phone)
            if (contact.address.isNotBlank()) put("address", contact.address)
            if (contact.serviceType.isNotBlank()) put("service_type", contact.serviceType)
            val cleared = com.strobingn.wildlifefieldops.ai.fieldops.NarrativeCleared.cleared(inspection.aiDraftSource)
            if (cleared.isNotEmpty()) {
                put("manual_fields", cleared.sorted().joinToString(","))
            }
        }
        return LiveInspectionUpsert(
            id = inspection.id.ifBlank { UUID.randomUUID().toString() },
            jobId = inspection.jobId.takeIf { it.isNotBlank() && SyncIds.isUuid(it) },
            inspectionType = inspection.inspectionType.name,
            // Always sent; cleared notes go up as "" so the server copy is cleared too.
            notes = inspection.notes,
            findings = findingsJson
        )
    }

    fun fieldObservation(
        observation: FieldObservation,
        photoStoragePath: String? = null,
        photoPublicUrl: String? = null
    ): LiveFieldObservationUpsert = LiveFieldObservationUpsert(
        id = observation.id.ifBlank { UUID.randomUUID().toString() },
        notes = observation.notes,
        latitude = observation.latitude,
        longitude = observation.longitude,
        // Live field_observations (dump 2026-09-30) includes photo_path plus
        // photo_storage_path / photo_public_url.
        photoPath = photoPublicUrl?.takeIf { it.isNotBlank() }
            ?: photoStoragePath?.takeIf { it.isNotBlank() }
            ?: observation.photoLocalPath.takeIf { ObservationPhotoPaths.isRemoteUrl(it) },
        photoId = observation.photoId?.takeIf { it.isNotBlank() },
        jobId = observation.jobId?.takeIf { it.isNotBlank() && SyncIds.isUuid(it) },
        // Always sent; a cleared species ID goes up as "" so the server copy is cleared too.
        speciesHint = observation.speciesHint,
        accuracyMeters = observation.accuracyMeters?.toDouble(),
        observedAt = Instant.ofEpochMilli(observation.observedAt).toString(),
        photoStoragePath = photoStoragePath?.takeIf { it.isNotBlank() },
        photoPublicUrl = photoPublicUrl?.takeIf { it.isNotBlank() }
    )

    fun photo(
        photo: Photo,
        storagePath: String,
        publicUrl: String
    ): LivePhotoUpsert = LivePhotoUpsert(
        id = photo.id.ifBlank { UUID.randomUUID().toString() },
        jobId = photo.jobId?.takeIf { it.isNotBlank() && SyncIds.isUuid(it) },
        imageUrl = publicUrl,
        storagePath = storagePath,
        tag = photo.category.name.lowercase()
    )

    fun jobPhotoLink(
        photo: Photo,
        storagePath: String,
        publicUrl: String
    ): LiveJobPhotoUpsert = LiveJobPhotoUpsert(
        id = photo.id.ifBlank { UUID.randomUUID().toString() },
        jobId = photo.jobId?.takeIf { it.isNotBlank() && SyncIds.isUuid(it) },
        path = storagePath,
        publicUrl = publicUrl,
        tag = photo.category.name.lowercase(),
        notes = photo.description.takeIf { it.isNotBlank() }
    )

    fun encodedKeys(value: LiveJobUpsert): Set<String> {
        val element = json.encodeToJsonElement(LiveJobUpsert.serializer(), value)
        return (element as JsonObject).keys
    }

    /**
     * "12 Oak St, Middletown, NY" → Middletown. Used when Room has no town column.
     */
    fun inferTown(address: String, state: String?): String? {
        val parts = address.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        val withoutStateZip = parts.filter { part ->
            val head = part.substringBefore(' ')
            val looksLikeStateZip = head.length == 2 &&
                head.all { it.isLetter() } &&
                part.matches(Regex("[A-Za-z]{2}(\\s+\\d{5}(-\\d{4})?)?"))
            val matchesKnownState = state != null && head.equals(state, ignoreCase = true)
            !looksLikeStateZip && !matchesKnownState
        }
        return when {
            withoutStateZip.size >= 2 -> withoutStateZip.last()
            withoutStateZip.size == 1 && parts.size >= 2 -> withoutStateZip.first()
            else -> null
        }?.takeIf { it.length in 2..48 }
    }
}

@Serializable
data class LiveJobUpsert(
    val id: String,
    @SerialName("customer_name") val customerName: String,
    val customer: String,
    val title: String,
    val species: String,
    val status: String,
    val priority: String,
    val address: String,
    val town: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val scope: String? = null,
    val notes: String? = null,
    @SerialName("ai_notes") val aiNotes: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val estimate: Double? = null,
    @SerialName("subtotal") val subtotal: Double? = null,
    @SerialName("tax_rate") val taxRate: Double? = null,
    @SerialName("tax_amount") val taxAmount: Double? = null,
    @SerialName("grand_total") val grandTotal: Double? = null,
    @Serializable(with = FieldOpsPricingSerializer::class)
    val pricing: JobPricing,
    @SerialName("scheduled_start") val scheduledStart: String? = null,
    @SerialName("completed_at") val completedAt: String? = null
)

@Serializable
data class LiveCustomerUpsert(
    val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val town: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val notes: String? = null
)

@Serializable
data class LiveInspectionUpsert(
    val id: String,
    @SerialName("job_id") val jobId: String? = null,
    @SerialName("inspection_type") val inspectionType: String,
    val notes: String? = null,
    val findings: JsonObject = buildJsonObject { }
)

@Serializable
data class LiveFieldObservationUpsert(
    val id: String,
    // No default: with encodeDefaults = false a "" default was left out of the upload,
    // so cleared notes never reached the server.
    val notes: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("photo_path") val photoPath: String? = null,
    @SerialName("photo_id") val photoId: String? = null,
    @SerialName("job_id") val jobId: String? = null,
    @SerialName("species_hint") val speciesHint: String? = null,
    @SerialName("accuracy_meters") val accuracyMeters: Double? = null,
    @SerialName("observed_at") val observedAt: String? = null,
    @SerialName("photo_storage_path") val photoStoragePath: String? = null,
    @SerialName("photo_public_url") val photoPublicUrl: String? = null
)

@Serializable
data class LivePhotoUpsert(
    val id: String,
    @SerialName("job_id") val jobId: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("storage_path") val storagePath: String? = null,
    val tag: String? = null
)

@Serializable
data class LiveJobPhotoUpsert(
    val id: String,
    @SerialName("job_id") val jobId: String? = null,
    val path: String? = null,
    @SerialName("public_url") val publicUrl: String? = null,
    val tag: String? = null,
    val notes: String? = null
)

object SyncIds {
    fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value); true }.getOrDefault(false)
}

/**
 * Live upserts use encodeDefaults=false globally (omit unused PostgREST keys).
 * Field-ops extras must still send blank strings and [JobPricing.manualFields]
 * so a remote replace cannot resurrect an old AI value.
 */
object FieldOpsPricingSerializer : KSerializer<JobPricing> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("FieldOpsPricing")

    override fun serialize(encoder: Encoder, value: JobPricing) {
        val json = encoder as JsonEncoder
        val element = if (value.hasSyncPayload()) {
            PricingJson.json.encodeToJsonElement(JobPricing.serializer(), value)
        } else {
            JsonObject(emptyMap())
        }
        json.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): JobPricing {
        val json = decoder as JsonDecoder
        return PricingJson.decode(json.decodeJsonElement().toString())
    }
}
