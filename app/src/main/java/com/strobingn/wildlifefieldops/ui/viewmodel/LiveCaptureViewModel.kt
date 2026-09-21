package com.strobingn.wildlifefieldops.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.ARMeasurementHelper
import com.strobingn.wildlifefieldops.ai.RepairScopeHelper
import com.strobingn.wildlifefieldops.ai.AiAnalysisResult
import com.strobingn.wildlifefieldops.ai.HybridAIService
import com.strobingn.wildlifefieldops.ai.camera.CaptureGuidanceAction
import com.strobingn.wildlifefieldops.ai.camera.ChecklistSession
import com.strobingn.wildlifefieldops.ai.camera.InspectionCaptureChecklist
import com.strobingn.wildlifefieldops.ai.species.OnDeviceSpeciesClassifier
import com.strobingn.wildlifefieldops.ai.species.SpeciesRecognition
import com.strobingn.wildlifefieldops.ai.species.SpeciesSuggestion
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.PhotoDao
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
import com.strobingn.wildlifefieldops.data.observation.ObservationEvent
import com.strobingn.wildlifefieldops.data.observation.ObservationEventStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class SmartCaptureState {
    data object Idle : SmartCaptureState()
    data object Capturing : SmartCaptureState()
    data object Analyzing : SmartCaptureState()
    data object Narrating : SmartCaptureState()
    data class Ready(
        val photo: Photo,
        val analysis: AiAnalysisResult,
        val guidanceAction: CaptureGuidanceAction,
        val reasonCode: String,
        val frameId: Long,
        val checklistItemId: String? = null,
        val checklistTitle: String? = null,
        val techNotes: String = "",
        val customerSummary: String = "",
        val narrationSource: String = "",
        val voiceTranscript: String = "",
        val evidenceSummary: String = "",
        val arMeasurementLabel: String = "",
        val equipmentTags: List<String> = emptyList(),
        val repairScopeLabel: String = "",
        val repairScopeNotes: String = "",
        val speciesSuggestion: SpeciesSuggestion? = null,
        val speciesConfirmed: Boolean = false,
        val operationalSpecies: String? = null,
        val technicianSpeciesLabel: String = "",
        val inferenceEventId: String? = null
    ) : SmartCaptureState()
    data class Error(val message: String) : SmartCaptureState()
}

@HiltViewModel
class LiveCaptureViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val hybridAI: HybridAIService,
    private val photoDao: PhotoDao,
    private val jobDao: JobDao,
    private val observationEventStore: ObservationEventStore
) : ViewModel() {

    private val _smartCapture = MutableStateFlow<SmartCaptureState>(SmartCaptureState.Idle)
    val smartCapture: StateFlow<SmartCaptureState> = _smartCapture.asStateFlow()

    private val _checklistEnabled = MutableStateFlow(true)
    val checklistEnabled: StateFlow<Boolean> = _checklistEnabled.asStateFlow()

    private val _checklist = MutableStateFlow(InspectionCaptureChecklist.newSession())
    val checklist: StateFlow<ChecklistSession> = _checklist.asStateFlow()

    private val _jobId = MutableStateFlow<String?>(null)
    val jobId: StateFlow<String?> = _jobId.asStateFlow()

    private val _inspectionId = MutableStateFlow<String?>(null)
    val inspectionId: StateFlow<String?> = _inspectionId.asStateFlow()

    private val _linkedJob = MutableStateFlow<Job?>(null)
    val linkedJob: StateFlow<Job?> = _linkedJob.asStateFlow()

    private val _voiceTranscript = MutableStateFlow("")
    val voiceTranscript: StateFlow<String> = _voiceTranscript.asStateFlow()

    private val _arMeasurement = MutableStateFlow<ARMeasurementHelper.MeasurementResult?>(null)
    val arMeasurement: StateFlow<ARMeasurementHelper.MeasurementResult?> = _arMeasurement.asStateFlow()

    private val _repairScope = MutableStateFlow<RepairScopeHelper.RepairScopeDraft?>(null)
    val repairScope: StateFlow<RepairScopeHelper.RepairScopeDraft?> = _repairScope.asStateFlow()

    private val _lastEquipmentTags = MutableStateFlow<List<String>>(emptyList())
    val lastEquipmentTags: StateFlow<List<String>> = _lastEquipmentTags.asStateFlow()

    val recentJobs = jobDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Lazy — never probe ARCore during ViewModel construction (native link crashes). */
    val arSupported: Boolean by lazy {
        try {
            ARMeasurementHelper.isARCoreSupported(appContext)
        } catch (_: Throwable) {
            false
        }
    }

    fun clearSmartCapture() {
        _smartCapture.value = SmartCaptureState.Idle
    }

    fun setTechnicianSpeciesLabel(label: String) {
        val current = _smartCapture.value as? SmartCaptureState.Ready ?: return
        _smartCapture.value = current.copy(
            technicianSpeciesLabel = label,
            speciesConfirmed = false,
            operationalSpecies = null
        )
    }

    fun confirmSpeciesId() {
        val current = _smartCapture.value as? SmartCaptureState.Ready ?: return
        val label = current.technicianSpeciesLabel.ifBlank { current.speciesSuggestion?.primaryLabel }.orEmpty().trim()
        if (label.isEmpty()) return
        viewModelScope.launch {
            val inference = current.inferenceEventId?.let { id ->
                val entityId = speciesEntityId(current.photo)
                observationEventStore.eventsFor(entityId).firstOrNull { it.eventId == id }
            }
            if (inference != null) {
                val commit = SpeciesRecognition.confirm(
                    inference = inference,
                    technicianLabel = label,
                    confirmedAt = System.currentTimeMillis()
                )
                observationEventStore.append(commit.verificationEvent)
                _smartCapture.value = current.copy(
                    technicianSpeciesLabel = label,
                    speciesConfirmed = true,
                    operationalSpecies = commit.operationalLabel
                )
            } else {
                persistInferenceThenConfirm(current, label)
            }
        }
    }

    private suspend fun persistInferenceThenConfirm(current: SmartCaptureState.Ready, label: String) {
        val suggestion = current.speciesSuggestion
            ?: SpeciesRecognition.suggest(mapOf(label to 1f), backendTag = "human")
            ?: return
        val inference = writeInference(current.photo, suggestion)
        val commit = SpeciesRecognition.confirm(
            inference = inference,
            technicianLabel = label,
            confirmedAt = System.currentTimeMillis() + 1
        )
        observationEventStore.append(commit.verificationEvent)
        _smartCapture.value = current.copy(
            speciesSuggestion = suggestion,
            technicianSpeciesLabel = label,
            speciesConfirmed = true,
            operationalSpecies = commit.operationalLabel,
            inferenceEventId = inference.eventId
        )
    }

    fun bindJob(jobId: String?, inspectionId: String? = null) {
        val safeJob = jobId?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        val safeInsp = inspectionId?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        _jobId.value = safeJob
        _inspectionId.value = safeInsp
        viewModelScope.launch {
            _linkedJob.value = try {
                safeJob?.let { jobDao.getById(it) }
            } catch (t: Throwable) {
                Log.w(TAG, "bindJob lookup failed for $safeJob", t)
                null
            }
        }
    }

    fun appendVoice(text: String) {
        val chunk = text.trim()
        if (chunk.isEmpty()) return
        _voiceTranscript.update { cur ->
            if (cur.isBlank()) chunk else "$cur $chunk"
        }
    }

    fun clearVoice() {
        _voiceTranscript.value = ""
    }

    fun setArMeasurement(
        result: ARMeasurementHelper.MeasurementResult?,
        entryTags: List<String> = emptyList(),
        species: List<String> = emptyList(),
        damageTags: List<String> = emptyList(),
        equipmentTags: List<String> = emptyList()
    ) {
        _arMeasurement.value = result
        if (result != null) {
            try {
                val draft = RepairScopeHelper.fromMeasurementResult(
                    result = result,
                    entryTags = entryTags,
                    species = species,
                    damageTags = damageTags,
                    equipmentTags = equipmentTags.ifEmpty { _lastEquipmentTags.value }
                )
                _repairScope.value = draft
            } catch (t: Throwable) {
                Log.w(TAG, "repair scope from AR failed", t)
            }
        }
    }

    fun clearArMeasurement() {
        _arMeasurement.value = null
        _repairScope.value = null
    }

    fun clearArMeasurementKeepVoice() {
        _arMeasurement.value = null
        // keep repair scope suggestion until next measure
    }

    fun rememberEquipmentTags(tags: List<String>) {
        if (tags.isNotEmpty()) {
            _lastEquipmentTags.value = tags.distinct().take(6)
        }
    }

    fun applySuggestedArSpan(subjectCoverage: Float) {
        try {
            val inches = ARMeasurementHelper.suggestEntryInches(
                subjectCoverage = subjectCoverage,
                checklistId = _checklist.value.active?.def?.id
            )
            val meters = inches / 39.3701f
            val result = ARMeasurementHelper.MeasurementResult(
                distanceMeters = meters,
                confidence = 0.55f,
                planeType = "suggested",
                notes = "Suggested entry/damage span from live coverage (~${String.format("%.0f", inches)} in). Confirm with AR or tape."
            )
            _arMeasurement.value = result
            _repairScope.value = RepairScopeHelper.fromMeasurementResult(
                result = result,
                entryTags = emptyList(),
                species = emptyList(),
                equipmentTags = _lastEquipmentTags.value
            )
        } catch (t: Throwable) {
            Log.w(TAG, "applySuggestedArSpan failed", t)
        }
    }

    fun setChecklistEnabled(enabled: Boolean) {
        _checklistEnabled.value = enabled
        if (enabled && _checklist.value.items.isEmpty()) {
            _checklist.value = InspectionCaptureChecklist.newSession()
        }
    }

    fun resetChecklist() {
        _checklist.value = InspectionCaptureChecklist.newSession()
    }

    fun selectChecklistItem(index: Int) {
        val session = _checklist.value
        if (index !in session.items.indices) return
        _checklist.value = session.copy(activeIndex = index)
    }

    fun skipChecklistItem() {
        val session = _checklist.value
        val i = session.activeIndex
        val item = session.items.getOrNull(i) ?: return
        val updated = session.items.toMutableList()
        updated[i] = item.copy(completed = false, skipped = true, reasonCode = "SKIPPED")
        val next = ((i + 1) until updated.size).firstOrNull { !updated[it].completed && !updated[it].skipped } ?: i
        _checklist.value = session.copy(items = updated, activeIndex = next)
    }

    fun smartCapture(
        imageCapture: ImageCapture?,
        executor: Executor,
        guidanceAction: CaptureGuidanceAction?,
        reasonCode: String,
        frameId: Long,
        requireAccept: Boolean = true,
        evidenceSummary: String = "",
        evidenceEntries: List<String> = emptyList(),
        evidenceEquipment: List<String> = emptyList(),
        evidenceSpecies: List<String> = emptyList(),
        evidenceDamage: List<String> = emptyList(),
        subjectCoverage: Float = 0f
    ) {
        if (imageCapture == null) {
            _smartCapture.value = SmartCaptureState.Error("Camera still capture not ready")
            return
        }
        if (requireAccept && guidanceAction != CaptureGuidanceAction.ACCEPT) {
            _smartCapture.value = SmartCaptureState.Error(
                "Policy says ${guidanceAction ?: "WAIT"} ($reasonCode) — wait for ACCEPT or force capture"
            )
            return
        }
        if (_smartCapture.value is SmartCaptureState.Capturing ||
            _smartCapture.value is SmartCaptureState.Analyzing ||
            _smartCapture.value is SmartCaptureState.Narrating
        ) {
            return
        }

        val checklistOn = _checklistEnabled.value
        val activeItem = if (checklistOn) _checklist.value.active else null
        val voice = _voiceTranscript.value
        val job = _linkedJob.value
        val jobContext = buildString {
            job?.let {
                append(it.customerName)
                append(" · ")
                append(it.address)
                if (it.type.isNotBlank()) {
                    append(" · ")
                    append(it.type)
                }
            }
        }
        var ar = _arMeasurement.value
        if (ar == null && requireAccept) {
            applySuggestedArSpan(subjectCoverage)
            ar = _arMeasurement.value
        }
        val arLabel = ar?.feetLabel.orEmpty()
        val arNotes = ar?.notes.orEmpty()
        val equipment = evidenceEquipment.ifEmpty { _lastEquipmentTags.value }.distinct().take(6)
        if (equipment.isNotEmpty()) _lastEquipmentTags.value = equipment
        var repair = _repairScope.value
        if (repair == null && ar != null) {
            try {
                repair = RepairScopeHelper.fromMeasurementResult(
                    result = ar,
                    entryTags = evidenceEntries,
                    species = evidenceSpecies,
                    damageTags = evidenceDamage,
                    equipmentTags = equipment
                )
                _repairScope.value = repair
            } catch (t: Throwable) {
                Log.w(TAG, "repair scope build failed", t)
            }
        }
        val repairLabel = repair?.let { RepairScopeHelper.stampLine(it) }.orEmpty()
        val repairNotes = repair?.stampedNotes.orEmpty()

        viewModelScope.launch {
            try {
                _smartCapture.value = SmartCaptureState.Capturing
                val (uri, file) = takeStill(imageCapture, executor)
                val photo = Photo(
                    filePath = uri.toString(),
                    localPath = file.absolutePath,
                    jobId = _jobId.value,
                    inspectionId = _inspectionId.value,
                    category = if (checklistOn) PhotoCategory.INSPECTION else PhotoCategory.EVIDENCE,
                    description = buildString {
                        append("Live smart capture")
                        if (activeItem != null) {
                            append(" · checklist=")
                            append(activeItem.def.id)
                            append(" (")
                            append(activeItem.def.title)
                            append(")")
                        }
                        if (frameId > 0) append(" · frame=$frameId")
                        append(" · policy=${guidanceAction ?: "FORCE"}/$reasonCode")
                        if (arLabel.isNotBlank()) append(" · span=$arLabel")
                        if (equipment.isNotEmpty()) append(" · gear=${equipment.joinToString()}")
                        if (repairLabel.isNotBlank()) append(" · repair=$repairLabel")
                        if (voice.isNotBlank()) append(" · voice=yes")
                        append(" · ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())}")
                    },
                    takenAt = System.currentTimeMillis(),
                    fileSize = file.length(),
                    latitude = job?.latitude,
                    longitude = job?.longitude
                )
                photoDao.insert(photo)

                _smartCapture.value = SmartCaptureState.Analyzing
                val analysis = withContext(Dispatchers.IO) {
                    hybridAI.analyzePhotoAndFillForm(
                        context = appContext,
                        imageUri = uri,
                        jobContext = jobContext,
                        voiceTranscript = voice,
                        evidenceSummary = evidenceSummary,
                        entryTags = evidenceEntries,
                        arMeasurement = listOf(arLabel, arNotes).filter { it.isNotBlank() }.joinToString(" — "),
                        equipmentTags = equipment,
                        repairScope = repairNotes
                    )
                }

                _smartCapture.value = SmartCaptureState.Narrating
                val narration = withContext(Dispatchers.IO) {
                    hybridAI.narrateAcceptedCapture(
                        analysis = analysis,
                        checklistTitle = activeItem?.def?.title,
                        reasonCode = reasonCode,
                        jobContext = jobContext,
                        voiceTranscript = voice,
                        evidenceSummary = evidenceSummary.ifBlank { analysis.evidenceSummary },
                        arMeasurement = listOf(arLabel, arNotes).filter { it.isNotBlank() }.joinToString(" — "),
                        equipmentTags = equipment,
                        repairScope = repairNotes
                    )
                }

                val enriched = photo.copy(
                    description = buildString {
                        append(photo.description)
                        append("\nEvidence: ")
                        append(analysis.evidenceSummary.ifBlank { evidenceSummary }.ifBlank { "—" })
                        append("\nAI: ")
                        append(analysis.suggestedServiceType)
                        if (analysis.species.isNotEmpty()) {
                            append(" · ")
                            append(analysis.species.joinToString())
                        }
                        append(" · src=")
                        append(analysis.source)
                        if (equipment.isNotEmpty()) {
                            append("\nTRAP/EQUIPMENT: ")
                            append(equipment.joinToString())
                        }
                        if (repairNotes.isNotBlank()) {
                            append("\n")
                            append(repairNotes)
                        }
                        if (voice.isNotBlank()) {
                            append("\nVOICE:\n")
                            append(voice.take(1200))
                        }
                        append("\nTECH:\n")
                        append(narration.techNotes)
                        append("\nCUSTOMER:\n")
                        append(narration.customerSummary)
                    }
                )
                photoDao.update(enriched)

                // B) Repair-scope autofill onto linked job notes (Whisperer branding).
                val jid = _jobId.value
                if (!jid.isNullOrBlank() && repairNotes.isNotBlank()) {
                    try {
                        val linked = jobDao.getById(jid)
                        if (linked != null) {
                            val stamp = "\n\n--- Repair scope (Live Capture) ---\n" + repairNotes.trim()
                            val notes = if (linked.notes.contains("--- Repair scope (Live Capture) ---")) {
                                val idx = linked.notes.indexOf("--- Repair scope (Live Capture) ---")
                                linked.notes.substring(0, idx).trimEnd() + stamp
                            } else {
                                linked.notes + stamp
                            }
                            jobDao.insert(
                                linked.copy(
                                    notes = notes.trim(),
                                    updatedAt = System.currentTimeMillis(),
                                    isSynced = false
                                )
                            )
                            _linkedJob.value = linked.copy(notes = notes.trim())
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "repair scope job autofill failed", t)
                    }
                }

                if (checklistOn && activeItem != null && requireAccept) {
                    completeActiveChecklistItem(enriched.id, reasonCode, frameId)
                }

                val suggestion = OnDeviceSpeciesClassifier.suggestFromAnalysis(analysis)
                val inference = suggestion?.let { writeInference(enriched, it) }

                _smartCapture.value = SmartCaptureState.Ready(
                    photo = enriched,
                    analysis = analysis,
                    guidanceAction = guidanceAction ?: CaptureGuidanceAction.ACCEPT,
                    reasonCode = reasonCode,
                    frameId = frameId,
                    checklistItemId = activeItem?.def?.id,
                    checklistTitle = activeItem?.def?.title,
                    techNotes = narration.techNotes,
                    customerSummary = narration.customerSummary,
                    narrationSource = narration.source,
                    voiceTranscript = voice,
                    evidenceSummary = analysis.evidenceSummary.ifBlank { evidenceSummary },
                    arMeasurementLabel = arLabel,
                    equipmentTags = equipment.ifEmpty { analysis.equipmentTypes },
                    repairScopeLabel = repairLabel,
                    repairScopeNotes = repairNotes,
                    speciesSuggestion = suggestion,
                    technicianSpeciesLabel = suggestion?.primaryLabel.orEmpty(),
                    inferenceEventId = inference?.eventId
                )
            } catch (t: Throwable) {
                Log.e(TAG, "smartCapture failed", t)
                _smartCapture.value = SmartCaptureState.Error(t.message ?: "Smart capture failed")
            }
        }
    }

    private fun completeActiveChecklistItem(photoId: String, reasonCode: String, frameId: Long) {
        _checklist.update { session ->
            val i = session.activeIndex
            val item = session.items.getOrNull(i) ?: return@update session
            val updated = session.items.toMutableList()
            updated[i] = item.copy(
                completed = true,
                skipped = false,
                photoId = photoId,
                reasonCode = reasonCode,
                frameId = frameId
            )
            val next = ((i + 1) until updated.size).firstOrNull { !updated[it].completed && !updated[it].skipped }
                ?: updated.indexOfFirst { !it.completed && !it.skipped }.takeIf { it >= 0 }
                ?: i
            session.copy(items = updated, activeIndex = next)
        }
    }

    private suspend fun takeStill(
        imageCapture: ImageCapture,
        executor: Executor
    ): Pair<Uri, File> = suspendCancellableCoroutine { cont ->
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = File(appContext.filesDir, "photos").apply { mkdirs() }
        val file = File(storageDir, "LIVE_${timeStamp}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(file).build()
        imageCapture.takePicture(
            output,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val uri = FileProvider.getUriForFile(
                            appContext,
                            "${appContext.packageName}.provider",
                            file
                        )
                        cont.resume(uri to file)
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            }
        )
    }

    private suspend fun writeInference(photo: Photo, suggestion: SpeciesSuggestion): ObservationEvent {
        val observedAt = photo.takenAt.takeIf { it > 0L } ?: System.currentTimeMillis()
        val event = SpeciesRecognition.recordInference(
            entityId = speciesEntityId(photo),
            suggestion = suggestion,
            observedAt = observedAt,
            deviceId = localDeviceId(),
            operatorId = "field-tech",
            frameHash = OnDeviceSpeciesClassifier.frameHash(
                pathOrUri = photo.localPath.ifBlank { photo.filePath },
                observedAt = observedAt,
                extra = photo.id
            ),
            modelHash = OnDeviceSpeciesClassifier.modelHash(appContext),
            mediaUri = photo.filePath.ifBlank { photo.localPath },
            captureQuality = suggestion.confidence.coerceIn(0.2f, 1f),
            geometryTrust = 0f
        )
        observationEventStore.append(event)
        return event
    }

    private fun speciesEntityId(photo: Photo): String =
        photo.jobId?.takeIf { it.isNotBlank() } ?: "photo:${photo.id}"

    private fun localDeviceId(): String {
        val androidId = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        return androidId.ifBlank { Build.MODEL.ifBlank { "android-device" } }
    }

    companion object {
        private const val TAG = "LiveCaptureVM"
    }
}
