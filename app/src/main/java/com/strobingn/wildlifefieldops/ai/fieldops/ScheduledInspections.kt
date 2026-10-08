package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import java.text.SimpleDateFormat
import java.util.Calendar
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

    /** One day's worth of scheduled inspections for the Scheduled tab. */
    data class Section(val label: String, val jobs: List<Job>)

    /**
     * Today, Tomorrow, then each later day in order; then undated ones; then past
     * appointments that still need approved or declined, most recent first.
     */
    fun sections(
        jobs: List<Job>,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault()
    ): List<Section> {
        val all = list(jobs, now)
        val today = startOfDay(now, zone)
        val tomorrow = startOfDay(today + DAY_MS + DAY_MS / 2, zone)
        val dated = all.filter { it.scheduledDate != null && it.scheduledDate >= today }
        val undated = all.filter { it.scheduledDate == null }
        val past = all.filter { it.scheduledDate != null && it.scheduledDate < today }
        val byDay = dated.groupBy { startOfDay(it.scheduledDate!!, zone) }.toSortedMap()
        val out = mutableListOf<Section>()
        byDay.forEach { (day, rows) ->
            val label = when (day) {
                today -> "Today"
                tomorrow -> "Tomorrow"
                else -> SimpleDateFormat("EEE MMM d", Locale.US).apply { timeZone = zone }.format(Date(day))
            }
            out += Section(label, rows.sortedBy { it.scheduledDate })
        }
        if (undated.isNotEmpty()) out += Section("No time set", undated)
        if (past.isNotEmpty()) out += Section("Past — mark approved or declined", past.sortedByDescending { it.scheduledDate })
        return out
    }

    /** Case-insensitive match on customer, title, address and notes. Blank query keeps all. */
    fun matches(job: Job, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return listOf(job.customerName, job.title, job.address, job.notes).any { it.contains(q, ignoreCase = true) }
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun startOfDay(millis: Long, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
