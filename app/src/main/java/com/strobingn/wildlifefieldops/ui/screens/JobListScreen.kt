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
import com.strobingn.wildlifefieldops.data.model.Reminder
import com.strobingn.wildlifefieldops.navigation.ManualJobEntry
import com.strobingn.wildlifefieldops.navigation.VoiceJobEntry
import com.strobingn.wildlifefieldops.ui.components.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel

data class JobListPreview(
    val jobs: List<Job>,
    val recentJobs: List<Job> = emptyList(),
    val searchQuery: String = "",
    val selectedStatus: JobStatus? = null,
    val openOnly: Boolean = false,
    val isLoading: Boolean = false,
    val scheduledCount: Int = 0,
    val inProgressCount: Int = 0,
    val completedCount: Int = 0,
    val reminders: List<Reminder> = emptyList(),
    val dueNextSteps: List<Job> = emptyList(),
    val weatherChips: Map<String, String> = emptyMap()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobListScreen(
    onNavigateToJobDetail: (String) -> Unit,
    onNavigateToJobForm: () -> Unit,
    onNavigateToDictate: () -> Unit = {},
    onImportFromText: () -> Unit = {},
    onBack: () -> Unit,
    showBack: Boolean = true,
    requestOpenJobs: Boolean = false,
    onOpenJobsFilterApplied: () -> Unit = {},
    preview: JobListPreview? = null
) {
    if (preview != null) {
        JobListContent(
            jobs = preview.jobs,
            recentJobs = preview.recentJobs,
            searchQuery = preview.searchQuery,
            selectedStatus = preview.selectedStatus,
            openOnly = preview.openOnly,
            isLoading = preview.isLoading,
            scheduledCount = preview.scheduledCount,
            inProgressCount = preview.inProgressCount,
            completedCount = preview.completedCount,
            reminders = preview.reminders,
            dueNextSteps = preview.dueNextSteps,
            weatherChips = preview.weatherChips,
            onSearch = {},
            onStatus = {},
            onNavigateToJobDetail = onNavigateToJobDetail,
            onNavigateToJobForm = onNavigateToJobForm,
            onNavigateToDictate = onNavigateToDictate,
            onImportFromText = onImportFromText,
            onBack = onBack,
            showBack = showBack
        )
        return
    }
    val viewModel: JobsViewModel = hiltViewModel()
    val jobs by viewModel.jobs.collectAsState()
    val recentJobs by viewModel.recentJobs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedStatus by viewModel.selectedStatus.collectAsState()
    val openOnly by viewModel.openOnly.collectAsState()
    LaunchedEffect(requestOpenJobs) {
        if (requestOpenJobs) {
            viewModel.showOpenJobs()
            onOpenJobsFilterApplied()
        }
    }
    val isLoading by viewModel.isLoading.collectAsState()
    val flagCounts by viewModel.flagCounts.collectAsState()
    val reminders by viewModel.pendingReminders.collectAsState()
    val dueNextSteps by viewModel.dueNextSteps.collectAsState()
    val weatherAlertsVm: com.strobingn.wildlifefieldops.ui.viewmodel.WeatherAlertsViewModel = hiltViewModel()
    LaunchedEffect(jobs) { weatherAlertsVm.trackJobs(jobs) }
    val jobAlerts by weatherAlertsVm.jobAlerts.collectAsState()
    val weatherChips = jobAlerts.mapValues { (_, alerts) ->
        com.strobingn.wildlifefieldops.weather.WeatherAlertEngine.chipText(alerts).orEmpty()
    }.filterValues { it.isNotBlank() }
    JobListContent(
        jobs = jobs,
        recentJobs = recentJobs,
        searchQuery = searchQuery,
        selectedStatus = selectedStatus,
        openOnly = openOnly,
        isLoading = isLoading,
        scheduledCount = flagCounts.scheduled,
        inProgressCount = flagCounts.inProgress,
        completedCount = flagCounts.completed,
        reminders = reminders,
        dueNextSteps = dueNextSteps,
        weatherChips = weatherChips,
        onSearch = viewModel::setSearchQuery,
        onStatus = viewModel::setStatusFilter,
        onNavigateToJobDetail = onNavigateToJobDetail,
        onNavigateToJobForm = onNavigateToJobForm,
        onNavigateToDictate = onNavigateToDictate,
        onImportFromText = onImportFromText,
        onBack = onBack,
        showBack = showBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JobListContent(
    jobs: List<Job>,
    recentJobs: List<Job>,
    searchQuery: String,
    selectedStatus: JobStatus?,
    openOnly: Boolean,
    isLoading: Boolean,
    scheduledCount: Int,
    inProgressCount: Int,
    completedCount: Int,
    reminders: List<Reminder>,
    dueNextSteps: List<Job>,
    weatherChips: Map<String, String> = emptyMap(),
    onSearch: (String) -> Unit,
    onStatus: (JobStatus?) -> Unit,
    onNavigateToJobDetail: (String) -> Unit,
    onNavigateToJobForm: () -> Unit,
    onNavigateToDictate: () -> Unit,
    onImportFromText: () -> Unit,
    onBack: () -> Unit,
    showBack: Boolean
) {
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "flags") {
                JobFlagSummaryRow(
                    scheduled = scheduledCount,
                    inProgress = inProgressCount,
                    completed = completedCount,
                    selected = selectedStatus,
                    openOnly = openOnly,
                    onSelect = onStatus
                )
            }
            item(key = "recent-header") {
                SectionHeader(
                    title = "Recent jobs",
                    actionLabel = "View all",
                    onAction = {
                        onStatus(null)
                        onSearch("")
                    }
                )
            }
            if (recentJobs.isEmpty()) {
                item(key = "recent-empty") {
                    EmptyState(
                        icon = {
                            Icon(
                                Icons.Default.WorkOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp)
                            )
                        },
                        title = "No jobs yet",
                        subtitle = "Tap ${ManualJobEntry.ACTION_LABEL} to create your first one",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                items(recentJobs, key = { "recent-${it.id}" }) { job ->
                    JobCard(
                        job = job,
                        onClick = { onNavigateToJobDetail(job.id) },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        weatherWarning = weatherChips[job.id]
                    )
                }
            }
            item(key = "search") {
                FieldSearchBar(
                    value = searchQuery,
                    onValueChange = onSearch,
                    placeholder = "Search jobs, customers, addresses…"
                )
            }
            item(key = "create") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
            ImportFromTextButton(onClick = onImportFromText, modifier = Modifier.fillMaxWidth())
            }
            }

            // Status filter chips
            item(key = "chips") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                item {
                    FilterChip(
                        selected = !openOnly && selectedStatus == null,
                        onClick = { onStatus(null) },
                        label = { Text("All") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                items(com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.stages) { status ->
                    val selected = if (openOnly) {
                        status == JobStatus.SCHEDULED || status == JobStatus.IN_PROGRESS
                    } else {
                        selectedStatus == status
                    }
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onStatus(if (selected) null else status)
                        },
                        label = { Text(com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.label(status)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
            }

            item(key = "count") {
                Text(
                    "${jobs.size} job${if (jobs.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (isLoading) {
                item(key = "loading") {
                    ListShimmer(modifier = Modifier.fillMaxWidth().height(240.dp))
                }
            } else if (jobs.isEmpty()) {
                item(key = "empty") {
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
                            onClick = { onNavigateToJobDetail(job.id) },
                            weatherWarning = weatherChips[job.id]
                        )
                    }
                }
            }

            if (dueNextSteps.isNotEmpty()) {
                item(key = "next-steps-header") {
                    SectionHeader(title = "Next steps due")
                }
                items(dueNextSteps, key = { "step-${it.id}" }) { job ->
                    DueNextStepCard(job = job, onClick = { onNavigateToJobDetail(job.id) })
                }
            }

            if (reminders.isNotEmpty()) {
                item(key = "reminders-header") {
                    SectionHeader(title = "Reminders")
                }
                items(reminders, key = { "rem-${it.id}" }) { reminder ->
                    ReminderCard(reminder = reminder)
                }
            }
        }
    }
}

/** Shared with the Inspections tab so a scheduled inspection looks exactly like a job. */
@Composable
internal fun JobListItem(job: Job, onClick: () -> Unit, weatherWarning: String? = null) {
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            JobDirectionsIconButton(job)
        }

        if (!weatherWarning.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            JobWeatherWarningChip(weatherWarning)
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
