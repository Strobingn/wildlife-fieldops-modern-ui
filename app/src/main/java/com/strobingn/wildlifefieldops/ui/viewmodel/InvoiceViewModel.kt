package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.InvoiceDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.model.*
import com.strobingn.wildlifefieldops.data.repository.JobRepository
import com.strobingn.wildlifefieldops.pricing.EstimateInvoiceCarry
import com.strobingn.wildlifefieldops.tax.CountyLookupService
import com.strobingn.wildlifefieldops.ai.fieldops.MoneyFieldOpsStore
import com.strobingn.wildlifefieldops.tax.NyCountyTaxRates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** Outcome of an automatic county + tax-rate resolution attempt. */
sealed class CountyTaxState {
    /** Not yet looked up. */
    object Idle : CountyTaxState()

    /** Lookup is in progress. */
    object Loading : CountyTaxState()

    /**
     * County was resolved and a tax rate is available.
     *
     * @param displayLabel  Human-readable label, e.g. "Orange County · 8.125%"
     * @param ratePercent   Rate as a percentage, e.g. 8.125
     */
    data class Resolved(val displayLabel: String, val ratePercent: Double) : CountyTaxState()

    /**
     * Lookup failed or returned no usable county. The operator should set the tax rate manually.
     */
    object Unknown : CountyTaxState()
}

@HiltViewModel
class InvoiceViewModel @Inject constructor(
    private val invoiceDao: InvoiceDao,
    private val jobDao: JobDao,
    private val jobRepository: JobRepository,
    private val countyLookupService: CountyLookupService,
    private val moneyFieldOpsStore: MoneyFieldOpsStore
) : ViewModel() {

    val invoices = invoiceDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _countyTaxState = MutableStateFlow<CountyTaxState>(CountyTaxState.Idle)
    val countyTaxState: StateFlow<CountyTaxState> = _countyTaxState.asStateFlow()

    private val invoiceWrite = Mutex()

    fun getInvoiceById(id: String): Flow<Invoice?> = flow {
        emit(invoiceDao.getById(id))
    }

    fun getInvoicesByJob(jobId: String): Flow<List<Invoice>> =
        invoiceDao.getByJob(jobId)

    fun saveInvoice(invoice: Invoice) = viewModelScope.launch {
        moneyFieldOpsStore.saveInvoice(invoice)
    }

    fun deleteInvoice(invoice: Invoice) = viewModelScope.launch {
        invoiceDao.delete(invoice)
    }

    /**
     * Resolves the county for [job] and updates [_countyTaxState].
     *
     * If the job already has a cached [Job.county] that maps to a known rate, the
     * cached value is used immediately (no network call). Otherwise the
     * [CountyLookupService] is invoked and the result is persisted back to the
     * database for future offline use.
     *
     * Call this when the InvoiceScreen opens, or when the operator taps "Refresh county".
     */
    fun resolveCountyTax(job: Job) = viewModelScope.launch {
        // Fast path: already resolved and cached on this job row.
        if (!job.county.isNullOrBlank()) {
            applyCountyResult(job.county, job.state ?: "NY")
            return@launch
        }

        _countyTaxState.value = CountyTaxState.Loading

        val result = countyLookupService.resolveCounty(
            latitude = job.latitude,
            longitude = job.longitude,
            address = job.address
        )

        if (result != null) {
            // Persist so subsequent offline opens skip the lookup.
            jobRepository.updateJobCounty(job.id, result.county, result.state)
            applyCountyResult(result.county, result.state)
        } else {
            _countyTaxState.value = CountyTaxState.Unknown
        }
    }

    /**
     * Re-runs the county lookup even if a cached value exists (e.g. after the operator
     * updates the job address or coordinates).
     */
    fun refreshCountyTax(job: Job) = viewModelScope.launch {
        _countyTaxState.value = CountyTaxState.Loading

        val result = countyLookupService.resolveCounty(
            latitude = job.latitude,
            longitude = job.longitude,
            address = job.address
        )

        if (result != null) {
            jobRepository.updateJobCounty(job.id, result.county, result.state)
            applyCountyResult(result.county, result.state)
        } else {
            _countyTaxState.value = CountyTaxState.Unknown
        }
    }

    private fun applyCountyResult(county: String, state: String) {
        val rate = NyCountyTaxRates.taxRatePercentForCounty(county, state)
        _countyTaxState.value = if (rate != null) {
            val display = NyCountyTaxRates.displayName(county)
            CountyTaxState.Resolved(
                displayLabel = "$display · ${"%.3f".format(rate).trimEnd('0').trimEnd('.')}%",
                ratePercent = rate
            )
        } else {
            CountyTaxState.Unknown
        }
    }

    /**
     * Writes the editor onto the job's invoice and embeds the full body in
     * `jobs.pricing` so background sync keeps line items, blanks, and tax locks.
     * [markJobInvoiced] stays on the explicit Save button.
     */
    fun saveEditorInvoice(
        jobId: String,
        existingId: String?,
        form: EstimateInvoiceCarry.InvoiceFormState,
        manuallyEdited: Boolean,
        markJobInvoiced: Boolean
    ) = viewModelScope.launch {
        invoiceWrite.withLock {
            val job = jobDao.getById(jobId) ?: return@withLock
            val existing = resolveExisting(jobId, existingId)
            val now = System.currentTimeMillis()
            val invoice = EstimateInvoiceCarry.buildInvoice(
                job = job,
                form = form,
                existing = existing,
                invoiceNumber = generateInvoiceNumber(),
                now = now,
                manuallyEdited = manuallyEdited
            )
            val unchanged = existing != null && EstimateInvoiceCarry.sameMoneyContent(existing, invoice)
            if (!unchanged) moneyFieldOpsStore.saveInvoice(invoice)
            if (!markJobInvoiced) return@withLock
            val fresh = jobDao.getById(jobId) ?: return@withLock
            val status = if (fresh.status == JobStatus.PAID) JobStatus.PAID else JobStatus.INVOICED
            jobDao.update(
                fresh.copy(
                    status = status,
                    actualCost = invoice.totalAmount,
                    updatedAt = System.currentTimeMillis(),
                    isSynced = false
                )
            )
        }
    }

    /**
     * An invoice that was never manually edited keeps matching the estimate.
     * Does not create an invoice and does not change job status.
     */
    fun refreshUntouchedFromEstimate(jobId: String, existingId: String?) = viewModelScope.launch {
        if (existingId.isNullOrBlank()) return@launch
        invoiceWrite.withLock {
            val job = jobDao.getById(jobId) ?: return@withLock
            val existing = invoiceDao.getById(existingId) ?: return@withLock
            if (existing.manuallyEdited) return@withLock
            val worksheet = EstimateInvoiceCarry.worksheetForCarry(job.pricing, job.estimatedValue) ?: return@withLock
            val form = EstimateInvoiceCarry.formFromEstimate(worksheet)
            val now = System.currentTimeMillis()
            val invoice = EstimateInvoiceCarry.buildInvoice(
                job = job,
                form = form,
                existing = existing,
                invoiceNumber = existing.invoiceNumber,
                now = now,
                manuallyEdited = false
            )
            if (EstimateInvoiceCarry.sameMoneyContent(existing, invoice)) return@withLock
            moneyFieldOpsStore.saveInvoice(invoice)
        }
    }

    private suspend fun resolveExisting(jobId: String, existingId: String?): Invoice? {
        if (!existingId.isNullOrBlank()) {
            invoiceDao.getById(existingId)?.let { return it }
        }
        return invoiceDao.getByJobOnce(jobId).maxByOrNull { it.updatedAt }
    }

    fun markAsPaid(invoiceId: String) = viewModelScope.launch {
        val invoice = invoiceDao.getById(invoiceId)
        invoice?.let {
            invoiceDao.update(it.copy(
                status = InvoiceStatus.PAID,
                amountPaid = it.totalAmount,
                balanceDue = 0.0,
                updatedAt = System.currentTimeMillis()
            ))
            val job = jobDao.getById(it.jobId)
            job?.let { j ->
                jobDao.update(
                    j.copy(
                        status = JobStatus.PAID,
                        updatedAt = System.currentTimeMillis(),
                        isSynced = false
                    )
                )
            }
        }
    }

    private fun generateInvoiceNumber(): String {
        return "INV-${System.currentTimeMillis()}"
    }
}
