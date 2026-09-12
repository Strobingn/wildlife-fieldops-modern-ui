package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

data class DrivingDistance(
    val miles: Double,
    val meters: Long,
    val durationText: String,
    val origin: String,
    val destination: String
)

@Singleton
class DistanceService @Inject constructor() {

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    val isConfigured: Boolean
        get() {
            val key = mapsKey()
            return key.isNotBlank() && !key.contains("YOUR_")
        }

    suspend fun drivingMiles(origin: String, destination: String): Result<DrivingDistance> =
        withContext(Dispatchers.IO) {
            val from = origin.trim()
            val to = destination.trim()
            if (from.isBlank() || to.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Need shop address and job address to measure miles.")
                )
            }
            if (!isConfigured) {
                return@withContext Result.failure(
                    IllegalStateException("Maps key missing — cannot look up driving distance.")
                )
            }
            try {
                val url = buildString {
                    append("https://maps.googleapis.com/maps/api/distancematrix/json")
                    append("?units=imperial")
                    append("&origins=").append(enc(from))
                    append("&destinations=").append(enc(to))
                    append("&mode=driving")
                    append("&key=").append(mapsKey())
                }
                val body = httpGet(url)
                val root = json.parseToJsonElement(body).jsonObject
                val status = root["status"]?.jsonPrimitive?.content.orEmpty()
                if (status != "OK") {
                    val err = root["error_message"]?.jsonPrimitive?.content.orEmpty()
                    return@withContext Result.failure(
                        IllegalStateException("Distance lookup $status ${err.take(160)}".trim())
                    )
                }
                val element = root["rows"]
                    ?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("elements")
                    ?.jsonArray?.firstOrNull()
                    ?.jsonObject
                    ?: return@withContext Result.failure(IllegalStateException("No distance element returned."))
                val elemStatus = element["status"]?.jsonPrimitive?.content.orEmpty()
                if (elemStatus != "OK") {
                    return@withContext Result.failure(
                        IllegalStateException("Could not route shop → job ($elemStatus). Check both addresses.")
                    )
                }
                val meters = element["distance"]?.jsonObject?.get("value")?.jsonPrimitive?.longOrNull
                    ?: return@withContext Result.failure(IllegalStateException("Distance missing from Maps response."))
                val durationText = element["duration"]?.jsonObject?.get("text")?.jsonPrimitive?.content.orEmpty()
                val miles = (meters / 1609.344).let { raw ->
                    kotlin.math.round(raw * 10.0) / 10.0
                }
                Result.success(
                    DrivingDistance(
                        miles = miles,
                        meters = meters,
                        durationText = durationText,
                        origin = from,
                        destination = to
                    )
                )
            } catch (t: Throwable) {
                Result.failure(t)
            }
        }

    private fun mapsKey(): String =
        BuildConfig.GOOGLE_MAPS_API_KEY.trim().ifBlank { BuildConfig.GOOGLE_MAPS_API.trim() }

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }
}
