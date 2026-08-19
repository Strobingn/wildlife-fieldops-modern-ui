package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
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
    initialToolId: String? = null,
    viewModel: AIOperationsViewModel = hiltViewModel()
) {
    val data by viewModel.dashboard.collectAsState()
    var selectedToolId by rememberSaveable { mutableStateOf(initialToolId?.takeIf { it.isNotBlank() }) }
    var category by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    val selectedTool = IndividualAIToolCatalog.tools.firstOrNull { it.id == selectedToolId }
    if (selectedTool != null) {
        IndividualAIToolScreen(
            tool = selectedTool,
            dashboard = data,
            onBack = { selectedToolId = null }
        )
        return
    }

    val visibleTools = remember(category, query) {
        IndividualAIToolCatalog.tools.filter { tool ->
            val inCategory = category == "All" || tool.category == category
            val matches = query.isBlank() ||
                tool.title.contains(query, ignoreCase = true) ||
                tool.purpose.contains(query, ignoreCase = true)
            inCategory && matches
        }
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
                        Text("Tools are split by Dispatch, Money, Records, and Field.", color = TextSecondary)
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search operations") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                (listOf("All") + IndividualAIToolCatalog.categories + listOf("Insights")).forEach { label ->
                    FilterChip(
                        selected = category == label,
                        onClick = { category = label },
                        label = { Text(label) }
                    )
                }
            }

            if (category != "Insights") {
                Text(
                    if (category == "All") "20 Individual AI Tools" else "$category tools",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Tap a card to open that tool only.",
                    color = TextSecondary
                )
                visibleTools.forEach { tool ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedToolId = tool.id },
                        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp)) {
                            Surface(
                                color = SurfaceBright,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    tool.category.take(1),
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tool.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                                Text(tool.purpose, color = TextSecondary)
                                Spacer(Modifier.height(4.dp))
                                Text(tool.category, color = PrimaryGreen, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                if (visibleTools.isEmpty()) {
                    Note("No tools in this category match the search.")
                }
            }

            if (category == "All" || category == "Insights") {
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
                        Text(
                            if (item.missing.isEmpty()) "Complete" else "Missing: ${item.missing.joinToString()}",
                            color = if (item.missing.isEmpty()) PrimaryGreen else MaterialTheme.colorScheme.tertiary
                        )
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

                Section("65 Advanced AI Modules") {
                    data.advancedInsights.forEachIndexed { index, item ->
                        ItemTitle("${index + 1}. ${item.name} · ${item.score}/100")
                        Text(item.signal, color = PrimaryGreen)
                        Text(item.action, color = TextSecondary)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndividualAIToolScreen(
    tool: IndividualAIToolCatalog.Tool,
    dashboard: AIOperationsEngine.Dashboard,
    onBack: () -> Unit
) {
    val insight = dashboard.advancedInsights.firstOrNull { it.name == tool.insightName }
    val clipboard = LocalClipboardManager.current
    var copied by rememberSaveable(tool.id) { mutableStateOf(false) }
    val report = buildString {
        appendLine(tool.title)
        appendLine(tool.purpose)
        appendLine("Category: ${tool.category}")
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(tool.category, color = PrimaryGreen, style = MaterialTheme.typography.labelLarge)
                    Text("Live analysis", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(tool.purpose, color = TextSecondary)
                    HorizontalDivider()
                    Metric("Priority score", "${insight?.score ?: 0}/100")
                    Text(insight?.signal ?: "No job data available", color = PrimaryGreen)
                }
            }

            Section("Recommended Action") {
                Note(insight?.action ?: "Add real job records to run this tool.")
            }

            Section("Field Checklist") {
                tool.checklist.forEachIndexed { index, item ->
                    Text("${index + 1}. $item", color = TextPrimary)
                }
            }

            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(report))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = BackgroundDark)
            ) {
                Text(if (copied) "Report Copied" else "Copy AI Report", fontWeight = FontWeight.Bold)
            }

            Text(
                "This tool uses current job records and updates automatically when the database changes.",
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
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
