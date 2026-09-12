package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.remote.GeocodingService
import com.strobingn.wildlifefieldops.data.remote.WeatherService
import com.strobingn.wildlifefieldops.data.remote.WeatherSnapshot
import com.strobingn.wildlifefieldops.util.WildlifeWhispererBrand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class WeatherUiState {
    data object Idle : WeatherUiState()
    data object Loading : WeatherUiState()
    data class Ready(val snap: WeatherSnapshot, val placeLabel: String) : WeatherUiState()
    data class Unavailable(val reason: String) : WeatherUiState()
}

@HiltViewModel
class LiveWeatherViewModel @Inject constructor(
    private val weatherService: WeatherService,
    private val geocodingService: GeocodingService,
    private val shopSettings: ShopSettings
) : ViewModel() {

    private val _state = MutableStateFlow<WeatherUiState>(WeatherUiState.Idle)
    val state: StateFlow<WeatherUiState> = _state.asStateFlow()

    /** Shop / home-base weather for dashboard. */
    fun loadShopWeather() {
        viewModelScope.launch {
            _state.value = WeatherUiState.Loading
            if (!weatherService.isConfigured) {
                _state.value = WeatherUiState.Unavailable(
                    "OpenWeather key missing in this APK build (set VITE_OPENWEATHER_API_KEY secret)."
                )
                return@launch
            }
            val shop = shopSettings.address().ifBlank { WildlifeWhispererBrand.ADDRESS }
            val point = geocodingService.geocode(shop)
                ?: CORNWALL_FALLBACK
            val snap = weatherService.getWeather(point.latitude, point.longitude)
            _state.value = if (snap != null) {
                WeatherUiState.Ready(snap, shop)
            } else {
                WeatherUiState.Unavailable("Could not reach OpenWeather for $shop")
            }
        }
    }

    /** Job-site weather from coords or address. */
    fun loadJobWeather(latitude: Double?, longitude: Double?, address: String) {
        viewModelScope.launch {
            _state.value = WeatherUiState.Loading
            if (!weatherService.isConfigured) {
                _state.value = WeatherUiState.Unavailable("OpenWeather not configured in this build")
                return@launch
            }
            val point = when {
                latitude != null && longitude != null &&
                    latitude.isFinite() && longitude.isFinite() &&
                    latitude in -90.0..90.0 && longitude in -180.0..180.0 ->
                    com.strobingn.wildlifefieldops.data.remote.GeoPoint(latitude, longitude)
                address.isNotBlank() -> geocodingService.geocode(address)
                else -> null
            }
            if (point == null) {
                _state.value = WeatherUiState.Unavailable("Need job GPS or address for weather")
                return@launch
            }
            val label = address.ifBlank { "%.4f, %.4f".format(point.latitude, point.longitude) }
            val snap = weatherService.getWeather(point.latitude, point.longitude)
            _state.value = if (snap != null) {
                WeatherUiState.Ready(snap, label)
            } else {
                WeatherUiState.Unavailable("Weather lookup failed")
            }
        }
    }

    companion object {
        /** 210 Willow Avenue, Cornwall, NY approximate. */
        val CORNWALL_FALLBACK = com.strobingn.wildlifefieldops.data.remote.GeoPoint(41.4449, -74.0125)
    }
}
