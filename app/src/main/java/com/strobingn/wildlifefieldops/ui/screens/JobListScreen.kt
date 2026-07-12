package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.ui.components.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(
    onNavigateToJobDetail: (String) -> Unit,
    onNavigateToJobForm: () -> Unit,
    onBack: () -> Unit,
    viewModel: JobsViewModel = hiltViewModel()
) {
    val jobs by viewModel.jobs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jobs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToJobForm,
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Job", fontWeight = FontWeight.SemiBold) },
                shape = ShapePill
            )
        },
        containerColor = colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = { Text("Search jobs...", color = colorScheme.onSurfaceVariant) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = colorScheme.onSurfaceVariant)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colorScheme.primary,
                    unfocusedBorderColor = colorScheme.outlineVariant,
                    focusedTextColor = colorScheme.onSurface,
                    unfocusedTextColor = colorScheme.onSurface,
                    focusedContainerColor = colorScheme.surface,
                    unfocusedContainerColor = colorScheme.surface,
                    focusedPlaceholderColor = colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = colorScheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                singleLine = true,
                shape = ShapeInput
            )

            // Status filter chips
            if (selectedStatus != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = { viewModel.setStatusFilter(null) },
                        label = {
                            Text(
                                selectedStatus!!.name.replace("_", " "),
                                color = colorScheme.onSurface
                            )
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear",
                                modifier = Modifier.size(16.dp),
                                tint = colorScheme.onSurfaceVariant
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(containerColor = colorScheme.surface),
                        border = AssistChipDefaults.assistChipBorder(true, colorScheme.outlineVariant)
                    )
                }
            }

            // Job count
            Text(
                "${jobs.size} job${if (jobs.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            if (isLoading) {
                ListShimmer(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (jobs.isEmpty()) {
                        item {
                            EmptyState(
                                icon = {
                                    Icon(
                                        Icons.Default.WorkOutline,
                                        contentDescription = null,
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                },
                                title = "No jobs found",
                                subtitle = "Create a job to get started",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        itemsIndexed(jobs) { index, job ->
                            FadeSlideIn(index = index) {
                                JobListItem(
                                    job = job,
                                    onClick = { onNavigateToJobDetail(job.id) }
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Filter bottom sheet
    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            containerColor = colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    "Filter by Status",
                    style = MaterialTheme.typography.titleLarge,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))
                JobStatus.entries.forEach { status ->
                    val isSelected = selectedStatus == status
                    ListItem(
                        headlineContent = {
                            Text(
                                status.name.replace("_", " "),
                                color = colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        leadingContent = {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setStatusFilter(status)
                                    showFilterSheet = false
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = colorScheme.primary)
                            )
                        },
                        modifier = Modifier.clickable {
                            viewModel.setStatusFilter(status)
                            showFilterSheet = false
                        },
                        colors = ListItemDefaults.colors(containerColor = colorScheme.surface)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        viewModel.setStatusFilter(null)
                        showFilterSheet = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurfaceVariant),
                    shape = ShapeButton,
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(colorScheme.outlineVariant))
                ) {
                    Text("Clear Filter")
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun JobListItem(job: Job, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val statusColor = when (job.status) {
        JobStatus.PENDING -> StatusPending
        JobStatus.IN_PROGRESS -> colorScheme.secondary
        JobStatus.COMPLETED -> SuccessGreen
        JobStatus.CANCELLED -> ErrorRed
        JobStatus.INVOICED -> StatusInvoiced
        JobStatus.PAID -> colorScheme.primary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = ShapeCardSmall,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        job.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (job.customerName.isNotBlank()) {
                        Text(
                            job.customerName,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(ShapeChip)
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        job.status.name.replace("_", " "),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    job.address.ifBlank { "No address" },
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (job.estimatedValue > 0) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Est: $${String.format("%.2f", job.estimatedValue)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
