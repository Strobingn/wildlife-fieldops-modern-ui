package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.JobAiViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateScreen(
    jobId: String,
    autoDraft: Boolean = false,
    onBack: () -> Unit,
    jobsViewModel: JobsViewModel = hiltViewModel(),
    jobAiViewModel: JobAiViewModel = hiltViewModel()
) {
    val job by jobsViewModel.getJobById(jobId).collectAsState(initial = null)
    val draft by jobAiViewModel.estimateDraft.collectAsState()
    val estimateLoading by jobAiViewModel.estimateLoading.collectAsState()
    val aiMessage by jobAiViewModel.message.collectAsState()
    var autoDraftFired by remember { mutableStateOf(false) }

    LaunchedEffect(job, autoDraft) {
        val current = job
        if (autoDraft && !autoDraftFired && current != null && !estimateLoading) {
            autoDraftFired = true
            jobAiViewModel.draftEstimate(current)
        }
    }

    var laborHours by remember { mutableStateOf("2.0") }
    var laborRate by remember { mutableStateOf("85.00") }
    var materialsCost by remember { mutableStateOf("0.00") }
    var equipmentCost by remember { mutableStateOf("0.00") }
    var permitCost by remember { mutableStateOf("0.00") }
    var disposalCost by remember { mutableStateOf("0.00") }
    var mileage by remember { mutableStateOf("0") }
    var mileageRate by remember { mutableStateOf("0.65") }
    var taxRate by remember { mutableStateOf("8.125") }
    var discountPercent by remember { mutableStateOf("0") }
    var draftRationale by remember { mutableStateOf("") }
    var lineItemNotes by remember { mutableStateOf("") }

    LaunchedEffect(draft) {
        val d = draft ?: return@LaunchedEffect
        laborHours = formatNum(d.laborHours)
        laborRate = formatNum(d.laborRate)
        materialsCost = formatNum(d.materialsCost)
        equipmentCost = formatNum(d.equipmentCost)
        permitCost = formatNum(d.permitCost)
        disposalCost = formatNum(d.disposalCost)
        mileage = formatNum(d.mileage)
        mileageRate = formatNum(d.mileageRate)
        taxRate = formatNum(d.taxRate)
        discountPercent = formatNum(d.discountPercent)
        draftRationale = d.rationale
        lineItemNotes = d.lineItemNotes
    }

    val laborTotal = laborHours.toDoubleOrNull()?.times(laborRate.toDoubleOrNull() ?: 0.0) ?: 0.0
    val materialsTotal = materialsCost.toDoubleOrNull() ?: 0.0
    val equipmentTotal = equipmentCost.toDoubleOrNull() ?: 0.0
    val permitTotal = permitCost.toDoubleOrNull() ?: 0.0
    val disposalTotal = disposalCost.toDoubleOrNull() ?: 0.0
    val mileageTotal = mileage.toDoubleOrNull()?.times(mileageRate.toDoubleOrNull() ?: 0.0) ?: 0.0
    val subtotal = laborTotal + materialsTotal + equipmentTotal + permitTotal + disposalTotal + mileageTotal
    val discountAmount = subtotal * (discountPercent.toDoubleOrNull() ?: 0.0) / 100.0
    val taxableAmount = subtotal - discountAmount
    val taxAmount = taxableAmount * (taxRate.toDoubleOrNull() ?: 0.0) / 100.0
    val total = taxableAmount + taxAmount

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estimate Calculator", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    val current = job
                    if (current != null) {
                        IconButton(onClick = { jobAiViewModel.draftEstimate(current) }, enabled = !estimateLoading) {
                            if (estimateLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PrimaryGreen)
                            else Icon(Icons.Default.AutoAwesome, contentDescription = "AI draft from notes", tint = PrimaryGreen)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            job?.let {
                Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(it.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(it.customerName, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        if (it.address.isNotBlank()) Text(it.address, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
            }
            Button(
                onClick = { job?.let { jobAiViewModel.draftEstimate(it) } },
                enabled = job != null && !estimateLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (estimateLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Looking up miles + drafting…")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI draft from job notes", fontWeight = FontWeight.SemiBold)
                }
            }
            if (!aiMessage.isNullOrBlank()) Text(aiMessage!!, style = MaterialTheme.typography.labelMedium, color = PrimaryGreen)
            if (draftRationale.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Draft rationale", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(draftRationale, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
            EstimateSection("Labor") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EstimateField("Hours", laborHours, { laborHours = it }, Modifier.weight(1f))
                    EstimateField("Rate/hr", laborRate, { laborRate = it }, Modifier.weight(1f))
                }
            }
            EstimateSection("Materials & Equipment") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EstimateField("Materials", materialsCost, { materialsCost = it }, Modifier.weight(1f))
                    EstimateField("Equipment", equipmentCost, { equipmentCost = it }, Modifier.weight(1f))
                }
            }
            EstimateSection("Other Costs") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EstimateField("Permits", permitCost, { permitCost = it }, Modifier.weight(1f))
                    EstimateField("Disposal", disposalCost, { disposalCost = it }, Modifier.weight(1f))
                }
            }
            EstimateSection("Mileage") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EstimateField("Miles", mileage, { mileage = it }, Modifier.weight(1f))
                    EstimateField("Rate/mi", mileageRate, { mileageRate = it }, Modifier.weight(1f))
                }
            }
            EstimateSection("Adjustments") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EstimateField("Tax %", taxRate, { taxRate = it }, Modifier.weight(1f))
                    EstimateField("Discount %", discountPercent, { discountPercent = it }, Modifier.weight(1f))
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.1f)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    SummaryRow("Subtotal", subtotal)
                    if (discountAmount > 0) SummaryRow("Discount", -discountAmount, color = SuccessGreen)
                    SummaryRow("Tax (${taxRate}%)", taxAmount)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = BorderDark)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("$${String.format("%.2f", total)}", style = MaterialTheme.typography.headlineSmall, color = PrimaryGreen, fontWeight = FontWeight.Bold)
                    }
                }
            }
            job?.let { j ->
                OutlinedButton(
                    onClick = {
                        jobsViewModel.updateJobDetails(
                            jobId = j.id, title = j.title, description = j.description,
                            customerId = j.customerId, customerName = j.customerName, address = j.address,
                            type = j.type, priority = j.priority, estimatedValue = total, notes = j.notes
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save total as job estimated value")
                }
            }
        }
    }
}

private fun formatNum(value: Double): String {
    return if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.3f", value).trimEnd('0').trimEnd('.')
}

@Composable
private fun EstimateSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun EstimateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(label) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PrimaryGreen, unfocusedBorderColor = BorderDark,
            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
            focusedContainerColor = BackgroundDark, unfocusedContainerColor = BackgroundDark
        ),
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(10.dp),
        singleLine = true
    )
}

@Composable
private fun SummaryRow(label: String, amount: Double, color: Color = TextSecondary) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
        Text("$${String.format("%.2f", amount)}", style = MaterialTheme.typography.bodySmall, color = if (amount < 0) SuccessGreen else TextPrimary)
    }
}
