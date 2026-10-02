package com.strobingn.wildlifefieldops.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.ErrorRed
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.update.AppUpdatePhase
import com.strobingn.wildlifefieldops.update.AppUpdateUiState
import com.strobingn.wildlifefieldops.update.AppUpdateViewModel

@Composable
fun AppUpdateHost(viewModel: AppUpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.checkOnStart() }
    AppUpdateDialogs(state = state, viewModel = viewModel)
}

@Composable
fun AppUpdateBannerBar(viewModel: AppUpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    if (!state.showBanner || !state.updateAvailable || state.latest == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Update available", color = TextPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text(state.latestLabel, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = viewModel::showDialog) { Text("View", color = PrimaryGreen) }
            TextButton(onClick = viewModel::dismissBanner) { Text("Later", color = TextSecondary) }
        }
    }
}

@Composable
fun AppUpdateSettingsSection(viewModel: AppUpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.checkOnSettingsOpened() }
    AppUpdateSettingsCard(state = state, onCheck = viewModel::checkNow, onUpdate = viewModel::startUpdate)
}

@Composable
fun AppUpdateSettingsCard(
    state: AppUpdateUiState,
    onCheck: () -> Unit,
    onUpdate: () -> Unit
) {
    val checking = state.phase == AppUpdatePhase.Checking
    val working = state.phase == AppUpdatePhase.Downloading ||
        state.phase == AppUpdatePhase.Verifying ||
        state.phase == AppUpdatePhase.Flushing ||
        state.phase == AppUpdatePhase.Installing
    Text(
        "Current: ${state.currentLabel}",
        color = TextPrimary,
        style = MaterialTheme.typography.bodyMedium
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Latest main: ${if (state.latest != null) state.latestLabel else "Not checked yet"}",
        color = TextSecondary,
        style = MaterialTheme.typography.bodySmall
    )
    state.statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            message,
            color = if (state.lastError != null) ErrorRed else TextTertiary,
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (state.phase == AppUpdatePhase.Downloading) {
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { state.downloadFraction },
            modifier = Modifier.fillMaxWidth(),
            color = PrimaryGreen,
            trackColor = TextTertiary.copy(alpha = 0.3f)
        )
        Text(
            downloadLabel(state.downloadBytes, state.downloadTotal),
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall
        )
    }
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedButton(
        onClick = onCheck,
        modifier = Modifier.fillMaxWidth(),
        enabled = !checking && !working,
        shape = RoundedCornerShape(12.dp)
    ) {
        if (checking) {
            CircularProgressIndicator(Modifier.size(18.dp), color = PrimaryGreen, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Icon(Icons.Default.SystemUpdate, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(if (checking) "Checking…" else "Check for updates")
    }
    if (state.updateAvailable) {
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onUpdate,
            modifier = Modifier.fillMaxWidth(),
            enabled = !working && !checking,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Update", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AppUpdateDialogs(
    state: AppUpdateUiState,
    viewModel: AppUpdateViewModel
) {
    val context = LocalContext.current
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onReturnedFromUnknownSources()
    }

    if (state.showUnknownSourcesPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("Allow FieldOps updates?", color = TextPrimary) },
            text = {
                Text(
                    "Android blocks APK installs until you allow Wildlife FieldOps to install unknown apps. " +
                        "This is a one-time settings toggle. After you turn it on, the update resumes and keeps local jobs and photos.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { unknownSourcesLauncher.launch(viewModel.unknownSourcesIntent()) }
                        .onFailure {
                            runCatching { context.startActivity(viewModel.unknownSourcesIntent()) }
                        }
                }) { Text("Open settings", color = PrimaryGreen) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text("Not now", color = TextSecondary) }
            },
            containerColor = BackgroundCard
        )
        return
    }

    if (state.phase == AppUpdatePhase.AwaitingUnsynced && state.showDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("Unsynced work is still on this phone", color = TextPrimary) },
            text = {
                Text(
                    "${state.pendingUnsynced} items are still waiting to sync. The update will not wipe them, " +
                        "but they may not be in the cloud yet. Wait a bit longer, or install anyway.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::proceedDespiteUnsynced) {
                    Text("Install anyway", color = PrimaryGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::waitAndRetrySync) { Text("Wait and sync", color = TextSecondary) }
            },
            containerColor = BackgroundCard
        )
        return
    }

    if (state.showDialog && state.latest != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = { Text("Update available", color = TextPrimary) },
            text = { AppUpdateDialogBody(state) },
            confirmButton = {
                val working = state.phase == AppUpdatePhase.Downloading ||
                    state.phase == AppUpdatePhase.Verifying ||
                    state.phase == AppUpdatePhase.Flushing ||
                    state.phase == AppUpdatePhase.Installing
                TextButton(
                    onClick = viewModel::startUpdate,
                    enabled = !working
                ) { Text(if (working) "Working…" else "Update", color = PrimaryGreen) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDialog) { Text("Later", color = TextSecondary) }
            },
            containerColor = BackgroundCard
        )
    }
}

@Composable
fun AppUpdateDialogBody(state: AppUpdateUiState) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("Current: ${state.currentLabel}", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Text("Latest: ${state.latestLabel}", color = TextPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        val changelog = state.latest?.changelog?.trim().orEmpty()
        if (changelog.isNotEmpty()) {
            Text("What's new", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(changelog, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                "Main-branch debug APK from GitHub. Local jobs, photos, and settings stay on this phone.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        state.statusMessage?.takeIf { it.isNotBlank() }?.let { message ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(message, color = if (state.lastError != null) ErrorRed else TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
        if (state.phase == AppUpdatePhase.Downloading) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.downloadFraction },
                modifier = Modifier.fillMaxWidth(),
                color = PrimaryGreen,
                trackColor = TextTertiary.copy(alpha = 0.3f)
            )
            Text(
                downloadLabel(state.downloadBytes, state.downloadTotal),
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (state.phase == AppUpdatePhase.Checking ||
            state.phase == AppUpdatePhase.Verifying ||
            state.phase == AppUpdatePhase.Flushing ||
            state.phase == AppUpdatePhase.Installing
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), color = PrimaryGreen, strokeWidth = 2.dp)
                Text("Please keep the app open.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun downloadLabel(read: Long, total: Long): String {
    fun mb(value: Long) = "%.1f MB".format(value / (1024.0 * 1024.0))
    return if (total > 0L) "${mb(read)} of ${mb(total)}" else mb(read)
}
