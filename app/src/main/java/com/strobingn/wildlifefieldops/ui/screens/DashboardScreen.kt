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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.ui.components.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.DashboardViewModel
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
    onNavigateToCustomers: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToAI: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsState()
    val recentJobs by viewModel.recentJobs.collectAsState()
    val reminders by viewModel.pendingReminders.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "FieldOps",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.background,
                    titleContentColor = colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = onNavigateToAI) {
                        Icon(
                            Icons.Default.Psychology,
                            contentDescription = "AI Assistant",
                            tint = colorScheme.tertiary
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToJobForm,
                containerColor = colorScheme.primary,
                contentColor = colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Job", fontWeight = FontWeight.SemiBold) },
                shape = ShapePill,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        },
        containerColor = colorScheme.background
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                HeroCard(
                    title = "Wildlife FieldOps",
                    subtitle = "Ready for the field",
                    stats = listOf(
                        HeroStat("Active", stats.inProgressJobs),
                        HeroStat("Pending", stats.pendingJobs)
                    )
                )
            }

            // Stats row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Completed",
                        value = stats.completedJobs,
                        icon = Icons.Default.CheckCircle,
                        color = colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToJobs
                    )
                    StatCard(
                        title = "Revenue",
                        value = stats.totalRevenue.toInt(),
                        valuePrefix = "$",
                        icon = Icons.Default.AttachMoney,
                        color = colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        onClick = {}
                    )
                }
            }

            // Today's Overview
            item {
                OverviewCard(
                    title = "Today's Overview",
                    items = listOf(
                        OverviewItem("Jobs Today", stats.todayJobs.toString(), Icons.Default.Work, colorScheme.secondary),
                        OverviewItem("Overdue", stats.overdueJobs.toString(), Icons.Default.Warning, StatusUrgent),
                        OverviewItem("Customers", stats.totalCustomers.toString(), Icons.Default.People, colorScheme.tertiary),
                        OverviewItem("Inspections", stats.totalInspections.toString(), Icons.Default.Search, AccentCyan),
                        OverviewItem("Follow-ups", stats.followUpRequired.toString(), Icons.Default.FollowTheSigns, AccentOrange),
                        OverviewItem("Revenue", "$${String.format("%.0f", stats.totalRevenue)}", Icons.Default.AttachMoney, colorScheme.primary)
                    )
                )
            }

            // Quick Actions
            item {
                Text(
                    "Quick Actions",
                    style = MaterialTheme.typography.titleMedium,
                    color = colorScheme.onBackground,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionButton(
                        label = "New Job",
                        icon = Icons.Default.AddBox,
                        color = colorScheme.primary,
                        Modifier.weight(1f),
                        onNavigateToJobForm
                    )
                    QuickActionButton(
                        label = "Customers",
                        icon = Icons.Default.People,
                        color = colorScheme.tertiary,
                        Modifier.weight(1f),
                        onNavigateToCustomers
                    )
                    QuickActionButton(
                        label = "Map",
                        icon = Icons.Default.Map,
                        color = colorScheme.secondary,
                        Modifier.weight(1f),
                        onNavigateToMap
                    )
                    QuickActionButton(
                        label = "Schedule",
                        icon = Icons.Default.CalendarMonth,
                        color = AccentOrange,
                        Modifier.weight(1f),
                        onNavigateToSchedule
                    )
                }
            }

            // Recent Jobs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Jobs",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onBackground
                    )
                    TextButton(onClick = onNavigateToJobs) {
                        Text("View All", color = colorScheme.primary)
                    }
                }
            }

            if (recentJobs.isEmpty()) {
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
                        title = "No jobs yet",
                        subtitle = "Tap + to create your first job",
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

            // Pending Reminders
            if (reminders.isNotEmpty()) {
                item {
                    Text(
                        "Reminders",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onBackground,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(reminders) { reminder ->
                    ReminderCard(reminder = reminder)
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun HeroCard(
    title: String,
    subtitle: String,
    stats: List<HeroStat>
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = ShapeCard,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colorScheme.primary,
                            colorScheme.secondary
                        )
                    ),
                    shape = ShapeCard
                )
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Row(
                modifier = Modifier.align(Alignment.BottomStart),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                stats.forEach { stat ->
                    Column {
                        Text(
                            stat.value.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stat.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

private data class HeroStat(val label: String, val value: Int)

@Composable
private fun StatCard(
    title: String,
    value: Int,
    valuePrefix: String = "",
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .height(96.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = ShapeCardSmall,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                if (valuePrefix.isNotBlank()) {
                    Text(
                        valuePrefix,
                        style = MaterialTheme.typography.titleLarge,
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                AnimatedCounter(
                    target = value,
                    style = MaterialTheme.typography.headlineMedium,
                    durationMillis = 800
                )
            }
        }
    }
}

@Composable
private fun OverviewCard(title: String, items: List<OverviewItem>) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = ShapeCard,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                items.chunked(2).forEach { rowItems ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowItems.forEach { item ->
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(item.color.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(item.icon, contentDescription = null, tint = item.color, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                                    Text(
                                        item.value,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
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
private fun QuickActionButton(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(ShapeCardSmall)
            .background(colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
    }
}

@Composable
fun JobCard(job: Job, onClick: () -> Unit) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
                if (job.address.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            job.address,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
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
    }
}

@Composable
private fun ReminderCard(reminder: com.strobingn.wildlifefieldops.data.model.Reminder) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        shape = ShapeCardSmall,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorScheme.tertiary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = colorScheme.tertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(reminder.dueDate)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
