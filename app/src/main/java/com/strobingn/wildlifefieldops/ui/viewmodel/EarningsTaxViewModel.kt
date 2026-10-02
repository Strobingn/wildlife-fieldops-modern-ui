package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.fieldops.EarningsAdjKind
import com.strobingn.wildlifefieldops.ai.fieldops.EarningsAdjustment
import com.strobingn.wildlifefieldops.ai.fieldops.EarningsPeriodOverride
import com.strobingn.wildlifefieldops.ai.fieldops.EarningsTaxEngine
import com.strobingn.wildlifefieldops.ai.fieldops.EarningsTaxSnapshot
import com.strobingn.wildlifefieldops.ai.fieldops.ManualField
import com.strobingn.wildlifefieldops.ai.fieldops.MoneyFieldOpsStore
import com.strobingn.wildlifefieldops.ai.fieldops.NySalesQuarter
import com.strobingn.wildlifefieldops.ai.fieldops.NySalesTaxPeriods
import com.strobingn.wildlifefieldops.ai.fieldops.OpsLedger
import com.strobingn.wildlifefieldops.data.local.InvoiceDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class EarningsTaxViewModel @Inject constructor(
    private val store: MoneyFieldOpsStore,
    jobDao: JobDao,
    invoiceDao: InvoiceDao
) : ViewModel() {

    private val jobs = jobDao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val invoices = invoiceDao.getAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _grain = MutableStateFlow(NySalesTaxPeriods.Grain.MONTH)
    val grain: StateFlow<NySalesTaxPeriods.Grain> = _grain

    private val _anchor = MutableStateFlow(System.currentTimeMillis())
    val anchor: StateFlow<Long> = _anchor

    val adjustments: StateFlow<List<EarningsAdjustment>> = jobs.map { store.allAdjustments(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val snapshot: StateFlow<EarningsTaxSnapshot> = combine(jobs, invoices, _grain, _anchor) { jobList, inv, grain, now ->
        val (start, end) = NySalesTaxPeriods.bounds(grain, now)
        EarningsTaxEngine.snapshot(
            invoices = inv,
            jobs = jobList,
            adjustments = store.allAdjustments(jobList),
            overrides = store.allOverrides(jobList),
            start = start,
            end = end,
            grain = grain
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        EarningsTaxEngine.snapshot(emptyList(), emptyList(), emptyList(), emptyList(), 0L, 1L)
    )

    val quarters: StateFlow<List<Pair<NySalesQuarter, EarningsTaxSnapshot>>> = combine(jobs, invoices) { jobList, inv ->
        val year = Calendar.getInstance().get(Calendar.YEAR)
        NySalesTaxPeriods.quartersForCalendarYear(year).map { q ->
            q to EarningsTaxEngine.snapshot(
                invoices = inv,
                jobs = jobList,
                adjustments = store.allAdjustments(jobList),
                overrides = store.allOverrides(jobList),
                start = q.startMs,
                end = q.endMs,
                grain = NySalesTaxPeriods.Grain.QUARTER
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayCard: StateFlow<EarningsTaxSnapshot> = combine(jobs, invoices) { jobList, inv ->
        val (a, b) = EarningsRollupBounds.day(System.currentTimeMillis())
        EarningsTaxEngine.snapshot(inv, jobList.filterNot { OpsLedger.isLedger(it) }, store.allAdjustments(jobList), store.allOverrides(jobList), a, b, NySalesTaxPeriods.Grain.DAY)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), snapshot.value)

    fun setGrain(value: NySalesTaxPeriods.Grain) {
        _grain.value = value
    }

    fun shift(delta: Int) {
        val cal = Calendar.getInstance().apply { timeInMillis = _anchor.value }
        when (_grain.value) {
            NySalesTaxPeriods.Grain.DAY -> cal.add(Calendar.DATE, delta)
            NySalesTaxPeriods.Grain.WEEK -> cal.add(Calendar.WEEK_OF_YEAR, delta)
            NySalesTaxPeriods.Grain.MONTH -> cal.add(Calendar.MONTH, delta)
            NySalesTaxPeriods.Grain.YEAR -> cal.add(Calendar.YEAR, delta)
            NySalesTaxPeriods.Grain.QUARTER -> cal.add(Calendar.MONTH, delta * 3)
        }
        _anchor.value = cal.timeInMillis
    }

    fun addAdjustment(kind: EarningsAdjKind, amount: Double, county: String, taxable: Boolean, notes: String, date: Long) {
        viewModelScope.launch {
            store.saveAdjustment(
                EarningsAdjustment(date = date, kind = kind, amount = amount, county = county, taxable = taxable, notes = notes)
            )
        }
    }

    fun deleteAdjustment(id: String) {
        viewModelScope.launch { store.deleteAdjustment(id) }
    }

    fun overrideField(field: String, raw: String) {
        viewModelScope.launch {
            val (start, _) = NySalesTaxPeriods.bounds(_grain.value, _anchor.value)
            val current = store.allOverrides(jobs.value).firstOrNull {
                it.grain == _grain.value.name && it.startMs == start
            } ?: EarningsPeriodOverride(grain = _grain.value.name, startMs = start)
            val parsed = EarningsTaxEngine.parseLock(raw)
            val next = when (field) {
                "paid" -> current.copy(paid = parsed, locked = current.locked + ManualField.TAX_PAID)
                "invoiced" -> current.copy(invoiced = parsed, locked = current.locked + ManualField.TAX_INVOICED)
                "estimated" -> current.copy(estimated = parsed, locked = current.locked + ManualField.TAX_ESTIMATED)
                "taxCollected" -> current.copy(taxCollected = parsed, locked = current.locked + ManualField.TAX_COLLECTED)
                "taxable" -> current.copy(taxable = parsed, locked = current.locked + ManualField.TAX_TAXABLE)
                "nontaxable" -> current.copy(nontaxable = parsed, locked = current.locked + ManualField.TAX_NONTAXABLE)
                else -> current
            }
            store.savePeriodOverride(next)
        }
    }

    fun exportCsv(): String {
        val now = _anchor.value
        val labels = listOf(
            NySalesTaxPeriods.Grain.DAY to "Day",
            NySalesTaxPeriods.Grain.WEEK to "Week",
            NySalesTaxPeriods.Grain.MONTH to "Month",
            NySalesTaxPeriods.Grain.YEAR to "Year"
        )
        val snaps = labels.map { (grain, label) ->
            val (a, b) = NySalesTaxPeriods.bounds(grain, now)
            label to EarningsTaxEngine.snapshot(
                invoices.value,
                jobs.value,
                store.allAdjustments(jobs.value),
                store.allOverrides(jobs.value),
                a,
                b,
                grain
            )
        } + quarters.value.map { it.first.label to it.second }
        return EarningsTaxEngine.csv(snaps, snapshot.value.byCounty)
    }
}

private object EarningsRollupBounds {
    fun day(now: Long) = com.strobingn.wildlifefieldops.ai.fieldops.EarningsRollup.dayBounds(now)
}
