package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.operations.AIOperationsEngine
import com.strobingn.wildlifefieldops.ai.operations.IndividualAIToolCatalog
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.AIOperationsViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIOperationsScreen(
    onBack: () -> Unit,
    viewModel: AIOperationsViewModel = hiltViewModel()
) {
    val data by viewModel.dashboard.collectAsState()
    var selectedToolId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTool = resolveTool(selectedToolId, data)
    if (selectedTool != null) {
        IndividualAIToolScreen(
            tool = selectedTool,
            dashboard = data,
            onBack = { selectedToolId = null }
        )
        return
    }
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
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = AccentPurple.copy(alpha = .15f))) {
                Row(Modifier.padding(16.dp)) {
                    Icon(Icons.Default.AutoAwesome, null, tint = AccentPurple)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Live intelligence from your real jobs", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Tap a tool. Each one opens its own analysis, not a dummy tab.", color = TextSecondary)
                    }
                }
            }

            Text("20 Individual AI Tools", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("Each card opens a live job-data tool with score, recommended action, and a field checklist.", color = TextSecondary)
            IndividualAIToolCatalog.tools.forEachIndexed { index, tool ->
                LaunchableToolCard(index = index + 1, title = tool.title, subtitle = tool.purpose) {
                    selectedToolId = tool.id
                }
            }

            Section("Business AI") {
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
                    Text(item.serviceTypes.joinToString().ifBlank { "No service type" }, color = PrimaryGreen)
                    Text(item.recommendation, color = TextPrimary)
                    HorizontalDivider()
                }
            }
            Section("AI Quality Control") {
                data.qualityChecks.take(10).forEach { item ->
                    ItemTitle("${item.score}/100 · ${item.title}")
                    Text(if (item.missing.isEmpty()) "Complete" else "Missing: ${item.missing.joinToString()}", color = if (item.missing.isEmpty()) PrimaryGreen else MaterialTheme.colorScheme.tertiary)
                    HorizontalDivider()
                }
            }
            Section("Pricing and Profit") {
                if (data.pricing.isEmpty()) Note("Add estimated and actual costs to unlock pricing analysis.")
                data.pricing.take(10).forEach { item ->
                    ItemTitle(item.title)
                    Text("Estimate ${money(item.estimated)} · Actual ${money(item.actual)} · Variance ${money(item.variance)}", color = TextSecondary)
                    Text(item.marginSignal, color = PrimaryGreen)
                    HorizontalDivider()
                }
            }
            Section("Route Priority") {
                data.routePriorities.take(10).forEach { item ->
                    ItemTitle("Score ${item.score} · ${item.title}")
                    Text(item.address.ifBlank { "Address missing" }, color = TextSecondary)
                    Text(item.reason, color = PrimaryGreen)
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
            Section("Species Behavior Engine") {
                data.speciesGuidance.forEach { item ->
                    ItemTitle(item.species)
                    Text(item.activityWindow, color = PrimaryGreen)
                    Text(item.fieldPriority, color = TextPrimary)
                    Text(item.exclusionNote, color = TextSecondary)
                    HorizontalDivider()
                }
            }

            Text("65 Live Intelligence Modules", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("Same rule as the 20 tools: tap any module to open it. Nothing here is a placeholder tab.", color = TextSecondary)
            data.advancedInsights.forEachIndexed { index, item ->
                LaunchableToolCard(index = index + 1, title = "${item.name} · ${item.score}/100", subtitle = item.signal) {
                    selectedToolId = "insight:${item.name}"
                }
            }
        }
    }
}

private fun resolveTool(selectedToolId: String?, dashboard: AIOperationsEngine.Dashboard): IndividualAIToolCatalog.Tool? {
    if (selectedToolId.isNullOrBlank()) return null
    IndividualAIToolCatalog.tools.firstOrNull { it.id == selectedToolId }?.let { return it }
    val insightName = selectedToolId.removePrefix("insight:")
    IndividualAIToolCatalog.tools.firstOrNull { it.insightName == insightName }?.let { return it }
    val insight = dashboard.advancedInsights.firstOrNull { it.name == insightName } ?: return null
    return IndividualAIToolCatalog.Tool(
        id = "insight:${insight.name}",
        title = insight.name,
        purpose = insight.signal,
        insightName = insight.name,
        checklist = listOf(insight.action, "Open the matching job records", "Record the outcome in notes before leaving the property")
    )
}

@Composable
private fun LaunchableToolCard(index: Int, title: String, subtitle: String, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Surface(color = SurfaceBright, shape = RoundedCornerShape(10.dp)) {
                Text("$index", modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(subtitle, color = TextSecondary)
                Spacer(Modifier.height(4.dp))
                Text("Open tool", color = PrimaryGreen, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndividualAIToolScreen(tool: IndividualAIToolCatalog.Tool, dashboard: AIOperationsEngine.Dashboard, onBack: () -> Unit) {
    val insight = dashboard.advancedInsights.firstOrNull { it.name == tool.insightName }
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable(tool.id) { mutableStateOf(false) }
    val report = buildString {
        appendLine(tool.title)
        appendLine(tool.purpose)
        appendLine("Score: ${insight?.score ?: 0}/100")
        appendLine("Signal: ${insight?.signal ?: "No job data available"}")
        appendLine("Action: ${insight?.action ?: "Add job records to run this analysis."}")
        appendLine("Checklist:")
        tool.checklist.forEach { appendLine("- $it") }
    }.trim()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.title, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back to AI tools", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Live analysis", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(tool.purpose, color = TextSecondary)
                    HorizontalDivider()
                    Metric("Priority score", "${insight?.score ?: 0}/100")
                    Text(insight?.signal ?: "No job data available", color = PrimaryGreen)
                }
            }
            Section("Recommended Action") { Note(insight?.action ?: "Add real job records to run this tool.") }
            Section("Field Checklist") {
                tool.checklist.forEachIndexed { index, item -> Text("${index + 1}. $item", color = TextPrimary) }
            }
            Button(
                onClick = { clipboard.setText(AnnotatedString(report)); copied = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = BackgroundDark)
            ) {
                Text(if (copied) "Report Copied" else "Copy AI Report", fontWeight = FontWeight.Bold)
            }
            Text("This tool uses current job records and updates automatically when the database changes.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
        }
    }
}

@Composable private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextSecondary)
        Text(value, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}
@Composable private fun ItemTitle(text: String) = Text(text, color = TextPrimary, fontWeight = FontWeight.Bold)
@Composable private fun Note(text: String) = Text(text, color = TextSecondary)
private fun money(value: Double): String = "$" + String.format(Locale.US, "%,.2f", value)
