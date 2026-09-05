package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.InspectionDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.model.FindingSeverity
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.InspectionType
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.remote.AiService
import com.strobingn.wildlifefieldops.data.remote.InspectionReportContext
import com.strobingn.wildlifefieldops.data.remote.InspectionReportDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InspectionsViewModel @Inject constructor(
    private val inspectionDao: InspectionDao,
    private val jobDao: JobDao,
    private val aiService: AiService
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _reportLoading = MutableStateFlow(false)
    val reportLoading = _reportLoading.asStateFlow()

    private val _reportError = MutableStateFlow<String?>(null)
    val reportError = _reportError.asStateFlow()

    private val _reportSource = MutableStateFlow<String?>(null)
    val reportSource = _reportSource.asStateFlow()

    private val _lastReportDraft = MutableStateFlow<InspectionReportDraft?>(null)
    val lastReportDraft = _lastReportDraft.asStateFlow()

    private val _estimatePrepLoading = MutableStateFlow(false)
    val estimatePrepLoading = _estimatePrepLoading.asStateFlow()

    private val _estimatePrepMessage = MutableStateFlow<String?>(null)
    val estimatePrepMessage = _estimatePrepMessage.asStateFlow()

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

    fun observeJob(jobId: String): Flow<Job?> {
        if (jobId.isBlank()) return flowOf(null)
        return flow { emit(jobDao.getById(jobId)) }
    }

    suspend fun loadJobOnce(jobId: String): Job? {
        if (jobId.isBlank()) return null
        return jobDao.getById(jobId)
    }

    fun saveInspection(inspection: Inspection) = viewModelScope.launch {
        inspectionDao.insert(inspection)
    }

    fun updateInspection(inspection: Inspection) = viewModelScope.launch {
        inspectionDao.update(inspection.copy(updatedAt = System.currentTimeMillis()))
    }

    fun deleteInspection(inspection: Inspection) = viewModelScope.launch {
        inspectionDao.delete(inspection)
    }

    fun clearReportError() {
        _reportError.value = null
    }

    fun clearEstimatePrepMessage() {
        _estimatePrepMessage.value = null
    }

    fun writeReportFromDictation(
        transcript: String,
        context: InspectionReportContext,
        onFilled: (InspectionReportDraft) -> Unit
    ) {
        if (_reportLoading.value) return
        val text = transcript.trim()
        if (text.isBlank() &&
            context.existingFindings.isBlank() &&
            context.existingNotes.isBlank()
        ) {
            _reportError.value = "Dictate findings or fill some fields before AI Write Report."
            return
        }
        _reportLoading.value = true
        _reportError.value = null
        _reportSource.value = null
        viewModelScope.launch {
            val result = aiService.writeInspectionReportFromDictation(
                transcript = text.ifBlank {
                    listOf(
                        context.existingFindings,
                        context.existingRecommendations,
                        context.existingNotes
                    ).filter { it.isNotBlank() }.joinToString("\n")
                },
                context = context
            )
            _reportLoading.value = false
            if (result.draft != null) {
                _lastReportDraft.value = result.draft
                _reportSource.value = result.sourceLabel
                onFilled(result.draft)
            } else {
                _reportError.value = result.error ?: "AI report failed."
            }
        }
    }

    /**
     * Enrich the linked job's notes/description with the inspection report text so
     * EstimateScreen AI draft pricing reflects findings, then invoke [onReady] with jobId.
     */
    fun prepareJobForEstimate(
        jobId: String,
        reportText: String,
        onReady: (String) -> Unit
    ) {
        if (jobId.isBlank()) {
            _estimatePrepMessage.value = "No job linked — open Estimate from a job, or save this inspection with a job first."
            return
        }
        if (_estimatePrepLoading.value) return
        _estimatePrepLoading.value = true
        _estimatePrepMessage.value = null
        viewModelScope.launch {
            val job = jobDao.getById(jobId)
            if (job == null) {
                _estimatePrepLoading.value = false
                _estimatePrepMessage.value = "Linked job not found."
                return@launch
            }
            val stamp = "\n\n--- Inspection report (for estimate) ---\n${reportText.trim()}"
            val notes = if (job.notes.contains("--- Inspection report (for estimate) ---")) {
                val idx = job.notes.indexOf("--- Inspection report (for estimate) ---")
                job.notes.substring(0, idx).trimEnd() + stamp
            } else {
                job.notes + stamp
            }
            val description = if (job.description.isBlank()) {
                reportText.trim().take(500)
            } else if (!job.description.contains(reportText.trim().take(80)) && reportText.isNotBlank()) {
                (job.description.trimEnd() + "\n\nInspection findings:\n" + reportText.trim().take(400)).trim()
            } else {
                job.description
            }
            jobDao.insert(
                job.copy(
                    notes = notes.trim(),
                    description = description,
                    updatedAt = System.currentTimeMillis(),
                    isSynced = false
                )
            )
            _estimatePrepLoading.value = false
            _estimatePrepMessage.value = "Job notes updated with inspection report — opening Estimate."
            onReady(jobId)
        }
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
            followUpRequired = followUpRequired,
            followUpDate = followUpDate,
            weatherConditions = weatherConditions,
            notes = notes
        )
        inspectionDao.insert(inspection)
    }
}
