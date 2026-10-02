package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Photo

enum class SearchKind { JOB, CUSTOMER, INSPECTION, PHOTO }

data class SearchHit(
    val kind: SearchKind,
    val id: String,
    val title: String,
    val snippet: String,
    val jobId: String = ""
)

object SmartSearch {
    fun search(
        query: String,
        jobs: List<Job>,
        customers: List<Customer>,
        inspections: List<Inspection>,
        photos: List<Photo>
    ): List<SearchHit> {
        val q = query.trim().lowercase()
        if (q.length < 2) return emptyList()
        val out = mutableListOf<SearchHit>()
        jobs.forEach { job ->
            val hay = listOf(
                job.title, job.customerName, job.address, job.notes, job.description,
                job.type, job.confirmedSpecies, job.nextStep, job.legalNotes,
                job.pricing.warrantyCovered, job.pricing.photoAutoTags.flatMap { it.allTags() }.joinToString(),
                job.pricing.speciesChecklist.joinToString { it.label }
            ).joinToString(" ").lowercase()
            if (hay.contains(q)) {
                out += SearchHit(SearchKind.JOB, job.id, job.title.ifBlank { job.customerName }, snippet(hay, q), job.id)
            }
        }
        customers.filter { it.isActive }.forEach { c ->
            val hay = listOf(c.fullName, c.phone, c.email, c.address, c.city, c.notes).joinToString(" ").lowercase()
            if (hay.contains(q)) {
                out += SearchHit(SearchKind.CUSTOMER, c.id, c.fullName.ifBlank { c.phone }, snippet(hay, q))
            }
        }
        inspections.forEach { insp ->
            val hay = listOf(
                insp.customerName, insp.findings, insp.recommendations, insp.speciesIdentified,
                insp.entryPoints, insp.damageAssessment, insp.notes, insp.aiNarrativeDraft
            ).joinToString(" ").lowercase()
            if (hay.contains(q)) {
                out += SearchHit(
                    SearchKind.INSPECTION,
                    insp.id,
                    insp.customerName.ifBlank { "Inspection" },
                    snippet(hay, q),
                    insp.jobId
                )
            }
        }
        photos.forEach { photo ->
            val jobTags = jobs.firstOrNull { it.id == photo.jobId }
                ?.pricing?.photoAutoTags.orEmpty()
                .filter { it.photoId == photo.id }
                .flatMap { it.allTags() }
                .joinToString()
            val hay = listOf(photo.description, photo.category.name, jobTags).joinToString(" ").lowercase()
            if (hay.contains(q)) {
                out += SearchHit(
                    SearchKind.PHOTO,
                    photo.id,
                    photo.description.ifBlank { photo.category.name },
                    snippet(hay, q),
                    photo.jobId.orEmpty()
                )
            }
        }
        return out
    }

    fun snippet(hay: String, query: String, radius: Int = 42): String {
        val idx = hay.indexOf(query)
        if (idx < 0) return hay.take(radius * 2)
        val start = (idx - radius).coerceAtLeast(0)
        val end = (idx + query.length + radius).coerceAtMost(hay.length)
        return hay.substring(start, end).trim()
    }
}
