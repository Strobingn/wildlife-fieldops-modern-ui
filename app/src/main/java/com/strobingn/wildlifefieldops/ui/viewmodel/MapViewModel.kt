package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapProperty(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val status: com.strobingn.wildlifefieldops.data.model.JobStatus,
    val type: String
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val jobDao: JobDao,
    private val customerDao: CustomerDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isDrawingBoundary = MutableStateFlow(false)
    val isDrawingBoundary = _isDrawingBoundary.asStateFlow()

    private val _boundaryPoints = MutableStateFlow<List<com.google.android.gms.maps.model.LatLng>>(emptyList())
    val boundaryPoints = _boundaryPoints.asStateFlow()

    /**
     * Every job status is map-eligible, including COMPLETED, INVOICED, and PAID.
     * Older/local jobs may keep coordinates on the customer row instead of the
     * job row, so use that location as a safe fallback.
     */
    val properties: StateFlow<List<MapProperty>> = combine(
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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleDrawingMode() {
        _isDrawingBoundary.value = !_isDrawingBoundary.value
        if (!_isDrawingBoundary.value) {
            _boundaryPoints.value = emptyList()
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
}
