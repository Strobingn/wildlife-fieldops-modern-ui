package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.operations.AIFeatureScope
import com.strobingn.wildlifefieldops.ai.operations.RealAIFeature
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.AIOperationsViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.RealAIFeatureRunState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIOperationsScreen(
    onBack: () -> Unit,
    viewModel: AIOperationsViewModel = hiltViewModel()
) {
    val data by viewModel.dashboard.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val selectedJobId by viewModel.selectedJobId.collectAsState()
    val featureStates by viewModel.featureStates.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Operations", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard)) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = TextPrimary)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Live AI + offline operations intelligence", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Live tools call ${viewModel.aiProviderLabel}; offline analytics recalculate from Room job data.", color = TextSecondary)
                    }
                }
            }

            Section("25 Live AI Tools") {
                Text(
                    if (viewModel.aiConfigured) {
                        "Provider ready: ${viewModel.aiProviderLabel}. A successful result below came from the configured remote LLM."
                    } else {
                        "Provider not configured in this APK. The tools will report that condition instead of substituting heuristic text and calling it AI."
                    },
                    color = TextSecondary
                )

                if (jobs.isEmpty()) {
                    Note("Add or sync at least one real job before running the AI tools.")
                } else {
                    JobSelector(
                        jobs = jobs,
                        selectedJobId = selectedJobId,
                        onSelect = viewModel::selectJob
                    )
                    Text(
                        "20 tools use the selected job and its property history. 5 portfolio tools analyze the current job set.",
                        color = TextSecondary
                    )
                }

                viewModel.realAIFeatures.forEachIndexed { index, feature ->
                    LiveAIToolCard(
                        index = index,
                        feature = feature,
                        state = featureStates[feature.id] ?: RealAIFeatureRunState(),
                        enabled = jobs.isNotEmpty(),
                        onRun = { viewModel.runRealAIFeature(feature.id) },
                        onClear = { viewModel.clearRealAIResult(feature.id) }
                    )
                }
            }

            Section("Business Analytics") {
                Metric("Jobs", data.business.totalJobs.toString())
                Metric("Completed", data.business.completedJobs.toString())
                Metric("Close rate", "${data.business.closeRatePercent}%")
                Metric("Quoted", money(data.business.quotedRevenue))
                Metric("Actual cost", money(data.business.actualRevenue))
                Metric("Gross spread", money(data.business.grossVariance))
                Metric("Average ticket", money(data.business.averageTicket))
                Metric("Top service", data.business.topService)
                Note(data.business.recommendation)
            }

            Section("Property Intelligence") {
                if (data.properties.isEmpty()) Note("Add addresses to jobs to build property history.")
                data.properties.take(10).forEach { item ->
                    ItemTitle(item.address)
                    Text("${item.visitCount} visits · Repeat risk ${item.repeatRiskPercent}%", color = TextSecondary)
                    Text(item.serviceTypes.joinToString().ifBlank { "No service type" }, color = TextPrimary)
                    Text(item.recommendation, color = TextPrimary)
                    HorizontalDivider()
                }
            }

            Section("Offline Quality Control") {
                data.qualityChecks.take(10).forEach { item ->
                    ItemTitle("${item.score}/100 · ${item.title}")
                    Text(
                        if (item.missing.isEmpty()) "Complete" else "Missing: ${item.missing.joinToString()}",
                        color = TextSecondary
                    )
                    HorizontalDivider()
                }
            }

            Section("Pricing and Profit Analytics") {
                if (data.pricing.isEmpty()) Note("Add estimated and actual costs to unlock pricing analysis.")
                data.pricing.take(10).forEach { item ->
                    ItemTitle(item.title)
                    Text("Estimate ${money(item.estimated)} · Actual ${money(item.actual)} · Variance ${money(item.variance)}", color = TextSecondary)
                    Text(item.marginSignal, color = TextPrimary)
                    HorizontalDivider()
                }
            }

            Section("Route Priority Analytics") {
                data.routePriorities.take(10).forEach { item ->
                    ItemTitle("Score ${item.score} · ${item.title}")
                    Text(item.address.ifBlank { "Address missing" }, color = TextSecondary)
                    Text(item.reason, color = TextPrimary)
                    HorizontalDivider()
                }
            }

            Section("Inventory Forecast") {
                data.inventory.forEach { item ->
                    ItemTitle(item.item)
                    Text("Expected weekly use: ${item.expectedWeeklyUse} · Confidence ${item.confidencePercent}%", color = TextSecondary)
                    Text(item.reason, color = TextPrimary)
                    HorizontalDivider()
                }
            }

            Section("Species Behavior Reference") {
                data.speciesGuidance.forEach { item ->
                    ItemTitle(item.species)
                    Text(item.activityWindow, color = TextPrimary)
                    Text(item.fieldPriority, color = TextPrimary)
                    Text(item.exclusionNote, color = TextSecondary)
                    HorizontalDivider()
                }
            }

            Section("45 Offline Analytics Modules") {
                Text(
                    "These are deterministic operational signals calculated from local job records. They are intentionally not labeled as live LLM results.",
                    color = TextSecondary
                )
                data.advancedInsights.forEachIndexed { index, item ->
                    ItemTitle("${index + 1}. ${item.name} · ${item.score}/100")
                    Text(item.signal, color = TextPrimary)
                    Text(item.action, color = TextSecondary)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun JobSelector(
    jobs: List<Job>,
    selectedJobId: String?,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val ordered = remember(jobs) { jobs.sortedByDescending { it.updatedAt }.take(60) }
    val selected = jobs.firstOrNull { it.id == selectedJobId } ?: ordered.firstOrNull()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Selected job for job-specific AI", color = TextPrimary, fontWeight = FontWeight.SemiBold)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(selected?.title?.ifBlank { "Untitled job" } ?: "Select job", color = TextPrimary)
                    selected?.let {
                        Text(
                            listOf(it.customerName, it.address).filter { value -> value.isNotBlank() }.joinToString(" · ").ifBlank { it.type },
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                ordered.forEach { job ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(job.title.ifBlank { "Untitled job" }, color = TextPrimary)
                                Text(
                                    listOf(job.customerName, job.address).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { job.type },
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        },
                        onClick = {
                            onSelect(job.id)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveAIToolCard(
    index: Int,
    feature: RealAIFeature,
    state: RealAIFeatureRunState,
    enabled: Boolean,
    onRun: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundDark),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("${index + 1}. ${feature.title}", color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(
                if (feature.scope == AIFeatureScope.FOCUS_JOB) "Scope: selected job + same-property history" else "Scope: current portfolio",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium
            )
            Text(feature.description, color = TextSecondary)

            if (state.running) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("Calling live AI…", color = TextPrimary)
                }
            } else {
                Button(onClick = onRun, enabled = enabled) {
                    Text(if (state.result == null) "Run live AI" else "Run again")
                }
            }

            state.result?.let { result ->
                HorizontalDivider()
                Text(
                    if (result.success) "LIVE AI RESULT · ${result.provider}" else "AI NOT COMPLETED · ${result.provider}",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(result.output, color = TextSecondary)
                TextButton(onClick = onClear) { Text("Clear result") }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Card(
            colors = CardDefaults.cardColors(containerColor = BackgroundCard),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary)
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun ItemTitle(text: String) = Text(text, color = TextPrimary, fontWeight = FontWeight.Bold)
@Composable private fun Note(text: String) = Text(text, color = TextSecondary)
private fun money(value: Double): String = "$" + String.format(Locale.US, "%,.2f", value)
