package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPricing

/**
 * Field-ops extras live on [Job] columns (Room query/UI) and inside
 * [JobPricing] so they sync on the existing live `jobs.pricing` jsonb
 * without adding PostgREST keys that 400 when the SQL file is not applied yet.
 */
object JobFieldOpsCodec {

    fun mergeForSave(job: Job): Job {
        val fromPricing = extract(job.pricing)
        val extras = JobFieldOps(
            confirmedSpecies = job.confirmedSpecies.ifBlank { fromPricing.confirmedSpecies },
            legalNotes = job.legalNotes.ifBlank { fromPricing.legalNotes },
            nextStep = job.nextStep.ifBlank { fromPricing.nextStep },
            nextStepDueAt = job.nextStepDueAt ?: fromPricing.nextStepDueAt,
            nextStepSource = job.nextStepSource.ifBlank { fromPricing.nextStepSource },
            aiRuntime = job.aiRuntime.ifBlank { fromPricing.aiRuntime },
            photoLineItems = job.pricing.photoLineItems.ifEmpty { fromPricing.photoLineItems }
        )
        return job.copy(
            confirmedSpecies = extras.confirmedSpecies,
            legalNotes = extras.legalNotes,
            nextStep = extras.nextStep,
            nextStepDueAt = extras.nextStepDueAt,
            nextStepSource = extras.nextStepSource,
            aiRuntime = extras.aiRuntime,
            pricing = embed(job.pricing, extras)
        )
    }

    fun applyFromPricing(job: Job): Job {
        val extras = extract(job.pricing)
        return job.copy(
            confirmedSpecies = job.confirmedSpecies.ifBlank { extras.confirmedSpecies },
            legalNotes = job.legalNotes.ifBlank { extras.legalNotes },
            nextStep = job.nextStep.ifBlank { extras.nextStep },
            nextStepDueAt = job.nextStepDueAt ?: extras.nextStepDueAt,
            nextStepSource = job.nextStepSource.ifBlank { extras.nextStepSource },
            aiRuntime = job.aiRuntime.ifBlank { extras.aiRuntime }
        )
    }

    fun embed(pricing: JobPricing, extras: JobFieldOps): JobPricing = pricing.copy(
        confirmedSpecies = extras.confirmedSpecies,
        legalNotes = extras.legalNotes,
        nextStep = extras.nextStep,
        nextStepDueAt = extras.nextStepDueAt,
        nextStepSource = extras.nextStepSource,
        aiRuntime = extras.aiRuntime,
        photoLineItems = extras.photoLineItems.ifEmpty { pricing.photoLineItems }
    )

    fun extract(pricing: JobPricing): JobFieldOps = JobFieldOps(
        confirmedSpecies = pricing.confirmedSpecies,
        legalNotes = pricing.legalNotes,
        nextStep = pricing.nextStep,
        nextStepDueAt = pricing.nextStepDueAt,
        nextStepSource = pricing.nextStepSource,
        aiRuntime = pricing.aiRuntime,
        photoLineItems = pricing.photoLineItems
    )
}

data class JobFieldOps(
    val confirmedSpecies: String = "",
    val legalNotes: String = "",
    val nextStep: String = "",
    val nextStepDueAt: Long? = null,
    val nextStepSource: String = "",
    val aiRuntime: String = "",
    val photoLineItems: List<InvoiceLineItem> = emptyList()
)
