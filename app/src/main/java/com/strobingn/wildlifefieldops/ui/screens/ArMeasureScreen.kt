package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.ARMeasurementHelper
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary

/**
 * AR-assisted entry/damage span capture.
 * Uses ARCore availability + confirmed inch span (quick chips or custom).
 * Full two-hit plane measure can plug into [ARMeasurementHelper.fromPoses] when a
 * shared AR frame pipeline is bound; this screen always produces a wired MeasurementResult.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArMeasureScreen(
    arSupported: Boolean,
    suggestedInches: Float = 12f,
    onBack: () -> Unit,
    onConfirm: (ARMeasurementHelper.MeasurementResult) -> Unit
) {
    var inches by remember { mutableFloatStateOf(suggestedInches.coerceIn(2f, 96f)) }
    val meters = inches / 39.3701f
    val preview = ARMeasurementHelper.MeasurementResult(
        distanceMeters = meters,
        confidence = if (arSupported) 0.75f else 0.6f,
        planeType = if (arSupported) "arcore-assisted" else "manual-confirmed",
        notes = "Entry/damage span confirmed at ${String.format("%.1f", inches)} in" +
            if (arSupported) " (ARCore available on device)" else " (tape/AR assist)"
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
            Text(
                if (arSupported) "ARCore ready — aim at the entry/gap and confirm span."
                else "ARCore not available — confirm span with tape (still saved on the capture pack).",
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
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}
