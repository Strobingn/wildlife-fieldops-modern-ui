package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus

/**
 * Hidden job that holds operator-level extras (earnings adjustments, extra DEC rows,
 * NWCO profile) inside [com.strobingn.wildlifefieldops.pricing.JobPricing] so AutoSync
 * pushes them on live `jobs.pricing` jsonb. Never shown as a customer job.
 */
object OpsLedger {
    const val ID = "fieldops-ops-ledger"
    const val TITLE = "Ops ledger"

    fun isLedger(job: Job): Boolean = job.id == ID

    fun isLedgerId(id: String): Boolean = id == ID

    fun newJob(): Job = Job(
        id = ID,
        title = TITLE,
        description = "Internal FieldOps ledger for earnings adjustments and DEC log extras. Not a customer job.",
        status = JobStatus.CANCELLED,
        notes = "ops-ledger"
    )
}
