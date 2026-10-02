package com.strobingn.wildlifefieldops.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
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
import com.strobingn.wildlifefieldops.ai.fieldops.SeasonalKind
import com.strobingn.wildlifefieldops.ai.fieldops.SeasonalReminder
import com.strobingn.wildlifefieldops.ai.fieldops.WarrantyTracker
import com.strobingn.wildlifefieldops.data.model.InventoryItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
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
                Text(
                    "${usage.name} · ${usage.quantity} · $${"%.2f".format(usage.amount)}",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonalCard(job: Job, customerVm: CustomerFieldOpsViewModel) {
    val suggested = remember(job.id, job.confirmedSpecies, job.type) { customerVm.suggestSeasonal(job) }
    var kind by remember(job.pricing.seasonalKind) {
        mutableStateOf(
            runCatching { SeasonalKind.valueOf(job.pricing.seasonalKind) }.getOrDefault(suggested.kind)
        )
    }
    var title by remember(job.pricing.seasonalKind) { mutableStateOf(suggested.title) }
    var notes by remember { mutableStateOf(suggested.notes) }
    var dueText by remember(job.pricing.seasonalDueAt) {
        mutableStateOf((job.pricing.seasonalDueAt ?: suggested.dueAt).let { dayStamp(it) })
    }
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
                    value = SeasonalReminder.label(kind),
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
                    SeasonalKind.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(SeasonalReminder.label(option)) },
                            onClick = {
                                kind = option
                                val next = SeasonalReminder.suggest(
                                    when (option) {
                                        SeasonalKind.SPRING_BATS -> "bat"
                                        SeasonalKind.SPRING_SQUIRRELS -> "squirrel"
                                        SeasonalKind.FALL_EXCLUSION -> "raccoon"
                                        SeasonalKind.FALL_RODENTS -> "rodent"
                                    }
                                )
                                title = next.title
                                notes = next.notes
                                dueText = dayStamp(next.dueAt)
                                open = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), colors = batch4FieldColors())
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), colors = batch4FieldColors())
            OutlinedTextField(value = dueText, onValueChange = { dueText = it }, label = { Text("Due (yyyy-MM-dd)") }, modifier = Modifier.fillMaxWidth(), colors = batch4FieldColors())
            Button(
                onClick = {
                    customerVm.saveSeasonal(
                        job,
                        suggested.copy(
                            kind = kind,
                            title = title,
                            notes = notes,
                            dueAt = parseDayStamp(dueText) ?: suggested.dueAt
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) { Text("Save seasonal reminder") }
        }
    }
}

@Composable
private fun MessageDraftCard(
    job: Job,
    customerVm: CustomerFieldOpsViewModel,
    onShare: (subject: String, body: String, sms: Boolean) -> Unit
) {
    var kind by remember { mutableStateOf(CustomerMessageKind.ON_THE_WAY) }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Customer message", color = TextPrimary, fontWeight = FontWeight.Medium)
            Text("AI fills a draft. Anything you type is what the phone sends.", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CustomerMessageKind.entries.forEach { option ->
                    OutlinedButton(onClick = {
                        kind = option
                        val draft = customerVm.draftMessage(job, option)
                        subject = draft.subject
                        body = draft.body
                    }) { Text(option.name.lowercase().replace('_', ' ')) }
                }
            }
            OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), colors = batch4FieldColors())
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), minLines = 4, colors = batch4FieldColors())
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
