package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.FieldObservationDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.report.CountyDashboard
import com.strobingn.wildlifefieldops.data.report.CountyReportAggregator
import com.strobingn.wildlifefieldops.data.report.CountyReportInputs
import com.strobingn.wildlifefieldops.data.report.ReportWindow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class CountyReportUiState(
    val window: ReportWindow = ReportWindow.LAST_30_DAYS,
    val dashboard: CountyDashboard = CountyDashboard(
        window = ReportWindow.LAST_30_DAYS,
        generatedAt = 0L,
        observationCount = 0,
        completedJobCount = 0,
        timedCompletionCount = 0,
        unlocatedObservationCount = 0,
        unlabeledObservationCount = 0,
        counties = emptyList(),
    ),
    val isLoading: Boolean = true,
)

@HiltViewModel
class CountyReportViewModel @Inject constructor(
    fieldObservationDao: FieldObservationDao,
    jobDao: JobDao,
) : ViewModel() {

    private val window = MutableStateFlow(ReportWindow.LAST_30_DAYS)

    val state: StateFlow<CountyReportUiState> = combine(
        fieldObservationDao.getAll(),
        jobDao.getAll(),
        window,
    ) { observations, jobs, selectedWindow ->
        val now = System.currentTimeMillis()
        val dashboard = CountyReportAggregator.aggregate(
            observations = CountyReportInputs.observations(observations),
            completions = CountyReportInputs.completions(jobs),
            window = selectedWindow,
            nowMs = now,
        )
        CountyReportUiState(
            window = selectedWindow,
            dashboard = dashboard,
            isLoading = false,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CountyReportUiState(),
    )

    fun setWindow(next: ReportWindow) {
        window.value = next
    }
}
