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
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.fieldops.DecNwcoLog
import com.strobingn.wildlifefieldops.ai.fieldops.NwcoLogRecord
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.viewmodel.DecNwcoLogViewModel
import com.strobingn.wildlifefieldops.util.DecLogShare
import com.strobingn.wildlifefieldops.util.NwcoLogPdf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecNwcoLogScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    viewModel: DecNwcoLogViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsState()
    val operator by viewModel.operator.collectAsState()
    val year by viewModel.year.collectAsState()
    val context = LocalContext.current
    val visible = remember(rows, year) { DecNwcoLog.filterCalendarYear(rows, year) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DEC NWCO log", color = TextPrimary) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                Text(
                    "Official NYS DEC nuisance wildlife control log (2024 form). Rows auto-fill from jobs, trap checks, captures, species, and photo tags. Every cell stays editable — what you type is not overwritten.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(operator.displayName().ifBlank { "Set your NWCO name in Settings" }, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text(
                            "License ${operator.licenseNumber.ifBlank { "—" }}  ·  Region ${operator.decRegion.ifBlank { "—" }}  ·  ${operator.countyOfResidence.ifBlank { "County not set" }}",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = onOpenSettings) { Text("Edit license in Settings") }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.setYear(year - 1) }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous year") }
                    Text("Year $year", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    IconButton(onClick = { viewModel.setYear(year + 1) }) { Icon(Icons.Default.ChevronRight, contentDescription = "Next year") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.addBlankRow() },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("  Add row by hand")
                    }
                    OutlinedButton(onClick = {
                        DecLogShare.shareCsv(context, viewModel.csv(), "nys-dec-nwco-log-$year.csv")
                    }) { Text("CSV") }
                    OutlinedButton(onClick = {
                        val file = NwcoLogPdf.generate(context, operator, visible, NwcoLogPdf.licenseStartYear())
                        DecLogShare.sharePdf(context, file, "NYS DEC NWCO log")
                    }) { Text("PDF") }
                }
            }
            if (visible.isEmpty()) {
                item {
                    Text("No log rows for $year yet. Jobs and trap checks fill this automatically, or tap Add row by hand.", color = TextSecondary)
                }
            }
            items(visible, key = { it.id.ifBlank { it.sourceKey } }) { row ->
                NwcoRowCard(
                    row = row,
                    onChange = { key, value -> viewModel.updateCell(row, key, value) },
                    onDelete = { viewModel.deleteRow(row) }
                )
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun NwcoRowCard(
    row: NwcoLogRecord,
    onChange: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (row.manual) "Hand-entered row" else "Auto-filled · typed cells stay locked",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall
            )
            Cell("5 Complainant", row.complainant, "complainant" in row.locked) { onChange("complainant", it) }
            Cell("6 Date(s) performed", row.datesPerformed, "datesPerformed" in row.locked) { onChange("datesPerformed", it) }
            Cell("7 Nuisance species", row.species, "species" in row.locked) { onChange("species", it) }
            PickerCell("8 Complaint type", row.complaintType, DecNwcoLog.COMPLAINT_TYPES) { onChange("complaintType", it) }
            PickerCell("9 Abatement method", row.abatementMethod, DecNwcoLog.METHODS) { onChange("abatementMethod", it) }
            PickerCell("10 Area of complaint", row.areaOfComplaint, DecNwcoLog.AREAS) { onChange("areaOfComplaint", it) }
            Cell("11 Number of traps", row.trapsSet, "trapsSet" in row.locked) { onChange("trapsSet", it) }
            Cell("12 Species and number taken", row.speciesAndNumberTaken, "speciesAndNumberTaken" in row.locked) { onChange("speciesAndNumberTaken", it) }
            PickerCell("13 Disposition", row.disposition, DecNwcoLog.DISPOSITIONS + listOf(row.disposition).filter { it.isNotBlank() }) {
                onChange("disposition", it)
            }
            Cell("Released / relocated county or rehab #", row.disposition, "disposition" in row.locked) { onChange("disposition", it) }
            OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Remove row") }
        }
    }
}

@Composable
private fun Cell(label: String, value: String, locked: Boolean, onChange: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text(if (locked) "$label · typed" else label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PickerCell(label: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    Column {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            options.distinct().take(4).forEach { opt ->
                androidx.compose.material3.FilterChip(
                    selected = value == opt,
                    onClick = { onChange(opt) },
                    label = { Text(opt.take(18), style = MaterialTheme.typography.labelSmall) }
                )
            }
        }
        Cell(label, value, false, onChange)
    }
}
