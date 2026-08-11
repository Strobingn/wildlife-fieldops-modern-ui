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
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailScreen(
    jobId: String,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToInvoice: (String) -> Unit,
    onNavigateToEstimate: (String) -> Unit,
    onNavigateToInspectionForm: (String) -> Unit,
    onNavigateToARMeasure: (String) -> Unit = {},
    onBack: () -> Unit,
    viewModel: JobsViewModel = hiltViewModel()
) {
    val job by viewModel.getJobById(jobId).collectAsState(initial = null)
    var showStatusDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    job?.let { currentJob ->
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
                            TypeBadge(type = currentJob.type.name)
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

                // AR Measure row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        label = "AR Measure",
                        icon = Icons.Default.Straighten,
                        color = AccentOrange,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToARMeasure(currentJob.id) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Created: ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(currentJob.createdAt))}",
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
