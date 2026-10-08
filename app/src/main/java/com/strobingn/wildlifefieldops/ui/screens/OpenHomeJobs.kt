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
    const val HOME_CAP = 8

    fun list(jobs: List<Job>): List<Job> =
        jobs.filterNot { OpsLedger.isLedger(it) }
            .filterNot { it.status.isInspectionOnly() }
            .filter { JobStatusPipeline.flag(it.status) != JobStatus.COMPLETED }
            .sortedWith(
                compareBy<Job> { it.scheduledDate ?: Long.MAX_VALUE }
                    .thenBy { it.customerName.lowercase() }
                    .thenBy { it.id }
            )

    /** First [HOME_CAP] open jobs, plus the see-all label when more exist. */
    fun homeSlice(jobs: List<Job>): HomeSlice {
        val open = list(jobs)
        val shown = open.take(HOME_CAP)
        val seeAll = if (open.size > HOME_CAP) "See all open jobs (${open.size})" else null
        return HomeSlice(total = open.size, shown = shown, seeAllLabel = seeAll)
    }

    data class HomeSlice(
        val total: Int,
        val shown: List<Job>,
        val seeAllLabel: String?
    )

    /**
     * Jobs tab filter. Open means Scheduled and In progress, and drops Completed.
     * A scheduled inspection is not a job yet: it shows only under the Inspection chip.
     */
    fun matchesJobList(status: JobStatus, selected: JobStatus?, openOnly: Boolean): Boolean = when {
        status.isInspectionOnly() -> selected == JobStatus.INSPECTION
        openOnly -> JobStatusPipeline.flag(status) != JobStatus.COMPLETED
        selected == null -> true
        else -> JobStatusPipeline.matches(status, selected)
    }
}
