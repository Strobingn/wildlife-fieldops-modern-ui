package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.navigation.ManualJobEntry
import com.strobingn.wildlifefieldops.navigation.VoiceJobEntry
import com.strobingn.wildlifefieldops.ui.components.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(
    onNavigateToJobDetail: (String) -> Unit,
    onNavigateToJobForm: () -> Unit,
    onNavigateToDictate: () -> Unit = {},
    onBack: () -> Unit,
    showBack: Boolean = true,
    viewModel: JobsViewModel = hiltViewModel()
) {
    val jobs by viewModel.jobs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            FieldTopBar(
                title = "Jobs",
                onBack = if (showBack) onBack else null,
                actions = {
                    IconButton(onClick = onNavigateToDictate) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = VoiceJobEntry.ACTION_LABEL,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onNavigateToJobForm) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = ManualJobEntry.ACTION_LABEL,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = onNavigateToDictate,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = FieldShapes.fab
                ) {
                    Icon(Icons.Default.Mic, contentDescription = VoiceJobEntry.ACTION_LABEL)
                }
                ExtendedFloatingActionButton(
                    onClick = onNavigateToJobForm,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = FieldShapes.fab,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(ManualJobEntry.ACTION_LABEL, fontWeight = FontWeight.SemiBold) }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            FieldSearchBar(
                value = searchQuery,
                onValueChange = viewModel::setSearchQuery,
                placeholder = "Search jobs, customers, addresses…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onNavigateToDictate,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = FieldShapes.button
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(VoiceJobEntry.ACTION_LABEL, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Button(
                    onClick = onNavigateToJobForm,
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = FieldShapes.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(ManualJobEntry.ACTION_LABEL, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }

            // Status filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedStatus == null,
                        onClick = { viewModel.setStatusFilter(null) },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                items(com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.stages) { status ->
                    val selected = selectedStatus == status
                    FilterChip(
                        selected = selected,
                        onClick = {
                            viewModel.setStatusFilter(if (selected) null else status)
                        },
                        label = { Text(com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.label(status)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            Text(
                "${jobs.size} job${if (jobs.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (isLoading) {
                ListShimmer(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (jobs.isEmpty()) {
                        item {
                            EmptyState(
                                icon = {
                                    Icon(
                                        Icons.Default.WorkOutline,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(36.dp)
                                    )
                                },
                                title = "No jobs found",
                                subtitle = "Create a job or clear filters",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        itemsIndexed(jobs, key = { _, job -> job.id }) { index, job ->
                            FadeSlideIn(index = index) {
                                JobListItem(
                                    job = job,
                                    onClick = { onNavigateToJobDetail(job.id) }
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(168.dp)) }
                }
            }
        }
    }
}

@Composable
private fun JobListItem(job: Job, onClick: () -> Unit) {
    val statusColor = jobStatusColor(job.status)

    FieldCard(
        onClick = onClick,
        accentColor = statusColor
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    job.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                if (job.customerName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        job.customerName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(
                    text = com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.label(job.status),
                    color = statusColor
                )
                if (!job.syncError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Sync failed",
                        style = MaterialTheme.typography.labelSmall,
                        color = ErrorRed
                    )
                } else if (!job.isSynced) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Pending sync",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                job.address.ifBlank { "No address" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        if (job.estimatedValue > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.AttachMoney,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    String.format("%.2f est.", job.estimatedValue),
                    style = MaterialTheme.typography.labelMedium,
                    color = PrimaryGreen,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
