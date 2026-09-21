package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobPriority
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.ui.components.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.components.WeatherBanner
import com.strobingn.wildlifefieldops.ui.viewmodel.JobAiViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.LiveWeatherViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    jobId: String,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToInvoice: (String) -> Unit,
    onNavigateToEstimate: (String) -> Unit,
    onNavigateToInspectionForm: (String) -> Unit,
    onNavigateToLiveCapture: (String) -> Unit,
    onNavigateToVoiceLog: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: JobsViewModel = hiltViewModel(),
    jobAiViewModel: JobAiViewModel = hiltViewModel()
) {
    val job by viewModel.getJobById(jobId).collectAsState(initial = null)
    val summary by jobAiViewModel.summary.collectAsState()
    val summaryLoading by jobAiViewModel.summaryLoading.collectAsState()
    val aiMessage by jobAiViewModel.message.collectAsState()
    val weatherVm: LiveWeatherViewModel = hiltViewModel()
    val weatherState by weatherVm.state.collectAsState()

    var showStatusDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    job?.let { currentJob ->
        LaunchedEffect(currentJob.id, currentJob.latitude, currentJob.longitude, currentJob.address) {
            weatherVm.loadJobWeather(currentJob.latitude, currentJob.longitude, currentJob.address)
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Job Details", color = TextPrimary) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showStatusDialog = true }) {
                            Icon(Icons.Default.Flag, contentDescription = "Change Status", tint = TextSecondary)
                        }
                        IconButton(onClick = { onNavigateToEdit(currentJob.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary)
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
                )
            },
            containerColor = BackgroundDark
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title and Status
                Card(
                    colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            currentJob.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusBadge(status = currentJob.status)
                            Spacer(modifier = Modifier.width(8.dp))
                            PriorityBadge(priority = currentJob.priority)
                            Spacer(modifier = Modifier.width(8.dp))
                            TypeBadge(type = currentJob.type)
                        }
                    }
                }

                // Customer Info
                InfoCard(title = "Customer Information") {
                    InfoRow(Icons.Default.Person, currentJob.customerName.ifBlank { "Not assigned" })
                    InfoRow(Icons.Default.LocationOn, currentJob.address.ifBlank { "No address" })
                    if (currentJob.latitude != null && currentJob.longitude != null) {
                        InfoRow(Icons.Default.GpsFixed, "${currentJob.latitude}, ${currentJob.longitude}")
                    }
                }

                // Job Details
                InfoCard(title = "Job Details") {
                    InfoRow(Icons.Default.Description, currentJob.description.ifBlank { "No description" })
                    if (currentJob.scheduledDate != null) {
                        InfoRow(
                            Icons.Default.CalendarToday,
                            SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault())
                                .format(Date(currentJob.scheduledDate))
                        )
                    }
                    InfoRow(Icons.Default.PersonOutline, currentJob.assignedTo.ifBlank { "Unassigned" })
                    if (currentJob.estimatedValue > 0) {
                        InfoRow(Icons.Default.AttachMoney, "Estimated: $${String.format("%.2f", currentJob.estimatedValue)}")
                    }
                    if (currentJob.actualCost > 0) {
                        InfoRow(Icons.Default.Money, "Actual: $${String.format("%.2f", currentJob.actualCost)}")
                    }
                }

                // Notes
                if (currentJob.notes.isNotBlank()) {
                    InfoCard(title = "Notes") {
                        Text(currentJob.notes, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }

                // AI summary + estimate
                InfoCard(title = "AI tools (${jobAiViewModel.providerLabel})") {
                    Text(
                        if (jobAiViewModel.isConfigured) {
                            "Generate a handoff summary or open Estimate and draft from notes."
                        } else {
                            "Offline mode: still works with heuristics. Add XAI_API_KEY for SpaceXAI Grok."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { jobAiViewModel.generateSummary(currentJob) },
                            enabled = !summaryLoading,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentPurple)
                        ) {
                            if (summaryLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentPurple
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(if (summaryLoading) "Writing…" else "Summary")
                        }
                        Button(
                            onClick = { onNavigateToEstimate(currentJob.id) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentBlue,
                                contentColor = androidx.compose.ui.graphics.Color.White
                            )
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Estimate")
                        }
                    }
                    if (!aiMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(aiMessage!!, style = MaterialTheme.typography.labelSmall, color = PrimaryGreen)
                    }
                    if (!summary.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            summary!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { jobAiViewModel.appendSummaryToNotes(currentJob) }) {
                            Text("Save summary to notes", color = PrimaryGreen)
                        }
                    }
                }

                // Primary edit — always available after a job is entered
                Button(
                    onClick = { onNavigateToEdit(currentJob.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryGreen,
                        contentColor = androidx.compose.ui.graphics.Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit job", fontWeight = FontWeight.Bold)
                }

                Text("Site weather", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Spacer(modifier = Modifier.height(8.dp))
                WeatherBanner(
                    state = weatherState,
                    title = "Job site",
                    onRefresh = {
                        weatherVm.loadJobWeather(currentJob.latitude, currentJob.longitude, currentJob.address)
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Text("Actions", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        label = "Invoice",
                        icon = Icons.Default.Receipt,
                        color = AccentPurple,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToInvoice(currentJob.id) }
                    )
                    ActionButton(
                        label = "Estimate",
                        icon = Icons.Default.Calculate,
                        color = AccentBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToEstimate(currentJob.id) }
                    )
                    ActionButton(
                        label = "Inspect",
                        icon = Icons.Default.Search,
                        color = AccentCyan,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToInspectionForm(currentJob.id) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        label = "Live AI",
                        icon = Icons.Default.Videocam,
                        color = PrimaryGreen,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToLiveCapture(currentJob.id) }
                    )
                    ActionButton(
                        label = "Voice log",
                        icon = Icons.Default.Mic,
                        color = AccentBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToVoiceLog(currentJob.id) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Created: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(currentJob.createdAt))}" +
                        " · Updated: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(currentJob.updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }

        // Status Change Dialog
        if (showStatusDialog) {
            AlertDialog(
                onDismissRequest = { showStatusDialog = false },
                title = { Text("Change Status", color = TextPrimary) },
                text = {
                    Column {
                        JobStatus.entries.forEach { status ->
                            TextButton(
                                onClick = {
                                    viewModel.updateJobStatus(currentJob.id, status)
                                    showStatusDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(status.name.replace("_", " "), color = TextPrimary)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showStatusDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = BackgroundCard
            )
        }

        // Delete Confirmation
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Job?", color = TextPrimary) },
                text = { Text("This action cannot be undone.", color = TextSecondary) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteJob(currentJob)
                        showDeleteDialog = false
                        onBack()
                    }) {
                        Text("Delete", color = ErrorRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = BackgroundCard
            )
        }
    } ?: run {
        // Loading or not found
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryGreen)
        }
    }
}

@Composable
private fun StatusBadge(status: JobStatus) {
    val color = when (status) {
        JobStatus.PENDING -> StatusPending
        JobStatus.IN_PROGRESS -> AccentBlue
        JobStatus.COMPLETED -> SuccessGreen
        JobStatus.CANCELLED -> ErrorRed
        JobStatus.INVOICED -> AccentPurple
        JobStatus.PAID -> PrimaryGreen
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(status.name.replace("_", " "), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun PriorityBadge(priority: JobPriority) {
    val color = when (priority) {
        JobPriority.LOW -> TextSecondary
        JobPriority.MEDIUM -> StatusPending
        JobPriority.HIGH -> ErrorRed
        JobPriority.URGENT -> StatusUrgent
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(priority.name, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun TypeBadge(type: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(type, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
