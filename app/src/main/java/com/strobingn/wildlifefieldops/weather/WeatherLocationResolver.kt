package com.strobingn.wildlifefieldops.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.strobingn.wildlifefieldops.data.remote.GeoPoint
import com.strobingn.wildlifefieldops.data.remote.GeocodingService
import com.strobingn.wildlifefieldops.ui.viewmodel.settingsDataStore
import com.strobingn.wildlifefieldops.util.BusinessProfileStore
import com.strobingn.wildlifefieldops.util.WildlifeWhispererIdentity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherLocationResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geocoding: GeocodingService
) {
    private val memory = mutableMapOf<String, GeoPoint>()

    fun phoneLocation(): GeoPoint? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        val fix = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time } ?: return null
        if (!fix.latitude.isFinite() || !fix.longitude.isFinite()) return null
        return GeoPoint(fix.latitude, fix.longitude)
    }

    suspend fun businessAddress(): String {
        val profile = runCatching {
            BusinessProfileStore.read(context.settingsDataStore.data.first())
        }.getOrNull()
        return profile?.address?.trim().orEmpty().ifBlank { WildlifeWhispererIdentity.ADDRESS }
    }

    suspend fun resolvePrimary(known: List<CachedGeocode>): LocatedPoint? {
        phoneLocation()?.let { point ->
            return LocatedPoint(point.latitude, point.longitude, "This phone")
        }
        val address = businessAddress()
        val point = geocodeCached(address, known) ?: return null
        return LocatedPoint(point.latitude, point.longitude, address, geocodeAddress = address)
    }

    suspend fun geocodeCached(address: String, known: List<CachedGeocode>): GeoPoint? {
        val query = address.trim()
        if (query.isBlank()) return null
        val key = query.lowercase()
        memory[key]?.let { return it }
        known.firstOrNull { it.address.equals(query, ignoreCase = true) }?.let { hit ->
            val point = GeoPoint(hit.latitude, hit.longitude)
            memory[key] = point
            return point
        }
        val point = runCatching { geocoding.geocode(query) }.getOrNull() ?: return null
        memory[key] = point
        return point
    }
}
