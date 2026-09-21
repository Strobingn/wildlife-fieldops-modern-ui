package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.InvoiceDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.model.*
import com.strobingn.wildlifefieldops.data.repository.JobRepository
import com.strobingn.wildlifefieldops.tax.CountyLookupService
import com.strobingn.wildlifefieldops.tax.NyCountyTaxRates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    private val countyLookupService: CountyLookupService
) : ViewModel() {

    val invoices = invoiceDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _countyTaxState = MutableStateFlow<CountyTaxState>(CountyTaxState.Idle)
    val countyTaxState: StateFlow<CountyTaxState> = _countyTaxState.asStateFlow()

    fun getInvoiceById(id: String): Flow<Invoice?> = flow {
        emit(invoiceDao.getById(id))
    }

    fun getInvoicesByJob(jobId: String): Flow<List<Invoice>> =
        invoiceDao.getByJob(jobId)

    fun saveInvoice(invoice: Invoice) = viewModelScope.launch {
        invoiceDao.insert(invoice)
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

    fun generateInvoiceFromJob(
        jobId: String,
        lineItems: List<InvoiceLineItem>,
        taxRate: Double,
        discountAmount: Double,
        notes: String,
        terms: String
    ) = viewModelScope.launch {
        val job = jobDao.getById(jobId)
        job?.let {
            val subtotal = lineItems.sumOf { it.calculateTotal() }
            val taxAmount = subtotal * (taxRate / 100.0)
            val total = subtotal + taxAmount - discountAmount

            val invoice = Invoice(
                invoiceNumber = generateInvoiceNumber(),
                jobId = jobId,
                customerId = job.customerId,
                customerName = job.customerName,
                subtotal = subtotal,
                taxRate = taxRate,
                taxAmount = taxAmount,
                discountAmount = discountAmount,
                totalAmount = total,
                balanceDue = total,
                lineItems = lineItems,
                notes = notes,
                terms = terms
            )
            invoiceDao.insert(invoice)
            jobDao.update(job.copy(status = JobStatus.INVOICED))
        }
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
                jobDao.update(j.copy(status = JobStatus.PAID))
            }
        }
    }

    private fun generateInvoiceNumber(): String {
        return "INV-${System.currentTimeMillis()}"
    }
}
