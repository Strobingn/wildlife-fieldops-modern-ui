package com.strobingn.wildlifefieldops.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.strobingn.wildlifefieldops.data.map.WebMercatorTiles
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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

data class CachedMapRegion(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Int,
    val cachedAt: Long
) {
    fun contains(latitude: Double, longitude: Double): Boolean =
        WebMercatorTiles.contains(south, west, north, east, latitude, longitude)
}

/**
 * Practical offline map support without Play Services OfflineRegion tile packs:
 * persists recent job LatLng markers, last camera, and the service-area tile
 * snapshot metadata so MapScreen can restore pins + cached tiles offline.
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
    private val regionSouthKey = doublePreferencesKey("region_south")
    private val regionWestKey = doublePreferencesKey("region_west")
    private val regionNorthKey = doublePreferencesKey("region_north")
    private val regionEastKey = doublePreferencesKey("region_east")
    private val regionMinZoomKey = intPreferencesKey("region_min_zoom")
    private val regionMaxZoomKey = intPreferencesKey("region_max_zoom")
    private val regionTileCountKey = intPreferencesKey("region_tile_count")
    private val regionCachedAtKey = longPreferencesKey("region_cached_at")

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

    suspend fun saveRegion(region: CachedMapRegion) = withContext(Dispatchers.IO) {
        context.mapOfflineDataStore.edit { prefs ->
            prefs[regionSouthKey] = region.south
            prefs[regionWestKey] = region.west
            prefs[regionNorthKey] = region.north
            prefs[regionEastKey] = region.east
            prefs[regionMinZoomKey] = region.minZoom
            prefs[regionMaxZoomKey] = region.maxZoom
            prefs[regionTileCountKey] = region.tileCount
            prefs[regionCachedAtKey] = region.cachedAt
        }
    }

    suspend fun loadRegion(): CachedMapRegion? = withContext(Dispatchers.IO) {
        val prefs = context.mapOfflineDataStore.data.first()
        val south = prefs[regionSouthKey] ?: return@withContext null
        val west = prefs[regionWestKey] ?: return@withContext null
        val north = prefs[regionNorthKey] ?: return@withContext null
        val east = prefs[regionEastKey] ?: return@withContext null
        if (!south.isFinite() || !west.isFinite() || !north.isFinite() || !east.isFinite()) {
            return@withContext null
        }
        CachedMapRegion(
            south = south,
            west = west,
            north = north,
            east = east,
            minZoom = prefs[regionMinZoomKey] ?: 12,
            maxZoom = prefs[regionMaxZoomKey] ?: 14,
            tileCount = prefs[regionTileCountKey] ?: 0,
            cachedAt = prefs[regionCachedAtKey] ?: 0L
        )
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun observeNetwork(): Flow<Boolean> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            trySend(false)
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isNetworkAvailable())
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching { cm.registerNetworkCallback(request, callback) }
        trySend(isNetworkAvailable())
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()
}
