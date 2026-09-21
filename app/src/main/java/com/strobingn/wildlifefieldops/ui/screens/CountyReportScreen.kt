package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.report.CountyBucket
import com.strobingn.wildlifefieldops.data.report.CountyDashboard
import com.strobingn.wildlifefieldops.data.report.ReportWindow
import com.strobingn.wildlifefieldops.ui.components.EmptyState
import com.strobingn.wildlifefieldops.ui.components.FieldCard
import com.strobingn.wildlifefieldops.ui.components.FieldTopBar
import com.strobingn.wildlifefieldops.ui.components.LoadingIndicator
import com.strobingn.wildlifefieldops.ui.components.MetricTile
import com.strobingn.wildlifefieldops.ui.components.SectionHeader
import com.strobingn.wildlifefieldops.ui.theme.AccentBlue
import com.strobingn.wildlifefieldops.ui.theme.AccentCyan
import com.strobingn.wildlifefieldops.ui.theme.FieldShapes
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.StatusPending
import com.strobingn.wildlifefieldops.ui.theme.SuccessGreen
import com.strobingn.wildlifefieldops.ui.viewmodel.CountyReportViewModel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CountyReportScreen(
    onBack: () -> Unit,
    viewModel: CountyReportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val dash = state.dashboard

    Scaffold(
        topBar = { FieldTopBar(title = "County reports", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.isLoading) {
            LoadingIndicator()
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Observation events and completed jobs, rolled up by county from capture coordinates. Species comes from the event log — not mutable job fields.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReportWindow.entries.forEach { window ->
                        FilterChip(
                            selected = state.window == window,
                            onClick = { viewModel.setWindow(window) },
                            label = { Text(window.label) },
                            shape = FieldShapes.chip,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }

            if (dash.isEmpty) {
                item {
                    EmptyState(
                        icon = {
                            Icon(
                                Icons.Default.Assessment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp),
                            )
                        },
                        title = "No county activity in this window",
                        subtitle = "Log a field observation on the map or complete a job with GPS. This screen never invents counts.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                return@LazyColumn
            }

            item {
                DashboardTotals(dash)
            }

            if (dash.unlocatedObservationCount > 0) {
                item {
                    Text(
                        "${dash.unlocatedObservationCount} observation${if (dash.unlocatedObservationCount == 1) "" else "s"} could not be placed in a county (missing GPS or outside the NY service map).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SectionHeader(title = "By county")
            }

            itemsIndexed(dash.counties, key = { _, bucket -> bucket.county.key }) { _, bucket ->
                CountyBucketCard(bucket)
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DashboardTotals(dash: CountyDashboard) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = FieldShapes.cardLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "This window",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricTile(
                    label = "Observations",
                    value = dash.observationCount.toString(),
                    icon = Icons.Default.Place,
                    color = AccentBlue,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "Completed jobs",
                    value = dash.completedJobCount.toString(),
                    icon = Icons.Default.Work,
                    color = SuccessGreen,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricTile(
                    label = "Counties",
                    value = dash.counties.count { !it.county.isUnlocated }.toString(),
                    icon = Icons.Default.Assessment,
                    color = PrimaryGreen,
                    modifier = Modifier.weight(1f),
                )
                MetricTile(
                    label = "Unlabeled species",
                    value = dash.unlabeledObservationCount.toString(),
                    icon = Icons.Default.Pets,
                    color = StatusPending,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CountyBucketCard(bucket: CountyBucket) {
    FieldCard {
        Text(
            bucket.county.displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        if (bucket.county.state.isNotBlank()) {
            Text(
                bucket.county.state,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricTile(
                label = "Observations",
                value = bucket.observationCount.toString(),
                icon = Icons.Default.Place,
                color = AccentBlue,
                modifier = Modifier.weight(1f),
            )
            MetricTile(
                label = "Jobs done",
                value = bucket.completedJobCount.toString(),
                icon = Icons.Default.Work,
                color = SuccessGreen,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Schedule,
                contentDescription = null,
                tint = AccentCyan,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                responseTimeLabel(bucket),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (bucket.speciesBreakdown.isEmpty()) {
            if (bucket.observationCount > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "No species recorded on these observations.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Species",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            bucket.speciesBreakdown.forEach { row ->
                BreakdownRow(
                    label = row.label.replaceFirstChar { ch ->
                        if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
                    },
                    count = row.count,
                    total = bucket.observationCount,
                )
            }
            if (bucket.unlabeledObservationCount > 0) {
                BreakdownRow(
                    label = "Not recorded",
                    count = bucket.unlabeledObservationCount,
                    total = bucket.observationCount,
                )
            }
        }

        if (bucket.repeatSites.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Repeat sites",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            bucket.repeatSites.take(5).forEach { site ->
                BreakdownRow(
                    label = site.siteKey,
                    count = site.observationCount,
                    total = bucket.observationCount,
                )
            }
        }

        if (bucket.monthlyTrend.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Monthly observations",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                bucket.monthlyTrend.joinToString("  ·  ") { "${it.yearMonth} (${it.observationCount})" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BreakdownRow(label: String, count: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (total > 0) "$count" else count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun responseTimeLabel(bucket: CountyBucket): String {
    val median = bucket.medianResponseHours
    return when {
        median == null -> "Response time unavailable — no completion timestamps in this county"
        bucket.timedCompletionCount == 1 ->
            "Response time ${formatHours(median)} (1 timed job)"
        else ->
            "Median response ${formatHours(median)} (${bucket.timedCompletionCount} timed jobs)"
    }
}

private fun formatHours(hours: Double): String =
    if (hours < 24.0) {
        "${hours.roundToInt()} h"
    } else {
        val days = hours / 24.0
        String.format(Locale.US, "%.1f days", days)
    }
