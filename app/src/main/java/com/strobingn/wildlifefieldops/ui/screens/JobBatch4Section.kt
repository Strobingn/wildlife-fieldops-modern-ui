package com.strobingn.wildlifefieldops.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.fieldops.CustomerMessageKind
import com.strobingn.wildlifefieldops.ai.fieldops.FieldDate
import com.strobingn.wildlifefieldops.ai.fieldops.OperatorWins
import com.strobingn.wildlifefieldops.ai.fieldops.SeasonalKind
import com.strobingn.wildlifefieldops.ai.fieldops.SeasonalReminder
import com.strobingn.wildlifefieldops.ai.fieldops.SeasonalSave
import com.strobingn.wildlifefieldops.ai.fieldops.WarrantyTracker
import com.strobingn.wildlifefieldops.data.model.InventoryItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ui.components.ApplySuggestionChip
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.StatusUrgent
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.ui.viewmodel.CustomerFieldOpsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobBatch4Section(
    job: Job,
    customerVm: CustomerFieldOpsViewModel
) {
    val inventory by customerVm.inventory.collectAsState()
    val message by customerVm.message.collectAsState()
    val context = LocalContext.current

    MaterialsCard(job, inventory, customerVm)
    WarrantyCard(job, customerVm)
    SeasonalCard(job, customerVm)
    MessageDraftCard(job, customerVm) { subject, body, sms ->
        if (sms) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).apply {
                putExtra("sms_body", body)
            }
            runCatching { context.startActivity(intent) }
        } else {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
            }
            runCatching { context.startActivity(intent) }
        }
    }
    message?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.bodySmall) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaterialsCard(
    job: Job,
    inventory: List<InventoryItem>,
    customerVm: CustomerFieldOpsViewModel
) {
    var open by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<InventoryItem?>(null) }
    var qtyText by remember { mutableStateOf("1") }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Materials used", color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(
                "Pick a part. On-hand drops and the job keeps the line. Low stock is flagged.",
                color = TextTertiary,
                style = MaterialTheme.typography.bodySmall
            )
            if (inventory.isEmpty()) {
                Text("Add items on Inventory first.", color = TextSecondary)
            } else {
                ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                    OutlinedTextField(
                        value = selected?.let { "${it.name} (${it.quantityOnHand} ${it.unitOfMeasure})" }.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Inventory item") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = batch4FieldColors()
                    )
                    ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        inventory.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        buildString {
                                            append(item.name)
                                            append(" · ")
                                            append(item.quantityOnHand)
                                            append(" ")
                                            append(item.unitOfMeasure)
                                            if (item.isLowStock) append(" · LOW")
                                        }
                                    )
                                },
                                onClick = {
                                    selected = item
                                    open = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Quantity used") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = batch4FieldColors()
                )
                Button(
                    onClick = {
                        val item = selected ?: return@Button
                        customerVm.deductMaterial(job.id, item.id, qtyText.toDoubleOrNull() ?: 1.0)
                    },
                    enabled = selected != null,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("Deduct from inventory") }
            }
            job.pricing.materialUsages.forEach { usage ->
                key(usage.id) {
                    var lineQty by remember(usage.id, usage.quantity) {
                        mutableStateOf(usage.quantity.toString().trimEnd('0').trimEnd('.'))
                    }
                    Text(usage.name, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lineQty,
                            onValueChange = { lineQty = it },
                            label = { Text("Qty") },
                            modifier = Modifier.weight(1f),
                            colors = batch4FieldColors()
                        )
                        TextButton(onClick = {
                            val qty = lineQty.toDoubleOrNull()
                            if (qty == null) return@TextButton
                            customerVm.updateMaterial(job.id, usage.id, qty)
                        }) { Text("Save") }
                        TextButton(onClick = { customerVm.removeMaterial(job.id, usage.id) }) { Text("Remove") }
                    }
                    Text(
                        "$${"%.2f".format(usage.amount)} on this line. Removing puts the quantity back in stock.",
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

@Composable
private fun WarrantyCard(job: Job, customerVm: CustomerFieldOpsViewModel) {
    val plan = WarrantyTracker.fromJob(
        job.pricing.warrantyStartAt,
        job.pricing.warrantyTermMonths,
        job.pricing.warrantyCovered
    )
    var startText by remember(job.pricing.warrantyStartAt) {
        mutableStateOf(job.pricing.warrantyStartAt?.let { dayStamp(it) }.orEmpty())
    }
    var monthsText by remember(job.pricing.warrantyTermMonths) {
        mutableStateOf(job.pricing.warrantyTermMonths.toString())
    }
    var covered by remember(job.pricing.warrantyCovered) { mutableStateOf(job.pricing.warrantyCovered) }
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Warranty", color = TextPrimary, fontWeight = FontWeight.Medium)
            plan.expiresAt?.let {
                Text("Expires ${dayStamp(it)}", color = TextSecondary)
            }
            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it },
                label = { Text("Start date (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth(),
                colors = batch4FieldColors()
            )
            OutlinedTextField(
                value = monthsText,
                onValueChange = { monthsText = it.filter { ch -> ch.isDigit() } },
                label = { Text("Term months") },
                modifier = Modifier.fillMaxWidth(),
                colors = batch4FieldColors()
            )
            OutlinedTextField(
                value = covered,
                onValueChange = { covered = it },
                label = { Text("Covered work") },
                modifier = Modifier.fillMaxWidth(),
                colors = batch4FieldColors()
            )
            Button(
                onClick = {
                    val start = parseDayStamp(startText) ?: System.currentTimeMillis()
                    customerVm.saveWarranty(job.id, start, monthsText.toIntOrNull() ?: 12, covered)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) { Text("Save warranty + reminder") }
            OutlinedButton(
                onClick = {
                    val start = parseDayStamp(startText) ?: job.pricing.warrantyStartAt ?: System.currentTimeMillis()
                    val next = job.copy(
                        pricing = job.pricing.copy(
                            warrantyStartAt = start,
                            warrantyTermMonths = monthsText.toIntOrNull() ?: job.pricing.warrantyTermMonths,
                            warrantyCovered = covered
                        )
                    )
                    val path = com.strobingn.wildlifefieldops.util.WildlifeWhispererWarrantyPdf.generate(
                        context,
                        next
                    )
                    com.strobingn.wildlifefieldops.util.WildlifeWhispererContractPdf.share(
                        context,
                        path,
                        "Share warranty"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share warranty PDF") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonalCard(job: Job, customerVm: CustomerFieldOpsViewModel) {
    var kind by remember(job.id, job.pricing.seasonalKind) {
        mutableStateOf(runCatching { SeasonalKind.valueOf(job.pricing.seasonalKind) }.getOrNull())
    }
    var title by remember(job.id, job.pricing.seasonalTitle) { mutableStateOf(job.pricing.seasonalTitle) }
    var notes by remember(job.id, job.pricing.seasonalNotes) { mutableStateOf(job.pricing.seasonalNotes) }
    var dueText by remember(job.id, job.pricing.seasonalDueAt) {
        mutableStateOf(job.pricing.seasonalDueAt?.let { FieldDate.formatDay(it) }.orEmpty())
    }
    var dateError by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Seasonal reminder", color = TextPrimary, fontWeight = FontWeight.Medium)
            ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
                OutlinedTextField(
                    value = kind?.let { SeasonalReminder.label(it) }.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Season") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = batch4FieldColors()
                )
                ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuItem(
                        text = { Text("None") },
                        onClick = {
                            kind = null
                            open = false
                        }
                    )
                    SeasonalKind.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(SeasonalReminder.label(option)) },
                            onClick = {
                                kind = option
                                open = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), colors = batch4FieldColors())
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), colors = batch4FieldColors())
            OutlinedTextField(
                value = dueText,
                onValueChange = {
                    dueText = it
                    dateError = null
                },
                label = { Text("Due (yyyy-MM-dd)") },
                supportingText = { Text(dateError ?: "Blank clears the due date.", color = if (dateError != null) StatusUrgent else TextTertiary) },
                isError = dateError != null,
                modifier = Modifier.fillMaxWidth(),
                colors = batch4FieldColors()
            )
            OutlinedButton(onClick = {
                val next = customerVm.suggestSeasonal(job)
                val filled = SeasonalReminder.fillBlankFields(kind, title, notes, dueText, next, FieldDate::formatDay)
                kind = filled.kind
                title = filled.title
                notes = filled.notes
                dueText = filled.dueText
                dateError = null
            }) { Text("Suggest") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val due = FieldDate.parseDay(dueText)
                        if (!due.ok) {
                            dateError = due.error
                            return@Button
                        }
                        dateError = null
                        customerVm.saveSeasonal(
                            job,
                            SeasonalSave(kind = kind, title = title.trim(), notes = notes.trim(), dueAt = due.millis)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("Save seasonal reminder") }
                TextButton(onClick = {
                    kind = null
                    title = ""
                    notes = ""
                    dueText = ""
                    dateError = null
                    customerVm.saveSeasonal(job, SeasonalSave(kind = null, title = "", notes = "", dueAt = null))
                }) { Text("Clear") }
            }
        }
    }
}

@Composable
private fun MessageDraftCard(
    job: Job,
    customerVm: CustomerFieldOpsViewModel,
    onShare: (subject: String, body: String, sms: Boolean) -> Unit
) {
    var kind by remember(job.status) {
        mutableStateOf(
            if (job.status.isInspectionOnly()) CustomerMessageKind.INSPECTION_REMINDER
            else CustomerMessageKind.ON_THE_WAY
        )
    }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var subjectManual by remember { mutableStateOf(false) }
    var bodyManual by remember { mutableStateOf(false) }
    var subjectPreview by remember { mutableStateOf<String?>(null) }
    var bodyPreview by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Customer message", color = TextPrimary, fontWeight = FontWeight.Medium)
            Text("Type the message yourself, or tap Suggest. What you type is what the phone sends.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                CustomerMessageKind.entries.forEach { option ->
                    if (kind == option) {
                        Button(
                            onClick = { kind = option },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                        ) { Text(option.name.lowercase().replace('_', ' ')) }
                    } else {
                        OutlinedButton(onClick = { kind = option }) { Text(option.name.lowercase().replace('_', ' ')) }
                    }
                }
            }
            OutlinedButton(onClick = {
                val draft = customerVm.draftMessage(job, kind)
                subject = OperatorWins.suggest(subject, draft.subject, subjectManual)
                body = OperatorWins.suggest(body, draft.body, bodyManual)
                subjectPreview = OperatorWins.preview(subject, draft.subject, subjectManual)
                bodyPreview = OperatorWins.preview(body, draft.body, bodyManual)
            }) { Text("Suggest draft") }
            ApplySuggestionChip(subjectPreview) { subject = it; subjectManual = true; subjectPreview = null }
            ApplySuggestionChip(bodyPreview) { body = it; bodyManual = true; bodyPreview = null }
            OutlinedTextField(value = subject, onValueChange = { subject = it; subjectManual = true }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), colors = batch4FieldColors())
            OutlinedTextField(value = body, onValueChange = { body = it; bodyManual = true }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 4, colors = batch4FieldColors())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onShare(subject, body, true) },
                    enabled = body.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) { Text("SMS") }
                OutlinedButton(onClick = { onShare(subject, body, false) }, enabled = body.isNotBlank()) { Text("Email") }
            }
        }
    }
}

@Composable
private fun batch4FieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)

private fun dayStamp(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

private fun parseDayStamp(raw: String): Long? = runCatching {
    val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw.trim()) ?: return null
    parsed.time + 9 * 60 * 60 * 1000L
}.getOrNull()
