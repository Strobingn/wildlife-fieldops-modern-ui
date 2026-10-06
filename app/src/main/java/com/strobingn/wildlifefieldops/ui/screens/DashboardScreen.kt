package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.HomeShellHeader
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
    val openJobs: List<Job> = emptyList(),
    val reminders: List<Reminder>,
    val dueNextSteps: List<Job>,
    val dueTrapChecks: List<TrapCheckItem>,
    val weather: WeatherUiState,
    val sync: SyncSnapshot,
    val greeting: String,
    val todayLabel: String,
    val isLoading: Boolean = false,
    val weatherAlerts: List<com.strobingn.wildlifefieldops.weather.WeatherAlert> = emptyList(),
    val weatherAlertsFetchedAt: Long? = null,
    val weatherAlertsExpanded: Boolean = false,
    /** Fixed clock for previews and screenshots; null uses the live clock. */
    val nowMillis: Long? = null
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
    onSeeAllOpenJobs: () -> Unit = {},
    onNavigateToSyncStatus: () -> Unit = {},
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
            onOpenDrawer = onOpenDrawer,
            onSeeAllOpenJobs = onSeeAllOpenJobs,
            onNavigateToSyncStatus = onNavigateToSyncStatus
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
    val openJobs by viewModel.openJobs.collectAsState()
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
    val weatherAlertsVm: com.strobingn.wildlifefieldops.ui.viewmodel.WeatherAlertsViewModel = hiltViewModel()
    val weatherHome by weatherAlertsVm.home.collectAsState()

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
            openJobs = openJobs,
            reminders = reminders,
            dueNextSteps = dueNextSteps,
            dueTrapChecks = dueTrapChecks,
            weather = weatherState,
            sync = SyncSnapshot(syncLabel, syncColor),
            greeting = greeting,
            todayLabel = todayLabel,
            isLoading = isLoading,
            weatherAlerts = weatherHome.alerts,
            weatherAlertsFetchedAt = weatherHome.fetchedAtMillis
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
        onOpenDrawer = onOpenDrawer,
        onSeeAllOpenJobs = onSeeAllOpenJobs,
        onNavigateToSyncStatus = onNavigateToSyncStatus
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
    onOpenDrawer: () -> Unit,
    onSeeAllOpenJobs: () -> Unit,
    onNavigateToSyncStatus: () -> Unit = {}
) {
    val stats = preview.stats
    val todayOnSchedule = preview.todayOnSchedule
    val openJobs = preview.openJobs
    val isLoading = preview.isLoading
    val weatherState = preview.weather
    val greeting = preview.greeting
    val todayLabel = preview.todayLabel
    val listState = rememberLazyListState()
    var createBarBottomPx by remember { mutableIntStateOf(Int.MAX_VALUE) }
    var listTopPx by remember { mutableIntStateOf(0) }
    // In-page New Job / Dictate stay under the header. Floating copies appear
    // only once that row has left the viewport, so they never cover Today.
    val showFloatingCreate = createBarBottomPx <= listTopPx

    Scaffold(
        floatingActionButton = {
            if (showFloatingCreate) {
            Column(
                modifier = Modifier.testTag("home-floating-create"),
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
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { listTopPx = it.boundsInWindow().top.roundToInt() },
            contentPadding = PaddingValues(
                start = FieldMetrics.screenPadding,
                top = FieldMetrics.screenPadding,
                end = FieldMetrics.screenPadding,
                bottom = HomeListBottomClearance
            ),
            verticalArrangement = Arrangement.spacedBy(FieldMetrics.space12)
        ) {
            item(key = "header") {
                HomeShellHeader(
                    sync = preview.sync,
                    greeting = greeting,
                    todayLabel = todayLabel,
                    onOpenDrawer = onOpenDrawer,
                    onOpenAssistant = onNavigateToAI,
                    onOpenSettings = onNavigateToSettings,
                    onOpenSyncStatus = onNavigateToSyncStatus
                )
            }
            item(key = "create-bar") {
                DisposableEffect(Unit) {
                    onDispose { createBarBottomPx = Int.MIN_VALUE }
                }
                HomeCreateBar(
                    modifier = Modifier
                        .testTag("home-create-bar")
                        .onGloballyPositioned {
                            createBarBottomPx = it.boundsInWindow().bottom.roundToInt()
                        },
                    onNewJob = onNavigateToJobForm,
                    onDictate = onNavigateToDictate
                )
            }

            if (preview.weatherAlerts.isNotEmpty()) {
                item(key = "weather-alerts") {
                    WeatherAlertsCard(
                        alerts = preview.weatherAlerts,
                        fetchedAtMillis = preview.weatherAlertsFetchedAt,
                        startExpanded = preview.weatherAlertsExpanded
                    )
                }
            }

            item(key = "today") {
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


            item(key = "today-jobs") {
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
            if (preview.dueTrapChecks.isNotEmpty()) {
                item(key = "trap-checks") {
                    HomeTrapChecksCard(
                        items = preview.dueTrapChecks,
                        now = rememberMinuteTicker(preview.nowMillis),
                        onOpen = onNavigateToTrapChecks
                    )
                }
            }
            item(key = "weather") {
                WeatherBanner(
                    state = weatherState,
                    title = "Shop weather",
                    onRefresh = onRefreshWeather,
                    compact = true
                )
                if (showUpdateChip) AppUpdateHomeChip()
            }
            item(key = "open-jobs") {
                OpenJobsHeader(total = openJobs.size)
            }
            val shownOpenJobs = openJobs.take(OpenHomeJobs.HOME_CAP)
            if (shownOpenJobs.isEmpty()) {
                item(key = "open-jobs-empty") {
                    Text(
                        "No open jobs",
                        modifier = Modifier.testTag("home-open-jobs"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(shownOpenJobs, key = { "open-${it.id}" }) { job ->
                    OpenJobRow(
                        job = job,
                        onClick = { onNavigateToJobDetail(job.id) }
                    )
                }
            }
            val seeAll = OpenHomeJobs.homeSlice(openJobs).seeAllLabel
            if (seeAll != null) {
                item(key = "open-jobs-see-all") {
                    TextButton(
                        onClick = onSeeAllOpenJobs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("home-open-jobs-see-all"),
                        contentPadding = PaddingValues(horizontal = 0.dp)
                    ) {
                        Text(
                            seeAll,
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenJobsHeader(total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Open jobs",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            total.toString(),
            modifier = Modifier.testTag("home-open-jobs-count"),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun OpenJobRow(job: Job, onClick: () -> Unit) {
    val whenLabel = job.scheduledDate?.let { at ->
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(at))
    } ?: "No time set"
    FieldCard(
        onClick = onClick,
        accentColor = jobStatusColor(job.status),
        modifier = Modifier.testTag("home-open-job"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                job.customerName.ifBlank { job.title }.ifBlank { "Open job" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(8.dp))
            StatusChip(
                text = com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline.label(job.status),
                color = jobStatusColor(job.status)
            )
            JobDirectionsIconButton(job)
        }
        if (job.address.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                job.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            whenLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** Clears the stacked New Job and Dictate job buttons so the last Open jobs row stays visible. */
internal val HomeListBottomClearance = 240.dp

@Composable
private fun HomeCreateBar(
    onNewJob: () -> Unit,
    onDictate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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

@Composable
fun JobCard(
    job: Job,
    onClick: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    weatherWarning: String? = null
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
        if (!weatherWarning.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            JobWeatherWarningChip(weatherWarning)
        }
    }
}

@Composable
internal fun DueNextStepCard(job: Job, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
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

@Composable
internal fun ReminderCard(reminder: com.strobingn.wildlifefieldops.data.model.Reminder) {
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
