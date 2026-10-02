package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.strobingn.wildlifefieldops.ai.fieldops.FieldDate
import com.strobingn.wildlifefieldops.ai.fieldops.OpsLedger
import com.strobingn.wildlifefieldops.ai.fieldops.MileageLogEntry
import com.strobingn.wildlifefieldops.ai.fieldops.MileageTaxLog
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.StatusUrgent
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
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
    var editing by remember { mutableStateOf<MileageLogEntry?>(null) }
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
                        Row {
                            TextButton(onClick = { editing = row }) { Text("Edit", color = PrimaryGreen) }
                            TextButton(onClick = { viewModel.deleteMileage(row.jobId, row.id) }) {
                                Text("Delete", color = TextSecondary)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
    if (showAdd || editing != null) {
        MileageEditorDialog(
            jobs = jobs,
            initial = editing,
            onDismiss = {
                showAdd = false
                editing = null
            },
            onSave = {
                viewModel.saveMileage(it)
                showAdd = false
                editing = null
            }
        )
    }
}

@Composable
private fun MileageEditorDialog(
    jobs: List<Job>,
    initial: MileageLogEntry? = null,
    onDismiss: () -> Unit,
    onSave: (MileageLogEntry) -> Unit
) {
    var jobId by remember { mutableStateOf(initial?.jobId.orEmpty()) }
    var miles by remember { mutableStateOf(initial?.miles?.takeIf { it > 0 }?.toString().orEmpty()) }
    var purpose by remember { mutableStateOf(initial?.purpose?.ifBlank { "Job travel" } ?: "Job travel") }
    var dateText by remember {
        mutableStateOf(
            initial?.date?.takeIf { it > 0L }?.let { FieldDate.formatDay(it) }
                ?: FieldDate.formatDay(System.currentTimeMillis())
        )
    }
    var dateError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BackgroundCard,
        title = { Text(if (initial == null) "Add mileage" else "Edit mileage", color = TextPrimary) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = miles, onValueChange = { miles = it }, label = { Text("Miles") }, modifier = Modifier.fillMaxWidth(), colors = field())
                OutlinedTextField(value = purpose, onValueChange = { purpose = it }, label = { Text("Purpose") }, modifier = Modifier.fillMaxWidth(), colors = field())
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; dateError = null },
                    label = { Text("Date yyyy-MM-dd") },
                    supportingText = { Text(dateError ?: "A bad date is not saved as today.", color = if (dateError != null) StatusUrgent else TextTertiary) },
                    isError = dateError != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = field()
                )
                SearchableJobPicker(
                    jobs = jobs.filterNot { OpsLedger.isLedger(it) },
                    selectedId = jobId,
                    onSelect = { jobId = it },
                    allowNone = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val day = FieldDate.parseDay(dateText)
                    if (!day.ok || day.millis == null) {
                        dateError = day.error ?: "Enter a date (yyyy-MM-dd)."
                        return@Button
                    }
                    val job = jobs.find { it.id == jobId }
                    onSave(
                        MileageTaxLog.suggestFromEstimate(
                            jobId = job?.id.orEmpty(),
                            jobTitle = job?.title?.ifBlank { job.customerName }.orEmpty().ifBlank { "No job" },
                            miles = miles.toDoubleOrNull() ?: 0.0,
                            date = day.millis,
                            purpose = purpose
                        ).copy(id = initial?.id ?: java.util.UUID.randomUUID().toString())
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
