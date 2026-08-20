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
import androidx.compose.material.icons.filled.ContentCopy
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
import com.strobingn.wildlifefieldops.ai.HybridAIService
import com.strobingn.wildlifefieldops.ai.operations.AIOperationsEngine
import com.strobingn.wildlifefieldops.ai.operations.IndividualAIToolCatalog
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.AIOperationsViewModel
import java.util.Locale
import kotlinx.coroutines.launch

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
                        Text("Open a tool, type the job facts, run Grok", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("Checklists and job scores are not AI. Only the live answer is.", color = TextSecondary)
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
                    if (category == "All") "20 live Grok tools" else "$category tools",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text("Tap a card. Type facts. Run live AI.", color = TextSecondary)
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
                Section("Job numbers (not AI)") {
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

                Section("Property history (not AI)") {
                    if (data.properties.isEmpty()) Note("Add addresses to jobs to build property history.")
                    data.properties.take(10).forEach { item ->
                        ItemTitle(item.address)
                        Text("${item.visitCount} visits · Repeat risk ${item.repeatRiskPercent}%", color = TextSecondary)
                        Text(item.serviceTypes.joinToString().ifBlank { "No service type" }, color = PrimaryGreen)
                        Text(item.recommendation, color = TextPrimary)
                        HorizontalDivider()
                    }
                }

                Section("Record completeness (not AI)") {
                    data.qualityChecks.take(10).forEach { item ->
                        ItemTitle("${item.score}/100 · ${item.title}")
                        Text(
                            if (item.missing.isEmpty()) "Complete" else "Missing: ${item.missing.joinToString()}",
                            color = if (item.missing.isEmpty()) PrimaryGreen else MaterialTheme.colorScheme.tertiary
                        )
                        HorizontalDivider()
                    }
                }

                Section("Pricing from invoices (not AI)") {
                    if (data.pricing.isEmpty()) Note("Add estimated and actual costs to unlock pricing analysis.")
                    data.pricing.take(10).forEach { item ->
                        ItemTitle(item.title)
                        Text("Estimate ${money(item.estimated)} · Actual ${money(item.actual)} · Variance ${money(item.variance)}", color = TextSecondary)
                        Text(item.marginSignal, color = PrimaryGreen)
                        HorizontalDivider()
                    }
                }

                Section("Route scores (not AI)") {
                    data.routePriorities.take(10).forEach { item ->
                        ItemTitle("Score ${item.score} · ${item.title}")
                        Text(item.address.ifBlank { "Address missing" }, color = TextSecondary)
                        Text(item.reason, color = PrimaryGreen)
                        HorizontalDivider()
                    }
                }

                Section("Inventory forecast (not AI)") {
                    data.inventory.forEach { item ->
                        ItemTitle(item.item)
                        Text("Expected weekly use: ${item.expectedWeeklyUse} · Confidence ${item.confidencePercent}%", color = TextSecondary)
                        Text(item.reason, color = TextPrimary)
                        HorizontalDivider()
                    }
                }

                Section("Season notes (not AI)") {
                    data.speciesGuidance.forEach { item ->
                        ItemTitle(item.species)
                        Text(item.activityWindow, color = PrimaryGreen)
                        Text(item.fieldPriority, color = TextPrimary)
                        Text(item.exclusionNote, color = TextSecondary)
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
    val scope = rememberCoroutineScope()
    var notes by rememberSaveable(tool.id) { mutableStateOf("") }
    var answer by rememberSaveable(tool.id) { mutableStateOf("") }
    var source by rememberSaveable(tool.id) { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var copied by rememberSaveable(tool.id) { mutableStateOf(false) }

    val snapshot = remember(dashboard, insight) {
        buildString {
            appendLine("Jobs ${dashboard.business.totalJobs}, completed ${dashboard.business.completedJobs}, close ${dashboard.business.closeRatePercent}%")
            appendLine("Quoted ${dashboard.business.quotedRevenue}, actual ${dashboard.business.actualRevenue}, avg ticket ${dashboard.business.averageTicket}")
            appendLine("Top service: ${dashboard.business.topService}")
            appendLine("Record score: ${insight?.score ?: 0}/100")
            appendLine("Record signal: ${insight?.signal ?: "none"}")
            appendLine("Record action: ${insight?.action ?: "none"}")
            dashboard.routePriorities.take(6).forEach {
                appendLine("Route ${it.title} @ ${it.address} score ${it.score}: ${it.reason}")
            }
            dashboard.qualityChecks.take(6).forEach {
                appendLine("QC ${it.title} ${it.score}/100 missing=${it.missing.joinToString()}")
            }
            dashboard.pricing.take(6).forEach {
                appendLine("Price ${it.title} est ${it.estimated} actual ${it.actual}")
            }
        }
    }

    fun runLive() {
        if (loading) return
        loading = true
        copied = false
        scope.launch {
            val result = HybridAIService.answerFieldTool(
                toolTitle = tool.title,
                purpose = tool.purpose,
                steps = tool.checklist,
                species = "",
                siteNotes = notes,
                jobSnapshot = snapshot
            )
            answer = result.text
            source = result.source
            loading = false
        }
    }

    val report = buildString {
        appendLine(tool.title)
        appendLine(notes)
        appendLine()
        if (answer.isNotBlank()) {
            appendLine("Live $source:")
            appendLine(answer)
        }
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
            Text(tool.category, color = PrimaryGreen, style = MaterialTheme.typography.labelLarge)
            Text(tool.purpose, color = TextSecondary)

            Section("Record snapshot (not AI)") {
                Metric("Score", "${insight?.score ?: 0}/100")
                Note(insight?.signal ?: "No job data in the database yet.")
                Note(insight?.action ?: "Add real jobs, then run live AI.")
            }

            Section("SOP (not AI)") {
                tool.checklist.forEachIndexed { index, item ->
                    Text("${index + 1}. $item", color = TextPrimary)
                }
            }

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Job facts for Grok") },
                placeholder = { Text("Addresses, species, dates, money, what you saw") },
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Button(
                onClick = { runLive() },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = BackgroundDark)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = BackgroundDark
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Asking Grok…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Run live AI", fontWeight = FontWeight.Bold)
                }
            }

            if (answer.isNotBlank()) {
                Section(if (source == "Offline") "Offline — not AI" else "Live $source answer") {
                    Text(answer, color = TextPrimary)
                }
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(report))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = BackgroundDark)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (copied) "Copied" else "Copy live answer", fontWeight = FontWeight.Bold)
                }
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
