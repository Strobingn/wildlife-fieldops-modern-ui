package com.strobingn.wildlifefieldops.weather

import java.util.Locale

/** Free forecast URLs. No API key is added. */
object WeatherForecastUrls {
    const val USER_AGENT = "WildlifeFieldOps/2.8.0 (austin@wildlifewhispererllc.com)"

    fun openMeteo(latitude: Double, longitude: Double): String {
        val lat = String.format(Locale.US, "%.4f", latitude)
        val lon = String.format(Locale.US, "%.4f", longitude)
        return "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&hourly=precipitation,precipitation_probability,rain,snowfall,wind_speed_10m,wind_gusts_10m,weather_code" +
            "&temperature_unit=fahrenheit" +
            "&wind_speed_unit=mph" +
            "&precipitation_unit=inch" +
            "&timezone=America%2FNew_York" +
            "&forecast_days=3"
    }

    fun nwsActive(latitude: Double, longitude: Double): String {
        val lat = String.format(Locale.US, "%.4f", latitude)
        val lon = String.format(Locale.US, "%.4f", longitude)
        return "https://api.weather.gov/alerts/active?point=$lat,$lon"
    }
}
