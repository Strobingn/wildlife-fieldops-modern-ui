package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.fieldops.EarningsRollup
import com.strobingn.wildlifefieldops.ai.fieldops.InvoiceReminder
import com.strobingn.wildlifefieldops.ai.fieldops.JobTimer
import com.strobingn.wildlifefieldops.ai.fieldops.JobTimerState
import com.strobingn.wildlifefieldops.ai.fieldops.MileageLogEntry
import com.strobingn.wildlifefieldops.ai.fieldops.MileageTaxLog
import com.strobingn.wildlifefieldops.ai.fieldops.MoneyFieldOpsStore
import com.strobingn.wildlifefieldops.ai.fieldops.ProfitResult
import com.strobingn.wildlifefieldops.data.local.InvoiceDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class MoneyFieldOpsViewModel @Inject constructor(
    private val store: MoneyFieldOpsStore,
    private val jobDao: JobDao,
    private val invoiceDao: InvoiceDao
) : ViewModel() {

    val jobs: StateFlow<List<Job>> = jobDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val invoices: StateFlow<List<Invoice>> = invoiceDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val overdue: StateFlow<List<Invoice>> = invoices
        .map { InvoiceReminder.overdueList(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _tick = MutableStateFlow(0L)
    val tick: StateFlow<Long> = _tick

    private val _mileage = MutableStateFlow<List<MileageLogEntry>>(emptyList())
    val mileage: StateFlow<List<MileageLogEntry>> = _mileage

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch {
            jobDao.getAllOnce().forEach { store.hydrateFromJob(it) }
            refreshMileage()
        }
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                _tick.value = System.currentTimeMillis()
            }
        }
    }

    fun timerState(job: Job): JobTimerState =
        JobTimerState(job.pricing.timerStartedAt, job.pricing.timerElapsedMs)

    fun startTimer(jobId: String) = viewModelScope.launch {
        store.startTimer(jobId)
        _message.value = "Timer started."
    }

    fun stopTimer(jobId: String) = viewModelScope.launch {
        store.stopTimer(jobId)
        _message.value = "Timer stopped and saved on the visit."
    }

    fun setMinutes(jobId: String, minutes: Long) = viewModelScope.launch {
        store.setElapsedMinutes(jobId, minutes)
    }

    suspend fun profit(job: Job): ProfitResult = store.profitFor(job)

    fun saveProfit(jobId: String, materials: Double, laborOverride: Double?, paid: Double) = viewModelScope.launch {
        store.saveProfitInputs(jobId, materials, laborOverride, paid)
        _message.value = "Profit inputs saved."
    }

    fun refreshMileage() = viewModelScope.launch {
        _mileage.value = store.allMileage()
    }

    fun saveMileage(entry: MileageLogEntry) = viewModelScope.launch {
        store.saveMileage(entry)
        refreshMileage()
        _message.value = "Mileage logged."
    }

    fun deleteMileage(jobId: String, id: String) = viewModelScope.launch {
        store.deleteMileage(jobId, id)
        refreshMileage()
    }

    fun mileageCsv(year: Int): String = MileageTaxLog.toCsv(MileageTaxLog.forYear(_mileage.value, year))

    fun markInvoice(invoice: Invoice, status: InvoiceStatus) = viewModelScope.launch {
        store.saveInvoice(InvoiceReminder.withStatus(invoice, status))
        _message.value = "Invoice marked ${status.name}."
    }

    fun markReminded(invoice: Invoice) = viewModelScope.launch {
        store.saveInvoice(invoice.copy(updatedAt = System.currentTimeMillis(), isSynced = false, notes = invoice.notes))
    }

    fun earningsToday(now: Long = System.currentTimeMillis()) =
        EarningsRollup.dayBounds(now).let { (a, b) -> EarningsRollup.summarize(invoices.value, jobs.value, a, b) }

    fun earningsWeek(now: Long = System.currentTimeMillis()) =
        EarningsRollup.weekBounds(now).let { (a, b) -> EarningsRollup.summarize(invoices.value, jobs.value, a, b) }

    fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

    fun clearMessage() {
        _message.value = null
    }

    fun formatTimer(job: Job, now: Long = _tick.value.takeIf { it > 0 } ?: System.currentTimeMillis()): String =
        JobTimer.format(timerState(job).displayedMs(now))
}
