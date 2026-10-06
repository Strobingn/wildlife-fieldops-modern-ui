package com.strobingn.wildlifefieldops.ui.screens

import com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline
import com.strobingn.wildlifefieldops.ai.fieldops.OpsLedger
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus

/**
 * Home's last section: every job still Scheduled or In progress.
 * Completed (including paid, closed, and cancelled) stays off this list.
 * Soonest appointment first. A job with no time sits after the dated ones.
 */
object OpenHomeJobs {
    fun list(jobs: List<Job>): List<Job> =
        jobs.filterNot { OpsLedger.isLedger(it) }
            .filter { JobStatusPipeline.flag(it.status) != JobStatus.COMPLETED }
            .sortedWith(
                compareBy<Job> { it.scheduledDate ?: Long.MAX_VALUE }
                    .thenBy { it.customerName.lowercase() }
                    .thenBy { it.id }
            )
}
