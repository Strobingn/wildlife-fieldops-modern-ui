package com.strobingn.wildlifefieldops.ui.screens

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.fieldops.EarningsAdjKind
import com.strobingn.wildlifefieldops.ai.fieldops.NySalesTaxPeriods
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.viewmodel.EarningsTaxViewModel
import com.strobingn.wildlifefieldops.util.DecLogShare
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EarningsTaxScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    onNavigateToInvoices: () -> Unit = {},
    onNavigateToMileage: () -> Unit = {},
    viewModel: EarningsTaxViewModel = hiltViewModel()
) {
    val grain by viewModel.grain.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val quarters by viewModel.quarters.collectAsState()
    val adjustments by viewModel.adjustments.collectAsState()
    val anchor by viewModel.anchor.collectAsState()
    val context = LocalContext.current
    var showAdjust by remember { mutableStateOf(false) }
    val periodLabel = remember(grain, anchor) {
        SimpleDateFormat(
            when (grain) {
                NySalesTaxPeriods.Grain.DAY -> "EEE, MMM d yyyy"
                NySalesTaxPeriods.Grain.WEEK -> "'Week of' MMM d yyyy"
                NySalesTaxPeriods.Grain.MONTH -> "MMMM yyyy"
                NySalesTaxPeriods.Grain.YEAR -> "yyyy"
                NySalesTaxPeriods.Grain.QUARTER -> "MMM yyyy"
            },
            Locale.US
        ).format(Date(anchor))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Earnings & sales tax", color = TextPrimary) },
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                        }
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
                Text("Paid, invoiced, and estimated from jobs and invoices. Type any total to lock it. Add adjustment for cash or extra tax.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            item {
                Text("Invoices & mileage", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("Invoice list and mileage log live on this tab.", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onNavigateToInvoices,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                    ) { Text("Invoices") }
                    OutlinedButton(onClick = onNavigateToMileage) { Text("Mileage") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NySalesTaxPeriods.Grain.entries.filter { it != NySalesTaxPeriods.Grain.QUARTER }.forEach { g ->
                        FilterChip(selected = grain == g, onClick = { viewModel.setGrain(g) }, label = { Text(g.name.lowercase().replaceFirstChar { it.titlecase() }) })
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.shift(-1) }) { Icon(Icons.Default.ChevronLeft, contentDescription = "Previous") }
                    Text(periodLabel, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    IconButton(onClick = { viewModel.shift(1) }) { Icon(Icons.Default.ChevronRight, contentDescription = "Next") }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (snapshot.overrideApplied) {
                            Text("Manual totals win for this period.", color = PrimaryGreen, style = MaterialTheme.typography.bodySmall)
                        }
                        OverrideField("Paid", snapshot.paid) { viewModel.overrideField("paid", it) }
                        OverrideField("Invoiced", snapshot.invoiced) { viewModel.overrideField("invoiced", it) }
                        OverrideField("Estimated", snapshot.estimated) { viewModel.overrideField("estimated", it) }
                        OverrideField("Taxable sales", snapshot.taxable) { viewModel.overrideField("taxable", it) }
                        OverrideField("Non-taxable", snapshot.nontaxable) { viewModel.overrideField("nontaxable", it) }
                        OverrideField("NY sales tax collected", snapshot.taxCollected) { viewModel.overrideField("taxCollected", it) }
                    }
                }
            }
            item {
                Text("Tax by county", fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            items(snapshot.byCounty) { row ->
                Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(row.county, fontWeight = FontWeight.Medium, color = TextPrimary)
                        Text(
                            "Rate ${"%.3f".format(row.ratePercent)}%  ·  taxable $${"%.2f".format(row.taxable)}  ·  non-taxable $${"%.2f".format(row.nontaxable)}  ·  tax $${"%.2f".format(row.taxCollected)}",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                Text("NYS quarterly filing (ST-100)", fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text("Mar–May due Jun 20 · Jun–Aug due Sep 20 · Sep–Nov due Dec 20 · Dec–Feb due Mar 20", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
            items(quarters) { (q, snap) ->
                Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${q.label}  ·  due ${q.dueLabel}", fontWeight = FontWeight.Medium, color = TextPrimary)
                        Text(
                            "Paid $${"%.2f".format(snap.paid)}  ·  invoiced $${"%.2f".format(snap.invoiced)}  ·  tax $${"%.2f".format(snap.taxCollected)}",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showAdjust = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("  Add adjustment")
                    }
                    OutlinedButton(onClick = {
                        DecLogShare.shareCsv(context, viewModel.exportCsv(), "ny-sales-tax-earnings.csv")
                    }) { Text("CSV") }
                    OutlinedButton(onClick = {
                        val file = writeTaxPdf(context.cacheDir, viewModel.exportCsv())
                        DecLogShare.sharePdf(context, file, "NY earnings and sales tax")
                    }) { Text("PDF") }
                }
            }
            if (adjustments.isNotEmpty()) {
                item { Text("Hand adjustments", fontWeight = FontWeight.SemiBold, color = TextPrimary) }
                items(adjustments, key = { it.id }) { adj ->
                    Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(10.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${adj.kind}  $${"%.2f".format(adj.amount)}", color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text(
                                    listOf(adj.county, adj.notes, if (adj.taxable) "taxable" else "non-taxable").filter { it.isNotBlank() }.joinToString(" · "),
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            OutlinedButton(onClick = { viewModel.deleteAdjustment(adj.id) }) { Text("Remove") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showAdjust) {
        AdjustmentDialog(
            onDismiss = { showAdjust = false },
            onSave = { kind, amount, county, taxable, notes ->
                viewModel.addAdjustment(kind, amount, county, taxable, notes, System.currentTimeMillis())
                showAdjust = false
            }
        )
    }
}

@Composable
private fun OverrideField(label: String, value: Double, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf("%.2f".format(value)) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f),
            supportingText = { Text("Type a total, then Lock. Manual wins.") }
        )
        OutlinedButton(onClick = { onCommit(text) }) { Text("Lock") }
    }
}

@Composable
private fun AdjustmentDialog(
    onDismiss: () -> Unit,
    onSave: (EarningsAdjKind, Double, String, Boolean, String) -> Unit
) {
    var kind by remember { mutableStateOf(EarningsAdjKind.PAID) }
    var amount by remember { mutableStateOf("") }
    var county by remember { mutableStateOf("Orange") }
    var taxable by remember { mutableStateOf(true) }
    var notes by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add adjustment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EarningsAdjKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.name.lowercase()) })
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = county, onValueChange = { county = it }, label = { Text("County") })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
                FilterChip(selected = taxable, onClick = { taxable = !taxable }, label = { Text(if (taxable) "Taxable" else "Non-taxable") })
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(kind, amount.toDoubleOrNull() ?: 0.0, county, taxable, notes) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) { Text("Save") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun writeTaxPdf(cacheDir: File, csv: String): File {
    val pdf = PdfDocument()
    val page = pdf.startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create())
    val paint = Paint().apply {
        isAntiAlias = true
        textSize = 10f
        color = Color.rgb(20, 20, 22)
        typeface = Typeface.MONOSPACE
    }
    val title = Paint(paint).apply {
        textSize = 14f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    var y = 48f
    page.canvas.drawText("Wildlife Whisperer  ·  Earnings & NY sales tax", 48f, y, title)
    y += 20f
    csv.lineSequence().forEach { line ->
        if (y > 760f) return@forEach
        page.canvas.drawText(line.take(90), 48f, y, paint)
        y += 14f
    }
    pdf.finishPage(page)
    val file = File(cacheDir, "ny-earnings-sales-tax.pdf")
    FileOutputStream(file).use { pdf.writeTo(it) }
    pdf.close()
    return file
}
