package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.fieldops.MileageLogEntry
import com.strobingn.wildlifefieldops.ai.fieldops.MileageTaxLog
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.viewmodel.MoneyFieldOpsViewModel
import com.strobingn.wildlifefieldops.util.DecLogShare
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MileageLogScreen(
    onBack: () -> Unit,
    viewModel: MoneyFieldOpsViewModel = hiltViewModel()
) {
    val entries by viewModel.mileage.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val year = viewModel.currentYear()
    val yearRows = MileageTaxLog.forYear(entries, year)
    val context = LocalContext.current
    var showAdd by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshMileage() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mileage tax log", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        DecLogShare.shareCsv(context, viewModel.mileageCsv(year), "irs-mileage-$year.csv")
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Export year", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = PrimaryGreen, contentColor = OnPrimary) {
                Icon(Icons.Default.Add, contentDescription = "Add miles")
            }
        },
        containerColor = BackgroundDark
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    "$year · ${"%.1f".format(MileageTaxLog.totalMiles(yearRows))} mi · $${"%.2f".format(MileageTaxLog.totalAmount(yearRows))}",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(yearRows, key = { it.id }) { row ->
                Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${row.miles} mi · ${row.purpose.ifBlank { "Job travel" }}", color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text(row.jobTitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Text(
                            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(row.date)),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                        TextButton(onClick = { viewModel.deleteMileage(row.jobId, row.id) }) {
                            Text("Delete", color = TextSecondary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
    if (showAdd) {
        MileageEditorDialog(
            jobs = jobs,
            onDismiss = { showAdd = false },
            onSave = {
                viewModel.saveMileage(it)
                showAdd = false
            }
        )
    }
}

@Composable
private fun MileageEditorDialog(
    jobs: List<Job>,
    onDismiss: () -> Unit,
    onSave: (MileageLogEntry) -> Unit
) {
    var jobId by remember { mutableStateOf(jobs.firstOrNull()?.id.orEmpty()) }
    var miles by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("Job travel") }
    var dateText by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text("Add mileage", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = miles, onValueChange = { miles = it }, label = { Text("Miles") }, modifier = Modifier.fillMaxWidth(), colors = field())
                OutlinedTextField(value = purpose, onValueChange = { purpose = it }, label = { Text("Purpose") }, modifier = Modifier.fillMaxWidth(), colors = field())
                OutlinedTextField(value = dateText, onValueChange = { dateText = it }, label = { Text("Date yyyy-MM-dd") }, modifier = Modifier.fillMaxWidth(), colors = field())
                val job = jobs.find { it.id == jobId }
                Text("Job: ${job?.title?.ifBlank { job.customerName } ?: "none"}", color = TextSecondary)
                jobs.take(8).forEach { j ->
                    TextButton(onClick = { jobId = j.id }) {
                        Text(j.title.ifBlank { j.customerName }.ifBlank { j.id }, color = PrimaryGreen)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val job = jobs.find { it.id == jobId } ?: return@Button
                    val day = runCatching {
                        SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateText.trim())?.time
                    }.getOrNull() ?: System.currentTimeMillis()
                    onSave(
                        MileageTaxLog.suggestFromEstimate(
                            jobId = job.id,
                            jobTitle = job.title.ifBlank { job.customerName },
                            miles = miles.toDoubleOrNull() ?: job.pricing.mileage,
                            date = day,
                            purpose = purpose
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
private fun field() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
