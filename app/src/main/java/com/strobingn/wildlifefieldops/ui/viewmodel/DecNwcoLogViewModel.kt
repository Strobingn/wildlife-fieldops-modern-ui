package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.fieldops.DecNwcoLog
import com.strobingn.wildlifefieldops.ai.fieldops.DecNwcoLogStore
import com.strobingn.wildlifefieldops.ai.fieldops.NwcoLogRecord
import com.strobingn.wildlifefieldops.ai.fieldops.NwcoOperatorProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class DecNwcoLogViewModel @Inject constructor(
    private val store: DecNwcoLogStore
) : ViewModel() {

    private val _rows = MutableStateFlow<List<NwcoLogRecord>>(emptyList())
    val rows: StateFlow<List<NwcoLogRecord>> = _rows

    private val _operator = MutableStateFlow(NwcoOperatorProfile())
    val operator: StateFlow<NwcoOperatorProfile> = _operator

    private val _year = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val year: StateFlow<Int> = _year

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _operator.value = store.operator()
            _rows.value = store.compiledRows()
        }
    }

    fun visibleRows(): List<NwcoLogRecord> = DecNwcoLog.filterCalendarYear(_rows.value, _year.value)

    fun setYear(year: Int) {
        _year.value = year
    }

    fun updateCell(row: NwcoLogRecord, key: String, value: String) {
        val next = DecNwcoLog.withTyped(row, key, value)
        _rows.value = _rows.value.map { if (it.id == row.id || it.sourceKey == row.sourceKey) next else it }
        viewModelScope.launch { store.saveRow(next) }
    }

    fun addBlankRow() {
        viewModelScope.launch {
            store.saveRow(DecNwcoLog.blankManual())
            refresh()
            _message.value = "Blank DEC row added. Type every cell by hand."
        }
    }

    fun deleteRow(row: NwcoLogRecord) {
        viewModelScope.launch {
            store.deleteRow(row)
            refresh()
        }
    }

    fun saveOperator(profile: NwcoOperatorProfile) {
        viewModelScope.launch {
            store.saveOperator(profile)
            _operator.value = profile
            _message.value = "NWCO license profile saved."
        }
    }

    fun csv(): String = DecNwcoLog.toCsv(_operator.value, visibleRows())

    fun clearMessage() {
        _message.value = null
    }
}
