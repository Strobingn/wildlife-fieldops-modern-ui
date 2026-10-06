package com.strobingn.wildlifefieldops.weather

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.strobingn.wildlifefieldops.ui.viewmodel.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherAlertSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val settings: Flow<WeatherAlertSettings> = context.settingsDataStore.data.map { read(it) }

    suspend fun current(): WeatherAlertSettings = settings.first()

    suspend fun update(block: (WeatherAlertSettings) -> WeatherAlertSettings) {
        context.settingsDataStore.edit { prefs ->
            write(prefs, block(read(prefs)))
        }
    }

    suspend fun addNotified(keys: Collection<String>) {
        if (keys.isEmpty()) return
        update { current ->
            val merged = (current.notifiedKeys + keys).toList().takeLast(MAX_NOTIFIED).toSet()
            current.copy(notifiedKeys = merged)
        }
    }

    private fun read(prefs: Preferences): WeatherAlertSettings = WeatherAlertSettings(
        rainEnabled = prefs[RAIN] ?: true,
        heavyRainEnabled = prefs[HEAVY_RAIN] ?: true,
        highWindEnabled = prefs[HIGH_WIND] ?: true,
        snowEnabled = prefs[SNOW] ?: true,
        notificationsEnabled = prefs[NOTIFICATIONS] ?: true,
        windMphThreshold = positive(prefs[WIND_MPH], WeatherAlertSettings.DEFAULT_WIND_MPH),
        heavyRainHourInches = positive(prefs[HEAVY_HOUR], WeatherAlertSettings.DEFAULT_HEAVY_HOUR_IN),
        heavyRainDayInches = positive(prefs[HEAVY_DAY], WeatherAlertSettings.DEFAULT_HEAVY_DAY_IN),
        notifiedKeys = prefs[NOTIFIED].orEmpty()
    )

    private fun write(prefs: MutablePreferences, settings: WeatherAlertSettings) {
        prefs[RAIN] = settings.rainEnabled
        prefs[HEAVY_RAIN] = settings.heavyRainEnabled
        prefs[HIGH_WIND] = settings.highWindEnabled
        prefs[SNOW] = settings.snowEnabled
        prefs[NOTIFICATIONS] = settings.notificationsEnabled
        prefs[WIND_MPH] = settings.windMphThreshold.toString()
        prefs[HEAVY_HOUR] = settings.heavyRainHourInches.toString()
        prefs[HEAVY_DAY] = settings.heavyRainDayInches.toString()
        prefs[NOTIFIED] = settings.notifiedKeys
    }

    private fun positive(raw: String?, default: Double): Double {
        val parsed = raw?.toDoubleOrNull()
        return if (parsed == null || parsed <= 0.0 || parsed.isNaN()) default else parsed
    }

    companion object {
        private const val MAX_NOTIFIED = 300
        private val RAIN = booleanPreferencesKey("weather_alert_rain")
        private val HEAVY_RAIN = booleanPreferencesKey("weather_alert_heavy_rain")
        private val HIGH_WIND = booleanPreferencesKey("weather_alert_wind")
        private val SNOW = booleanPreferencesKey("weather_alert_snow")
        private val NOTIFICATIONS = booleanPreferencesKey("weather_alert_notifications")
        private val WIND_MPH = stringPreferencesKey("weather_alert_wind_mph")
        private val HEAVY_HOUR = stringPreferencesKey("weather_alert_heavy_hour_in")
        private val HEAVY_DAY = stringPreferencesKey("weather_alert_heavy_day_in")
        private val NOTIFIED = stringSetPreferencesKey("weather_alert_notified_keys")
    }
}
