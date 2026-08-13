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

    /** One production intelligence module backed by the current Room job history. */
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
     * Forty-five offline-safe AI features. Every signal is recalculated from real job
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
        val cancelled = jobs.count { it.status == JobStatus.CANCELLED }
        val invoiced = jobs.count { it.status == JobStatus.INVOICED }
        val unassigned = open.count { it.assignedTo.isBlank() }
        val missingCoordinates = open.count { it.latitude == null || it.longitude == null }
        val unsynced = jobs.count { !it.isSynced }
        val syncFailures = jobs.count { !it.syncError.isNullOrBlank() }
        val staleRecords = jobs.count { now - it.updatedAt > 30L * 86_400_000L && it.status != JobStatus.PAID && it.status != JobStatus.CANCELLED }
        val futureScheduled = open.filter { (it.scheduledDate ?: Long.MIN_VALUE) >= now }
        val nextSevenDays = futureScheduled.count { (it.scheduledDate ?: Long.MAX_VALUE) < now + 7L * 86_400_000L }
        val crowdedDays = futureScheduled.groupingBy { (it.scheduledDate ?: 0L) / 86_400_000L }.eachCount().count { it.value >= 5 }
        val completionLagHours = completed.mapNotNull { job ->
            job.completedDate?.let { ((it - job.createdAt).coerceAtLeast(0L) / 3_600_000L).toInt() }
        }
        val averageCompletionHours = completionLagHours.average().takeUnless { it.isNaN() }?.toInt() ?: 0
        val negativeVariance = completed.count { it.actualCost > it.estimatedValue && it.estimatedValue > 0.0 }
        val zeroValueCompleted = completed.count { it.estimatedValue <= 0.0 }
        val photoRich = completed.count { it.photos.size >= 3 }
        val noTechnicianCloseout = completed.count { it.assignedTo.isBlank() }
        val duplicateCustomers = jobs.filter { it.customerName.isNotBlank() }
            .groupingBy { it.customerName.trim().lowercase(Locale.US) }.eachCount().count { it.value > 1 }
        val serviceConcentration = serviceCounts.maxOfOrNull { it.value }
            ?.let { min(100, (it * 100) / max(1, jobs.size)) } ?: 0
        val afterHoursTerms = jobs.count { job ->
            val text = "${job.title} ${job.description} ${job.notes}".lowercase(Locale.US)
            listOf("after hours", "overnight", "emergency", "inside bedroom", "living space").any(text::contains)
        }
        val accessTerms = jobs.count { job ->
            val text = "${job.title} ${job.description} ${job.notes}".lowercase(Locale.US)
            listOf("attic", "crawlspace", "roof", "chimney", "confined", "steep").any(text::contains)
        }
        val youngTerms = jobs.count { job ->
            val text = "${job.title} ${job.description} ${job.notes}".lowercase(Locale.US)
            listOf("young", "babies", "pups", "kits", "nest", "maternity").any(text::contains)
        }
        val sanitationTerms = jobs.count { job ->
            val text = "${job.title} ${job.description} ${job.notes} ${job.type}".lowercase(Locale.US)
            listOf("guano", "feces", "urine", "odor", "carcass", "cleanup", "sanitation").any(text::contains)
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
            insight("Customer update drafter", risk(unscheduled, open.size), "$unscheduled open jobs are not scheduled", "Draft appointment, arrival, completion, and follow-up messages from verified job facts."),

            // 21-45: additional production signals requested for field operations.
            insight("Technician assignment gap", risk(unassigned, open.size), "$unassigned open jobs have no technician", "Assign ownership before dispatch so urgent work cannot sit unnoticed."),
            insight("Seven-day capacity forecast", min(100, nextSevenDays * 12), "$nextSevenDays appointments fall in the next 7 days", "Hold capacity for emergencies when the coming week is heavily booked."),
            insight("Overloaded-day detector", min(100, crowdedDays * 30), "$crowdedDays days contain at least 5 stops", "Move flexible follow-ups away from overloaded dates."),
            insight("Cancellation pattern monitor", risk(cancelled), "$cancelled of ${jobs.size} jobs are cancelled", "Review lead source, wait time, pricing clarity, and cancellation reasons."),
            insight("Invoice backlog detector", risk(invoiced, completed.size + invoiced), "$invoiced jobs remain invoiced but unpaid", "Run a daily collection queue ordered by invoice age and value."),
            insight("Geocode readiness auditor", risk(missingCoordinates, open.size), "$missingCoordinates open jobs lack map coordinates", "Validate addresses and coordinates before route planning."),
            insight("Offline sync health", risk(unsynced), "$unsynced records are waiting to sync", "Sync on a reliable connection before relying on multi-device data."),
            insight("Sync failure investigator", risk(syncFailures), "$syncFailures records contain a sync error", "Surface the error details and retry only after correcting authentication or data conflicts."),
            insight("Stale work-order detector", risk(staleRecords, open.size), "$staleRecords active records were not updated for 30 days", "Confirm status with the customer and close, reschedule, or cancel each stale record."),
            insight("Completion-time benchmark", min(100, averageCompletionHours / 2), "Average recorded lead-to-completion time is $averageCompletionHours hours", "Compare completion time by service type before changing appointment blocks."),
            insight("Cost-overrun classifier", risk(negativeVariance, completed.size), "$negativeVariance completed jobs cost more than estimated", "Review material, labor, access, and return-visit drivers on overruns."),
            insight("Unpriced closeout alert", risk(zeroValueCompleted, completed.size), "$zeroValueCompleted completed jobs have no estimate", "Correct missing financial records before reporting revenue or margins."),
            insight("Evidence completeness model", risk(completed.size - photoRich, completed.size), "$photoRich completed jobs have at least 3 photos", "Capture overview, entry-point, and completed-repair evidence."),
            insight("Closeout accountability", risk(noTechnicianCloseout, completed.size), "$noTechnicianCloseout completed jobs have no assigned technician", "Require technician attribution for warranty and quality review."),
            insight("Customer history linker", min(100, duplicateCustomers * 12), "$duplicateCustomers customer names have multiple jobs", "Open prior property history before estimating or dispatching repeat customers."),
            insight("Service concentration risk", serviceConcentration, "$topService represents $serviceConcentration% of recorded jobs", "Protect the core service while building capacity in adjacent profitable work."),
            insight("After-hours demand detector", min(100, afterHoursTerms * 15), "$afterHoursTerms jobs mention urgent living-space or after-hours conditions", "Define an after-hours triage and pricing policy for genuine emergencies."),
            insight("Difficult-access planner", min(100, accessTerms * 10), "$accessTerms jobs mention roofs, attics, chimneys, or confined access", "Pre-plan ladders, fall protection, lighting, PPE, and a second technician where required."),
            insight("Dependent-young safeguard", min(100, youngTerms * 20), "$youngTerms jobs mention nests or dependent young", "Verify species, life stage, season, and legal method before exclusion."),
            insight("Sanitation opportunity detector", min(100, sanitationTerms * 12), "$sanitationTerms jobs mention contamination, odor, carcasses, or cleanup", "Document contamination and quote only justified remediation with proper PPE."),
            insight("Appointment data quality", risk(unscheduled + overdue, open.size), "${unscheduled + overdue} open jobs are unscheduled or overdue", "Give every accepted job a current appointment or explicit follow-up date."),
            insight("High-value pipeline guard", risk(highValueOpen, open.size), "$highValueOpen open jobs exceed the adaptive high-value threshold", "Review high-value estimates daily for next action, schedule, and customer response."),
            insight("Actual-cost capture coach", risk(missingActual, completed.size), "$missingActual completed jobs lack actual cost", "Record labor and material cost at closeout to improve future estimates."),
            insight("Property prevention planner", min(100, repeatProperties * 16), "$repeatProperties properties have repeat work history", "Build a property-specific exclusion checklist from every prior visit and repair."),
            insight("Operational data trust score", risk(incomplete + syncFailures, max(1, jobs.size)), "$incomplete incomplete records and $syncFailures sync failures reduce confidence", "Correct the lowest-quality and failed-sync records before using forecasts for decisions.")
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
