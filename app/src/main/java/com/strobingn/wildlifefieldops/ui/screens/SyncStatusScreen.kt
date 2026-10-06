package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.repository.SyncBacklogSnapshot
import com.strobingn.wildlifefieldops.ui.theme.FieldShapes
import com.strobingn.wildlifefieldops.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncStatusUi(
    val lastSuccessAt: Long? = null,
    val lastErrorAt: Long? = null,
    val lastError: String? = null,
    val lastAttemptAt: Long? = null,
    val lastAttemptOk: Boolean? = null,
    val lastMessage: String? = null,
    val backlog: SyncBacklogSnapshot? = null,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val connection: String = ""
) {
    val failed: Boolean get() = backlog?.hasFailures == true || lastAttemptOk == false
    val pending: Int get() = backlog?.pendingTotal ?: 0
    val headline: String
        get() = when {
            isSyncing -> "Syncing…"
            failed -> "Sync failed"
            pending > 0 -> "Pending sync · $pending"
            else -> "Synced"
        }
}

internal fun syncTimeLabel(ms: Long?): String? =
    ms?.takeIf { it > 0L }?.let { SimpleDateFormat("EEE, MMM d · h:mm a", Locale.US).format(Date(it)) }

/** Read-only view of the existing sync state, plus the same Sync now as Settings. */
@Composable
fun SyncStatusScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val backlog by viewModel.backlog.collectAsState()
    val lastOk by viewModel.lastSyncOk.collectAsState()
    val lastAt by viewModel.lastSyncAt.collectAsState()
    val lastMessage by viewModel.lastSyncMessage.collectAsState()
    val lastSuccessAt by viewModel.lastSyncSuccessAt.collectAsState()
    val lastErrorAt by viewModel.lastSyncErrorAt.collectAsState()
    val lastError by viewModel.lastSyncError.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val connection by viewModel.connectionStatus.collectAsState()
    LaunchedEffect(lastAt) { viewModel.refreshBacklog() }
    SyncStatusBody(
        state = SyncStatusUi(
            lastSuccessAt = lastSuccessAt,
            lastErrorAt = lastErrorAt,
            lastError = lastError,
            lastAttemptAt = lastAt,
            lastAttemptOk = lastOk,
            lastMessage = lastMessage,
            backlog = backlog,
            isSyncing = isSyncing,
            syncMessage = syncMessage,
            connection = connection
        ),
        onBack = onBack,
        onSyncNow = viewModel::triggerManualSync
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusBody(
    state: SyncStatusUi,
    onBack: () -> Unit,
    onSyncNow: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync status", color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("sync-status"),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                StatusCard {
                    Text(
                        state.headline,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (state.failed && !state.isSyncing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                    if (state.connection.isNotBlank()) {
                        Text(state.connection, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                StatusCard {
                    Label("Last successful sync")
                    Value(syncTimeLabel(state.lastSuccessAt) ?: "Not yet on this phone")
                }
            }
            item {
                StatusCard {
                    Label("Last error")
                    val at = syncTimeLabel(state.lastErrorAt)
                    if (at == null || state.lastError.isNullOrBlank()) {
                        Value("No errors recorded")
                    } else {
                        Value(at)
                        Text(
                            state.lastError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            item {
                StatusCard {
                    Label("Last attempt")
                    Value(syncTimeLabel(state.lastAttemptAt) ?: "None yet")
                    state.lastMessage?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                StatusCard {
                    Label("Waiting to upload")
                    val b = state.backlog
                    if (b == null) {
                        Value("Counting…")
                    } else {
                        CountRow("Jobs", b.pendingJobs)
                        CountRow("Customers", b.pendingCustomers)
                        CountRow("Inspections", b.pendingInspections)
                        CountRow("Photos", b.pendingPhotos)
                        CountRow("Map pins", b.pendingObservations)
                        CountRow("Species IDs", b.pendingEvents)
                        if (b.hasFailures) {
                            Spacer(Modifier.height(4.dp))
                            Label("Failed")
                            CountRow("Jobs", b.failedJobs, error = b.failedJobs > 0)
                            CountRow("Photos", b.failedPhotos, error = b.failedPhotos > 0)
                            b.recentFailures.forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onSyncNow,
                    enabled = !state.isSyncing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = FieldShapes.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(if (state.isSyncing) "Syncing…" else "Sync now", fontWeight = FontWeight.SemiBold)
                }
            }
            state.syncMessage?.takeIf { it.isNotBlank() }?.let { msg ->
                item {
                    Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FieldShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Value(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun CountRow(label: String, count: Int, error: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(
            count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
