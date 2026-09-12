package com.strobingn.wildlifefieldops.data.remote

import android.content.Context
import android.location.Geocoder
import com.strobingn.wildlifefieldops.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class GeoPoint(val latitude: Double, val longitude: Double)

@Singleton
class GeocodingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun geocode(address: String): GeoPoint? = withContext(Dispatchers.IO) {
        val query = address.trim()
        if (query.isBlank()) return@withContext null
        androidGeocode(query) ?: mapsGeocode(query)
    }

    private fun androidGeocode(address: String): GeoPoint? {
        return try {
            if (!Geocoder.isPresent()) return null
            val results = Geocoder(context, Locale.getDefault()).getFromLocationName(address, 1)
            val hit = results?.firstOrNull() ?: return null
            if (!hit.latitude.isFinite() || !hit.longitude.isFinite()) return null
            GeoPoint(hit.latitude, hit.longitude)
        } catch (_: Throwable) {
            null
        }
    }

    private fun mapsGeocode(address: String): GeoPoint? {
        val key = BuildConfig.GOOGLE_MAPS_API_KEY.trim().ifBlank { BuildConfig.GOOGLE_MAPS_API.trim() }
        if (key.isBlank() || key.contains("YOUR_")) return null
        return try {
            val url =
                "https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(address, "UTF-8")}&key=$key"
            val body = httpGet(url)
            val root = json.parseToJsonElement(body).jsonObject
            if (root["status"]?.jsonPrimitive?.content != "OK") return null
            val loc = root["results"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("geometry")?.jsonObject?.get("location")?.jsonObject
                ?: return null
            val lat = loc["lat"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
            val lng = loc["lng"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
            GeoPoint(lat, lng)
        } catch (_: Throwable) {
            null
        }
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
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
