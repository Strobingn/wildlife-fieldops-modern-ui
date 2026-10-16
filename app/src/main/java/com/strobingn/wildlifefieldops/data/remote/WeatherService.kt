package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class WeatherSnapshot(
    val tempF: Int,
    val condition: String,
    val description: String,
    val humidity: Int?,
    val windMph: Float?,
    val summaryLine: String
)

@Singleton
class WeatherService @Inject constructor() {

    val isConfigured: Boolean
        get() = BuildConfig.OPENWEATHER_API_KEY.isNotBlank() &&
            !BuildConfig.OPENWEATHER_API_KEY.contains("your_", ignoreCase = true) &&
            !BuildConfig.OPENWEATHER_API_KEY.contains("YOUR_")

    suspend fun getWeather(lat: Double, lon: Double): WeatherSnapshot? = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext null
        val key = BuildConfig.OPENWEATHER_API_KEY
        val url = URL(
            "https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&appid=$key&units=imperial"
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
        }
        try {
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val main = json.getJSONObject("main")
            val weather0 = json.getJSONArray("weather").getJSONObject(0)
            val wind = json.optJSONObject("wind")
            val temp = main.getDouble("temp").toInt()
            val condition = weather0.getString("main")
            val description = weather0.getString("description")
            val humidity = main.optInt("humidity", -1).takeIf { it >= 0 }
            // optDouble() returns NaN when "speed" is absent; don't surface that as a wind reading.
            val windMph = wind?.optDouble("speed", Double.NaN)
                ?.takeIf { !it.isNaN() }
                ?.toFloat()
            WeatherSnapshot(
                tempF = temp,
                condition = condition,
                description = description,
                humidity = humidity,
                windMph = windMph,
                summaryLine = "$temp°F $condition (${description})"
            )
        } catch (e: Exception) {
            android.util.Log.e("WeatherService", "Weather fetch failed", e)
            null
        } finally {
            connection.disconnect()
        }
    }
}
