package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.InspectionDao
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.InspectionType
import com.strobingn.wildlifefieldops.data.model.FindingSeverity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InspectionsViewModel @Inject constructor(
    private val inspectionDao: InspectionDao
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    val inspections = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            inspectionDao.getAll()
        } else {
            inspectionDao.getAll().map { list ->
                list.filter {
                    it.customerName.contains(query, ignoreCase = true) ||
                    it.findings.contains(query, ignoreCase = true) ||
                    it.speciesIdentified.contains(query, ignoreCase = true)
                }
            }
        }
    }.onEach { _isLoading.value = false }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inspectionCount = inspectionDao.getAll()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val followUpCount = inspectionDao.getAll()
        .map { it.count { i -> i.followUpRequired } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun getInspectionById(id: String): Flow<Inspection?> = flow {
        emit(inspectionDao.getById(id))
    }

    fun getInspectionsByJob(jobId: String): Flow<List<Inspection>> =
        inspectionDao.getByJob(jobId)

    fun saveInspection(inspection: Inspection) = viewModelScope.launch {
        inspectionDao.insert(inspection)
    }

    fun updateInspection(inspection: Inspection) = viewModelScope.launch {
        inspectionDao.update(inspection.copy(updatedAt = System.currentTimeMillis()))
    }

    fun deleteInspection(inspection: Inspection) = viewModelScope.launch {
        inspectionDao.delete(inspection)
    }

    fun createInspection(
        jobId: String,
        customerId: String,
        customerName: String,
        inspectorName: String,
        inspectionType: InspectionType,
        inspectionDate: Long,
        findings: String,
        recommendations: String,
        severity: FindingSeverity,
        speciesIdentified: String,
        entryPoints: String,
        damageAssessment: String,
        photos: List<String>,
        voiceFieldNotes: String,
        aiReportSource: String,
        followUpRequired: Boolean,
        followUpDate: Long?,
        weatherConditions: String,
        notes: String
    ) = viewModelScope.launch {
        val inspection = Inspection(
            jobId = jobId,
            customerId = customerId,
            customerName = customerName,
            inspectorName = inspectorName,
            inspectionType = inspectionType,
            inspectionDate = inspectionDate,
            findings = findings,
            recommendations = recommendations,
            severity = severity,
            speciesIdentified = speciesIdentified,
            entryPoints = entryPoints,
            damageAssessment = damageAssessment,
            photos = photos,
            voiceFieldNotes = voiceFieldNotes,
            aiReportSource = aiReportSource,
            followUpRequired = followUpRequired,
            followUpDate = followUpDate,
            weatherConditions = weatherConditions,
            notes = notes
        )
        inspectionDao.insert(inspection)
    }
}
