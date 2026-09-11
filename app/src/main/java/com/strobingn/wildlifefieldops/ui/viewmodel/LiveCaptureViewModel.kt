package com.strobingn.wildlifefieldops.ui.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.AiAnalysisResult
import com.strobingn.wildlifefieldops.ai.HybridAIService
import com.strobingn.wildlifefieldops.ai.camera.CaptureGuidanceAction
import com.strobingn.wildlifefieldops.ai.camera.ChecklistSession
import com.strobingn.wildlifefieldops.ai.camera.InspectionCaptureChecklist
import com.strobingn.wildlifefieldops.data.local.PhotoDao
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        val narrationSource: String = ""
    ) : SmartCaptureState()
    data class Error(val message: String) : SmartCaptureState()
}

@HiltViewModel
class LiveCaptureViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val hybridAI: HybridAIService,
    private val photoDao: PhotoDao
) : ViewModel() {

    private val _smartCapture = MutableStateFlow<SmartCaptureState>(SmartCaptureState.Idle)
    val smartCapture: StateFlow<SmartCaptureState> = _smartCapture.asStateFlow()

    private val _checklistEnabled = MutableStateFlow(true)
    val checklistEnabled: StateFlow<Boolean> = _checklistEnabled.asStateFlow()

    private val _checklist = MutableStateFlow(InspectionCaptureChecklist.newSession())
    val checklist: StateFlow<ChecklistSession> = _checklist.asStateFlow()

    fun clearSmartCapture() {
        _smartCapture.value = SmartCaptureState.Idle
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

    /**
     * Policy-gated smart capture: still → Room photo → hybrid form AI → LLM narration.
     * When checklist mode is on, ACCEPT capture completes the active checklist item.
     */
    fun smartCapture(
        imageCapture: ImageCapture?,
        executor: Executor,
        guidanceAction: CaptureGuidanceAction?,
        reasonCode: String,
        frameId: Long,
        requireAccept: Boolean = true,
        jobId: String? = null,
        jobContext: String = ""
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

        viewModelScope.launch {
            try {
                _smartCapture.value = SmartCaptureState.Capturing
                val (uri, file) = takeStill(imageCapture, executor)
                val photo = Photo(
                    filePath = uri.toString(),
                    localPath = file.absolutePath,
                    jobId = jobId,
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
                        append(" · ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())}")
                    },
                    takenAt = System.currentTimeMillis(),
                    fileSize = file.length()
                )
                photoDao.insert(photo)

                _smartCapture.value = SmartCaptureState.Analyzing
                val analysis = withContext(Dispatchers.IO) {
                    hybridAI.analyzePhotoAndFillForm(appContext, uri, jobContext)
                }

                _smartCapture.value = SmartCaptureState.Narrating
                val narration = withContext(Dispatchers.IO) {
                    hybridAI.narrateAcceptedCapture(
                        analysis = analysis,
                        checklistTitle = activeItem?.def?.title,
                        reasonCode = reasonCode,
                        jobContext = jobContext
                    )
                }

                val enriched = photo.copy(
                    description = buildString {
                        append(photo.description)
                        append("\nAI: ")
                        append(analysis.suggestedServiceType)
                        if (analysis.species.isNotEmpty()) {
                            append(" · ")
                            append(analysis.species.joinToString())
                        }
                        append(" · src=")
                        append(analysis.source)
                        append("\nTECH:\n")
                        append(narration.techNotes)
                        append("\nCUSTOMER:\n")
                        append(narration.customerSummary)
                    }
                )
                photoDao.update(enriched)

                if (checklistOn && activeItem != null && requireAccept) {
                    completeActiveChecklistItem(
                        photoId = enriched.id,
                        reasonCode = reasonCode,
                        frameId = frameId
                    )
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
                    narrationSource = narration.source
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
