package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.CustomerType
import com.strobingn.wildlifefieldops.data.model.FieldObservation
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobPriority
import com.strobingn.wildlifefieldops.data.model.JobStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

@Serializable
data class RemoteCustomerDto(
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
data class RemoteJobDto(
    val id: String,
    @SerialName("customer_name") val customerName: String = "",
    val customer: String? = null,
    val title: String = "",
    val species: String = "Wildlife",
    @SerialName("customer_id") val customerId: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val town: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val status: String = "Active",
    val priority: String? = "Normal",
    @SerialName("assigned_tech") val assignedTech: String? = null,
    val notes: String? = null,
    val scope: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val latitude: String? = null,
    @Serializable(with = FlexibleStringSerializer::class)
    val longitude: String? = null,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val estimate: Double? = 0.0,
    @SerialName("grand_total")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val grandTotal: Double? = 0.0,
    @SerialName("scheduled_start") val scheduledStart: String? = null,
    @SerialName("completed_at") val completedAt: String? = null
)

@Serializable
data class RemoteInspectionDto(
    val id: String,
    @SerialName("job_id") val jobId: String? = null,
    @SerialName("inspection_type") val inspectionType: String? = "ROUTINE",
    val notes: String? = null,
    val findings: JsonObject = buildJsonObject { },
    @SerialName("customer_name") val customerName: String? = null,
    val species: String? = null,
    val status: String? = null,
    val priority: String? = null
)

@Serializable
data class RemoteFieldObservationDto(
    val id: String,
    val notes: String = "",
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
data class RemoteObservationEventDto(
    @SerialName("event_id") val eventId: String,
    @SerialName("entity_id") val entityId: String,
    @SerialName("observed_at") val observedAt: Long,
    @SerialName("uploaded_at") val uploadedAt: Long,
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("operator_id") val operatorId: String = "",
    @SerialName("model_id") val modelId: String = "",
    @SerialName("model_hash") val modelHash: String = "",
    @SerialName("backend_tag") val backendTag: String = "",
    @SerialName("quantizer_tag") val quantizerTag: String = "",
    @SerialName("frame_hash") val frameHash: String = "",
    @SerialName("crop_hash") val cropHash: String = "",
    @SerialName("media_uri") val mediaUri: String? = null,
    @SerialName("media_storage_path") val mediaStoragePath: String? = null,
    @SerialName("label_distribution") val labelDistribution: JsonObject = buildJsonObject { },
    @SerialName("capture_quality") val captureQuality: Double = 0.0,
    @SerialName("geometry_trust") val geometryTrust: Double = 0.0,
    @SerialName("human_verification") val humanVerification: String = "UNREVIEWED",
    @SerialName("supersedes_event_id") val supersedesEventId: String? = null
)

@Serializable
data class AiEdgeRequest(
    val mode: String = "field_plan",
    val observation: String = "",
    val species: String = "",
    val businessContext: String = "Wildlife Whisperer LLC — native FieldOps Android app"
)

fun Customer.toRemoteDto(): RemoteCustomerDto = RemoteCustomerDto(
    id = id.ifBlank { UUID.randomUUID().toString() },
    name = fullName.trim().ifBlank { "Customer" },
    phone = phone.ifBlank { null },
    email = email.ifBlank { null },
    address = address.ifBlank { null },
    town = city.ifBlank { null },
    state = state.ifBlank { null },
    zip = zipCode.ifBlank { null },
    notes = notes.ifBlank { null }
)

fun RemoteCustomerDto.toLocal(existing: Customer? = null): Customer {
    val parts = name.trim().split(" ", limit = 2)
    return Customer(
        id = id,
        firstName = parts.getOrNull(0).orEmpty().ifBlank { existing?.firstName.orEmpty() },
        lastName = parts.getOrNull(1).orEmpty().ifBlank { existing?.lastName.orEmpty() },
        email = email.orEmpty().ifBlank { existing?.email.orEmpty() },
        phone = phone.orEmpty().ifBlank { existing?.phone.orEmpty() },
        address = address.orEmpty().ifBlank { existing?.address.orEmpty() },
        city = town.orEmpty().ifBlank { existing?.city.orEmpty() },
        state = state.orEmpty().ifBlank { existing?.state.orEmpty() },
        zipCode = zip.orEmpty().ifBlank { existing?.zipCode.orEmpty() },
        notes = notes.orEmpty().ifBlank { existing?.notes.orEmpty() },
        companyName = existing?.companyName.orEmpty(),
        alternatePhone = existing?.alternatePhone.orEmpty(),
        latitude = existing?.latitude,
        longitude = existing?.longitude,
        customerType = existing?.customerType ?: CustomerType.RESIDENTIAL,
        billingAddress = existing?.billingAddress.orEmpty(),
        billingContact = existing?.billingContact.orEmpty(),
        paymentTerms = existing?.paymentTerms ?: "Net 30",
        isActive = existing?.isActive ?: true,
        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        updatedAt = existing?.updatedAt ?: System.currentTimeMillis(),
        isSynced = true
    )
}

fun Job.toRemoteDto(): RemoteJobDto {
    val name = customerName.ifBlank { title.ifBlank { "Customer" } }
    val jobTitle = title.ifBlank { name }
    val speciesGuess = when {
        type.isNotBlank() && type.length <= 40 -> type
        description.isNotBlank() && description.length <= 60 -> description
        else -> "Wildlife"
    }
    return RemoteJobDto(
        id = id.ifBlank { UUID.randomUUID().toString() },
        customerName = name,
        customer = name,
        title = jobTitle,
        species = speciesGuess,
        customerId = customerId.takeIf { it.isNotBlank() && isUuid(it) },
        address = address.ifBlank { null },
        status = status.toRemoteStatus(),
        priority = priority.toRemotePriority(),
        assignedTech = assignedTo.ifBlank { null },
        notes = notes.ifBlank { null },
        scope = description.ifBlank { null },
        latitude = latitude?.toString(),
        longitude = longitude?.toString(),
        estimate = estimatedValue,
        grandTotal = actualCost,
        scheduledStart = scheduledDate?.let { Instant.ofEpochMilli(it).toString() },
        completedAt = completedDate?.let { Instant.ofEpochMilli(it).toString() }
    )
}

fun RemoteJobDto.toLocal(existing: Job? = null): Job {
    val displayCustomer = customerName.ifBlank { customer.orEmpty() }.ifBlank { existing?.customerName.orEmpty() }
    val mappedStatus = status.fromRemoteStatus()
    val status = when {
        existing == null -> mappedStatus
        existing.status == JobStatus.INVOICED || existing.status == JobStatus.PAID -> existing.status
        else -> mappedStatus
    }
    val mappedType = species.takeIf { it.isNotBlank() && !it.equals("Wildlife", ignoreCase = true) }
        ?: existing?.type
        ?: "Inspection"
    return Job(
        id = id,
        title = title.ifBlank { displayCustomer }.ifBlank { existing?.title.orEmpty() },
        description = scope.orEmpty().ifBlank { existing?.description.orEmpty() },
        customerId = customerId.orEmpty().ifBlank { existing?.customerId.orEmpty() },
        customerName = displayCustomer,
        address = address.orEmpty().ifBlank { existing?.address.orEmpty() },
        latitude = latitude?.toDoubleOrNull() ?: existing?.latitude,
        longitude = longitude?.toDoubleOrNull() ?: existing?.longitude,
        status = status,
        priority = existing?.priority ?: priority.fromRemotePriority(),
        type = if (existing != null && existing.type.isNotBlank()) existing.type else mappedType,
        estimatedValue = (estimate ?: 0.0).takeIf { it > 0 } ?: (existing?.estimatedValue ?: 0.0),
        actualCost = (grandTotal ?: 0.0).takeIf { it > 0 } ?: (existing?.actualCost ?: 0.0),
        assignedTo = assignedTech.orEmpty().ifBlank { existing?.assignedTo.orEmpty() },
        notes = notes.orEmpty().ifBlank { existing?.notes.orEmpty() },
        photos = existing?.photos ?: emptyList(),
        scheduledDate = scheduledStart?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: existing?.scheduledDate,
        completedDate = completedAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: existing?.completedDate,
        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
        updatedAt = existing?.updatedAt ?: System.currentTimeMillis(),
        isSynced = true
    )
}

fun FieldObservation.toRemoteDto(
    photoStoragePath: String? = null,
    photoPublicUrl: String? = null
): RemoteFieldObservationDto = RemoteFieldObservationDto(
    id = id.ifBlank { UUID.randomUUID().toString() },
    notes = notes,
    latitude = latitude,
    longitude = longitude,
    photoPath = photoLocalPath.takeIf { it.isNotBlank() },
    photoId = photoId?.takeIf { it.isNotBlank() },
    jobId = jobId?.takeIf { it.isNotBlank() && isUuid(it) },
    speciesHint = speciesHint.takeIf { it.isNotBlank() },
    accuracyMeters = accuracyMeters?.toDouble(),
    observedAt = Instant.ofEpochMilli(observedAt).toString(),
    photoStoragePath = photoStoragePath?.takeIf { it.isNotBlank() },
    photoPublicUrl = photoPublicUrl?.takeIf { it.isNotBlank() }
)

fun Inspection.toRemoteDtoOrNull(): RemoteInspectionDto {
    val findingsJson = buildJsonObject {
        put("text", findings)
        put("recommendations", recommendations)
        put("species", speciesIdentified)
        put("entry_points", entryPoints)
        put("severity", severity.name)
        put("customer", customerName)
        put("inspector", inspectorName)
        put("weather", weatherConditions)
        put("damage", damageAssessment)
    }
    return RemoteInspectionDto(
        id = id.ifBlank { UUID.randomUUID().toString() },
        jobId = jobId.takeIf { it.isNotBlank() && isUuid(it) },
        inspectionType = inspectionType.name,
        notes = notes.ifBlank { null },
        findings = findingsJson,
        customerName = customerName.ifBlank { null },
        species = speciesIdentified.ifBlank { null },
        status = if (followUpRequired) "follow_up" else "completed",
        priority = severity.name.lowercase()
    )
}

private fun isUuid(value: String): Boolean =
    runCatching { UUID.fromString(value); true }.getOrDefault(false)

private fun JobStatus.toRemoteStatus(): String = when (this) {
    JobStatus.PENDING -> "Active"
    JobStatus.IN_PROGRESS -> "In Progress"
    JobStatus.COMPLETED -> "Closed"
    JobStatus.CANCELLED -> "Cancelled"
    JobStatus.INVOICED -> "Closed"
    JobStatus.PAID -> "Closed"
}

private fun String?.fromRemoteStatus(): JobStatus = when (this?.lowercase()) {
    "active", "scheduled", "needs follow-up" -> JobStatus.PENDING
    "in progress" -> JobStatus.IN_PROGRESS
    "closed" -> JobStatus.COMPLETED
    "cancelled" -> JobStatus.CANCELLED
    else -> JobStatus.PENDING
}

private fun JobPriority.toRemotePriority(): String = when (this) {
    JobPriority.LOW -> "Low"
    JobPriority.MEDIUM -> "Normal"
    JobPriority.HIGH -> "High"
    JobPriority.URGENT -> "Critical"
}

private fun String?.fromRemotePriority(): JobPriority = when (this?.lowercase()) {
    "low" -> JobPriority.LOW
    "high" -> JobPriority.HIGH
    "critical" -> JobPriority.URGENT
    else -> JobPriority.MEDIUM
}
