package com.strobingn.wildlifefieldops.data.backup

import com.strobingn.wildlifefieldops.ai.fieldops.FieldDataExchange
import com.strobingn.wildlifefieldops.ai.fieldops.FieldRecord
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.CustomerType
import com.strobingn.wildlifefieldops.data.model.FindingSeverity
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.InspectionType
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobPriority
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PricingJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * JSON field-data file inside a zip. Import merges by id (later updatedAt wins;
 * a tie keeps the phone's row). Photo rows are metadata only.
 */
@Serializable
data class FieldDataBundle(
    val format: String = FieldDataExchange.FORMAT,
    val exportedAt: String = "",
    val jobs: List<JobSnapshot> = emptyList(),
    val customers: List<CustomerSnapshot> = emptyList(),
    val inspections: List<InspectionSnapshot> = emptyList(),
    val photos: List<PhotoSnapshot> = emptyList(),
    val settings: Map<String, String> = emptyMap()
)

@Serializable
data class JobSnapshot(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: String = JobStatus.PENDING.name,
    val priority: String = JobPriority.MEDIUM.name,
    val type: String = "",
    val estimatedValue: Double = 0.0,
    val actualCost: Double = 0.0,
    val assignedTo: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val scheduledDate: Long? = null,
    val completedDate: Long? = null,
    val notes: String = "",
    val photos: List<String> = emptyList(),
    val county: String? = null,
    val state: String? = null,
    val pricingJson: String = "{}",
    val confirmedSpecies: String = "",
    val legalNotes: String = "",
    val nextStep: String = "",
    val nextStepDueAt: Long? = null,
    val nextStepSource: String = "",
    val aiRuntime: String = "",
    val weatherTrapAdvice: String = "",
    val followUpKind: String = "",
    val followUpDueAt: Long? = null,
    val followUpNotes: String = ""
)

@Serializable
data class CustomerSnapshot(
    val id: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val companyName: String = "",
    val email: String = "",
    val phone: String = "",
    val alternatePhone: String = "",
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val customerType: String = CustomerType.RESIDENTIAL.name,
    val notes: String = "",
    val billingAddress: String = "",
    val billingContact: String = "",
    val paymentTerms: String = "Net 30",
    val isActive: Boolean = true,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

@Serializable
data class InspectionSnapshot(
    val id: String = "",
    val jobId: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val inspectorName: String = "",
    val inspectionType: String = InspectionType.ROUTINE.name,
    val inspectionDate: Long = 0L,
    val findings: String = "",
    val recommendations: String = "",
    val severity: String = FindingSeverity.NONE.name,
    val speciesIdentified: String = "",
    val entryPoints: String = "",
    val damageAssessment: String = "",
    val photos: List<String> = emptyList(),
    val followUpRequired: Boolean = false,
    val followUpDate: Long? = null,
    val notes: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val aiNarrativeDraft: String = "",
    val aiDraftSource: String = ""
)

@Serializable
data class PhotoSnapshot(
    val id: String = "",
    val filePath: String = "",
    val localPath: String = "",
    val remoteUrl: String = "",
    val thumbnailPath: String = "",
    val jobId: String? = null,
    val inspectionId: String? = null,
    val customerId: String? = null,
    val category: String = PhotoCategory.JOB_SITE.name,
    val description: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val takenAt: Long = 0L,
    val takenBy: String = "",
    val fileSize: Long = 0L,
    val createdAt: Long = 0L
)

object FieldDataCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun encode(bundle: FieldDataBundle): String = json.encodeToString(bundle)

    fun decode(raw: String): FieldDataBundle = json.decodeFromString(raw)

    fun zipBytes(bundle: FieldDataBundle): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("field-data.json"))
            zip.write(encode(bundle).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    fun readBytes(bytes: ByteArray): FieldDataBundle {
        val text = if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            jsonFromZip(bytes)
        } else {
            bytes.toString(Charsets.UTF_8)
        }
        val bundle = decode(text)
        if (bundle.format != FieldDataExchange.FORMAT) {
            error("This file is not a Wildlife Whisperer field-data backup.")
        }
        return bundle
    }

    fun mergeJobs(local: List<JobSnapshot>, incoming: List<JobSnapshot>): List<JobSnapshot> =
        mergeSnapshots(local, incoming, { it.id }, { it.updatedAt })

    fun mergeCustomers(local: List<CustomerSnapshot>, incoming: List<CustomerSnapshot>): List<CustomerSnapshot> =
        mergeSnapshots(local, incoming, { it.id }, { it.updatedAt })

    fun mergeInspections(local: List<InspectionSnapshot>, incoming: List<InspectionSnapshot>): List<InspectionSnapshot> =
        mergeSnapshots(local, incoming, { it.id }, { it.updatedAt })

    fun mergePhotos(local: List<PhotoSnapshot>, incoming: List<PhotoSnapshot>): List<PhotoSnapshot> =
        mergeSnapshots(local, incoming, { it.id }, { it.takenAt })

    private fun <T> mergeSnapshots(
        local: List<T>,
        incoming: List<T>,
        id: (T) -> String,
        updatedAt: (T) -> Long
    ): List<T> {
        val localRecords = local.map { FieldRecord(id(it), updatedAt(it), id(it)) }
        val incomingRecords = incoming.map { FieldRecord(id(it), updatedAt(it), id(it)) }
        val chosen = FieldDataExchange.mergeById(localRecords, incomingRecords)
        val localById = local.associateBy { id(it) }
        val incomingById = incoming.associateBy { id(it) }
        return chosen.mapNotNull { winner ->
            val localRow = localById[winner.id]
            val incomingRow = incomingById[winner.id]
            when {
                localRow != null && incomingRow != null && updatedAt(incomingRow) > updatedAt(localRow) -> incomingRow
                localRow != null && incomingRow != null -> localRow
                incomingRow != null -> incomingRow
                else -> localRow
            }
        }
    }

    private fun jsonFromZip(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".json")) {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        error("Backup zip has no field-data JSON.")
    }
}

fun Job.toSnapshot(): JobSnapshot = JobSnapshot(
    id = id,
    title = title,
    description = description,
    customerId = customerId,
    customerName = customerName,
    address = address,
    latitude = latitude,
    longitude = longitude,
    status = status.name,
    priority = priority.name,
    type = type,
    estimatedValue = estimatedValue,
    actualCost = actualCost,
    assignedTo = assignedTo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    scheduledDate = scheduledDate,
    completedDate = completedDate,
    notes = notes,
    photos = photos,
    county = county,
    state = state,
    pricingJson = PricingJson.encode(pricing),
    confirmedSpecies = confirmedSpecies,
    legalNotes = legalNotes,
    nextStep = nextStep,
    nextStepDueAt = nextStepDueAt,
    nextStepSource = nextStepSource,
    aiRuntime = aiRuntime,
    weatherTrapAdvice = weatherTrapAdvice,
    followUpKind = followUpKind,
    followUpDueAt = followUpDueAt,
    followUpNotes = followUpNotes
)

fun JobSnapshot.toJob(): Job = Job(
    id = id,
    title = title,
    description = description,
    customerId = customerId,
    customerName = customerName,
    address = address,
    latitude = latitude,
    longitude = longitude,
    status = runCatching { JobStatus.valueOf(status) }.getOrDefault(JobStatus.PENDING),
    priority = runCatching { JobPriority.valueOf(priority) }.getOrDefault(JobPriority.MEDIUM),
    type = type,
    estimatedValue = estimatedValue,
    actualCost = actualCost,
    assignedTo = assignedTo,
    createdAt = createdAt,
    updatedAt = updatedAt,
    scheduledDate = scheduledDate,
    completedDate = completedDate,
    notes = notes,
    photos = photos,
    isSynced = false,
    county = county,
    state = state,
    pricing = runCatching { PricingJson.decode(pricingJson) }.getOrDefault(JobPricing()),
    confirmedSpecies = confirmedSpecies,
    legalNotes = legalNotes,
    nextStep = nextStep,
    nextStepDueAt = nextStepDueAt,
    nextStepSource = nextStepSource,
    aiRuntime = aiRuntime,
    weatherTrapAdvice = weatherTrapAdvice,
    followUpKind = followUpKind,
    followUpDueAt = followUpDueAt,
    followUpNotes = followUpNotes
)

fun Customer.toSnapshot(): CustomerSnapshot = CustomerSnapshot(
    id = id,
    firstName = firstName,
    lastName = lastName,
    companyName = companyName,
    email = email,
    phone = phone,
    alternatePhone = alternatePhone,
    address = address,
    city = city,
    state = state,
    zipCode = zipCode,
    latitude = latitude,
    longitude = longitude,
    customerType = customerType.name,
    notes = notes,
    billingAddress = billingAddress,
    billingContact = billingContact,
    paymentTerms = paymentTerms,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CustomerSnapshot.toCustomer(): Customer = Customer(
    id = id,
    firstName = firstName,
    lastName = lastName,
    companyName = companyName,
    email = email,
    phone = phone,
    alternatePhone = alternatePhone,
    address = address,
    city = city,
    state = state,
    zipCode = zipCode,
    latitude = latitude,
    longitude = longitude,
    customerType = runCatching { CustomerType.valueOf(customerType) }.getOrDefault(CustomerType.RESIDENTIAL),
    notes = notes,
    billingAddress = billingAddress,
    billingContact = billingContact,
    paymentTerms = paymentTerms,
    isActive = isActive,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isSynced = false
)

fun Inspection.toSnapshot(): InspectionSnapshot = InspectionSnapshot(
    id = id,
    jobId = jobId,
    customerId = customerId,
    customerName = customerName,
    inspectorName = inspectorName,
    inspectionType = inspectionType.name,
    inspectionDate = inspectionDate,
    findings = findings,
    recommendations = recommendations,
    severity = severity.name,
    speciesIdentified = speciesIdentified,
    entryPoints = entryPoints,
    damageAssessment = damageAssessment,
    photos = photos,
    followUpRequired = followUpRequired,
    followUpDate = followUpDate,
    notes = notes,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt,
    updatedAt = updatedAt,
    aiNarrativeDraft = aiNarrativeDraft,
    aiDraftSource = aiDraftSource
)

fun InspectionSnapshot.toInspection(): Inspection = Inspection(
    id = id,
    jobId = jobId,
    customerId = customerId,
    customerName = customerName,
    inspectorName = inspectorName,
    inspectionType = runCatching { InspectionType.valueOf(inspectionType) }.getOrDefault(InspectionType.ROUTINE),
    inspectionDate = inspectionDate,
    findings = findings,
    recommendations = recommendations,
    severity = runCatching { FindingSeverity.valueOf(severity) }.getOrDefault(FindingSeverity.NONE),
    speciesIdentified = speciesIdentified,
    entryPoints = entryPoints,
    damageAssessment = damageAssessment,
    photos = photos,
    followUpRequired = followUpRequired,
    followUpDate = followUpDate,
    notes = notes,
    latitude = latitude,
    longitude = longitude,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isSynced = false,
    aiNarrativeDraft = aiNarrativeDraft,
    aiDraftSource = aiDraftSource
)

fun Photo.toSnapshot(): PhotoSnapshot = PhotoSnapshot(
    id = id,
    filePath = filePath,
    localPath = localPath,
    remoteUrl = remoteUrl,
    thumbnailPath = thumbnailPath,
    jobId = jobId,
    inspectionId = inspectionId,
    customerId = customerId,
    category = category.name,
    description = description,
    latitude = latitude,
    longitude = longitude,
    takenAt = takenAt,
    takenBy = takenBy,
    fileSize = fileSize,
    createdAt = createdAt
)

fun PhotoSnapshot.toPhoto(): Photo = Photo(
    id = id,
    filePath = filePath,
    localPath = localPath,
    remoteUrl = remoteUrl,
    thumbnailPath = thumbnailPath,
    jobId = jobId,
    inspectionId = inspectionId,
    customerId = customerId,
    category = runCatching { PhotoCategory.valueOf(category) }.getOrDefault(PhotoCategory.JOB_SITE),
    description = description,
    latitude = latitude,
    longitude = longitude,
    takenAt = takenAt,
    takenBy = takenBy,
    fileSize = fileSize,
    isUploaded = false,
    createdAt = createdAt
)
