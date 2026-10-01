package com.strobingn.wildlifefieldops.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.strobingn.wildlifefieldops.pricing.JobPricing
import java.util.UUID

enum class JobStatus {
    PENDING, IN_PROGRESS, COMPLETED, CANCELLED, INVOICED, PAID
}

enum class JobPriority {
    LOW, MEDIUM, HIGH, URGENT
}

@Entity(tableName = "jobs")
data class Job(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val description: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val address: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val status: JobStatus = JobStatus.PENDING,
    val priority: JobPriority = JobPriority.MEDIUM,
    /** Free-form service type label (built-in or user-defined). */
    val type: String = DefaultServiceTypes.all.first(),
    val estimatedValue: Double = 0.0,
    val actualCost: Double = 0.0,
    val assignedTo: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val scheduledDate: Long? = null,
    val completedDate: Long? = null,
    val notes: String = "",
    val photos: List<String> = emptyList(),
    val isSynced: Boolean = false,
    val syncError: String? = null,
    /** Resolved county name (e.g. "Orange County"). Persisted so offline invoices work. */
    val county: String? = null,
    /** Two-letter state abbreviation resolved alongside [county] (e.g. "NY"). */
    val state: String? = null,
    /**
     * Estimate worksheet + computed-field overrides. Empty default preserves
     * existing jobs; [estimatedValue] stays the effective quote total.
     */
    val pricing: JobPricing = JobPricing(),
    /** Operator-confirmed species (AI may suggest; typed value wins). */
    val confirmedSpecies: String = "",
    /** NY DEC / rabies / protected-species notes Sir accepted or rewrote. */
    val legalNotes: String = "",
    /** Next field action (AI draft or typed). */
    val nextStep: String = "",
    val nextStepDueAt: Long? = null,
    val nextStepSource: String = "",
    /** Last AI runtime used on this job: cloud / on_device / heuristic. */
    val aiRuntime: String = ""
)
