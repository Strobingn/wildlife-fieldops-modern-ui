package com.strobingn.wildlifefieldops.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherForecastClient @Inject constructor() {

    suspend fun fetch(latitude: Double, longitude: Double): ForecastFetch? = withContext(Dispatchers.IO) {
        try {
            val meteoBody = httpGet(WeatherForecastUrls.openMeteo(latitude, longitude), userAgent = WeatherForecastUrls.USER_AGENT)
                ?: return@withContext null
            val hourly = OpenMeteoParser.parse(meteoBody)
            if (hourly.isEmpty()) return@withContext null
            val nwsBody = runCatching {
                httpGet(
                    WeatherForecastUrls.nwsActive(latitude, longitude),
                    userAgent = WeatherForecastUrls.USER_AGENT
                )
            }.getOrElse {
                Log.w(TAG, "NWS alerts unavailable", it)
                null
            }
            val nwsFetched = nwsBody != null
            val nws = if (nwsBody.isNullOrBlank()) emptyList() else NwsAlertParser.parse(nwsBody)
            ForecastFetch(
                hourly = hourly,
                nws = nws,
                fetchedAtMillis = System.currentTimeMillis(),
                nwsFetched = nwsFetched
            )
        } catch (error: Exception) {
            Log.e(TAG, "Forecast fetch failed", error)
            null
        }
    }

    private fun httpGet(url: String, userAgent: String): String? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", userAgent)
        }
        return try {
            if (connection.responseCode !in 200..299) {
                Log.w(TAG, "HTTP ${connection.responseCode} for $url")
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TAG = "WeatherForecast"
    }
}
