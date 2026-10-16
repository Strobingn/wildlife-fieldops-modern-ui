package com.strobingn.wildlifefieldops.ai.fieldops

import android.content.Context
import com.strobingn.wildlifefieldops.data.local.ExpenseDao
import com.strobingn.wildlifefieldops.data.local.InvoiceDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.VisitDao
import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Visit
import com.strobingn.wildlifefieldops.pricing.EstimateInvoiceCarry
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PricingJson
import com.strobingn.wildlifefieldops.pricing.SyncedInvoiceRecord
import com.strobingn.wildlifefieldops.pricing.markManual
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

@Singleton
class MoneyFieldOpsStore @Inject constructor(
    private val jobDao: JobDao,
    private val visitDao: VisitDao,
    private val invoiceDao: InvoiceDao,
    private val expenseDao: ExpenseDao,
    @ApplicationContext context: Context
) {
    private val unassignedFile = File(context.filesDir, "unassigned-mileage.json")
    private val mileageList = ListSerializer(MileageLogEntry.serializer())

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
            ).markManual(ManualField.PAID_AMOUNT)
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

    suspend fun saveMileage(entry: MileageLogEntry): Boolean {
        importLegacyUnassigned()
        val id = entry.id.ifBlank { UUID.randomUUID().toString() }
        val targetIsLedger = entry.jobId.isBlank() || OpsLedger.isLedgerId(entry.jobId)
        if (!targetIsLedger && jobDao.getById(entry.jobId) == null) return false
        val homeId = if (targetIsLedger) OpsLedger.ID else entry.jobId
        stripMileage(id)
        val home = if (targetIsLedger) ensureLedger() else jobDao.getById(homeId) ?: return false
        val title = if (targetIsLedger) {
            entry.jobTitle.ifBlank { "No job" }
        } else {
            entry.jobTitle.ifBlank { home.title.ifBlank { home.customerName } }
        }
        val saved = entry.copy(id = id, jobId = if (targetIsLedger) "" else homeId, jobTitle = title)
        val placed = MileageTaxLog.relocate(
            logsByJob = mapOf(home.id to home.pricing.mileageLogs),
            entry = saved,
            homeJobId = home.id
        )
        persistPricing(home) { it.copy(mileageLogs = placed[home.id].orEmpty()) }
        return true
    }

    suspend fun deleteMileage(jobId: String, entryId: String) {
        importLegacyUnassigned()
        if (jobId.isNotBlank()) {
            jobDao.getById(jobId)?.let { job ->
                if (job.pricing.mileageLogs.any { it.id == entryId }) {
                    persistPricing(job) { it.copy(mileageLogs = it.mileageLogs.filterNot { log -> log.id == entryId }) }
                }
            }
        }
        stripMileage(entryId)
    }

    suspend fun allMileage(): List<MileageLogEntry> {
        importLegacyUnassigned()
        return jobDao.getAllOnce().flatMap { job ->
            job.pricing.mileageLogs.map { log ->
                if (OpsLedger.isLedger(job) || log.jobId.isBlank()) {
                    log.copy(jobId = "", jobTitle = log.jobTitle.ifBlank { "No job" })
                } else if (log.jobTitle.isBlank()) {
                    log.copy(jobTitle = job.title.ifBlank { job.customerName }, jobId = log.jobId.ifBlank { job.id })
                } else {
                    log
                }
            }
        }.sortedBy { it.date }
    }

    private suspend fun stripMileage(entryId: String) {
        jobDao.getAllOnce().forEach { job ->
            if (job.pricing.mileageLogs.none { it.id == entryId }) return@forEach
            persistPricing(job) { it.copy(mileageLogs = it.mileageLogs.filterNot { log -> log.id == entryId }) }
        }
    }

    /** One-time move of the old phone-only file onto the synced ops ledger. */
    private suspend fun importLegacyUnassigned() {
        // null = the file exists but could not be read or parsed. Keep it: deleting it
        // would silently throw away the only copy of the old mileage log.
        val legacy = readUnassigned() ?: return
        if (legacy.isEmpty()) {
            withContext(Dispatchers.IO) { if (unassignedFile.exists()) unassignedFile.delete() }
            return
        }
        val ledger = ensureLedger()
        val merged = legacy.fold(ledger.pricing.mileageLogs) { acc, entry ->
            MileageTaxLog.upsert(acc, entry.copy(jobId = "", jobTitle = entry.jobTitle.ifBlank { "No job" }))
        }
        persistPricing(ledger) { it.copy(mileageLogs = merged) }
        withContext(Dispatchers.IO) { unassignedFile.delete() }
    }

    private suspend fun readUnassigned(): List<MileageLogEntry>? = withContext(Dispatchers.IO) {
        if (!unassignedFile.exists()) return@withContext emptyList()
        val raw = runCatching { unassignedFile.readText() }.getOrNull() ?: return@withContext null
        if (raw.isBlank()) return@withContext emptyList()
        runCatching { PricingJson.json.decodeFromString(mileageList, raw) }.getOrNull()
    }

    private suspend fun writeUnassigned(entries: List<MileageLogEntry>) = withContext(Dispatchers.IO) {
        unassignedFile.parentFile?.mkdirs()
        unassignedFile.writeText(PricingJson.json.encodeToString(mileageList, entries))
    }

    suspend fun saveInvoice(invoice: Invoice) {
        invoiceDao.insert(invoice.copy(isSynced = false, updatedAt = System.currentTimeMillis()))
        val job = jobDao.getById(invoice.jobId) ?: return
        embedInvoices(job)
    }

    suspend fun hydrateFromJob(job: Job) {
        job.pricing.invoiceRecords.forEach { rec ->
            val local = if (rec.id.isBlank()) null else invoiceDao.getById(rec.id)
            val incoming = rec.toInvoice(job)
            if (local == null) {
                invoiceDao.insert(incoming)
            } else if (EstimateInvoiceCarry.shouldReplaceWithRemote(local, rec)) {
                invoiceDao.insert(
                    incoming.copy(
                        id = local.id,
                        createdAt = local.createdAt,
                        invoiceNumber = incoming.invoiceNumber.ifBlank { local.invoiceNumber },
                        technicianSignature = incoming.technicianSignature.ifBlank { local.technicianSignature },
                        customerSignature = incoming.customerSignature.ifBlank { local.customerSignature }
                    )
                )
            }
        }
    }

    private suspend fun embedInvoices(job: Job) {
        val invoices = invoiceDao.getByJobOnce(job.id)
        persistPricing(job) { pricing ->
            pricing.copy(invoiceRecords = invoices.map { it.toSynced() })
        }
    }

    fun allAdjustments(jobs: List<Job>): List<EarningsAdjustment> =
        jobs.flatMap { it.pricing.earningsAdjustments }.distinctBy { it.id }.sortedBy { it.date }

    fun allOverrides(jobs: List<Job>): List<EarningsPeriodOverride> =
        jobs.flatMap { it.pricing.earningsPeriodOverrides }.distinctBy { it.id }

    suspend fun saveAdjustment(entry: EarningsAdjustment) {
        val ledger = ensureLedger()
        val id = entry.id.ifBlank { UUID.randomUUID().toString() }
        val saved = entry.copy(id = id)
        persistPricing(ledger) { pricing ->
            pricing.copy(earningsAdjustments = pricing.earningsAdjustments.filterNot { it.id == id } + saved)
        }
    }

    suspend fun deleteAdjustment(id: String) {
        val ledger = ensureLedger()
        persistPricing(ledger) { pricing ->
            pricing.copy(earningsAdjustments = pricing.earningsAdjustments.filterNot { it.id == id })
        }
    }

    suspend fun savePeriodOverride(override: EarningsPeriodOverride) {
        val ledger = ensureLedger()
        persistPricing(ledger) { pricing ->
            val next = pricing.earningsPeriodOverrides
                .filterNot { it.grain == override.grain && it.startMs == override.startMs } + override
            pricing.copy(earningsPeriodOverrides = next)
        }
    }

    private suspend fun ensureLedger(): Job {
        val existing = jobDao.getById(OpsLedger.ID)
        if (existing != null) return existing
        val created = OpsLedger.newJob()
        jobDao.insert(JobFieldOpsCodec.mergeForSave(created))
        return jobDao.getById(OpsLedger.ID) ?: created
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
    customerEmail = customerEmail,
    subtotal = subtotal,
    taxRate = taxRate,
    taxAmount = taxAmount,
    discountPercent = discountPercent,
    discountAmount = discountAmount,
    notes = notes,
    terms = terms,
    lineItems = lineItems,
    subtotalOverride = subtotalOverride,
    taxAmountOverride = taxAmountOverride,
    discountAmountOverride = discountAmountOverride,
    totalOverride = totalOverride,
    taxRateManual = taxRateManual,
    manuallyEdited = manuallyEdited,
    updatedAt = updatedAt,
    customerAddress = customerAddress
)

fun SyncedInvoiceRecord.toInvoice(job: Job): Invoice = Invoice(
    id = id.ifBlank { UUID.randomUUID().toString() },
    invoiceNumber = invoiceNumber,
    jobId = job.id,
    customerId = job.customerId,
    customerName = customerName.ifBlank { job.customerName },
    customerEmail = customerEmail,
    customerAddress = customerAddress.ifBlank { job.address },
    issueDate = issueDate,
    dueDate = dueDate,
    status = runCatching { InvoiceStatus.valueOf(status) }.getOrDefault(InvoiceStatus.DRAFT),
    subtotal = subtotal,
    taxRate = taxRate,
    taxAmount = taxAmount,
    discountPercent = discountPercent,
    discountAmount = discountAmount,
    totalAmount = totalAmount,
    subtotalOverride = subtotalOverride,
    taxAmountOverride = taxAmountOverride,
    discountAmountOverride = discountAmountOverride,
    totalOverride = totalOverride,
    taxRateManual = taxRateManual,
    amountPaid = amountPaid,
    balanceDue = balanceDue,
    lineItems = lineItems,
    notes = notes,
    terms = terms,
    updatedAt = updatedAt,
    isSynced = false,
    manuallyEdited = manuallyEdited
)
