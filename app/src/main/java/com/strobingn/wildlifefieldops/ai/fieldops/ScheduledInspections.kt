package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A scheduled inspection is a job row with [JobStatus.INSPECTION]: same customer, address,
 * appointment, calendar visit, directions and texting as a job, but no work agreed yet.
 * The customer's yes flips the same row to [JobStatus.SCHEDULED], so the inspection report,
 * photos and notes stay attached to the job it becomes.
 */
object ScheduledInspections {

    /** Upcoming first, soonest at the top; undated ones after, then past ones most recent first. */
    fun list(jobs: List<Job>, now: Long = System.currentTimeMillis()): List<Job> {
        val inspections = jobs.filter { it.status.isInspectionOnly() }
        val upcoming = inspections.filter { (it.scheduledDate ?: Long.MAX_VALUE) >= now }
            .sortedWith(compareBy<Job> { it.scheduledDate ?: Long.MAX_VALUE }.thenBy { it.customerName.lowercase() })
        val past = inspections.filter { (it.scheduledDate ?: Long.MAX_VALUE) < now }
            .sortedByDescending { it.scheduledDate }
        return upcoming + past
    }

    /** Status the row takes when the customer approves the work. */
    val APPROVED_STATUS: JobStatus = JobStatus.SCHEDULED

    /** Status the row takes when the customer passes after the inspection. */
    val DECLINED_STATUS: JobStatus = JobStatus.CANCELLED

    fun canApprove(job: Job): Boolean = job.status.isInspectionOnly()

    /** "Tue Oct 14 at 9:00 AM" for customer texts; blank when no appointment is set. */
    fun appointmentText(millis: Long?, zone: TimeZone = TimeZone.getDefault()): String {
        if (millis == null || millis <= 0L) return ""
        val day = SimpleDateFormat("EEE MMM d", Locale.US).apply { timeZone = zone }.format(Date(millis))
        val time = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = zone }.format(Date(millis))
        return "$day at $time"
    }
}
