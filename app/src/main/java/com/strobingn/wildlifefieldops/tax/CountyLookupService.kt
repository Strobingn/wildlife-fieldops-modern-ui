package com.strobingn.wildlifefieldops.tax

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

data class CountyResult(
    val county: String,
    val state: String
)

/**
 * Resolves the county (and state) for a given geographic location so the invoice
 * screen can auto-fill the correct NY sales tax rate.
 *
 * Resolution order:
 *   1. Android [Geocoder] reverse-geocode from lat/lng (no quota, works offline after
 *      first device location fix).
 *   2. Google Maps Geocoding API reverse-geocode (if GOOGLE_MAPS_API_KEY is configured).
 *   3. Android [Geocoder] forward-geocode from address text.
 *   4. Google Maps Geocoding API forward-geocode from address text.
 *
 * Returns `null` on any failure so the caller can keep the tax field manually editable.
 */
@Singleton
class CountyLookupService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Primary entry point. Tries reverse-geocode first (preferred — requires coords),
     * then forward-geocode from [address] as a fallback.
     */
    suspend fun resolveCounty(
        latitude: Double?,
        longitude: Double?,
        address: String
    ): CountyResult? = withContext(Dispatchers.IO) {
        if (latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite()
        ) {
            reverseGeocode(latitude, longitude)
                ?: forwardGeocodeCounty(address)
        } else {
            forwardGeocodeCounty(address)
        }
    }

    // ── reverse geocode ───────────────────────────────────────────────────────────

    private fun reverseGeocode(lat: Double, lng: Double): CountyResult? {
        return androidReverseGeocode(lat, lng) ?: mapsReverseGeocode(lat, lng)
    }

    @Suppress("DEPRECATION")
    private fun androidReverseGeocode(lat: Double, lng: Double): CountyResult? {
        return try {
            if (!Geocoder.isPresent()) return null
            val results = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
            val addr = results?.firstOrNull() ?: return null
            val county = addr.subAdminArea?.takeIf { it.isNotBlank() } ?: return null
            val state = addr.adminArea?.takeIf { it.isNotBlank() } ?: "NY"
            CountyResult(county = county, state = state)
        } catch (_: Throwable) {
            null
        }
    }

    private fun mapsReverseGeocode(lat: Double, lng: Double): CountyResult? {
        val key = mapsApiKey() ?: return null
        return try {
            val url =
                "https://maps.googleapis.com/maps/api/geocode/json?latlng=$lat,$lng&result_type=administrative_area_level_2&key=$key"
            parseGoogleCounty(httpGet(url))
        } catch (_: Throwable) {
            null
        }
    }

    // ── forward geocode ───────────────────────────────────────────────────────────

    private fun forwardGeocodeCounty(address: String): CountyResult? {
        val query = address.trim().ifBlank { return null }
        return androidForwardGeocodeCounty(query) ?: mapsForwardGeocodeCounty(query)
    }

    @Suppress("DEPRECATION")
    private fun androidForwardGeocodeCounty(address: String): CountyResult? {
        return try {
            if (!Geocoder.isPresent()) return null
            val results = Geocoder(context, Locale.getDefault()).getFromLocationName(address, 1)
            val addr = results?.firstOrNull() ?: return null
            val county = addr.subAdminArea?.takeIf { it.isNotBlank() } ?: return null
            val state = addr.adminArea?.takeIf { it.isNotBlank() } ?: "NY"
            CountyResult(county = county, state = state)
        } catch (_: Throwable) {
            null
        }
    }

    private fun mapsForwardGeocodeCounty(address: String): CountyResult? {
        val key = mapsApiKey() ?: return null
        return try {
            val url =
                "https://maps.googleapis.com/maps/api/geocode/json?address=${URLEncoder.encode(address, "UTF-8")}&key=$key"
            parseGoogleCounty(httpGet(url))
        } catch (_: Throwable) {
            null
        }
    }

    // ── JSON parsing ──────────────────────────────────────────────────────────────

    /**
     * Extracts county (administrative_area_level_2) and state (administrative_area_level_1)
     * from a Google Geocoding API JSON response body.
     */
    private fun parseGoogleCounty(body: String): CountyResult? {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            if (root["status"]?.jsonPrimitive?.content != "OK") return null
            val components = root["results"]?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("address_components")?.jsonArray ?: return null

            var county: String? = null
            var state: String? = null

            for (comp in components) {
                val obj = comp.jsonObject
                val types = obj["types"]?.jsonArray?.map { it.jsonPrimitive.content } ?: continue
                val longName = obj["long_name"]?.jsonPrimitive?.content ?: continue
                val shortName = obj["short_name"]?.jsonPrimitive?.content ?: longName

                if ("administrative_area_level_2" in types) {
                    county = longName
                        .replace(Regex("(?i)\\s+county$"), "")
                        .trim()
                        .ifBlank { null }
                        ?.let { "$it County" }
                }
                if ("administrative_area_level_1" in types) {
                    state = shortName
                }
            }

            if (county != null) CountyResult(county = county, state = state ?: "NY") else null
        } catch (_: Throwable) {
            null
        }
    }

    // ── utilities ─────────────────────────────────────────────────────────────────

    private fun mapsApiKey(): String? {
        val key = runCatching { BuildConfig.GOOGLE_MAPS_API_KEY.trim() }.getOrElse { "" }
            .ifBlank {
                runCatching { BuildConfig.GOOGLE_MAPS_API.trim() }.getOrElse { "" }
            }
        return key.takeIf { it.isNotBlank() && !it.contains("YOUR_") }
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val stream =
                if (connection.responseCode in 200..299) connection.inputStream
                else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        } finally {
            connection.disconnect()
        }
    }
}
