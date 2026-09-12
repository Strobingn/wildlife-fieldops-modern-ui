package com.strobingn.wildlifefieldops.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.mapOfflineDataStore by preferencesDataStore(name = "map_offline_cache")

@Serializable
data class CachedMapMarker(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val status: String,
    val type: String
)

data class CachedCamera(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float
)

/**
 * Practical offline map support without Play Services OfflineRegion tile packs:
 * persists recent job LatLng markers + last camera so MapScreen can show pins
 * and restore the viewport when the network is unavailable.
 */
@Singleton
class MapOfflineCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val markersKey = stringPreferencesKey("markers_json")
    private val camLatKey = doublePreferencesKey("cam_lat")
    private val camLngKey = doublePreferencesKey("cam_lng")
    private val camZoomKey = floatPreferencesKey("cam_zoom")

    suspend fun saveMarkers(markers: List<CachedMapMarker>) = withContext(Dispatchers.IO) {
        context.mapOfflineDataStore.edit { prefs ->
            prefs[markersKey] = json.encodeToString(markers)
        }
    }

    suspend fun loadMarkers(): List<CachedMapMarker> = withContext(Dispatchers.IO) {
        val raw = context.mapOfflineDataStore.data.map { it[markersKey] }.first().orEmpty()
        if (raw.isBlank()) return@withContext emptyList()
        runCatching { json.decodeFromString<List<CachedMapMarker>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun saveCamera(latitude: Double, longitude: Double, zoom: Float) = withContext(Dispatchers.IO) {
        if (!latitude.isFinite() || !longitude.isFinite() || !zoom.isFinite()) return@withContext
        context.mapOfflineDataStore.edit { prefs ->
            prefs[camLatKey] = latitude
            prefs[camLngKey] = longitude
            prefs[camZoomKey] = zoom
        }
    }

    suspend fun loadCamera(): CachedCamera? = withContext(Dispatchers.IO) {
        val prefs = context.mapOfflineDataStore.data.first()
        val lat = prefs[camLatKey] ?: return@withContext null
        val lng = prefs[camLngKey] ?: return@withContext null
        val zoom = prefs[camZoomKey] ?: 12f
        if (!lat.isFinite() || !lng.isFinite()) null else CachedCamera(lat, lng, zoom)
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
