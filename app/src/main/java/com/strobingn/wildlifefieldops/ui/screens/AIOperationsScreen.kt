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
                        Text("Offline analysis updates whenever job data changes.", color = TextSecondary)
                    }
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
