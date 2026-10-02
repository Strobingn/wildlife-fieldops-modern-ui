package com.strobingn.wildlifefieldops.data.inspection

import com.strobingn.wildlifefieldops.ai.fieldops.ManualField
import com.strobingn.wildlifefieldops.ai.fieldops.NarrativeCleared
import com.strobingn.wildlifefieldops.ai.fieldops.OperatorWins
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.InspectionType
import com.strobingn.wildlifefieldops.data.model.Job

/**
 * Manual job ↔ inspection linking. Auto-suggest same-customer / same-address
 * rows, but never writes a link without an operator tap.
 *
 * The Job page primary action is [LINK_BUTTON]. It opens the AI inspection
 * form already linked to this job, or the existing linked inspection.
 */
object JobInspectionLink {
    const val LINK_BUTTON = "Link job to inspection"
    const val OPEN_LINKED_BUTTON = "Open linked inspection"

    data class FieldSnapshot(
        val customerName: String = "",
        val phone: String = "",
        val address: String = "",
        val species: String = "",
        val serviceType: String = "",
        val inspectionType: InspectionType = InspectionType.ROUTINE
    )

    sealed class Destination {
        data class NewForm(val route: String, val jobId: String) : Destination()
        data class Existing(val route: String, val inspectionId: String) : Destination()
    }

    /** New AI inspection form, or the inspection already linked to this job. */
    fun destination(jobId: String, inspections: List<Inspection>): Destination {
        val id = jobId.trim()
        val linked = linkedTo(id, inspections).firstOrNull()
        return if (linked != null) {
            Destination.Existing(
                route = "inspection_detail/${linked.id}",
                inspectionId = linked.id
            )
        } else {
            Destination.NewForm(
                route = "inspection_form?jobId=$id",
                jobId = id
            )
        }
    }

    /**
     * Inspection the Job-page route creates: [Inspection.jobId] set, and
     * customer name, phone, address, and species/type copied only into empty
     * fields. A manual/cleared field stays as the operator left it.
     */
    fun inspectionForRoute(
        job: Job,
        phone: String,
        current: Inspection = Inspection(),
        manual: Set<String> = emptySet(),
        typeUntouched: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ): Inspection {
        val contact = InspectionContact.read(current.aiDraftSource)
        val filled = prefill(
            job = job,
            phone = phone,
            current = FieldSnapshot(
                customerName = current.customerName,
                phone = contact.phone,
                address = contact.address,
                species = current.speciesIdentified,
                serviceType = contact.serviceType,
                inspectionType = current.inspectionType
            ),
            manual = manual,
            typeUntouched = typeUntouched
        )
        val source = NarrativeCleared.pack(
            InspectionContact.embed(
                current.aiDraftSource,
                InspectionContact.Values(filled.phone, filled.address, filled.serviceType)
            ),
            manual
        )
        val id = job.id.trim()
        return current.copy(
            jobId = id.ifBlank { current.jobId },
            customerId = current.customerId.ifBlank { job.customerId },
            customerName = filled.customerName,
            speciesIdentified = filled.species,
            inspectionType = filled.inspectionType,
            aiDraftSource = source,
            updatedAt = nowMs,
            isSynced = false
        )
    }

    fun prefill(
        job: Job,
        phone: String,
        current: FieldSnapshot = FieldSnapshot(),
        manual: Set<String> = emptySet(),
        typeUntouched: Boolean = true
    ): FieldSnapshot {
        val mapped = inspectionTypeHint(job)
        val inspectionType = when {
            ManualField.INSPECTION_TYPE in manual -> current.inspectionType
            !typeUntouched -> current.inspectionType
            mapped == null -> current.inspectionType
            else -> mapped
        }
        return FieldSnapshot(
            customerName = OperatorWins.suggest(
                current.customerName,
                job.customerName,
                ManualField.CUSTOMER_NAME in manual
            ),
            phone = OperatorWins.suggest(current.phone, phone, ManualField.PHONE in manual),
            address = OperatorWins.suggest(current.address, job.address, ManualField.ADDRESS in manual),
            species = OperatorWins.suggest(current.species, speciesHint(job), ManualField.SPECIES in manual),
            serviceType = OperatorWins.suggest(
                current.serviceType,
                job.type,
                ManualField.SERVICE_TYPE in manual
            ),
            inspectionType = inspectionType
        )
    }

    fun speciesHint(job: Job): String {
        val confirmed = job.confirmedSpecies.trim()
        if (confirmed.isNotBlank()) return confirmed
        val type = job.type.trim()
        if (type.isBlank() || type.lowercase() in GENERIC_SERVICE_TYPES) return ""
        val head = type.substringBefore(' ').trim()
        return if (head.lowercase() in ANIMAL_HEADS) {
            head.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } else {
            ""
        }
    }

    fun inspectionTypeHint(job: Job): InspectionType? {
        val type = job.type.trim().lowercase()
        if (type.isBlank()) return null
        return when {
            "emergency" in type -> InspectionType.EMERGENCY
            "follow" in type -> InspectionType.FOLLOW_UP
            "prevent" in type -> InspectionType.PREVENTIVE
            "compliance" in type -> InspectionType.COMPLIANCE
            type == "inspection" || type.startsWith("inspect") -> InspectionType.INITIAL
            else -> null
        }
    }

    private val GENERIC_SERVICE_TYPES = setOf(
        "inspection",
        "removal",
        "repair",
        "prevention",
        "cleanup",
        "consultation",
        "emergency",
        "exclusion",
        "trapping",
        "other",
        "follow-up visit",
        "one-way door",
        "attic cleanout",
        "crawlspace cleanup",
        "chimney cap",
        "dead animal removal",
        "insulation remediation",
        "sanitation / disinfection"
    )

    private val ANIMAL_HEADS = setOf("bat", "bird", "squirrel", "raccoon", "skunk", "snake")

    fun applyLink(
        inspection: Inspection,
        jobId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Inspection {
        val id = jobId.trim()
        if (id.isBlank()) return inspection
        return inspection.copy(jobId = id, updatedAt = nowMs, isSynced = false)
    }

    fun applyUnlink(
        inspection: Inspection,
        nowMs: Long = System.currentTimeMillis()
    ): Inspection = inspection.copy(jobId = "", updatedAt = nowMs, isSynced = false)

    fun linkedTo(jobId: String, inspections: List<Inspection>): List<Inspection> =
        inspections.filter { it.jobId == jobId }.sortedByDescending { it.inspectionDate }

    /** Unlinked inspections that match this job — suggestion only, never auto-applied. */
    fun suggestedForJob(job: Job, inspections: List<Inspection>): List<Inspection> =
        inspections
            .filter { it.jobId.isBlank() }
            .filter { matchesJob(job, it) }
            .sortedByDescending { it.inspectionDate }

    fun suggestedJob(inspection: Inspection, jobs: List<Job>): Job? =
        jobs.filter { matchesJob(it, inspection) }
            .maxByOrNull { it.updatedAt }

    fun rankPicker(job: Job, inspections: List<Inspection>): List<Inspection> {
        val linked = linkedTo(job.id, inspections)
        val rest = inspections.filter { it.jobId != job.id }
            .sortedWith(
                compareByDescending<Inspection> { matchesJob(job, it) }
                    .thenByDescending { it.jobId.isBlank() }
                    .thenByDescending { it.inspectionDate }
            )
        return linked + rest
    }

    fun matchesJob(job: Job, inspection: Inspection): Boolean {
        if (job.customerId.isNotBlank() && inspection.customerId == job.customerId) return true
        val name = job.customerName.trim()
        if (name.isNotBlank() && inspection.customerName.equals(name, ignoreCase = true)) return true
        return addressOverlap(job.address, inspection)
    }

    fun addressOverlap(jobAddress: String, inspection: Inspection): Boolean {
        val street = streetKey(jobAddress)
        if (street.length < 5) return false
        val blob = listOf(
            inspection.customerName,
            inspection.findings,
            inspection.notes,
            inspection.entryPoints
        ).joinToString(" ").lowercase()
        return blob.contains(street)
    }

    private fun streetKey(address: String): String =
        address.lowercase()
            .substringBefore(',')
            .trim()
            .replace(Regex("\\s+"), " ")
}
