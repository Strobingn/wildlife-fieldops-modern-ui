package com.strobingn.wildlifefieldops.weather

import kotlinx.serialization.Serializable

enum class WeatherAlertKind {
    NWS,
    RAIN,
    HEAVY_RAIN,
    HIGH_WIND,
    SNOW
}

/**
 * Sir's weather-alert preferences. Stored in DataStore, not Room.
 * Wind alerts when speed or gusts are strictly over [windMphThreshold]
 * (30 mph is quiet, 31 mph alerts).
 */
data class WeatherAlertSettings(
    val rainEnabled: Boolean = true,
    val heavyRainEnabled: Boolean = true,
    val highWindEnabled: Boolean = true,
    val snowEnabled: Boolean = true,
    val notificationsEnabled: Boolean = true,
    val windMphThreshold: Double = DEFAULT_WIND_MPH,
    val heavyRainHourInches: Double = DEFAULT_HEAVY_HOUR_IN,
    val heavyRainDayInches: Double = DEFAULT_HEAVY_DAY_IN,
    val rainProbabilityPercent: Int = DEFAULT_RAIN_PROBABILITY,
    val notifiedKeys: Set<String> = emptySet()
) {
    companion object {
        const val DEFAULT_WIND_MPH = 30.0
        const val DEFAULT_HEAVY_HOUR_IN = 0.30
        const val DEFAULT_HEAVY_DAY_IN = 1.0
        const val DEFAULT_RAIN_PROBABILITY = 50
    }
}

@Serializable
data class HourlyWeather(
    val startMillis: Long,
    val precipitationInches: Double = 0.0,
    val precipitationProbability: Int = 0,
    val rainInches: Double = 0.0,
    val snowfallInches: Double = 0.0,
    val windSpeedMph: Double = 0.0,
    val windGustsMph: Double = 0.0,
    val weatherCode: Int = 0
)

@Serializable
data class NwsActiveAlert(
    val id: String,
    val event: String,
    val headline: String = "",
    val onsetMillis: Long? = null,
    val endsMillis: Long? = null
)

data class WeatherAlert(
    val kind: WeatherAlertKind,
    /** Full line, for example "Gusts 38 mph, steady 18 mph, Wed 2–6 PM". */
    val summary: String,
    /** Short chip, for example "Gusts 38 mph, steady 18 mph". */
    val amountLabel: String,
    val startMillis: Long,
    val endMillis: Long,
    val dedupeKey: String
)

@Serializable
data class CachedGeocode(
    val address: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class CachedPlace(
    val key: String,
    val latitude: Double,
    val longitude: Double,
    val label: String = "",
    val fetchedAtMillis: Long = 0L,
    val hourly: List<HourlyWeather> = emptyList(),
    val nws: List<NwsActiveAlert> = emptyList(),
    val error: String? = null
)

@Serializable
data class WeatherCacheFile(
    val schema: Int = 1,
    val primaryKey: String? = null,
    val places: List<CachedPlace> = emptyList(),
    val geocodes: List<CachedGeocode> = emptyList()
) {
    fun primaryPlace(): CachedPlace? = places.firstOrNull { it.key == primaryKey }
}

data class ForecastFetch(
    val hourly: List<HourlyWeather>,
    val nws: List<NwsActiveAlert>,
    val fetchedAtMillis: Long,
    /** False when api.weather.gov could not be read. Keep the previous official alerts. */
    val nwsFetched: Boolean = true
)

data class LocatedPoint(
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val geocodeAddress: String? = null
)

data class WeatherHomeState(
    val alerts: List<WeatherAlert> = emptyList(),
    val fetchedAtMillis: Long? = null,
    val checking: Boolean = false,
    val statusLine: String = "Not checked yet.",
    val placeLabel: String = ""
)
