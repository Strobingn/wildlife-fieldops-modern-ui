package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.os.Handler
import android.os.Looper
import android.content.pm.PackageManager
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.strobingn.wildlifefieldops.ai.camera.CaptureGuidance
import com.strobingn.wildlifefieldops.ai.camera.CaptureGuidanceAction
import com.strobingn.wildlifefieldops.ai.camera.LiveCameraAnalyzer
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveCaptureScreen(onBack: () -> Unit) {
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
            val cameraProvider = ProcessCameraProvider.getInstance(context).await(ContextCompat.getMainExecutor(context))
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { it.setAnalyzer(analysisExecutor, analyzer) }
            val selector = if (useFront) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            cameraProvider.unbindAll()
            analyzer.reset()
            framesDropped = 0L
            lastDrop = null
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        } catch (e: Exception) {
            Log.e("LiveCapture", "bind failed", e)
            bindError = e.message ?: "Camera bind failed"
        }
    }

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
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
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
        }
    }
}

/** ListenableFuture await without adding guava-coroutines if unavailable. */
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
                guidance?.userMessage ?: "Starting live analyzer…",
                color = accent,
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
                "sourceTimestamp kept separate (no unsafe clock subtract)",
                color = TextSecondary.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
