package com.strobingn.wildlifefieldops.ui.screens

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.strobingn.wildlifefieldops.ai.ARMeasurementHelper
import com.strobingn.wildlifefieldops.ui.ar.ArTwoTapSurfaceView
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary

/**
 * Full two-tap AR plane measurement (ARCore Session + GL camera + Compose overlay).
 * Falls back to the assistive inch slider when ARCore is missing or the session fails —
 * Throwable-safe so Live Capture never crashes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArMeasureScreen(
    arSupported: Boolean,
    suggestedInches: Float = 12f,
    onBack: () -> Unit,
    onConfirm: (ARMeasurementHelper.MeasurementResult) -> Unit
) {
    var forceManual by remember { mutableStateOf(!arSupported) }
    var arFatal by remember { mutableStateOf<String?>(null) }

    if (forceManual) {
        ManualArMeasureFallback(
            arSupported = arSupported && arFatal == null,
            suggestedInches = suggestedInches,
            banner = arFatal,
            onBack = onBack,
            onConfirm = onConfirm,
            onTryAr = if (arSupported) {
                {
                    arFatal = null
                    forceManual = false
                }
            } else null
        )
        return
    }

    TwoTapArMeasureContent(
        suggestedInches = suggestedInches,
        onBack = onBack,
        onConfirm = onConfirm,
        onFallbackManual = { reason ->
            arFatal = reason
            forceManual = true
        }
    )
}

/** Mutable tap state shared with the GL hit listener (avoids stale Compose closures). */
private class TwoTapSession {
    var pointA: PlacedPoint? = null
    var pointB: PlacedPoint? = null

    fun clearAnchorsOnly() {
        detach(pointA?.anchor)
        detach(pointB?.anchor)
        pointA = null
        pointB = null
    }

    fun reset() {
        clearAnchorsOnly()
    }

    private fun detach(a: Anchor?) {
        try {
            a?.detach()
        } catch (_: Throwable) {
        }
    }
}

private data class PlacedPoint(
    val pose: Pose,
    val planeType: String,
    val confidence: Float,
    val anchor: Anchor?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TwoTapArMeasureContent(
    suggestedInches: Float,
    onBack: () -> Unit,
    onConfirm: (ARMeasurementHelper.MeasurementResult) -> Unit,
    onFallbackManual: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val session = remember { TwoTapSession() }
    var surfaceRef by remember { mutableStateOf<ArTwoTapSurfaceView?>(null) }
    var statusMsg by remember { mutableStateOf("Starting AR…") }
    var planesVisible by remember { mutableIntStateOf(0) }
    var tracking by remember { mutableStateOf(false) }
    var placedCount by remember { mutableIntStateOf(0) }
    var missHint by remember { mutableStateOf<String?>(null) }
    var measured by remember { mutableStateOf<ARMeasurementHelper.MeasurementResult?>(null) }

    fun syncPlacedCount() {
        placedCount = listOfNotNull(session.pointA, session.pointB).size
    }

    fun resetPoints() {
        session.reset()
        measured = null
        missHint = null
        syncPlacedCount()
        statusMsg = if (planesVisible > 0) {
            "Plane locked — tap point A, then point B"
        } else {
            "Scan for a plane, then tap point A"
        }
    }

    DisposableEffect(lifecycleOwner, surfaceRef) {
        val surface = surfaceRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> surface?.resumeSession()
                Lifecycle.Event.ON_PAUSE -> surface?.pauseSession()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                surface?.setOnHitListener(null)
                surface?.destroySession()
            } catch (_: Throwable) {
            }
            session.clearAnchorsOnly()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AR measure", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    TextButton(onClick = { onFallbackManual("Switched to manual tape/slider") }) {
                        Text("Manual", color = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        ArTwoTapSurfaceView(
                            context = ctx,
                            onStatus = { s ->
                                statusMsg = when {
                                    measured != null -> statusMsg
                                    session.pointA != null && session.pointB == null ->
                                        "Point A set — tap point B (other end of span)"
                                    session.pointA == null -> s.message
                                    else -> s.message
                                }
                                planesVisible = s.planesVisible
                                tracking = s.tracking
                            },
                            onFatal = { reason -> onFallbackManual(reason) }
                        ).also { view ->
                            view.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            view.setOnHitListener { hit ->
                                if (hit == null) {
                                    missHint = "No plane hit — aim at a detected surface and try again"
                                    return@setOnHitListener
                                }
                                missHint = null
                                when {
                                    session.pointA == null -> {
                                        session.pointA = PlacedPoint(
                                            hit.pose, hit.planeType, hit.confidence, hit.anchor
                                        )
                                        measured = null
                                        statusMsg = "Point A set — tap point B (other end of span)"
                                        syncPlacedCount()
                                    }
                                    session.pointB == null -> {
                                        session.pointB = PlacedPoint(
                                            hit.pose, hit.planeType, hit.confidence, hit.anchor
                                        )
                                        val a = session.pointA
                                        if (a != null) {
                                            val poseA = try {
                                                a.anchor?.pose ?: a.pose
                                            } catch (_: Throwable) {
                                                a.pose
                                            }
                                            val poseB = try {
                                                hit.anchor?.pose ?: hit.pose
                                            } catch (_: Throwable) {
                                                hit.pose
                                            }
                                            val plane = when {
                                                a.planeType == hit.planeType -> a.planeType
                                                else -> "${a.planeType}+${hit.planeType}"
                                            }
                                            val conf = ((a.confidence + hit.confidence) / 2f)
                                                .coerceIn(0.1f, 1f)
                                            measured = ARMeasurementHelper.fromPoses(
                                                poseA, poseB, plane, conf
                                            )
                                            statusMsg = "Span ready — confirm or retake"
                                        }
                                        syncPlacedCount()
                                    }
                                    else -> {
                                        session.reset()
                                        session.pointA = PlacedPoint(
                                            hit.pose, hit.planeType, hit.confidence, hit.anchor
                                        )
                                        measured = null
                                        statusMsg = "Point A set — tap point B"
                                        syncPlacedCount()
                                    }
                                }
                            }
                            surfaceRef = view
                            try {
                                view.resumeSession()
                            } catch (_: Throwable) {
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    onRelease = { view ->
                        try {
                            view.setOnHitListener(null)
                            view.destroySession()
                        } catch (_: Throwable) {
                        }
                        if (surfaceRef === view) surfaceRef = null
                    }
                )

                Canvas(Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val arm = 28.dp.toPx()
                    val stroke = 2.dp.toPx()
                    val color = if (planesVisible > 0) Color(0xFF42A5F5) else Color(0xFFBDBDBD)
                    drawLine(color, Offset(cx - arm, cy), Offset(cx + arm, cy), stroke, cap = StrokeCap.Round)
                    drawLine(color, Offset(cx, cy - arm), Offset(cx, cy + arm), stroke, cap = StrokeCap.Round)
                    drawCircle(color.copy(alpha = 0.35f), radius = 6.dp.toPx(), center = Offset(cx, cy))
                }

                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Color(0x99000000))
                        .padding(12.dp)
                ) {
                    Text(statusMsg, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        buildString {
                            append(if (tracking) "Tracking" else "Searching")
                            append(" · planes=")
                            append(planesVisible)
                            if (placedCount >= 1) append(" · A✓")
                            if (placedCount >= 2) append(" · B✓")
                        },
                        color = Color(0xFFBDBDBD),
                        style = MaterialTheme.typography.labelSmall
                    )
                    missHint?.let {
                        Text(it, color = Color(0xFFFFAB91), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(BackgroundDark)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val result = measured
                if (result != null) {
                    Text(
                        result.feetLabel,
                        color = PrimaryGreen,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "plane=${result.planeType} · conf=${(result.confidence * 100).toInt()}%",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { onConfirm(result) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryGreen,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Confirm span")
                        }
                        TextButton(onClick = { resetPoints() }) {
                            Text("Retake")
                        }
                    }
                } else {
                    Text(
                        when {
                            placedCount == 0 ->
                                "Tap the screen (or Place) for point A — start of entry/damage span."
                            else -> "Tap point B — far end of the span."
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(
                        onClick = { surfaceRef?.queueCenterHit() },
                        enabled = tracking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryGreen,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            when {
                                placedCount == 0 -> "Place point A at crosshair"
                                else -> "Place point B at crosshair"
                            }
                        )
                    }
                    Text(
                        "Suggested tape fallback ~${String.format("%.0f", suggestedInches)}\" if AR misses.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManualArMeasureFallback(
    arSupported: Boolean,
    suggestedInches: Float,
    banner: String?,
    onBack: () -> Unit,
    onConfirm: (ARMeasurementHelper.MeasurementResult) -> Unit,
    onTryAr: (() -> Unit)?
) {
    var inches by remember { mutableFloatStateOf(suggestedInches.coerceIn(2f, 96f)) }
    val meters = inches / 39.3701f
    val preview = ARMeasurementHelper.MeasurementResult(
        distanceMeters = meters,
        confidence = if (arSupported) 0.7f else 0.6f,
        planeType = if (arSupported) "manual-with-arcore" else "manual-confirmed",
        notes = "Entry/damage span confirmed at ${String.format("%.1f", inches)} in" +
            if (arSupported) " (manual; ARCore present)" else " (tape/AR assist)"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AR measure", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            banner?.let {
                Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                if (arSupported) {
                    "Manual span — use when AR hit-test isn’t practical. ARCore is available on this device."
                } else {
                    "ARCore not available — confirm span with tape (still saved on the capture pack)."
                },
                color = TextSecondary
            )
            Text(
                preview.feetLabel,
                color = PrimaryGreen,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text("Span (inches): ${String.format("%.1f", inches)}", color = TextPrimary)
            Slider(
                value = inches,
                onValueChange = { inches = it },
                valueRange = 2f..96f
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(6f, 8f, 12f, 18f, 24f, 36f).forEach { chip ->
                    FilterChip(
                        selected = kotlin.math.abs(inches - chip) < 0.2f,
                        onClick = { inches = chip },
                        label = { Text("${chip.toInt()}\"") }
                    )
                }
            }
            Text(
                "Tip: for attic vents and soffit gaps, 8–18 in is common. Overview shots may be larger.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Button(
                onClick = { onConfirm(preview) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save measurement to capture")
            }
            onTryAr?.let { retry ->
                TextButton(onClick = retry, modifier = Modifier.fillMaxWidth()) {
                    Text("Try AR two-tap again")
                }
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.size(1.dp))
        }
    }
}
