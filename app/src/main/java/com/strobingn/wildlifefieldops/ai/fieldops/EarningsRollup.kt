package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.pricing.Money
import java.util.Calendar

data class EarningsWindow(
    val paid: Double,
    val invoiced: Double,
    val estimated: Double,
    val invoiceCount: Int,
    val jobCount: Int
)

object EarningsRollup {

    fun dayBounds(now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        // add(DATE) follows the wall clock; start + 24h is wrong on the 23h/25h DST days.
        cal.add(Calendar.DATE, 1)
        return start to cal.timeInMillis
    }

    fun weekBounds(now: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.DATE, 7)
        return start to cal.timeInMillis
    }

    fun summarize(
        invoices: List<Invoice>,
        jobs: List<Job>,
        start: Long,
        end: Long
    ): EarningsWindow {
        val periodInvoices = invoices.filter { stamp(it) in start until end }
        val paid = periodInvoices.filter { it.status == InvoiceStatus.PAID }.sumOf { it.amountPaid.takeIf { p -> p > 0 } ?: it.totalAmount }
        val invoiced = periodInvoices
            .filter { it.status != InvoiceStatus.CANCELLED && it.status != InvoiceStatus.DRAFT }
            .sumOf { it.totalAmount }
        val periodJobs = jobs.filter { jobStamp(it) in start until end && it.status != JobStatus.CANCELLED }
        val estimated = periodJobs.sumOf { it.estimatedValue }
        return EarningsWindow(
            paid = Money.round(paid),
            invoiced = Money.round(invoiced),
            estimated = Money.round(estimated),
            invoiceCount = periodInvoices.size,
            jobCount = periodJobs.size
        )
    }

    private fun stamp(invoice: Invoice): Long = invoice.issueDate.takeIf { it > 0L } ?: invoice.createdAt

    private fun jobStamp(job: Job): Long = job.completedDate ?: job.scheduledDate ?: job.createdAt
}
