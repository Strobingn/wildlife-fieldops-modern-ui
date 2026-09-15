package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.strobingn.wildlifefieldops.ai.ARMeasurementHelper
import com.strobingn.wildlifefieldops.ai.camera.CaptureGuidance
import com.strobingn.wildlifefieldops.ai.camera.CaptureGuidanceAction
import com.strobingn.wildlifefieldops.ai.camera.CaptureSoakStats
import com.strobingn.wildlifefieldops.ai.camera.ChecklistSession
import com.strobingn.wildlifefieldops.ai.camera.LiveCameraAnalyzer
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.viewmodel.LiveCaptureViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.SmartCaptureState
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCaptureScreen(
    onBack: () -> Unit,
    jobId: String? = null,
    inspectionId: String? = null,
    viewModel: LiveCaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMic = granted }

    LaunchedEffect(Unit) {
        if (!hasCamera) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(jobId, inspectionId) {
        viewModel.bindJob(jobId, inspectionId)
    }

    var guidance by remember { mutableStateOf<CaptureGuidance?>(null) }
    var useFront by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf<String?>(null) }
    var framesSeen by remember { mutableLongStateOf(0L) }
    var framesDropped by remember { mutableLongStateOf(0L) }
    var lastDrop by remember { mutableStateOf<String?>(null) }
    var soakLine by remember { mutableStateOf("") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var showJobPicker by remember { mutableStateOf(false) }
    var showArMeasure by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    var partialVoice by remember { mutableStateOf("") }
    var dictationError by remember { mutableStateOf<String?>(null) }

    val smartCapture by viewModel.smartCapture.collectAsState()
    val checklistEnabled by viewModel.checklistEnabled.collectAsState()
    val checklist by viewModel.checklist.collectAsState()
    val linkedJob by viewModel.linkedJob.collectAsState()
    val voiceTranscript by viewModel.voiceTranscript.collectAsState()
    val arMeasurement by viewModel.arMeasurement.collectAsState()
    val repairScope by viewModel.repairScope.collectAsState()
    val lastEquipmentTags by viewModel.lastEquipmentTags.collectAsState()
    val recentJobs by viewModel.recentJobs.collectAsState()

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val soakStats = remember { CaptureSoakStats() }
    val analyzer = remember {
        LiveCameraAnalyzer(
            context = context,
            onGuidance = { g ->
                // True overlay commit: stamp when guidance is applied on the main thread.
                mainHandler.post {
                    val drawnNs = SystemClock.elapsedRealtimeNanos()
                    val committed = g.resultCommittedElapsedNs
                    val arrival = g.analyzerArrivalElapsedNs
                    val resultToOverlay = if (committed > 0L && drawnNs >= committed) {
                        (drawnNs - committed) / 1_000_000L
                    } else {
                        -1L
                    }
                    val arrivalToOverlay = if (arrival > 0L && drawnNs >= arrival) {
                        (drawnNs - arrival) / 1_000_000L
                    } else {
                        -1L
                    }
                    guidance = g.copy(
                        overlayDrawnElapsedNs = drawnNs,
                        resultToOverlayMs = resultToOverlay,
                        arrivalToOverlayMs = arrivalToOverlay
                    )
                    framesSeen = g.frameId
                    if (g.evidenceEquipment.isNotEmpty()) {
                        viewModel.rememberEquipmentTags(g.evidenceEquipment)
                    }
                }
            },
            onTrace = { trace ->
                soakStats.record(trace)
                val line = soakStats.compactLine()
                mainHandler.post {
                    soakLine = line
                    if (trace.droppedReason != null) {
                        lastDrop = trace.droppedReason
                        framesDropped++
                    }
                }
                if (trace.droppedReason != null) {
                    Log.d("LiveCapture", "drop frame=${trace.frameId} reason=${trace.droppedReason}")
                }
            }
        )
    }

    // RESTORED CORE: overlay/soak wiring is above. Full UI body restored from local checkout
    // by parent if this stub is still present — see tools/patches/2.3.6-livecapture-overlay-soak.patch
    // and agent-tools/RESTORE_LiveCaptureScreen.kt
    Text("LiveCapture restore pending — apply RESTORE_LiveCaptureScreen.kt")
}
