package com.strobingn.wildlifefieldops.ui.viewmodel

import android.location.Location
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.TileProvider
import com.strobingn.wildlifefieldops.data.local.CachedCamera
import com.strobingn.wildlifefieldops.data.local.CachedMapMarker
import com.strobingn.wildlifefieldops.data.local.CachedMapRegion
import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.FieldObservationDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.MapOfflineCache
import com.strobingn.wildlifefieldops.data.local.PhotoDao
import com.strobingn.wildlifefieldops.data.map.CachedMapTileProvider
import com.strobingn.wildlifefieldops.data.map.MapTileCache
import com.strobingn.wildlifefieldops.data.map.MapTileCachePlanner
import com.strobingn.wildlifefieldops.data.model.FieldObservation
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapProperty(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val status: JobStatus,
    val type: String
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val jobDao: JobDao,
    private val customerDao: CustomerDao,
    private val fieldObservationDao: FieldObservationDao,
    private val photoDao: PhotoDao,
    private val mapOfflineCache: MapOfflineCache,
    private val mapTileCache: MapTileCache
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isDrawingBoundary = MutableStateFlow(false)
    val isDrawingBoundary = _isDrawingBoundary.asStateFlow()

    private val _boundaryPoints = MutableStateFlow<List<com.google.android.gms.maps.model.LatLng>>(emptyList())
    val boundaryPoints = _boundaryPoints.asStateFlow()

    private val _isObserving = MutableStateFlow(false)
    val isObserving = _isObserving.asStateFlow()

    private val _pendingPin = MutableStateFlow<com.google.android.gms.maps.model.LatLng?>(null)
    val pendingPin = _pendingPin.asStateFlow()

    private val _isOffline = MutableStateFlow(!mapOfflineCache.isNetworkAvailable())
    val isOffline = _isOffline.asStateFlow()

    private val _cachedCamera = MutableStateFlow<CachedCamera?>(null)
    val cachedCamera = _cachedCamera.asStateFlow()

    private val _cachedRegion = MutableStateFlow<CachedMapRegion?>(null)
    val cachedRegion = _cachedRegion.asStateFlow()

    private val _hasCachedTiles = MutableStateFlow(false)
    val hasCachedTiles = _hasCachedTiles.asStateFlow()

    private val _isCaching = MutableStateFlow(false)
    val isCaching = _isCaching.asStateFlow()

    private val _cacheProgress = MutableStateFlow<String?>(null)
    val cacheProgress = _cacheProgress.asStateFlow()

    private val _cacheMessage = MutableStateFlow<String?>(null)
    val cacheMessage = _cacheMessage.asStateFlow()

    private val _fallbackMarkers = MutableStateFlow<List<MapProperty>>(emptyList())

    val tileProvider: TileProvider = CachedMapTileProvider(mapTileCache) {
        mapOfflineCache.isNetworkAvailable()
    }

    /**
     * Every job status is map-eligible, including COMPLETED, INVOICED, and PAID.
     * Older/local jobs may keep coordinates on the customer row instead of the
     * job row, so use that location as a safe fallback.
     */
    private val roomProperties: StateFlow<List<MapProperty>> = combine(
        jobDao.getAll(),
        customerDao.getAll()
    ) { jobs, customers ->
        val customersById = customers.associateBy { it.id }
        jobs.mapNotNull { job ->
            val customer = customersById[job.customerId]
            val latitude = job.latitude ?: customer?.latitude
            val longitude = job.longitude ?: customer?.longitude
            if (latitude == null || longitude == null ||
                !latitude.isFinite() || !longitude.isFinite() ||
                latitude !in -90.0..90.0 || longitude !in -180.0..180.0
            ) {
                null
            } else {
                MapProperty(
                    id = job.id,
                    name = job.title,
                    address = job.address,
                    latitude = latitude,
                    longitude = longitude,
                    status = job.status,
                    type = job.type
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val properties: StateFlow<List<MapProperty>> = combine(
        roomProperties,
        _fallbackMarkers
    ) { room, fallback ->
        if (room.isNotEmpty()) room else fallback
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val observations: StateFlow<List<FieldObservation>> = fieldObservationDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val unlocatedJobCount: StateFlow<Int> = combine(
        jobDao.getAll(),
        properties
    ) { jobs, located ->
        (jobs.size - located.map { it.id }.toSet().size).coerceAtLeast(0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val filteredProperties = combine(properties, _searchQuery) { props, query ->
        if (query.isBlank()) props
        else props.filter {
            it.name.contains(query, ignoreCase = true) ||
                it.address.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _cachedCamera.value = mapOfflineCache.loadCamera()
            _cachedRegion.value = mapOfflineCache.loadRegion()
            _fallbackMarkers.value = mapOfflineCache.loadMarkers().map { it.toProperty() }
            _hasCachedTiles.value = mapTileCache.cachedTileCount() > 0
            refreshConnectivity()
        }
        viewModelScope.launch {
            mapOfflineCache.observeNetwork().collect { online ->
                _isOffline.value = !online
            }
        }
        // Persist markers whenever Room-backed properties change (IO off main via DataStore).
        viewModelScope.launch {
            roomProperties.collect { props ->
                if (props.isNotEmpty()) {
                    mapOfflineCache.saveMarkers(props.map { it.toCached() })
                }
            }
        }
    }

    fun refreshConnectivity() {
        _isOffline.value = !mapOfflineCache.isNetworkAvailable()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleDrawingMode() {
        _isDrawingBoundary.value = !_isDrawingBoundary.value
        if (_isDrawingBoundary.value) {
            _isObserving.value = false
            _pendingPin.value = null
        }
        if (!_isDrawingBoundary.value) {
            _boundaryPoints.value = emptyList()
        }
    }

    fun toggleObserveMode() {
        _isObserving.value = !_isObserving.value
        if (_isObserving.value) {
            _isDrawingBoundary.value = false
        } else {
            _pendingPin.value = null
        }
    }

    fun onMapTapped(point: com.google.android.gms.maps.model.LatLng) {
        when {
            _isObserving.value -> _pendingPin.value = point
            _isDrawingBoundary.value -> addBoundaryPoint(point)
        }
    }

    fun addBoundaryPoint(point: com.google.android.gms.maps.model.LatLng) {
        if (_isDrawingBoundary.value) {
            _boundaryPoints.value = _boundaryPoints.value + point
        }
    }

    fun clearBoundary() {
        _boundaryPoints.value = emptyList()
        _isDrawingBoundary.value = false
    }

    fun saveBoundary() {
        viewModelScope.launch {
            _isDrawingBoundary.value = false
            _boundaryPoints.value = emptyList()
        }
    }

    fun cancelPendingObservation() {
        _pendingPin.value = null
    }

    fun saveObservation(
        notes: String,
        photoLocalPath: String?,
        photoUri: String?,
        speciesHint: String,
        jobId: String? = null,
        lastKnown: Location? = null
    ) {
        viewModelScope.launch {
            val pin = _pendingPin.value ?: return@launch
            val photo = if (!photoLocalPath.isNullOrBlank()) {
                Photo(
                    filePath = photoUri.orEmpty().ifBlank { photoLocalPath },
                    localPath = photoLocalPath,
                    jobId = jobId,
                    category = PhotoCategory.WILDLIFE,
                    description = notes.ifBlank { "Field observation" },
                    latitude = pin.latitude,
                    longitude = pin.longitude,
                    takenAt = System.currentTimeMillis(),
                    isUploaded = false
                ).also { photoDao.insert(it) }
            } else {
                null
            }
            val observation = FieldObservation(
                notes = notes.ifBlank { "Field observation" },
                latitude = pin.latitude,
                longitude = pin.longitude,
                photoLocalPath = photoLocalPath.orEmpty(),
                photoId = photo?.id,
                jobId = jobId,
                speciesHint = speciesHint.trim(),
                accuracyMeters = lastKnown?.accuracy,
                isSynced = false
            )
            fieldObservationDao.insert(observation)
            _pendingPin.value = null
            _isObserving.value = false
            _cacheMessage.value = if (_isOffline.value) {
                "Saved offline in Room. Will sync when you are back online."
            } else {
                "Observation saved. Sync from Settings when ready."
            }
        }
    }

    fun persistCamera(latitude: Double, longitude: Double, zoom: Float) {
        viewModelScope.launch {
            mapOfflineCache.saveCamera(latitude, longitude, zoom)
            _cachedCamera.value = CachedCamera(latitude, longitude, zoom)
        }
    }

    fun snapshotOfflineCache(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        zoom: Int
    ) {
        if (_isCaching.value) return
        viewModelScope.launch {
            _isCaching.value = true
            refreshConnectivity()
            try {
                val props = properties.value
                mapOfflineCache.saveMarkers(props.map { it.toCached() })
                val plan = MapTileCachePlanner.plan(south, west, north, east, zoom)
                _cacheProgress.value = "Caching map tiles 0/${plan.tiles.size}…"
                val result = mapTileCache.prefetch(plan.tiles) { done, total ->
                    _cacheProgress.value = "Caching map tiles $done/$total…"
                }
                val region = CachedMapRegion(
                    south = south,
                    west = west,
                    north = north,
                    east = east,
                    minZoom = plan.minZoom,
                    maxZoom = plan.maxZoom,
                    tileCount = result.usable,
                    cachedAt = System.currentTimeMillis()
                )
                mapOfflineCache.saveRegion(region)
                _cachedRegion.value = region
                _hasCachedTiles.value = mapTileCache.cachedTileCount() > 0
                val extra = if (plan.truncated) " (tile budget reached)" else ""
                _cacheMessage.value =
                    "Cached ${props.size} job markers + ${result.usable} map tiles$extra. " +
                        "Failed: ${result.failed}."
            } catch (t: Throwable) {
                _cacheMessage.value = "Cache failed: ${t.message ?: t.javaClass.simpleName}"
            } finally {
                _isCaching.value = false
                _cacheProgress.value = null
            }
        }
    }

    fun snapshotOfflineCache() {
        val cam = _cachedCamera.value
        val zoom = cam?.zoom?.toInt() ?: 13
        val pad = 360.0 / (1 shl zoom.coerceIn(8, 16))
        val lat = cam?.latitude ?: 41.45
        val lng = cam?.longitude ?: -74.05
        snapshotOfflineCache(lat - pad, lng - pad, lat + pad, lng + pad, zoom)
    }

    fun lastKnownLocation(locationManager: LocationManager): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.mapNotNull { provider ->
            runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull { it.time }
    }

    fun clearCacheMessage() {
        _cacheMessage.value = null
    }

    private fun MapProperty.toCached() = CachedMapMarker(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        status = status.name,
        type = type
    )

    private fun CachedMapMarker.toProperty() = MapProperty(
        id = id,
        name = name,
        address = address,
        latitude = latitude,
        longitude = longitude,
        status = runCatching { JobStatus.valueOf(status) }.getOrDefault(JobStatus.PENDING),
        type = type
    )
}
