package com.strobingn.wildlifefieldops.weather

import android.content.Context
import android.util.Log
import com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.remote.GeoPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherAlertRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val client: WeatherForecastClient,
    private val locations: WeatherLocationResolver,
    private val settingsStore: WeatherAlertSettingsStore,
    private val notifier: WeatherAlertNotifier
) {
    private val cache = WeatherAlertCache(File(context.filesDir, "weather-alerts-cache.json"))
    private val checking = MutableStateFlow(false)
    private val checkMutex = Mutex()
    private val jobPoints = MutableStateFlow<Map<String, GeoPoint>>(emptyMap())

    val settings: Flow<WeatherAlertSettings> = settingsStore.settings
    val cacheState: StateFlow<WeatherCacheFile> = cache.updates()
    val resolvedJobPoints: StateFlow<Map<String, GeoPoint>> = jobPoints.asStateFlow()

    val home: Flow<WeatherHomeState> = combine(settingsStore.settings, cache.updates(), checking) { settings, file, busy ->
        buildHome(settings, file, busy)
    }

    suspend fun updateSettings(block: (WeatherAlertSettings) -> WeatherAlertSettings) {
        runCatching { settingsStore.update(block) }
            .onFailure { Log.e(TAG, "Could not save weather alert settings", it) }
    }

    suspend fun refreshIfStale() {
        val fetched = cache.current().primaryPlace()?.fetchedAtMillis ?: 0L
        val age = System.currentTimeMillis() - fetched
        if (fetched == 0L || age > STALE_MS) checkNow(notify = true)
    }

    suspend fun checkNow(notify: Boolean) {
        checkMutex.withLock {
            checking.value = true
            try {
                val known = cache.current().geocodes
                val located = locations.resolvePrimary(known)
                if (located == null) {
                    cache.noteError("No location yet. Turn on location or set the business address in Settings.")
                    return
                }
                located.geocodeAddress?.let { address ->
                    cache.rememberGeocode(address, located.latitude, located.longitude)
                }
                val fetched = client.fetch(located.latitude, located.longitude)
                if (fetched == null) {
                    cache.noteError("Couldn't reach the forecast. Showing the last check.")
                    return
                }
                cache.savePlace(
                    latitude = located.latitude,
                    longitude = located.longitude,
                    label = located.label,
                    fetched = fetched,
                    makePrimary = true
                )
                if (notify) publishNewAlerts()
            } catch (error: Throwable) {
                Log.e(TAG, "Weather check failed", error)
                runCatching { cache.noteError("Couldn't reach the forecast. Showing the last check.") }
            } finally {
                checking.value = false
            }
        }
    }

    suspend fun ensureJobForecasts(jobs: List<Job>) {
        var fetches = 0
        for (job in jobs) {
            if (fetches >= MAX_JOB_FETCHES) return
            val flag = JobStatusPipeline.flag(job.status)
            if (flag != JobStatus.SCHEDULED && flag != JobStatus.IN_PROGRESS) continue
            if (job.scheduledDate == null) continue
            val file = cache.current()
            val point = resolveJobPoint(job, file) ?: continue
            rememberJobPoint(job.id, point)
            val miles = nearestMiles(point, file)
            if (miles <= WeatherAlertEngine.NEAR_MILES) continue
            val fetched = runCatching { client.fetch(point.latitude, point.longitude) }.getOrNull() ?: continue
            cache.savePlace(
                latitude = point.latitude,
                longitude = point.longitude,
                label = job.address.ifBlank { "Job site" },
                fetched = fetched,
                makePrimary = false
            )
            fetches++
        }
    }

    fun matchJobs(
        jobs: List<Job>,
        file: WeatherCacheFile,
        settings: WeatherAlertSettings,
        nowMillis: Long,
        resolved: Map<String, GeoPoint> = emptyMap()
    ): Map<String, List<WeatherAlert>> {
        val primaryPlace = file.primaryPlace()
        val primary = primaryPlace?.let { place ->
            ForecastPoint(
                place.latitude,
                place.longitude,
                WeatherAlertEngine.evaluate(place.hourly, place.nws, settings, nowMillis)
            )
        }
        val others = file.places.filter { it.key != file.primaryKey }.map { place ->
            ForecastPoint(
                place.latitude,
                place.longitude,
                WeatherAlertEngine.evaluate(place.hourly, place.nws, settings, nowMillis)
            )
        }
        return jobs.mapNotNull { job ->
            val resolvedPoint = resolved[job.id]
            val alerts = WeatherAlertEngine.alertsForJobLocation(
                status = job.status,
                scheduledDateMillis = job.scheduledDate,
                jobLatitude = job.latitude ?: resolvedPoint?.latitude,
                jobLongitude = job.longitude ?: resolvedPoint?.longitude,
                primary = primary,
                otherPlaces = others
            )
            if (alerts.isEmpty()) null else job.id to alerts
        }.toMap()
    }

    private suspend fun resolveJobPoint(job: Job, file: WeatherCacheFile): GeoPoint? {
        val lat = job.latitude
        val lon = job.longitude
        if (lat != null && lon != null && lat.isFinite() && lon.isFinite() && !(lat == 0.0 && lon == 0.0)) {
            return GeoPoint(lat, lon)
        }
        val address = job.address.trim()
        if (address.isBlank()) return null
        val point = locations.geocodeCached(address, file.geocodes) ?: return null
        if (file.geocodes.none { it.address.equals(address, ignoreCase = true) }) {
            cache.rememberGeocode(address, point.latitude, point.longitude)
        }
        return point
    }

    private fun rememberJobPoint(jobId: String, point: GeoPoint) {
        val existing = jobPoints.value[jobId]
        if (existing != null && existing.latitude == point.latitude && existing.longitude == point.longitude) return
        jobPoints.value = jobPoints.value + (jobId to point)
    }

    private fun nearestMiles(point: GeoPoint, file: WeatherCacheFile): Double {
        if (file.places.isEmpty()) return Double.MAX_VALUE
        return file.places.minOf { place ->
            WeatherAlertEngine.milesBetween(point.latitude, point.longitude, place.latitude, place.longitude)
        }
    }

    private suspend fun publishNewAlerts() {
        val settings = settingsStore.current()
        if (!settings.notificationsEnabled) return
        val place = cache.current().primaryPlace() ?: return
        val alerts = WeatherAlertEngine.evaluate(place.hourly, place.nws, settings, System.currentTimeMillis())
        val fresh = WeatherAlertEngine.unseen(alerts, settings.notifiedKeys)
        if (fresh.isEmpty()) return
        if (notifier.notify(fresh)) settingsStore.addNotified(fresh.map { it.dedupeKey })
    }

    private fun buildHome(settings: WeatherAlertSettings, file: WeatherCacheFile, busy: Boolean): WeatherHomeState {
        val place = file.primaryPlace()
        val fetchedAt = place?.fetchedAtMillis?.takeIf { it > 0L }
        val alerts = if (place == null) {
            emptyList()
        } else {
            WeatherAlertEngine.evaluate(place.hourly, place.nws, settings, System.currentTimeMillis())
        }
        val checked = fetchedAt?.let { WeatherAlertEngine.formatLastChecked(it) }
        val statusLine = when {
            busy -> "Checking…"
            place?.error != null && checked != null -> "${place.error} $checked."
            place?.error != null -> place.error.orEmpty()
            checked == null -> "Not checked yet."
            alerts.isEmpty() -> "No alerts in the next 48 hours. $checked."
            else -> "$checked."
        }
        return WeatherHomeState(
            alerts = alerts,
            fetchedAtMillis = fetchedAt,
            checking = busy,
            statusLine = statusLine,
            placeLabel = place?.label.orEmpty()
        )
    }

    private suspend fun WeatherAlertCache.noteError(message: String) {
        update { file ->
            val key = file.primaryKey
            if (key == null || file.places.none { it.key == key }) {
                val placeholder = CachedPlace(key = "none", latitude = 0.0, longitude = 0.0, label = "", error = message)
                file.copy(primaryKey = "none", places = listOf(placeholder) + file.places)
            } else {
                file.copy(places = file.places.map { if (it.key == key) it.copy(error = message) else it })
            }
        }
    }

    private suspend fun WeatherAlertCache.rememberGeocode(address: String, latitude: Double, longitude: Double) {
        update { file ->
            if (file.geocodes.any { it.address.equals(address, ignoreCase = true) }) return@update file
            val next = (file.geocodes + CachedGeocode(address.trim(), latitude, longitude)).takeLast(100)
            file.copy(geocodes = next)
        }
    }

    private suspend fun WeatherAlertCache.savePlace(
        latitude: Double,
        longitude: Double,
        label: String,
        fetched: ForecastFetch,
        makePrimary: Boolean
    ) {
        val key = placeKey(latitude, longitude)
        update { file ->
            val previous = file.places.firstOrNull { it.key == key }
            val place = CachedPlace(
                key = key,
                latitude = latitude,
                longitude = longitude,
                label = label,
                fetchedAtMillis = fetched.fetchedAtMillis,
                hourly = fetched.hourly,
                nws = if (fetched.nwsFetched) fetched.nws else previous?.nws.orEmpty(),
                error = null
            )
            val without = file.places.filter { it.key != key }
            val primaryKey = if (makePrimary) key else file.primaryKey
            val trimmed = (listOf(place) + without).take(MAX_PLACES).toMutableList()
            val keepPrimary = if (makePrimary) null else file.primaryPlace()
            if (keepPrimary != null && trimmed.none { it.key == keepPrimary.key }) {
                if (trimmed.isEmpty()) trimmed += keepPrimary else trimmed[trimmed.lastIndex] = keepPrimary
            }
            file.copy(primaryKey = primaryKey, places = trimmed)
        }
    }

    companion object {
        private const val TAG = "WeatherAlerts"
        private const val STALE_MS = 3L * 60L * 60L * 1000L
        private const val MAX_JOB_FETCHES = 3
        private const val MAX_PLACES = 12

        fun placeKey(latitude: Double, longitude: Double): String =
            String.format(Locale.US, "%.2f,%.2f", latitude, longitude)
    }
}
