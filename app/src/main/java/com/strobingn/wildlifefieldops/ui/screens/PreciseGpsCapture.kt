package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.strobingn.wildlifefieldops.location.CaptureReadout
import com.strobingn.wildlifefieldops.location.PreciseGpsMath
import com.strobingn.wildlifefieldops.location.PreciseGpsSession
import com.strobingn.wildlifefieldops.location.PreciseSpot
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.StatusUrgent
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PreciseGpsCaptureButton(
    onSpot: (PreciseSpot) -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    var showCapture by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted || fine) showCapture = true
        else onMessage(PreciseGpsMath.PERMISSION_DENIED)
    }
    OutlinedButton(
        onClick = {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            if (fine) showCapture = true else showRationale = true
        },
        modifier = modifier.testTag("precise-gps")
    ) {
        Text("Precise GPS", color = TextPrimary)
    }
    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            containerColor = BackgroundCard,
            title = { Text("Precise location", color = TextPrimary) },
            text = { Text(PreciseGpsMath.RATIONALE, color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }) { Text("Continue", color = PrimaryGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) { Text("Not now", color = TextSecondary) }
            }
        )
    }
    if (showCapture) {
        PreciseGpsCaptureDialog(
            onUse = {
                showCapture = false
                onSpot(it)
            },
            onDismiss = { showCapture = false }
        )
    }
}

@Composable
fun PreciseGpsCaptureDialog(
    onUse: (PreciseSpot) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val session = remember { PreciseGpsSession(context) }
    var readout by remember { mutableStateOf(CaptureReadout(0L, null, null, null, 0, 0, false, 0f)) }
    var message by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf(false) }
    LaunchedEffect(session) {
        val error = try {
            session.start { readout = it }
        } catch (_: RuntimeException) {
            PreciseGpsMath.GPS_UNAVAILABLE
        }
        if (error != null) {
            message = error
            return@LaunchedEffect
        }
        try {
            while (isActive && !finished) {
                val read = session.tick()
                readout = read
                session.failureMessage()?.let { message = it }
                if (read.autoFinish) {
                    val spot = session.spot(System.currentTimeMillis())
                    if (spot != null) {
                        finished = true
                        onUse(spot)
                    } else {
                        message = PreciseGpsMath.NO_FIX_TIMEOUT
                    }
                    return@LaunchedEffect
                }
                delay(200)
            }
        } finally {
            session.stop()
        }
    }
    Dialog(onDismissRequest = {
        finished = true
        session.stop()
        onDismiss()
    }) {
        PreciseGpsCaptureBody(
            readout = readout,
            message = message,
            onUseNow = {
                val spot = session.spot(System.currentTimeMillis())
                if (spot == null) {
                    message = PreciseGpsMath.NO_FIX
                } else {
                    finished = true
                    session.stop()
                    onUse(spot)
                }
            },
            onCancel = {
                finished = true
                session.stop()
                onDismiss()
            }
        )
    }
}

@Composable
fun PreciseGpsCaptureBody(
    readout: CaptureReadout,
    message: String?,
    onUseNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val seconds = (readout.elapsedMs / 1000L).toInt().coerceIn(0, 60)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BackgroundCard
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Save trap spot", color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(
                "Fixes worse than ±15 m are ignored. The spot is an accuracy-weighted average.",
                color = TextSecondary
            )
            Text(
                PreciseGpsMath.accuracyLabel(readout.averaged, readout.latestAccuracyMeters),
                color = TextPrimary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.testTag("precise-gps-accuracy")
            )
            Text(PreciseGpsMath.sampleLabel(readout.acceptedCount), color = TextSecondary)
            Text(PreciseGpsMath.satelliteLabel(readout.satellites), color = TextSecondary)
            LinearProgressIndicator(
                progress = { readout.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("precise-gps-progress"),
                color = PrimaryGreen,
                trackColor = BorderDark
            )
            Text("$seconds s / 60 s", color = TextSecondary)
            if (!message.isNullOrBlank()) {
                Text(message, color = StatusUrgent)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onCancel, modifier = Modifier.testTag("precise-gps-cancel")) {
                    Text("Cancel", color = TextSecondary)
                }
                Button(
                    onClick = onUseNow,
                    modifier = Modifier.testTag("precise-gps-use-now"),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("Use now") }
            }
        }
    }
}

@Composable
fun TrapSpotDirectionsButton(
    latitude: Double?,
    longitude: Double?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val target = JobDirections.fromTrap(latitude, longitude)
    val enabled = JobDirections.hasDestination(target)
    TextButton(
        onClick = { if (enabled) JobDirections.open(context, target) },
        enabled = enabled,
        modifier = modifier.testTag("trap-spot-directions")
    ) {
        Text("Directions", color = if (enabled) PrimaryGreen else TextSecondary)
    }
}
