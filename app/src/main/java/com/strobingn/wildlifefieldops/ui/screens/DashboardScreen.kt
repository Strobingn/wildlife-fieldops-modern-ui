package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.MoreShellHeader
import com.strobingn.wildlifefieldops.SyncSnapshot
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.model.Reminder
import com.strobingn.wildlifefieldops.navigation.ManualJobEntry
import com.strobingn.wildlifefieldops.navigation.VoiceJobEntry
import com.strobingn.wildlifefieldops.ui.components.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.DashboardStats
import com.strobingn.wildlifefieldops.ui.viewmodel.DashboardViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.LiveWeatherViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.SettingsViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.WeatherUiState
import com.strobingn.wildlifefieldops.ui.components.WeatherBanner
import java.text.SimpleDateFormat
import java.util.*

data class DashboardPreview(
    val stats: DashboardStats,
    val recentJobs: List<Job>,
    val todayOnSchedule: List<Job>,
    val reminders: List<Reminder>,
    val dueNextSteps: List<Job>,
    val dueTrapChecks: List<TrapCheckItem>,
    val weather: WeatherUiState,
    val sync: SyncSnapshot,
    val greeting: String,
    val todayLabel: String,
    val isLoading: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToJobs: () -> Unit,
    onNavigateToInspections: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToJobDetail: (String) -> Unit,
    onNavigateToJobForm: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onNavigateToCountyReports: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
    onNavigateToAI: () -> Unit,
    onNavigateToTrapChecks: () -> Unit = {},
    onNavigateToDictate: () -> Unit = {},
    onNavigateToTodayRoute: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    preview: DashboardPreview? = null
) {
    if (preview != null) {
        HomeDashboard(
            preview = preview,
            showUpdateChip = false,
            onRefreshWeather = {},
            onNavigateToJobs = onNavigateToJobs,
            onNavigateToInspections = onNavigateToInspections,
            onNavigateToSchedule = onNavigateToSchedule,
            onNavigateToJobDetail = onNavigateToJobDetail,
            onNavigateToJobForm = onNavigateToJobForm,
            onNavigateToMap = onNavigateToMap,
            onNavigateToRoutes = onNavigateToRoutes,
            onNavigateToCountyReports = onNavigateToCountyReports,
            onNavigateToSettings = onNavigateToSettings,
            onNavigateToAI = onNavigateToAI,
            onNavigateToTrapChecks = onNavigateToTrapChecks,
            onNavigateToDictate = onNavigateToDictate,
            onNavigateToTodayRoute = onNavigateToTodayRoute,
            onOpenDrawer = onOpenDrawer
        )
        return
    }
    val viewModel: DashboardViewModel = hiltViewModel()
    val stats by viewModel.stats.collectAsState()
    val recentJobs by viewModel.recentJobs.collectAsState()
    val reminders by viewModel.pendingReminders.collectAsState()
    val dueNextSteps by viewModel.dueNextSteps.collectAsState()
    val dueTrapChecks by viewModel.dueTrapChecks.collectAsState()
    val todayOnSchedule by viewModel.todayOnSchedule.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val syncVm: SettingsViewModel = hiltViewModel()
    val backlog by syncVm.backlog.collectAsState()
    val lastOk by syncVm.lastSyncOk.collectAsState()
    val isSyncing by syncVm.isSyncing.collectAsState()
    LaunchedEffect(Unit) { syncVm.refreshBacklog() }
    val pendingSync = backlog?.pendingTotal ?: 0
    val syncFailed = backlog?.hasFailures == true || lastOk == false
    val syncLabel = when {
        isSyncing -> "Syncing…"
        syncFailed -> "Sync failed"
        pendingSync > 0 -> "Pending sync · $pendingSync"
        else -> "Synced"
    }
    val syncColor = when {
        syncFailed -> ErrorRed
        pendingSync > 0 -> TextTertiary
        else -> TextSecondary
    }
    val weatherVm: LiveWeatherViewModel = hiltViewModel()
    val weatherState by weatherVm.state.collectAsState()
    LaunchedEffect(Unit) { weatherVm.loadShopWeather() }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val todayLabel = remember {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date())
    }
    HomeDashboard(
        preview = DashboardPreview(
            stats = stats,
            recentJobs = recentJobs,
            todayOnSchedule = todayOnSchedule,
            reminders = reminders,
            dueNextSteps = dueNextSteps,
            dueTrapChecks = dueTrapChecks,
            weather = weatherState,
            sync = SyncSnapshot(syncLabel, syncColor),
            greeting = greeting,
            todayLabel = todayLabel,
            isLoading = isLoading
        ),
        showUpdateChip = true,
        onRefreshWeather = { weatherVm.loadShopWeather() },
        onNavigateToJobs = onNavigateToJobs,
        onNavigateToInspections = onNavigateToInspections,
        onNavigateToSchedule = onNavigateToSchedule,
        onNavigateToJobDetail = onNavigateToJobDetail,
        onNavigateToJobForm = onNavigateToJobForm,
        onNavigateToMap = onNavigateToMap,
        onNavigateToRoutes = onNavigateToRoutes,
        onNavigateToCountyReports = onNavigateToCountyReports,
        onNavigateToSettings = onNavigateToSettings,
        onNavigateToAI = onNavigateToAI,
        onNavigateToTrapChecks = onNavigateToTrapChecks,
        onNavigateToDictate = onNavigateToDictate,
        onNavigateToTodayRoute = onNavigateToTodayRoute,
        onOpenDrawer = onOpenDrawer
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeDashboard(
    preview: DashboardPreview,
    showUpdateChip: Boolean,
    onRefreshWeather: () -> Unit,
    onNavigateToJobs: () -> Unit,
    onNavigateToInspections: () -> Unit,
    onNavigateToSchedule: () -> Unit,
    onNavigateToJobDetail: (String) -> Unit,
    onNavigateToJobForm: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToRoutes: () -> Unit,
    onNavigateToCountyReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAI: () -> Unit,
    onNavigateToTrapChecks: () -> Unit,
    onNavigateToDictate: () -> Unit,
    onNavigateToTodayRoute: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val stats = preview.stats
    val recentJobs = preview.recentJobs
    val reminders = preview.reminders
    val dueNextSteps = preview.dueNextSteps
    val dueTrapChecks = preview.dueTrapChecks
    val todayOnSchedule = preview.todayOnSchedule
    val isLoading = preview.isLoading
    val syncLabel = preview.sync.label
    val syncColor = preview.sync.color
    val weatherState = preview.weather
    val greeting = preview.greeting
    val todayLabel = preview.todayLabel

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToDictate,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = FieldShapes.fab,
                    icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                    text = {
                        Text(VoiceJobEntry.ACTION_LABEL, fontWeight = FontWeight.SemiBold)
                    }
                )
                ExtendedFloatingActionButton(
                    onClick = onNavigateToJobForm,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = FieldShapes.fab,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = {
                        Text(ManualJobEntry.ACTION_LABEL, fontWeight = FontWeight.SemiBold)
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (isLoading) {
            DashboardShimmer(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(FieldMetrics.screenPadding),
            verticalArrangement = Arrangement.spacedBy(FieldMetrics.space12)
        ) {
            item {
                MoreShellHeader(preview.sync)
            }
            item {
                HomeCreateBar(
                    onNewJob = onNavigateToJobForm,
                    onDictate = onNavigateToDictate
                )
            }
            item {
                WeatherBanner(
                    state = weatherState,
                    title = "Shop weather",
                    onRefresh = onRefreshWeather
                )
                if (showUpdateChip) AppUpdateHomeChip()
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "More",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Column {
                            Text(
                                greeting,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "FieldOps",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                todayLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row {
                        IconButton(onClick = onNavigateToAI) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(AccentPurple.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Psychology,
                                    contentDescription = "AI Assistant",
                                    tint = AccentPurple,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShapes.hero,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FieldMetrics.space16),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Today",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${stats.todayJobs} jobs scheduled",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(FieldMetrics.space8)) {
                            FilledTonalButton(
                                onClick = onNavigateToSchedule,
                                modifier = Modifier.height(48.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = FieldShapes.button
                            ) {
                                Text("Schedule", maxLines = 1)
                            }
                            FilledTonalButton(
                                onClick = onNavigateToTodayRoute,
                                modifier = Modifier.height(48.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = FieldShapes.button
                            ) {
                                Text("Today's route", maxLines = 1)
                            }
                        }
                    }
                }
            }

            // ── Stats ─────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FieldMetrics.space12)
                ) {
                    ScaleIn(delayMillis = 0) {
                        StatPillCard(
                            title = "In progress",
                            value = stats.inProgressJobs,
                            icon = Icons.Default.PlayCircle,
                            color = AccentBlue,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToJobs
                        )
                    }
                    ScaleIn(delayMillis = 80) {
                        StatPillCard(
                            title = "Scheduled",
                            value = stats.scheduledJobs,
                            icon = Icons.Default.Schedule,
                            color = StatusPending,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToJobs
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(FieldMetrics.space12)
                ) {
                    ScaleIn(delayMillis = 160) {
                        StatPillCard(
                            title = "Completed",
                            value = stats.completedJobs,
                            icon = Icons.Default.CheckCircle,
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToJobs
                        )
                    }
                    ScaleIn(delayMillis = 240) {
                        StatPillCard(
                            title = "Inspections",
                            value = stats.totalInspections,
                            icon = Icons.Default.Search,
                            color = AccentCyan,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToInspections
                        )
                    }
                }
            }

            // ── Overview grid ─────────────────────────────────────────────
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShapes.cardLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "At a glance",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(FieldMetrics.space8))
                        Text(
                            syncLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = syncColor,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(FieldMetrics.space12))
                        Column(verticalArrangement = Arrangement.spacedBy(FieldMetrics.space12)) {
                            listOf(
                                OverviewItem("Customers", stats.totalCustomers.toString(), Icons.Default.People, AccentPurple),
                                OverviewItem("Inspections", stats.totalInspections.toString(), Icons.Default.Search, AccentCyan),
                                OverviewItem("Follow-ups", stats.followUpRequired.toString(), Icons.Default.FollowTheSigns, AccentOrange)
                            ).chunked(2).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    row.forEach { item ->
                                        MetricTile(
                                            label = item.label,
                                            value = item.value,
                                            icon = item.icon,
                                            color = item.color,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            // ── Quick actions ─────────────────────────────────────────────
            item {
                SectionHeader(title = "Quick actions")
                Column(verticalArrangement = Arrangement.spacedBy(FieldMetrics.space12)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FieldMetrics.space12)
                    ) {
                        QuickActionTile(ManualJobEntry.ACTION_LABEL, Icons.Default.AddBox, PrimaryGreen, Modifier.weight(1f), onNavigateToJobForm)
                        QuickActionTile(VoiceJobEntry.ACTION_LABEL, Icons.Default.Mic, PrimaryGreen, Modifier.weight(1f), onNavigateToDictate)
                        QuickActionTile("Schedule", Icons.Default.CalendarMonth, AccentPurple, Modifier.weight(1f), onNavigateToSchedule)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FieldMetrics.space12)
                    ) {
                        QuickActionTile("Map", Icons.Default.Map, AccentBlue, Modifier.weight(1f), onNavigateToMap)
                        QuickActionTile("Inspect", Icons.Default.Search, AccentCyan, Modifier.weight(1f), onNavigateToInspections)
                        QuickActionTile("Routes", Icons.Default.Route, AccentBlue, Modifier.weight(1f), onNavigateToRoutes)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(FieldMetrics.space12)
                    ) {
                        QuickActionTile("Reports", Icons.Default.Assessment, AccentBlue, Modifier.weight(1f), onNavigateToCountyReports)
                        Spacer(modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Today's jobs",
                    actionLabel = "Schedule",
                    onAction = onNavigateToSchedule
                )
            }
            if (todayOnSchedule.isEmpty()) {
                item {
                    Text(
                        "Nothing scheduled today.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(todayOnSchedule, key = { "today-${it.id}" }) { job ->
                    JobCard(
                        job = job,
                        onClick = { onNavigateToJobDetail(job.id) },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // ── Recent jobs ───────────────────────────────────────────────
            item {
                SectionHeader(
                    title = "Recent jobs",
                    actionLabel = "View all",
                    onAction = onNavigateToJobs
                )
            }

            if (recentJobs.isEmpty()) {
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
                        title = "No jobs yet",
                        subtitle = "Tap ${ManualJobEntry.ACTION_LABEL} to create your first one",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                itemsIndexed(recentJobs) { index, job ->
                    FadeSlideIn(index = index) {
                        JobCard(
                            job = job,
                            onClick = { onNavigateToJobDetail(job.id) },
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (dueTrapChecks.isNotEmpty()) {
                item {
                    SectionHeader(title = "Trap checks due")
                }
                items(dueTrapChecks) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.trap.jobId.isNotBlank()) onNavigateToJobDetail(item.trap.jobId)
                                else onNavigateToTrapChecks()
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = FieldShapes.card
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(
                                item.trap.trapId.ifBlank { "Trap" } + " · " + item.trap.status.name.replace('_', ' '),
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (item.jobTitle.isNotBlank()) {
                                Text(item.jobTitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            } else if (item.trap.trapLocation.isNotBlank()) {
                                Text(item.trap.trapLocation, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckPlanner.dueLabel(item.dueState),
                                color = TextTertiary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            if (dueNextSteps.isNotEmpty()) {
                item {
                    SectionHeader(title = "Next steps due")
                }
                items(dueNextSteps) { job ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToJobDetail(job.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        shape = FieldShapes.card
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Text(job.title.ifBlank { job.customerName }, color = TextPrimary, fontWeight = FontWeight.SemiBold)
                            Text(job.nextStep, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                            job.nextStepDueAt?.let { due ->
                                Text(
                                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(due)),
                                    color = TextTertiary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            if (reminders.isNotEmpty()) {
                item {
                    SectionHeader(title = "Reminders")
                }
                items(reminders) { reminder ->
                    ReminderCard(reminder = reminder)
                }
            }

            item { Spacer(modifier = Modifier.height(168.dp)) }
        }
    }
}

@Composable
private fun HomeCreateBar(onNewJob: () -> Unit, onDictate: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FieldMetrics.space12)
    ) {
        Button(
            onClick = onNewJob,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = FieldShapes.button,
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(ManualJobEntry.ACTION_LABEL, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Button(
            onClick = onDictate,
            modifier = Modifier.weight(1f).height(56.dp),
            shape = FieldShapes.button,
            contentPadding = PaddingValues(horizontal = 12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(Icons.Default.Mic, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(VoiceJobEntry.ACTION_LABEL, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private data class OverviewItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun JobCard(
    job: Job,
    onClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(16.dp)
) {
    val statusColor = jobStatusColor(job.status)

    FieldCard(
        onClick = onClick,
        accentColor = statusColor,
        contentPadding = contentPadding
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
                if (job.address.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            job.address,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            StatusChip(
                text = com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.label(job.status),
                color = statusColor
            )
        }
    }
}

@Composable
private fun ReminderCard(reminder: com.strobingn.wildlifefieldops.data.model.Reminder) {
    FieldCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentOrange.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = AccentOrange,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(reminder.dueDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
