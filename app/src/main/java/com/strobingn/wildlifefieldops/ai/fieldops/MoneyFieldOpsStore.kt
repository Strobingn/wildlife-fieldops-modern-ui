package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.local.ExpenseDao
import com.strobingn.wildlifefieldops.data.local.InvoiceDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.VisitDao
import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Visit
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.SyncedInvoiceRecord
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class MoneyFieldOpsStore @Inject constructor(
    private val jobDao: JobDao,
    private val visitDao: VisitDao,
    private val invoiceDao: InvoiceDao,
    private val expenseDao: ExpenseDao
) {

    suspend fun timerState(job: Job): JobTimerState = JobTimerState(
        startedAt = job.pricing.timerStartedAt,
        elapsedMs = job.pricing.timerElapsedMs
    )

    suspend fun startTimer(jobId: String) {
        val job = jobDao.getById(jobId) ?: return
        val next = JobTimer.start(timerState(job))
        val now = next.startedAt ?: System.currentTimeMillis()
        persistPricing(job) { it.copy(timerStartedAt = next.startedAt, timerElapsedMs = next.elapsedMs) }
        val open = visitDao.getByJobOnce(jobId).firstOrNull { it.startTime != null && it.endTime == null }
        if (open == null) {
            visitDao.insert(
                Visit(
                    jobId = jobId,
                    customerId = job.customerId,
                    customerName = job.customerName,
                    technicianName = job.assignedTo,
                    visitDate = now,
                    startTime = now,
                    isCompleted = false,
                    isSynced = false
                )
            )
        }
    }

    suspend fun stopTimer(jobId: String) {
        val job = jobDao.getById(jobId) ?: return
        val next = JobTimer.stop(timerState(job))
        persistPricing(job) { it.copy(timerStartedAt = null, timerElapsedMs = next.elapsedMs) }
        val now = System.currentTimeMillis()
        visitDao.getByJobOnce(jobId)
            .filter { it.startTime != null && it.endTime == null }
            .forEach { visit ->
                visitDao.insert(
                    visit.copy(
                        endTime = now,
                        isCompleted = true,
                        updatedAt = now,
                        isSynced = false,
                        notes = visit.notes.ifBlank { "On-site timer ${JobTimer.format(next.elapsedMs)}" }
                    )
                )
            }
    }

    suspend fun setElapsedMinutes(jobId: String, minutes: Long) {
        val job = jobDao.getById(jobId) ?: return
        val next = JobTimer.withTypedMinutes(timerState(job), minutes)
        persistPricing(job) { it.copy(timerElapsedMs = next.elapsedMs, timerStartedAt = next.startedAt) }
    }

    suspend fun saveProfitInputs(jobId: String, materialsCost: Double, laborCostOverride: Double?, paidAmount: Double) {
        val job = jobDao.getById(jobId) ?: return
        persistPricing(job) {
            it.copy(
                materialsCostActual = materialsCost,
                laborCostOverride = laborCostOverride,
                paidAmount = paidAmount
            )
        }
    }

    suspend fun profitFor(job: Job): ProfitResult {
        val expenses = expenseDao.getByJob(job.id).first().sumOf { if (it.totalAmount > 0) it.totalAmount else it.amount }
        val timerHours = timerState(job).displayedMinutes() / 60.0
        val laborHours = if (timerHours > 0) timerHours else job.pricing.laborHours
        val materials = if (job.pricing.materialsCostActual > 0) {
            job.pricing.materialsCostActual
        } else {
            job.pricing.materialsTotalOverride
                ?: (job.pricing.materialsQty * job.pricing.materialsPrice)
        }
        val mileageCost = job.pricing.mileageLogs.sumOf { it.amount }.takeIf { it > 0 }
            ?: (job.pricing.mileage * job.pricing.mileageRate)
        return JobProfit.compute(
            ProfitInput(
                quoted = job.estimatedValue,
                paid = job.pricing.paidAmount,
                materialsCost = materials,
                laborHours = laborHours,
                laborRate = job.pricing.laborRate,
                laborCostOverride = job.pricing.laborCostOverride,
                expenses = expenses,
                mileageCost = mileageCost
            )
        )
    }

    suspend fun saveMileage(entry: MileageLogEntry) {
        val jobId = entry.jobId
        if (jobId.isBlank()) return
        val job = jobDao.getById(jobId) ?: return
        val id = entry.id.ifBlank { UUID.randomUUID().toString() }
        val saved = entry.copy(id = id, jobTitle = entry.jobTitle.ifBlank { job.title.ifBlank { job.customerName } })
        val next = job.pricing.mileageLogs.filterNot { it.id == id } + saved
        persistPricing(job) { it.copy(mileageLogs = next.sortedBy { log -> log.date }) }
    }

    suspend fun deleteMileage(jobId: String, entryId: String) {
        val job = jobDao.getById(jobId) ?: return
        persistPricing(job) { it.copy(mileageLogs = it.mileageLogs.filterNot { log -> log.id == entryId }) }
    }

    suspend fun allMileage(): List<MileageLogEntry> =
        jobDao.getAllOnce().flatMap { it.pricing.mileageLogs.map { log ->
            if (log.jobTitle.isBlank()) log.copy(jobTitle = it.title.ifBlank { it.customerName }, jobId = log.jobId.ifBlank { it.id })
            else log
        } }.sortedBy { it.date }

    suspend fun saveInvoice(invoice: Invoice) {
        invoiceDao.insert(invoice.copy(isSynced = false, updatedAt = System.currentTimeMillis()))
        val job = jobDao.getById(invoice.jobId) ?: return
        embedInvoices(job)
    }

    suspend fun hydrateFromJob(job: Job) {
        job.pricing.invoiceRecords.forEach { rec ->
            val local = invoiceDao.getById(rec.id)
            if (local == null) {
                invoiceDao.insert(rec.toInvoice(job))
            }
        }
    }

    private suspend fun embedInvoices(job: Job) {
        val invoices = invoiceDao.getByJobOnce(job.id)
        persistPricing(job) { pricing ->
            pricing.copy(invoiceRecords = invoices.map { it.toSynced() })
        }
    }

    private suspend fun persistPricing(job: Job, update: (JobPricing) -> JobPricing) {
        val now = System.currentTimeMillis()
        val next = job.copy(
            pricing = update(job.pricing),
            updatedAt = now,
            isSynced = false
        )
        jobDao.insert(JobFieldOpsCodec.mergeForSave(next))
    }
}

fun Invoice.toSynced(): SyncedInvoiceRecord = SyncedInvoiceRecord(
    id = id,
    invoiceNumber = invoiceNumber,
    status = status.name,
    totalAmount = totalAmount,
    amountPaid = amountPaid,
    balanceDue = balanceDue,
    dueDate = dueDate,
    issueDate = issueDate,
    customerName = customerName,
    customerEmail = customerEmail
)

fun SyncedInvoiceRecord.toInvoice(job: Job): Invoice = Invoice(
    id = id.ifBlank { UUID.randomUUID().toString() },
    invoiceNumber = invoiceNumber,
    jobId = job.id,
    customerId = job.customerId,
    customerName = customerName.ifBlank { job.customerName },
    customerEmail = customerEmail,
    customerAddress = job.address,
    issueDate = issueDate,
    dueDate = dueDate,
    status = runCatching { InvoiceStatus.valueOf(status) }.getOrDefault(InvoiceStatus.DRAFT),
    totalAmount = totalAmount,
    amountPaid = amountPaid,
    balanceDue = balanceDue,
    isSynced = false
)
