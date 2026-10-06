package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.weather.WeatherAlert
import com.strobingn.wildlifefieldops.weather.WeatherAlertRepository
import com.strobingn.wildlifefieldops.weather.WeatherAlertSettings
import com.strobingn.wildlifefieldops.weather.WeatherHomeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WeatherAlertsViewModel @Inject constructor(
    private val repository: WeatherAlertRepository
) : ViewModel() {
    private val trackedJobs = MutableStateFlow<List<Job>>(emptyList())

    val settings: StateFlow<WeatherAlertSettings> = repository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WeatherAlertSettings()
    )
    val home: StateFlow<WeatherHomeState> = repository.home.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        WeatherHomeState()
    )
    val jobAlerts: StateFlow<Map<String, List<WeatherAlert>>> = combine(
        trackedJobs,
        repository.cacheState,
        repository.settings,
        repository.resolvedJobPoints
    ) { jobs, cache, settings, points ->
        runCatching {
            repository.matchJobs(jobs, cache, settings, System.currentTimeMillis(), points)
        }.getOrDefault(emptyMap())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch { runCatching { repository.refreshIfStale() } }
    }

    fun trackJobs(jobs: List<Job>) {
        trackedJobs.value = jobs
        viewModelScope.launch { runCatching { repository.ensureJobForecasts(jobs) } }
    }

    fun setRainEnabled(enabled: Boolean) = update { it.copy(rainEnabled = enabled) }
    fun setHeavyRainEnabled(enabled: Boolean) = update { it.copy(heavyRainEnabled = enabled) }
    fun setHighWindEnabled(enabled: Boolean) = update { it.copy(highWindEnabled = enabled) }
    fun setSnowEnabled(enabled: Boolean) = update { it.copy(snowEnabled = enabled) }
    fun setNotificationsEnabled(enabled: Boolean) = update { it.copy(notificationsEnabled = enabled) }

    fun setWindThreshold(mph: Double) {
        if (mph <= 0.0) return
        update { it.copy(windMphThreshold = mph) }
    }

    fun setHeavyHourInches(inches: Double) {
        if (inches <= 0.0) return
        update { it.copy(heavyRainHourInches = inches) }
    }

    fun setHeavyDayInches(inches: Double) {
        if (inches <= 0.0) return
        update { it.copy(heavyRainDayInches = inches) }
    }

    fun checkNow() {
        viewModelScope.launch { runCatching { repository.checkNow(notify = true) } }
    }

    private fun update(block: (WeatherAlertSettings) -> WeatherAlertSettings) {
        viewModelScope.launch { runCatching { repository.updateSettings(block) } }
    }
}
