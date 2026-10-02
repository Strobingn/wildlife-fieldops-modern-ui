package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.SyncedTrapRecord
import com.strobingn.wildlifefieldops.pricing.isManual

/**
 * Field-ops extras live on [Job] columns (Room query/UI) and inside
 * [JobPricing] so they sync on the existing live `jobs.pricing` jsonb
 * without adding PostgREST keys that 400 when the SQL file is not applied yet.
 *
 * Empty string is a real value. On save, job columns win (including blank).
 * On pull, pricing wins when the field is marked manual or the column is blank
 * only because live PostgREST has no dedicated extras columns.
 */
object JobFieldOpsCodec {

    /**
     * Job columns are what Sir typed. Blank stays blank and is written into
     * pricing so a later read cannot refill the field from the previous save.
     */
    fun mergeForSave(job: Job): Job {
        val advice = job.weatherTrapAdvice
        val extras = JobFieldOps(
            confirmedSpecies = job.confirmedSpecies,
            legalNotes = job.legalNotes,
            nextStep = job.nextStep,
            nextStepDueAt = job.nextStepDueAt,
            nextStepSource = job.nextStepSource,
            aiRuntime = job.aiRuntime,
            photoLineItems = job.pricing.photoLineItems,
            trapRecords = job.pricing.trapRecords,
            weatherTrapAdvice = advice,
            weatherTrapAdviceAt = if (advice.isBlank()) null else job.pricing.weatherTrapAdviceAt,
            weatherTrapAdviceSource = if (advice.isBlank()) "" else job.pricing.weatherTrapAdviceSource,
            followUpKind = job.followUpKind,
            followUpDueAt = job.followUpDueAt,
            followUpNotes = job.followUpNotes,
            followUpVisitId = job.pricing.followUpVisitId,
            followUpReminderId = job.pricing.followUpReminderId
        )
        return job.copy(
            confirmedSpecies = extras.confirmedSpecies,
            legalNotes = extras.legalNotes,
            nextStep = extras.nextStep,
            nextStepDueAt = extras.nextStepDueAt,
            nextStepSource = extras.nextStepSource,
            aiRuntime = extras.aiRuntime,
            weatherTrapAdvice = extras.weatherTrapAdvice,
            followUpKind = extras.followUpKind,
            followUpDueAt = extras.followUpDueAt,
            followUpNotes = extras.followUpNotes,
            pricing = embed(job.pricing, extras)
        )
    }

    fun applyFromPricing(job: Job): Job {
        val extras = extract(job.pricing)
        val pricing = job.pricing
        return job.copy(
            confirmedSpecies = hydrate(job.confirmedSpecies, extras.confirmedSpecies, pricing.isManual(ManualField.SPECIES)),
            legalNotes = hydrate(job.legalNotes, extras.legalNotes, pricing.isManual(ManualField.LEGAL_NOTES)),
            nextStep = hydrate(job.nextStep, extras.nextStep, pricing.isManual(ManualField.NEXT_STEP)),
            nextStepDueAt = hydrateDue(job.nextStepDueAt, extras.nextStepDueAt, pricing.isManual(ManualField.NEXT_STEP_DUE)),
            nextStepSource = hydrate(job.nextStepSource, extras.nextStepSource, pricing.isManual(ManualField.NEXT_STEP)),
            aiRuntime = job.aiRuntime.ifBlank { extras.aiRuntime },
            weatherTrapAdvice = hydrate(job.weatherTrapAdvice, extras.weatherTrapAdvice, pricing.isManual(ManualField.WEATHER)),
            followUpKind = hydrate(job.followUpKind, extras.followUpKind, pricing.isManual(ManualField.FOLLOW_KIND)),
            followUpDueAt = hydrateDue(job.followUpDueAt, extras.followUpDueAt, pricing.isManual(ManualField.FOLLOW_DUE)),
            followUpNotes = hydrate(job.followUpNotes, extras.followUpNotes, pricing.isManual(ManualField.FOLLOW_NOTES))
        )
    }

    fun embed(pricing: JobPricing, extras: JobFieldOps): JobPricing = pricing.copy(
        confirmedSpecies = extras.confirmedSpecies,
        legalNotes = extras.legalNotes,
        nextStep = extras.nextStep,
        nextStepDueAt = extras.nextStepDueAt,
        nextStepSource = extras.nextStepSource,
        aiRuntime = extras.aiRuntime,
        photoLineItems = extras.photoLineItems.ifEmpty { pricing.photoLineItems },
        trapRecords = extras.trapRecords.ifEmpty { pricing.trapRecords },
        weatherTrapAdvice = extras.weatherTrapAdvice,
        weatherTrapAdviceAt = extras.weatherTrapAdviceAt,
        weatherTrapAdviceSource = extras.weatherTrapAdviceSource,
        followUpKind = extras.followUpKind,
        followUpDueAt = extras.followUpDueAt,
        followUpNotes = extras.followUpNotes,
        followUpVisitId = extras.followUpVisitId,
        followUpReminderId = extras.followUpReminderId
    )

    fun extract(pricing: JobPricing): JobFieldOps = JobFieldOps(
        confirmedSpecies = pricing.confirmedSpecies,
        legalNotes = pricing.legalNotes,
        nextStep = pricing.nextStep,
        nextStepDueAt = pricing.nextStepDueAt,
        nextStepSource = pricing.nextStepSource,
        aiRuntime = pricing.aiRuntime,
        photoLineItems = pricing.photoLineItems,
        trapRecords = pricing.trapRecords,
        weatherTrapAdvice = pricing.weatherTrapAdvice,
        weatherTrapAdviceAt = pricing.weatherTrapAdviceAt,
        weatherTrapAdviceSource = pricing.weatherTrapAdviceSource,
        followUpKind = pricing.followUpKind,
        followUpDueAt = pricing.followUpDueAt,
        followUpNotes = pricing.followUpNotes,
        followUpVisitId = pricing.followUpVisitId,
        followUpReminderId = pricing.followUpReminderId
    )

    private fun hydrate(column: String, pricing: String, manual: Boolean): String {
        if (manual) return pricing
        return column.ifBlank { pricing }
    }

    private fun hydrateDue(column: Long?, pricing: Long?, manual: Boolean): Long? {
        if (manual) return pricing
        return column ?: pricing
    }
}

data class JobFieldOps(
    val confirmedSpecies: String = "",
    val legalNotes: String = "",
    val nextStep: String = "",
    val nextStepDueAt: Long? = null,
    val nextStepSource: String = "",
    val aiRuntime: String = "",
    val photoLineItems: List<InvoiceLineItem> = emptyList(),
    val trapRecords: List<SyncedTrapRecord> = emptyList(),
    val weatherTrapAdvice: String = "",
    val weatherTrapAdviceAt: Long? = null,
    val weatherTrapAdviceSource: String = "",
    val followUpKind: String = "",
    val followUpDueAt: Long? = null,
    val followUpNotes: String = "",
    val followUpVisitId: String = "",
    val followUpReminderId: String = ""
)
