package com.strobingn.wildlifefieldops.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.WalkthroughVideoAnalyzer
import com.strobingn.wildlifefieldops.ai.fieldops.AiRuntimeMode
import com.strobingn.wildlifefieldops.ai.fieldops.AiRuntimeStatus
import com.strobingn.wildlifefieldops.ai.fieldops.InspectionEvidence
import com.strobingn.wildlifefieldops.ai.fieldops.InspectionNarrativeDraft
import com.strobingn.wildlifefieldops.ai.fieldops.InspectionNarrativeEngine
import com.strobingn.wildlifefieldops.ai.fieldops.PhotoEvidence
import com.strobingn.wildlifefieldops.ai.fieldops.NarrativeCleared
import com.strobingn.wildlifefieldops.data.inspection.JobInspectionLink
import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.DeletedRecordDao
import com.strobingn.wildlifefieldops.data.local.InspectionDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.PhotoDao
import com.strobingn.wildlifefieldops.data.model.DeletedRecord
import com.strobingn.wildlifefieldops.data.model.FindingSeverity
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.InspectionType
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.remote.AiService
import com.strobingn.wildlifefieldops.data.remote.InspectionReportContext
import com.strobingn.wildlifefieldops.data.remote.InspectionReportDraft
import com.strobingn.wildlifefieldops.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InspectionsViewModel @Inject constructor(
    private val inspectionDao: InspectionDao,
    private val jobDao: JobDao,
    private val customerDao: CustomerDao,
    private val photoDao: PhotoDao,
    private val deletedRecordDao: DeletedRecordDao,
    private val syncRepository: SyncRepository,
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

    private val _walkthroughLoading = MutableStateFlow(false)
    val walkthroughLoading = _walkthroughLoading.asStateFlow()

    private val _walkthroughHint = MutableStateFlow<String?>(null)
    val walkthroughHint = _walkthroughHint.asStateFlow()

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

    val allJobs = jobDao.getAll()
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

    suspend fun customerPhone(customerId: String): String {
        if (customerId.isBlank()) return ""
        return customerDao.getById(customerId)?.phone?.trim().orEmpty()
    }

    fun saveInspection(inspection: Inspection) = viewModelScope.launch {
        inspectionDao.insert(
            inspection.copy(isSynced = false, updatedAt = System.currentTimeMillis(), syncError = null)
        )
    }

    fun linkInspectionToJob(inspectionId: String, jobId: String) = viewModelScope.launch {
        val inspection = inspectionDao.getById(inspectionId) ?: return@launch
        val job = jobDao.getById(jobId) ?: return@launch
        val seeded = JobInspectionLink.inspectionForRoute(
            job = job,
            phone = customerPhone(job.customerId),
            current = inspection,
            manual = NarrativeCleared.cleared(inspection.aiDraftSource),
            typeUntouched = false
        )
        inspectionDao.insert(JobInspectionLink.applyLink(seeded, jobId))
    }

    fun unlinkInspectionFromJob(inspectionId: String) = viewModelScope.launch {
        val inspection = inspectionDao.getById(inspectionId) ?: return@launch
        inspectionDao.insert(JobInspectionLink.applyUnlink(inspection))
    }

    fun updateInspection(inspection: Inspection) = viewModelScope.launch {
        inspectionDao.update(
            inspection.copy(updatedAt = System.currentTimeMillis(), isSynced = false)
        )
    }

    fun deleteInspection(inspection: Inspection) = viewModelScope.launch {
        deletedRecordDao.insert(
            DeletedRecord(
                id = inspection.id,
                entityType = DeletedRecord.TYPE_INSPECTION,
                synced = false
            )
        )
        inspectionDao.delete(inspection)
        syncRepository.tryRemoteDelete(DeletedRecord.TYPE_INSPECTION, inspection.id)
    }

    fun clearReportError() {
        _reportError.value = null
    }

    fun clearEstimatePrepMessage() {
        _estimatePrepMessage.value = null
    }

    fun aiRuntime(): AiRuntimeStatus = AiRuntimeStatus.resolve(
        cloudConfigured = aiService.isConfigured,
        onDeviceReady = aiService.localLlmReady
    )

    fun draftNarrativeFromEvidence(
        jobId: String,
        context: InspectionReportContext,
        replace: Boolean,
        onFilled: (InspectionNarrativeDraft) -> Unit
    ) {
        if (_reportLoading.value) return
        _reportLoading.value = true
        _reportError.value = null
        _reportSource.value = null
        viewModelScope.launch {
            val photos = if (jobId.isBlank()) emptyList() else photoDao.getByJobOnce(jobId)
            val job = if (jobId.isBlank()) null else jobDao.getById(jobId)
            val evidence = InspectionEvidence(
                customerName = context.customerName,
                jobTitle = context.jobTitle.ifBlank { job?.title.orEmpty() },
                jobAddress = context.jobAddress.ifBlank { job?.address.orEmpty() },
                jobType = job?.confirmedSpecies?.ifBlank { job.type }.orEmpty(),
                jobNotes = job?.notes.orEmpty(),
                existingFindings = context.existingFindings,
                existingRecommendations = context.existingRecommendations,
                existingSpecies = context.existingSpecies,
                existingEntryPoints = context.existingEntryPoints,
                existingDamage = context.existingDamage,
                existingNotes = context.existingNotes,
                photoTags = PhotoEvidence.tags(photos),
                photoNotes = PhotoEvidence.notes(photos)
            )
            val heuristic = InspectionNarrativeEngine.draft(evidence)
            val current = InspectionNarrativeDraft(
                findings = context.existingFindings,
                recommendations = context.existingRecommendations,
                speciesIdentified = context.existingSpecies,
                entryPoints = context.existingEntryPoints,
                damageAssessment = context.existingDamage,
                notes = context.existingNotes
            )
            val transcript = InspectionNarrativeEngine.evidenceTranscript(evidence)
            val ai = aiService.writeInspectionReportFromDictation(transcript, context)
            val suggested = if (ai.draft != null) {
                InspectionNarrativeDraft(
                    findings = ai.draft.findings,
                    recommendations = ai.draft.recommendations,
                    speciesIdentified = ai.draft.speciesIdentified,
                    entryPoints = ai.draft.entryPoints,
                    damageAssessment = ai.draft.damageAssessment,
                    notes = listOf(ai.draft.notes, ai.draft.summary).filter { it.isNotBlank() }.joinToString("\n"),
                    source = when {
                        ai.sourceLabel.contains("On-device", ignoreCase = true) -> AiRuntimeMode.ON_DEVICE
                        ai.sourceLabel.contains("Cloud", ignoreCase = true) -> AiRuntimeMode.CLOUD
                        else -> AiRuntimeMode.HEURISTIC
                    }
                )
            } else {
                heuristic
            }
            val merged = InspectionNarrativeEngine.apply(current, suggested, replace)
            _lastReportDraft.value = com.strobingn.wildlifefieldops.data.remote.InspectionReportDraft(
                findings = merged.findings,
                recommendations = merged.recommendations,
                speciesIdentified = merged.speciesIdentified,
                entryPoints = merged.entryPoints,
                damageAssessment = merged.damageAssessment,
                notes = merged.notes,
                summary = merged.findings
            )
            _reportSource.value = AiRuntimeStatus.of(merged.source).label
            _reportLoading.value = false
            if (ai.draft == null && ai.error != null && suggested.source == AiRuntimeMode.HEURISTIC) {
                _reportError.value = null
            }
            onFilled(merged)
        }
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
            val spoken = text.ifBlank {
                listOf(
                    context.existingFindings,
                    context.existingRecommendations,
                    context.existingNotes
                ).filter { it.isNotBlank() }.joinToString("\n")
            }
            val result = aiService.writeInspectionReportFromDictation(
                transcript = spoken,
                context = context
            )
            val draft = structuredReport(spoken, context, result.draft)
            _reportLoading.value = false
            _lastReportDraft.value = draft
            _reportSource.value = if (result.draft != null) {
                result.sourceLabel
            } else {
                "Offline structured draft"
            }
            onFilled(draft)
        }
    }

    /**
     * AI JSON wins when a field is non-blank. Empty or failed model output still fills
     * findings / species / entry / damage from the transcript. Notes stay empty unless
     * the model wrote a summary that is not the raw dictation.
     */
    private fun structuredReport(
        spoken: String,
        context: InspectionReportContext,
        ai: InspectionReportDraft?
    ): InspectionReportDraft {
        val heuristic = InspectionNarrativeEngine.fromDictation(
            transcript = spoken,
            customerName = context.customerName,
            jobTitle = context.jobTitle,
            jobAddress = context.jobAddress,
            existingSpecies = context.existingSpecies,
            existingEntryPoints = context.existingEntryPoints,
            existingDamage = context.existingDamage,
            existingFindings = context.existingFindings,
            existingRecommendations = context.existingRecommendations
        )
        fun pick(aiValue: String?, fallback: String): String =
            aiValue?.trim().orEmpty().ifBlank { fallback }
        val aiNotes = ai?.notes?.trim().orEmpty()
        val notes = if (aiNotes.isBlank() || aiNotes == spoken.trim()) "" else aiNotes
        return InspectionReportDraft(
            findings = pick(ai?.findings, heuristic.findings),
            recommendations = pick(ai?.recommendations, heuristic.recommendations),
            speciesIdentified = pick(ai?.speciesIdentified, heuristic.speciesIdentified),
            entryPoints = pick(ai?.entryPoints, heuristic.entryPoints),
            damageAssessment = pick(ai?.damageAssessment, heuristic.damageAssessment),
            severity = ai?.severity?.trim()?.ifBlank { "MODERATE" } ?: "MODERATE",
            notes = notes,
            summary = ""
        )
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


    /**
     * E) Walkthrough video → frame sample → vision → editable InspectionReportDraft.
     * Uses existing AI path when available; always produces an offline lexicon draft.
     */
    fun analyzeWalkthroughVideo(
        context: Context,
        videoUri: Uri,
        reportContext: InspectionReportContext,
        onFilled: (InspectionReportDraft) -> Unit
    ) {
        if (_walkthroughLoading.value) return
        _walkthroughLoading.value = true
        _reportError.value = null
        _walkthroughHint.value = "Sampling ~${WalkthroughVideoAnalyzer.GUIDANCE_SECONDS_MIN}–${WalkthroughVideoAnalyzer.GUIDANCE_SECONDS_MAX}s walkthrough…"
        viewModelScope.launch {
            try {
                val result = WalkthroughVideoAnalyzer.analyze(
                    context = context,
                    videoUri = videoUri,
                    enrichWithAi = { transcript ->
                        val ai = aiService.writeInspectionReportFromDictation(
                            transcript = transcript,
                            context = reportContext
                        )
                        ai.draft
                    }
                )
                _walkthroughLoading.value = false
                _lastReportDraft.value = result.draft
                _reportSource.value = result.sourceLabel
                _walkthroughHint.value = buildString {
                    append(result.guidanceHint)
                    append(" · frames=")
                    append(result.framesSampled)
                    append(" · ")
                    append(result.evidenceSummary)
                    if (result.offline) append(" · offline draft")
                }
                onFilled(result.draft)
            } catch (t: Throwable) {
                _walkthroughLoading.value = false
                _reportError.value = t.message ?: "Walkthrough analysis failed"
                _walkthroughHint.value = "Record a ${WalkthroughVideoAnalyzer.GUIDANCE_SECONDS_MIN}–${WalkthroughVideoAnalyzer.GUIDANCE_SECONDS_MAX}s walkthrough and retry (works offline)."
            }
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
        notes: String,
        aiNarrativeDraft: String = "",
        aiDraftSource: String = ""
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
            notes = notes,
            isSynced = false,
            aiNarrativeDraft = aiNarrativeDraft,
            aiDraftSource = aiDraftSource
        )
        inspectionDao.insert(inspection)
    }
}
