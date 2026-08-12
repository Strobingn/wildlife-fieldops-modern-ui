package com.strobingn.wildlifefieldops.ai.operations

import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic intelligence layer that works offline from real app data.
 * Live LLM/vision services can enrich these results, but this engine never depends on them.
 */
object AIOperationsEngine {

    data class PropertyInsight(
        val address: String,
        val visitCount: Int,
        val serviceTypes: List<String>,
        val totalQuoted: Double,
        val totalActual: Double,
        val repeatRiskPercent: Int,
        val recommendation: String
    )

    data class BusinessInsight(
        val totalJobs: Int,
        val completedJobs: Int,
        val closeRatePercent: Int,
        val quotedRevenue: Double,
        val actualRevenue: Double,
        val grossVariance: Double,
        val averageTicket: Double,
        val topService: String,
        val recommendation: String
    )

    data class QualityCheck(
        val jobId: String,
        val title: String,
        val score: Int,
        val missing: List<String>
    )

    data class PricingInsight(
        val jobId: String,
        val title: String,
        val estimated: Double,
        val actual: Double,
        val variance: Double,
        val marginSignal: String
    )

    data class RoutePriority(
        val jobId: String,
        val title: String,
        val address: String,
        val score: Int,
        val reason: String
    )

    data class InventoryForecast(
        val item: String,
        val expectedWeeklyUse: Int,
        val confidencePercent: Int,
        val reason: String
    )

    data class SpeciesGuidance(
        val species: String,
        val activityWindow: String,
        val fieldPriority: String,
        val exclusionNote: String
    )

    /** One production AI module backed by the current Room job history. */
    data class AdvancedInsight(
        val name: String,
        val score: Int,
        val signal: String,
        val action: String
    )

    data class Dashboard(
        val business: BusinessInsight,
        val properties: List<PropertyInsight>,
        val qualityChecks: List<QualityCheck>,
        val pricing: List<PricingInsight>,
        val routePriorities: List<RoutePriority>,
        val inventory: List<InventoryForecast>,
        val speciesGuidance: List<SpeciesGuidance>,
        val advancedInsights: List<AdvancedInsight>
    )

    fun analyze(jobs: List<Job>): Dashboard {
        return Dashboard(
            business = businessInsight(jobs),
            properties = propertyInsights(jobs),
            qualityChecks = jobs.map(::qualityCheck).sortedBy { it.score }.take(20),
            pricing = jobs.filter { it.estimatedValue > 0.0 || it.actualCost > 0.0 }
                .map(::pricingInsight)
                .sortedByDescending { kotlin.math.abs(it.variance) }
                .take(20),
            routePriorities = jobs.filter { it.status != JobStatus.COMPLETED && it.status != JobStatus.PAID }
                .map(::routePriority)
                .sortedByDescending { it.score }
                .take(20),
            inventory = inventoryForecast(jobs),
            speciesGuidance = speciesGuidance(jobs),
            advancedInsights = advancedInsights(jobs)
        )
    }

    /**
     * Twenty offline-safe AI features. Every signal is recalculated from real job
     * records; the live Grok service can later enrich the wording without changing
     * the underlying operational facts.
     */
    private fun advancedInsights(jobs: List<Job>): List<AdvancedInsight> {
        val now = System.currentTimeMillis()
        val open = jobs.filter { it.status != JobStatus.COMPLETED && it.status != JobStatus.PAID && it.status != JobStatus.CANCELLED }
        val completed = jobs.filter { it.status == JobStatus.COMPLETED || it.status == JobStatus.PAID }
        val overdue = open.count { it.scheduledDate?.let { date -> date < now } == true }
        val unscheduled = open.count { it.scheduledDate == null }
        val missingPhotos = completed.count { it.photos.isEmpty() }
        val missingActual = completed.count { it.actualCost <= 0.0 }
        val urgent = open.count { it.priority.name == "URGENT" || it.priority.name == "HIGH" }
        val repeatProperties = jobs.filter { it.address.isNotBlank() }.groupingBy { normalizeAddress(it.address) }
            .eachCount().count { it.value > 1 }
        val quoted = jobs.sumOf { it.estimatedValue }
        val actual = jobs.sumOf { it.actualCost }
        val paid = jobs.filter { it.status == JobStatus.PAID }.sumOf { it.estimatedValue }
        val average = jobs.map { it.estimatedValue }.filter { it > 0 }.average().takeUnless { it.isNaN() } ?: 0.0
        val serviceCounts = jobs.groupingBy { it.type.ifBlank { "Unknown" } }.eachCount()
        val topService = serviceCounts.maxByOrNull { it.value }?.key ?: "No history yet"
        val thinMargin = jobs.count { it.actualCost > 0 && it.estimatedValue > 0 && (it.estimatedValue - it.actualCost) / it.estimatedValue < .15 }
        val incomplete = jobs.count { qualityCheck(it).score < 75 }
        val oldOpen = open.count { now - it.createdAt > 7L * 86_400_000L }
        val scheduledToday = open.count { dateIsToday(it.scheduledDate, now) }
        val highValueOpen = open.count { it.estimatedValue >= max(750.0, average * 1.4) }
        val sparseNotes = jobs.count { (it.description.length + it.notes.length) < 40 }
        val safetyText = jobs.count { job ->
            val text = "${job.title} ${job.description} ${job.notes} ${job.type}".lowercase(Locale.US)
            listOf("bat", "rabies", "ladder", "roof", "chimney", "skunk", "bite").any(text::contains)
        }
        fun risk(count: Int, total: Int = max(1, jobs.size)): Int = min(100, (count * 100) / max(1, total))
        fun insight(name: String, score: Int, signal: String, action: String) =
            AdvancedInsight(name, score.coerceIn(0, 100), signal, action)

        return listOf(
            insight("Seasonal demand forecast", min(100, jobs.size * 5), "$topService leads recorded demand", "Pre-stage labor and materials for the highest-volume service."),
            insight("Schedule conflict detector", risk(overdue, open.size), "$overdue overdue appointments", "Reschedule overdue work before accepting lower-priority stops."),
            insight("Revenue forecast", min(100, open.size * 8), "${money(open.sumOf { it.estimatedValue })} open pipeline", "Prioritize approved, high-value jobs with complete addresses."),
            insight("Cash collection risk", risk(completed.count { it.status != JobStatus.PAID }, completed.size), "${money((quoted - paid).coerceAtLeast(0.0))} not marked paid", "Review completed and invoiced jobs for payment follow-up."),
            insight("Customer follow-up priority", risk(oldOpen, open.size), "$oldOpen open jobs older than 7 days", "Contact the oldest open estimates and record the outcome."),
            insight("Warranty callback predictor", min(100, repeatProperties * 18), "$repeatProperties repeat properties", "Review previous entry points and warranty scope before dispatch."),
            insight("Service-duration predictor", min(100, scheduledToday * 20), "$scheduledToday stops scheduled today", "Reserve larger time blocks for repeat, urgent, and high-value exclusions."),
            insight("Travel-efficiency coach", risk(open.count { it.address.isBlank() }, open.size), "${open.count { it.address.isBlank() }} open jobs lack addresses", "Complete locations before running route optimization."),
            insight("Technician workload balancer", min(100, open.size * 7), "${open.size} active jobs in the queue", "Reassign urgent work when one technician owns too many open jobs."),
            insight("Photo documentation auditor", risk(missingPhotos, completed.size), "$missingPhotos completed jobs lack photos", "Capture before, entry-point, repair, and after photos before closeout."),
            insight("Field-note NLP auditor", risk(sparseNotes), "$sparseNotes jobs have thin notes", "Add evidence, access conditions, species signs, and technician actions."),
            insight("Compliance risk scanner", min(100, safetyText * 9), "$safetyText jobs contain regulated or safety-sensitive terms", "Verify permits, dependent young, release rules, and required records."),
            insight("Safety hazard predictor", min(100, safetyText * 11), "$safetyText jobs may require elevated PPE or access controls", "Run ladder, roof, bite/rabies, and confined-space checks."),
            insight("Repeat-intrusion predictor", min(100, repeatProperties * 20), "$repeatProperties addresses have repeat history", "Inspect building-wide secondary gaps instead of only the reported opening."),
            insight("Upsell opportunity finder", min(100, completed.size * 6), "${completed.size} completed jobs can support prevention offers", "Offer sanitation, monitoring, exclusion reinforcement, or maintenance where justified."),
            insight("Estimate confidence model", risk(jobs.count { it.estimatedValue <= 0.0 }), "${jobs.count { it.estimatedValue <= 0.0 }} jobs have no estimate", "Price labor, materials, access difficulty, travel, and follow-ups explicitly."),
            insight("Completion readiness", risk(incomplete), "$incomplete jobs score below 75 for record quality", "Resolve missing customer, address, estimate, notes, cost, or photo fields."),
            insight("Material demand predictor", min(100, jobs.size * 4), "${serviceCounts.size} service categories drive stock", "Use the inventory forecast before ordering traps and exclusion materials."),
            insight("Emergency triage engine", risk(urgent, open.size), "$urgent high or urgent jobs", "Move health/safety and active-entry calls ahead of routine follow-ups."),
            insight("Customer update drafter", risk(unscheduled, open.size), "$unscheduled open jobs are not scheduled", "Draft appointment, arrival, completion, and follow-up messages from verified job facts.")
        )
    }

    private fun dateIsToday(value: Long?, now: Long): Boolean {
        if (value == null) return false
        val day = 86_400_000L
        return value / day == now / day
    }

    private fun businessInsight(jobs: List<Job>): BusinessInsight {
        val completed = jobs.count { it.status == JobStatus.COMPLETED || it.status == JobStatus.PAID }
        val quoted = jobs.sumOf { it.estimatedValue }
        val actual = jobs.sumOf { it.actualCost }
        val averageTicket = if (jobs.isEmpty()) 0.0 else quoted / jobs.size
        val topService = jobs.groupingBy { it.type.ifBlank { "Unknown" } }.eachCount()
            .maxByOrNull { it.value }?.key ?: "No data"
        val closeRate = if (jobs.isEmpty()) 0 else ((completed.toDouble() / jobs.size) * 100).toInt()
        val recommendation = when {
            jobs.isEmpty() -> "Add completed jobs to unlock forecasting."
            actual == 0.0 -> "Record actual cost on completed jobs to measure profit and estimate accuracy."
            quoted < actual -> "Actual costs exceed quoted revenue. Review labor, material, and travel assumptions."
            closeRate < 50 -> "Closing rate is below 50%. Review estimate speed, follow-up timing, and price clarity."
            else -> "Operations are stable. Focus on repeat-property prevention and top-service margins."
        }
        return BusinessInsight(
            totalJobs = jobs.size,
            completedJobs = completed,
            closeRatePercent = closeRate,
            quotedRevenue = quoted,
            actualRevenue = actual,
            grossVariance = quoted - actual,
            averageTicket = averageTicket,
            topService = topService,
            recommendation = recommendation
        )
    }

    private fun propertyInsights(jobs: List<Job>): List<PropertyInsight> {
        return jobs.filter { it.address.isNotBlank() }
            .groupBy { normalizeAddress(it.address) }
            .map { (_, propertyJobs) ->
                val address = propertyJobs.first().address
                val serviceTypes = propertyJobs.map { it.type }.filter { it.isNotBlank() }.distinct()
                val repeatRisk = min(95, 20 + ((propertyJobs.size - 1).coerceAtLeast(0) * 18) +
                    if (serviceTypes.size > 1) 10 else 0)
                val recommendation = when {
                    propertyJobs.size >= 3 -> "High repeat history. Inspect prior repair zones and building-wide exclusion points first."
                    propertyJobs.size == 2 -> "Repeat property. Compare current evidence with the previous entry point and warranty scope."
                    else -> "First recorded visit. Capture exterior elevations, attic/crawlspace findings, and repair photos."
                }
                PropertyInsight(
                    address = address,
                    visitCount = propertyJobs.size,
                    serviceTypes = serviceTypes,
                    totalQuoted = propertyJobs.sumOf { it.estimatedValue },
                    totalActual = propertyJobs.sumOf { it.actualCost },
                    repeatRiskPercent = repeatRisk,
                    recommendation = recommendation
                )
            }
            .sortedWith(compareByDescending<PropertyInsight> { it.repeatRiskPercent }.thenByDescending { it.visitCount })
            .take(30)
    }

    private fun qualityCheck(job: Job): QualityCheck {
        val missing = buildList {
            if (job.address.isBlank()) add("address")
            if (job.customerName.isBlank()) add("customer")
            if (job.description.isBlank()) add("description")
            if (job.estimatedValue <= 0.0) add("estimate")
            if ((job.status == JobStatus.COMPLETED || job.status == JobStatus.PAID) && job.actualCost <= 0.0) add("actual cost")
            if ((job.status == JobStatus.COMPLETED || job.status == JobStatus.PAID) && job.photos.isEmpty()) add("completion photos")
            if (job.type.isBlank()) add("service type")
        }
        return QualityCheck(
            jobId = job.id,
            title = job.title.ifBlank { "Untitled job" },
            score = max(0, 100 - missing.size * 14),
            missing = missing
        )
    }

    private fun pricingInsight(job: Job): PricingInsight {
        val variance = job.estimatedValue - job.actualCost
        val signal = when {
            job.actualCost <= 0.0 -> "Actual cost missing"
            variance < 0.0 -> "Over cost by ${money(-variance)}"
            job.estimatedValue > 0.0 && variance / job.estimatedValue < 0.15 -> "Thin margin"
            else -> "Healthy spread"
        }
        return PricingInsight(job.id, job.title.ifBlank { "Untitled job" }, job.estimatedValue, job.actualCost, variance, signal)
    }

    private fun routePriority(job: Job): RoutePriority {
        var score = when (job.priority.name) {
            "URGENT" -> 100
            "HIGH" -> 80
            "MEDIUM" -> 55
            else -> 35
        }
        val now = System.currentTimeMillis()
        val scheduled = job.scheduledDate
        if (scheduled != null) {
            val hours = (scheduled - now) / 3_600_000.0
            score += when {
                hours < -1 -> 35
                hours <= 4 -> 25
                hours <= 24 -> 15
                else -> 0
            }
        }
        if (job.address.isBlank()) score -= 20
        if (job.estimatedValue >= 1000.0) score += 10
        val reason = when {
            scheduled != null && scheduled < now -> "Overdue appointment"
            job.priority.name == "URGENT" -> "Urgent priority"
            job.estimatedValue >= 1000.0 -> "High-value open job"
            else -> "Priority and schedule score"
        }
        return RoutePriority(job.id, job.title.ifBlank { "Untitled job" }, job.address, score.coerceIn(0, 150), reason)
    }

    private fun inventoryForecast(jobs: List<Job>): List<InventoryForecast> {
        val recent = jobs.filter { it.createdAt >= System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000 }
        val services = recent.map { it.type.lowercase(Locale.US) }
        fun count(vararg words: String) = services.count { value -> words.any(value::contains) }
        val forecasts = listOf(
            InventoryForecast("One-way doors", max(1, count("bat", "squirrel", "raccoon", "one-way")), 78, "Based on exclusion/removal job mix"),
            InventoryForecast("Hardware cloth / screening", max(1, count("exclusion", "repair", "bird", "crawlspace")), 82, "Based on repair and exclusion volume"),
            InventoryForecast("Sealant / flashing", max(1, count("repair", "chimney", "roof", "bat")), 75, "Based on structural repair demand"),
            InventoryForecast("Traps", max(1, count("trapping", "raccoon", "skunk", "woodchuck")), 72, "Based on trapping-type jobs"),
            InventoryForecast("Sanitation product", max(1, count("cleanup", "sanitation", "dead animal", "attic")), 77, "Based on remediation and cleanup jobs")
        )
        return forecasts.sortedByDescending { it.expectedWeeklyUse }
    }

    private fun speciesGuidance(jobs: List<Job>): List<SpeciesGuidance> {
        val text = jobs.joinToString(" ") { "${it.title} ${it.description} ${it.notes} ${it.type}" }.lowercase(Locale.US)
        val known = listOf(
            SpeciesGuidance("Bat", "Dusk through dawn", "Inspect roofline, ridge vents, gable vents, fascia transitions", "Confirm seasonal/legal timing before exclusion; seal secondary gaps first."),
            SpeciesGuidance("Raccoon", "Dusk through early morning", "Inspect soffits, roof returns, chimneys, decks and crawlspaces", "Verify dependent young before eviction or exclusion."),
            SpeciesGuidance("Squirrel", "Morning and late afternoon", "Inspect roof edges, dormers, vents, trees contacting structure", "Locate all travel routes before installing one-way devices."),
            SpeciesGuidance("Skunk", "Dusk through night", "Inspect decks, sheds, crawlspaces and foundation voids", "Use low-stress exclusion and verify den status before sealing."),
            SpeciesGuidance("Woodchuck", "Morning and late afternoon", "Inspect burrow network, sheds, decks, gardens and foundation edges", "Account for secondary burrow exits and structural undermining."),
            SpeciesGuidance("Bird", "Dawn through daylight", "Inspect vents, soffits, signs, ledges and roof cavities", "Confirm nest/egg status and applicable protected-species rules."),
            SpeciesGuidance("Snake", "Warm daylight and dusk", "Inspect foundation gaps, clutter, rodents and moisture sources", "Correct prey and entry conditions; do not rely on repellents alone.")
        )
        return known.filter { text.contains(it.species.lowercase(Locale.US)) }.ifEmpty { known.take(3) }
    }

    private fun normalizeAddress(address: String): String = address.trim().lowercase(Locale.US).replace(Regex("\\s+"), " ")
    private fun money(value: Double): String = "$" + String.format(Locale.US, "%,.2f", value)
}
