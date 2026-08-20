package com.strobingn.wildlifefieldops.ai.operations

import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.remote.AiService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class AIFeatureScope {
    FOCUS_JOB,
    PORTFOLIO
}

data class RealAIFeature(
    val id: String,
    val title: String,
    val description: String,
    val scope: AIFeatureScope,
    val instruction: String
)

data class RealAIFeatureResult(
    val featureId: String,
    val title: String,
    val output: String,
    val provider: String,
    val success: Boolean
)

/**
 * Catalog of interactive features that only count as "AI" when a configured remote
 * LLM actually answers. Deterministic analytics remain in [AIOperationsEngine] and
 * are intentionally presented separately in the UI.
 */
object RealAIFeatureCatalog {
    val features: List<RealAIFeature> = listOf(
        RealAIFeature(
            "inspection_narrative",
            "Inspection Narrative Generator",
            "Turns the selected job's evidence and notes into a professional field inspection narrative.",
            AIFeatureScope.FOCUS_JOB,
            "Write a factual inspection narrative from the selected job. Separate observed evidence from inference. Include access areas inspected, signs found, likely entry mechanisms, unresolved questions, and recommended documentation. Do not invent observations."
        ),
        RealAIFeature(
            "scope_of_work",
            "Scope-of-Work Builder",
            "Builds a job-ready scope with labor, materials, access, exclusions, monitoring, and closeout steps.",
            AIFeatureScope.FOCUS_JOB,
            "Create a detailed scope of work for the selected job using only supplied facts. Break it into preparation, removal/exclusion, repair, monitoring/follow-up, cleanup, and closeout. Flag assumptions that require field verification."
        ),
        RealAIFeature(
            "exclusion_plan",
            "Exclusion Plan Architect",
            "Develops a prioritized structural exclusion plan from the selected property's history and current notes.",
            AIFeatureScope.FOCUS_JOB,
            "Develop a prioritized exclusion plan for the selected job. Identify likely primary and secondary entry zones supported by the record, sequencing, materials categories, one-way strategy considerations, verification steps, and conditions that should delay sealing. Never claim an opening was observed unless the data says so."
        ),
        RealAIFeature(
            "next_visit",
            "Next-Visit Planner",
            "Creates the next field visit checklist based on what is still unresolved.",
            AIFeatureScope.FOCUS_JOB,
            "Plan the next visit for the selected job. Produce an ordered field checklist, what evidence to re-check, equipment/material categories to bring, customer questions, completion criteria, and specific reasons a return visit may still be needed."
        ),
        RealAIFeature(
            "handoff",
            "Technician Handoff Brief",
            "Condenses the selected job into a fast operational handoff for another technician.",
            AIFeatureScope.FOCUS_JOB,
            "Create a technician handoff brief for the selected job. Include current status, customer/site facts, species/service context, work already documented, open risks, promised next steps, access constraints, financial context that matters in the field, and the first three actions on arrival."
        ),
        RealAIFeature(
            "customer_update",
            "Customer Update Writer",
            "Drafts a factual customer-facing progress update without exposing internal notes unnecessarily.",
            AIFeatureScope.FOCUS_JOB,
            "Draft a concise customer update for the selected job. Use plain language, state only verified facts, explain what has been completed and what remains, give the next step, and identify any scheduling or access need. Do not make legal guarantees or invent warranty terms."
        ),
        RealAIFeature(
            "estimate_review",
            "Estimate Assumption Reviewer",
            "Reviews whether the selected estimate appears to account for labor, materials, access difficulty, follow-ups, and risk.",
            AIFeatureScope.FOCUS_JOB,
            "Review the selected job's estimate assumptions. Do not produce a new price unless the record supports it. Identify cost drivers that appear included, cost drivers that may be missing, uncertainty, possible return-visit exposure, and questions that should be answered before the quote is finalized."
        ),
        RealAIFeature(
            "invoice_narrative",
            "Invoice Narrative Builder",
            "Creates a customer-readable description of documented work for invoicing and records.",
            AIFeatureScope.FOCUS_JOB,
            "Write an invoice/work-completion narrative from the selected job. Describe documented work performed, materials/categories if known, follow-up status, and outcome. Never claim work was completed if the job record does not support it."
        ),
        RealAIFeature(
            "callback_root_cause",
            "Callback Root-Cause Analyst",
            "Uses property history to reason about why repeat activity may have occurred.",
            AIFeatureScope.FOCUS_JOB,
            "Analyze the selected property's current and prior job context for plausible callback/repeat-intrusion causes. Rank hypotheses, cite the supplied facts supporting each, list evidence that would confirm or reject each hypothesis, and recommend the next inspection sequence. Do not state a hypothesis as fact."
        ),
        RealAIFeature(
            "property_history",
            "Property History Synthesizer",
            "Combines all recorded visits for the selected address into one operational history.",
            AIFeatureScope.FOCUS_JOB,
            "Synthesize the selected property's history across the supplied job records. Build a timeline, recurring species/service patterns, prior work themes, financial/closeout gaps, repeat-risk factors, and what a technician should verify before starting new work."
        ),
        RealAIFeature(
            "evidence_differential",
            "Species Evidence Differential",
            "Reasons through multiple plausible species from the actual evidence instead of making a single unsupported ID.",
            AIFeatureScope.FOCUS_JOB,
            "From the selected job's evidence and wording, produce a species differential. Rank plausible species, list evidence for and against each, identify missing discriminating evidence, and recommend non-invasive confirmation steps. Explicitly state uncertainty and do not fabricate tracks, droppings, sounds, or photos."
        ),
        RealAIFeature(
            "entry_point_reasoning",
            "Entry-Point Reasoning Assistant",
            "Builds a ranked search strategy for likely access points based on species, structure notes, and prior work.",
            AIFeatureScope.FOCUS_JOB,
            "Create a ranked entry-point search strategy for the selected job. Tie each search zone to the supplied species/service and building clues, explain what signs to look for, and distinguish observed openings from places that are merely likely inspection targets."
        ),
        RealAIFeature(
            "photo_plan",
            "Photo Documentation Planner",
            "Generates a specific before/during/after photo shot list for the selected job.",
            AIFeatureScope.FOCUS_JOB,
            "Create a photo documentation plan for the selected job. Include establishing shots, evidence close-ups, entry points, measurements where relevant, pre-repair condition, repair progression, completed work, and any follow-up comparison shots. Tailor it to the supplied job facts."
        ),
        RealAIFeature(
            "safety_brief",
            "Jobsite Safety Brief",
            "Produces a pre-job hazard brief from access, species, contamination, roof, attic, and customer-site clues.",
            AIFeatureScope.FOCUS_JOB,
            "Produce a pre-job safety brief for the selected job. Identify hazards supported by the record, plausible hazards that require verification, PPE/access-control categories, stop-work triggers, and questions to resolve before elevated, confined-space, bite-risk, or contamination work."
        ),
        RealAIFeature(
            "dependent_young",
            "Dependent-Young Decision Support",
            "Flags evidence that could change exclusion timing and lays out what must be verified before work proceeds.",
            AIFeatureScope.FOCUS_JOB,
            "Review the selected job for indications of nests, maternity activity, pups, kits, fledglings, or dependent young. Separate evidence from assumptions, identify what must be verified in the field, and explain how dependent young could affect sequencing. Tell the technician to verify current local/state requirements rather than inventing legal rules."
        ),
        RealAIFeature(
            "sanitation_scope",
            "Sanitation Scope Planner",
            "Turns contamination evidence into a documented remediation/cleanup work plan.",
            AIFeatureScope.FOCUS_JOB,
            "Create a sanitation/remediation scope from the selected job's documented contamination, odor, carcass, feces, urine, nesting, or attic/crawlspace evidence. Include containment/PPE categories, removal sequence, documentation, surfaces/areas needing verification, and what cannot be priced without additional inspection."
        ),
        RealAIFeature(
            "trap_strategy",
            "Trap/Monitoring Strategy Planner",
            "Builds a monitoring/removal decision plan from the job facts and service type.",
            AIFeatureScope.FOCUS_JOB,
            "Create a humane trap/monitoring strategy for the selected job at a high operational level. Cover objectives, placement logic categories, monitoring evidence, escalation/de-escalation criteria, exclusion coordination, and recordkeeping. Do not invent local legal requirements; tell the operator to confirm applicable regulations and label directions."
        ),
        RealAIFeature(
            "warranty_review",
            "Warranty Exposure Reviewer",
            "Examines the selected job and address history for conditions that could create callback or warranty ambiguity.",
            AIFeatureScope.FOCUS_JOB,
            "Review the selected job for warranty/callback exposure. Identify ambiguous scope, undocumented pre-existing conditions, secondary openings, incomplete evidence, follow-up dependencies, and customer-expectation risks. Suggest documentation that would make the scope and completion status clearer."
        ),
        RealAIFeature(
            "customer_objection",
            "Customer Objection Coach",
            "Creates factual talking points for likely questions about price, timing, repeat activity, and exclusion scope.",
            AIFeatureScope.FOCUS_JOB,
            "Based on the selected job, anticipate likely customer objections or questions and create factual response points. Cover price drivers, timing, why verification/follow-up may be needed, the difference between removal and exclusion, and uncertainty. Avoid pressure tactics and unsupported claims."
        ),
        RealAIFeature(
            "quote_audit",
            "Quote Completeness Auditor",
            "Audits the selected job record for missing information that could make a quote unreliable.",
            AIFeatureScope.FOCUS_JOB,
            "Audit the selected job for quote completeness. List missing facts, unclear scope boundaries, access unknowns, materials/labor uncertainties, follow-up assumptions, disposal/sanitation considerations, and documentation gaps. End with a go/no-go recommendation for finalizing the quote and explain why."
        ),
        RealAIFeature(
            "dispatch_brief",
            "Daily Dispatch Intelligence Brief",
            "Analyzes the active schedule and produces a prioritized dispatch brief across jobs.",
            AIFeatureScope.PORTFOLIO,
            "Create a daily dispatch intelligence brief from all supplied active jobs. Prioritize urgent/overdue/scheduled work, call out missing addresses or assignments, identify likely long or difficult-access stops, highlight customer follow-ups, and give a recommended dispatch sequence without fabricating travel times."
        ),
        RealAIFeature(
            "inventory_plan",
            "Inventory Purchase Planner",
            "Infers near-term material and equipment categories from the current pipeline and recent service mix.",
            AIFeatureScope.PORTFOLIO,
            "Analyze the supplied job pipeline and recent history to create an inventory purchase/pre-stage plan. Rank material/equipment categories by expected need, explain which jobs create demand, distinguish consumables from reusable gear, and flag where quantities cannot be inferred from the data."
        ),
        RealAIFeature(
            "seasonal_operations",
            "Seasonal Operations Advisor",
            "Combines current job mix with season-sensitive field considerations for staffing, exclusions, and customer communication.",
            AIFeatureScope.PORTFOLIO,
            "Create a seasonal operations advisory using the supplied job mix and the current date implied by the system. Identify service categories that deserve attention, scheduling/staffing implications, dependent-young or seasonal verification issues, customer-communication themes, and what the local operator should verify in current regulations."
        ),
        RealAIFeature(
            "technician_coach",
            "Technician Documentation Coach",
            "Reviews records for patterns that would make field notes, photos, closeout, and handoffs stronger.",
            AIFeatureScope.PORTFOLIO,
            "Review the supplied jobs as a documentation coach. Find recurring weaknesses in notes, photos, cost capture, assignment, closeout, scheduling, and evidence language. Give a prioritized coaching checklist with examples based on the record, while avoiding invented facts."
        ),
        RealAIFeature(
            "weekly_business_brief",
            "Weekly Business Intelligence Brief",
            "Generates an owner-level operational brief from live job, revenue, completion, service, and documentation data.",
            AIFeatureScope.PORTFOLIO,
            "Create an owner-level weekly business intelligence brief from the supplied jobs. Cover pipeline, completions, quoted value versus actual recorded cost, service mix, repeat properties, overdue/unscheduled work, documentation quality, operational risks, and the five highest-value actions for the next week. State data limitations clearly."
        )
    )

    fun byId(id: String): RealAIFeature? = features.firstOrNull { it.id == id }
}

@Singleton
class RealAIFeatureService @Inject constructor(
    private val aiService: AiService
) {
    val isConfigured: Boolean get() = aiService.isConfigured
    val providerLabel: String get() = aiService.providerLabel

    suspend fun run(
        featureId: String,
        jobs: List<Job>,
        focusJobId: String?
    ): RealAIFeatureResult {
        val feature = RealAIFeatureCatalog.byId(featureId)
            ?: return RealAIFeatureResult(featureId, "Unknown AI feature", "Feature id '$featureId' is not registered.", providerLabel, false)

        if (!isConfigured) {
            return RealAIFeatureResult(
                feature.id,
                feature.title,
                "Live AI is not configured in this APK. Add the repository secret XAI_API_KEY (or LLM_API_KEY) and rebuild. This tool will not substitute heuristic output and label it as AI.",
                providerLabel,
                false
            )
        }

        if (jobs.isEmpty()) {
            return RealAIFeatureResult(
                feature.id,
                feature.title,
                "No job records are available. Add or sync real jobs before running this AI tool.",
                providerLabel,
                false
            )
        }

        val focusJob = focusJobId?.let { id -> jobs.firstOrNull { it.id == id } }
            ?: jobs.maxByOrNull { it.updatedAt }

        if (feature.scope == AIFeatureScope.FOCUS_JOB && focusJob == null) {
            return RealAIFeatureResult(feature.id, feature.title, "Select a job before running this tool.", providerLabel, false)
        }

        val prompt = buildPrompt(feature, jobs, focusJob)
        val output = aiService.ask(prompt)
        val success = !looksLikeTransportFailure(output)

        return RealAIFeatureResult(
            feature.id,
            feature.title,
            if (success) output.trim() else output.substringBefore("\n\n").trim(),
            providerLabel,
            success
        )
    }

    private fun buildPrompt(feature: RealAIFeature, jobs: List<Job>, focusJob: Job?): String = buildString {
        appendLine("LIVE AI OPERATIONS TOOL: ${feature.title}")
        appendLine("Task: ${feature.instruction}")
        appendLine()
        appendLine("Grounding rules:")
        appendLine("- Use only the job data below plus general wildlife-control knowledge.")
        appendLine("- Never invent an observation, customer promise, completed repair, price, date, permit, or regulation.")
        appendLine("- Clearly label inference, uncertainty, and facts that require field verification.")
        appendLine("- When law or regulation matters, say what should be verified with the current authority instead of fabricating a rule.")
        appendLine("- Make the output operational and specific, not generic filler.")
        appendLine()

        if (feature.scope == AIFeatureScope.FOCUS_JOB && focusJob != null) {
            appendLine("=== SELECTED FOCUS JOB ===")
            appendLine(jobContext(focusJob))
            val sameAddress = jobs.filter {
                it.id != focusJob.id &&
                    focusJob.address.isNotBlank() &&
                    it.address.trim().equals(focusJob.address.trim(), ignoreCase = true)
            }.sortedByDescending { it.updatedAt }.take(8)
            if (sameAddress.isNotEmpty()) {
                appendLine("=== PRIOR/RELATED RECORDS AT SAME ADDRESS ===")
                sameAddress.forEachIndexed { index, job ->
                    appendLine("Related ${index + 1}:")
                    appendLine(jobContext(job))
                }
            }
        }

        appendLine("=== PORTFOLIO CONTEXT (MOST RECENT ${minOf(30, jobs.size)} OF ${jobs.size}) ===")
        jobs.sortedByDescending { it.updatedAt }.take(30).forEachIndexed { index, job ->
            appendLine("Job ${index + 1}:")
            appendLine(jobContext(job))
        }
        appendLine("=== END DATA ===")
        appendLine("Produce the requested result now.")
    }

    private fun jobContext(job: Job): String = buildString {
        appendLine("id=${job.id}")
        appendLine("title=${job.title.ifBlank { "(blank)" }}")
        appendLine("service=${job.type}")
        appendLine("status=${job.status}; priority=${job.priority}")
        appendLine("customer=${job.customerName.ifBlank { "(blank)" }}")
        appendLine("address=${job.address.ifBlank { "(blank)" }}")
        appendLine("assignedTo=${job.assignedTo.ifBlank { "(unassigned)" }}")
        appendLine("estimatedValue=${job.estimatedValue}; actualCost=${job.actualCost}")
        appendLine("created=${formatTime(job.createdAt)}; updated=${formatTime(job.updatedAt)}; scheduled=${formatTime(job.scheduledDate)}; completed=${formatTime(job.completedDate)}")
        appendLine("description=${job.description.take(320).ifBlank { "(blank)" }}")
        appendLine("notes=${job.notes.take(420).ifBlank { "(blank)" }}")
        appendLine("photoCount=${job.photos.size}; synced=${job.isSynced}; syncError=${job.syncError ?: "none"}")
    }

    private fun formatTime(value: Long?): String {
        if (value == null || value <= 0L) return "none"
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(value))
    }

    private fun looksLikeTransportFailure(text: String): Boolean {
        val normalized = text.trim().lowercase(Locale.US)
        return normalized.startsWith("⚠️") ||
            normalized.startsWith("network error:") ||
            normalized.startsWith("ai is not configured") ||
            normalized.contains("invalid api key (http 401)") ||
            normalized.contains("ai rate limit (http 429)") ||
            normalized.contains("model/endpoint not found (http 404)")
    }
}
