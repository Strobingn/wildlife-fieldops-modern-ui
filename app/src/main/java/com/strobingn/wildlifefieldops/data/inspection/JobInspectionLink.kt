package com.strobingn.wildlifefieldops.data.inspection

import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.Job

/**
 * Manual job ↔ inspection linking. Auto-suggest same-customer / same-address
 * rows, but never writes a link without an operator tap.
 */
object JobInspectionLink {
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
