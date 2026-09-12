package com.strobingn.wildlifefieldops.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.ARMeasurementHelper
import com.strobingn.wildlifefieldops.ai.AiAnalysisResult
import com.strobingn.wildlifefieldops.ai.HybridAIService
import com.strobingn.wildlifefieldops.ai.camera.CaptureGuidanceAction
import com.strobingn.wildlifefieldops.ai.camera.ChecklistSession
import com.strobingn.wildlifefieldops.ai.camera.InspectionCaptureChecklist
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.PhotoDao
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
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
        val arMeasurementLabel: String = ""
    ) : SmartCaptureState()
    data class Error(val message: String) : SmartCaptureState()
}

@HiltViewModel
class LiveCaptureViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val hybridAI: HybridAIService,
    private val photoDao: PhotoDao,
    private val jobDao: JobDao
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

    val recentJobs = jobDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val arSupported: Boolean = ARMeasurementHelper.isARCoreSupported(appContext)

    fun clearSmartCapture() {
        _smartCapture.value = SmartCaptureState.Idle
    }

    fun bindJob(jobId: String?, inspectionId: String? = null) {
        _jobId.value = jobId?.takeIf { it.isNotBlank() && it != "null" }
        _inspectionId.value = inspectionId?.takeIf { it.isNotBlank() && it != "null" }
        viewModelScope.launch {
            _linkedJob.value = _jobId.value?.let { jobDao.getById(it) }
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

    fun setArMeasurement(result: ARMeasurementHelper.MeasurementResult?) {
        _arMeasurement.value = result
    }

    fun clearArMeasurement() {
        _arMeasurement.value = null
    }

    fun clearArMeasurementKeepVoice() {
        _arMeasurement.value = null
    }

    fun applySuggestedArSpan(subjectCoverage: Float) {
        val inches = ARMeasurementHelper.suggestEntryInches(
            subjectCoverage = subjectCoverage,
            checklistId = _checklist.value.active?.def?.id
        )
        val meters = inches / 39.3701f
        _arMeasurement.value = ARMeasurementHelper.MeasurementResult(
            distanceMeters = meters,
            confidence = 0.55f,
            planeType = "suggested",
            notes = "Suggested entry/damage span from live coverage (~${String.format("%.0f", inches)} in). Confirm with AR or tape."
        )
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
                        arMeasurement = listOf(arLabel, arNotes).filter { it.isNotBlank() }.joinToString(" — ")
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
                        arMeasurement = listOf(arLabel, arNotes).filter { it.isNotBlank() }.joinToString(" — ")
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

                if (checklistOn && activeItem != null && requireAccept) {
                    completeActiveChecklistItem(enriched.id, reasonCode, frameId)
                }

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
                    arMeasurementLabel = arLabel
                )
            } catch (e: Exception) {
                Log.e(TAG, "smartCapture failed", e)
                _smartCapture.value = SmartCaptureState.Error(e.message ?: "Smart capture failed")
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

    companion object {
        private const val TAG = "LiveCaptureVM"
    }
}
