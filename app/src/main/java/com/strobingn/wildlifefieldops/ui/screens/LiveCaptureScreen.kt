package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
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
import com.strobingn.wildlifefieldops.ai.camera.CaptureGuidance
import com.strobingn.wildlifefieldops.ai.camera.CaptureGuidanceAction
import com.strobingn.wildlifefieldops.ai.camera.LiveCameraAnalyzer
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.viewmodel.LiveCaptureViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.SmartCaptureState
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCaptureScreen(
    onBack: () -> Unit,
    jobId: String? = null,
    jobContext: String = "",
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
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    LaunchedEffect(Unit) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var guidance by remember { mutableStateOf<CaptureGuidance?>(null) }
    var useFront by remember { mutableStateOf(false) }
    var bindError by remember { mutableStateOf<String?>(null) }
    var framesSeen by remember { mutableLongStateOf(0L) }
    var framesDropped by remember { mutableLongStateOf(0L) }
    var lastDrop by remember { mutableStateOf<String?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val smartCapture by viewModel.smartCapture.collectAsState()
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val analyzer = remember {
        LiveCameraAnalyzer(
            onGuidance = { g ->
                mainHandler.post {
                    guidance = g
                    framesSeen = g.frameId
                }
            },
            onTrace = { trace ->
                if (trace.droppedReason != null) {
                    mainHandler.post {
                        lastDrop = trace.droppedReason
                        framesDropped++
                    }
                    Log.d("LiveCapture", "drop frame=${trace.frameId} reason=${trace.droppedReason}")
                }
            }
        )
    }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            analysisExecutor.shutdown()
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(hasCamera, useFront) {
        if (!hasCamera) return@LaunchedEffect
        bindError = null
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(context)
                .await(ContextCompat.getMainExecutor(context))
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { it.setAnalyzer(analysisExecutor, analyzer) }
            val still = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            val selector = if (useFront) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            cameraProvider.unbindAll()
            analyzer.reset()
            framesDropped = 0L
            lastDrop = null
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis, still)
            imageCapture = still
        } catch (e: Exception) {
            Log.e("LiveCapture", "bind failed", e)
            bindError = e.message ?: "Camera bind failed"
            imageCapture = null
        }
    }

    val acceptReady = guidance?.action == CaptureGuidanceAction.ACCEPT
    val busy = smartCapture is SmartCaptureState.Capturing || smartCapture is SmartCaptureState.Analyzing

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live capture", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { useFront = !useFront }) {
                        Icon(Icons.Default.Cameraswitch, contentDescription = "Flip camera", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        floatingActionButton = {
            if (hasCamera) {
                Column(horizontalAlignment = Alignment.End) {
                    if (!acceptReady && !busy) {
                        TextButton(
                            onClick = {
                                viewModel.smartCapture(
                                    imageCapture = imageCapture,
                                    executor = analysisExecutor,
                                    guidanceAction = guidance?.action,
                                    reasonCode = guidance?.reasonCode ?: "FORCE",
                                    frameId = guidance?.frameId ?: 0L,
                                    requireAccept = false,
                                    jobId = jobId,
                                    jobContext = jobContext
                                )
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary)
                        ) {
                            Text("Force capture")
                        }
                    }
                    ExtendedFloatingActionButton(
                        onClick = {
                            if (!acceptReady || busy) return@ExtendedFloatingActionButton
                            viewModel.smartCapture(
                                imageCapture = imageCapture,
                                executor = analysisExecutor,
                                guidanceAction = guidance?.action,
                                reasonCode = guidance?.reasonCode ?: "UNKNOWN",
                                frameId = guidance?.frameId ?: 0L,
                                requireAccept = true,
                                jobId = jobId,
                                jobContext = jobContext
                            )
                        },
                        expanded = true,
                        icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                        text = {
                            Text(
                                when {
                                    busy && smartCapture is SmartCaptureState.Capturing -> "Saving…"
                                    busy -> "AI analyzing…"
                                    acceptReady -> "AI capture"
                                    else -> "Wait for ACCEPT"
                                }
                            )
                        },
                        containerColor = if (acceptReady && !busy) PrimaryGreen else Color(0xFF455A64),
                        contentColor = if (acceptReady && !busy) Color.Black else TextPrimary,
                        modifier = Modifier
                    )
                }
            }
        },
        containerColor = BackgroundDark
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!hasCamera) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Camera permission needed for live guidance.", color = TextSecondary)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant camera")
                    }
                }
            } else {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )

                GuidanceHud(
                    guidance = guidance,
                    dropHint = lastDrop,
                    framesSeen = framesSeen,
                    framesDropped = framesDropped,
                    smartBusy = busy,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 88.dp)
                )

                bindError?.let { err ->
                    Text(
                        err,
                        color = Color(0xFFFF8A80),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    )
                }
            }

            when (val s = smartCapture) {
                is SmartCaptureState.Ready -> {
                    SmartCaptureResultSheet(
                        state = s,
                        onDismiss = { viewModel.clearSmartCapture() }
                    )
                }
                is SmartCaptureState.Error -> {
                    AlertDialog(
                        onDismissRequest = { viewModel.clearSmartCapture() },
                        confirmButton = {
                            TextButton(onClick = { viewModel.clearSmartCapture() }) { Text("OK") }
                        },
                        title = { Text("Smart capture") },
                        text = { Text(s.message) }
                    )
                }
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmartCaptureResultSheet(
    state: SmartCaptureState.Ready,
    onDismiss: () -> Unit
) {
    val a = state.analysis
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BackgroundDark
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AI form draft",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }
            Text(
                "Saved evidence · frame=${state.frameId} · policy=${state.guidanceAction}/${state.reasonCode} · src=${a.source}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(12.dp))
            ResultRow("Service", a.suggestedServiceType.ifBlank { "—" })
            ResultRow("Priority", a.suggestedPriority)
            ResultRow("Species", a.species.joinToString().ifBlank { "—" })
            ResultRow("Damage", a.damageTypes.joinToString().ifBlank { "—" })
            ResultRow("Price range", a.estimatedPriceRange.ifBlank { "—" })
            ResultRow("Confidence", "${"%.0f".format(a.confidence * 100)}%")
            Spacer(Modifier.height(8.dp))
            Text("Notes", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            Text(a.suggestedNotes.ifBlank { "—" }, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Text(
                "Photo kept in gallery as Evidence. Policy owned accept; AI only drafted the form fields.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.await(
    executor: java.util.concurrent.Executor
): T = suspendCancellableCoroutine { cont ->
    addListener(
        {
            try {
                cont.resume(get())
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        },
        executor
    )
}

@Composable
private fun GuidanceHud(
    guidance: CaptureGuidance?,
    dropHint: String?,
    framesSeen: Long,
    framesDropped: Long,
    smartBusy: Boolean,
    modifier: Modifier = Modifier
) {
    val action = guidance?.action ?: CaptureGuidanceAction.WAIT
    val accent = when (action) {
        CaptureGuidanceAction.ACCEPT -> PrimaryGreen
        CaptureGuidanceAction.HOLD_STEADY -> Color(0xFFFFC107)
        CaptureGuidanceAction.IMPROVE_LIGHTING -> Color(0xFFFF9800)
        CaptureGuidanceAction.MOVE_CLOSER, CaptureGuidanceAction.REFRAME -> Color(0xFF64B5F6)
        CaptureGuidanceAction.WAIT -> TextSecondary
    }
    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                when {
                    smartBusy -> "Smart capture in progress…"
                    else -> guidance?.userMessage ?: "Starting live analyzer…"
                },
                color = if (smartBusy) PrimaryGreen else accent,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(6.dp))
            val g = guidance
            if (g != null) {
                Text(
                    "action=${g.action} · reason=${g.reasonCode} · frame=${g.frameId}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "resultAge=${g.resultAgeFromArrivalMs}ms · analysis=${g.analysisDurationMs}ms · " +
                        "luma=${"%.0f".format(g.signals.meanLuma)} · sharp=${"%.0f".format(g.signals.sharpness)} · " +
                        "cover=${"%.0f".format(g.signals.subjectCoverage * 100)}%",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
                if (g.signals.labelHints.isNotEmpty()) {
                    Text(
                        "hints: ${g.signals.labelHints.joinToString()}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                "frames=$framesSeen dropped=$framesDropped" +
                    (dropHint?.let { " lastDrop=$it" } ?: "") +
                    " · KEEP_ONLY_LATEST",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "AI Capture saves still + runs photo→form when policy = ACCEPT",
                color = TextSecondary.copy(alpha = 0.9f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
