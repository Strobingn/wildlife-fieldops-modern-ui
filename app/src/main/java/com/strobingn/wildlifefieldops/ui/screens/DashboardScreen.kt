package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.navigation.ManualJobEntry
import com.strobingn.wildlifefieldops.navigation.VoiceJobEntry
import com.strobingn.wildlifefieldops.ui.components.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.DashboardViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.LiveWeatherViewModel
import com.strobingn.wildlifefieldops.ui.components.WeatherBanner
import java.text.SimpleDateFormat
import java.util.*

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
    onOpenDrawer: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsState()
    val recentJobs by viewModel.recentJobs.collectAsState()
    val reminders by viewModel.pendingReminders.collectAsState()
    val dueNextSteps by viewModel.dueNextSteps.collectAsState()
    val dueTrapChecks by viewModel.dueTrapChecks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
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
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                WeatherBanner(
                    state = weatherState,
                    title = "Shop weather",
                    onRefresh = { weatherVm.loadShopWeather() }
                )
                AppUpdateHomeChip()
            }

            // ── Hero header ───────────────────────────────────────────────
            item {
                Spacer(modifier = Modifier.height(4.dp))
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
                                contentDescription = "Open menu",
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

            // ── Today strip ───────────────────────────────────────────────
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FieldShapes.hero,
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(GradientStart, GradientMid, PrimaryContainer)
                                ),
                                FieldShapes.hero
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Today",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = OnPrimary.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${stats.todayJobs} jobs scheduled",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = OnPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                if (stats.overdueJobs > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    StatusChip(
                                        text = "${stats.overdueJobs} overdue",
                                        color = StatusUrgent
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = onNavigateToSchedule,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = OnPrimary.copy(alpha = 0.18f),
                                    contentColor = OnPrimary
                                ),
                                shape = FieldShapes.button
                            ) {
                                Text("Schedule")
                            }
                        }
                    }
                }
            }

            // ── Stats ─────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ScaleIn(delayMillis = 0) {
                        StatPillCard(
                            title = "Active",
                            value = stats.inProgressJobs,
                            icon = Icons.Default.PlayCircle,
                            color = AccentBlue,
                            modifier = Modifier.weight(1f),
                            onClick = onNavigateToJobs
                        )
                    }
                    ScaleIn(delayMillis = 80) {
                        StatPillCard(
                            title = "Pending",
                            value = stats.pendingJobs,
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
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ScaleIn(delayMillis = 160) {
                        StatPillCard(
                            title = "Done",
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
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            listOf(
                                OverviewItem("Customers", stats.totalCustomers.toString(), Icons.Default.People, AccentPurple),
                                OverviewItem("Inspections", stats.totalInspections.toString(), Icons.Default.Search, AccentCyan),
                                OverviewItem("Follow-ups", stats.followUpRequired.toString(), Icons.Default.FollowTheSigns, AccentOrange),
                                OverviewItem("Overdue", stats.overdueJobs.toString(), Icons.Default.Warning, StatusUrgent)
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
                                }
                            }
                        }
                    }
                }
            }

            // ── Quick actions ─────────────────────────────────────────────
            item {
                SectionHeader(title = "Quick actions")
                Spacer(modifier = Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile(ManualJobEntry.ACTION_LABEL, Icons.Default.AddBox, PrimaryGreen, Modifier.weight(1f), onNavigateToJobForm)
                        QuickActionTile(VoiceJobEntry.ACTION_LABEL, Icons.Default.Mic, PrimaryGreen, Modifier.weight(1f), onNavigateToDictate)
                        QuickActionTile("Schedule", Icons.Default.CalendarMonth, AccentPurple, Modifier.weight(1f), onNavigateToSchedule)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile("Map", Icons.Default.Map, AccentBlue, Modifier.weight(1f), onNavigateToMap)
                        QuickActionTile("Inspect", Icons.Default.Search, AccentCyan, Modifier.weight(1f), onNavigateToInspections)
                        QuickActionTile("Routes", Icons.Default.Route, AccentBlue, Modifier.weight(1f), onNavigateToRoutes)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickActionTile("Reports", Icons.Default.Assessment, AccentBlue, Modifier.weight(1f), onNavigateToCountyReports)
                        Spacer(modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.weight(1f))
                    }
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
                            onClick = { onNavigateToJobDetail(job.id) }
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
                        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
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
                                item.dueState.name.replace('_', ' '),
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
                        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
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

private data class OverviewItem(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun JobCard(job: Job, onClick: () -> Unit) {
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
                text = job.status.name.replace("_", " "),
                color = statusColor
            )
        }
    }
}

@Composable
private fun ReminderCard(reminder: com.strobingn.wildlifefieldops.data.model.Reminder) {
    FieldCard {
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
