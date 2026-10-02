@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.strobingn.wildlifefieldops.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.fieldops.CustomerHistoryRow
import com.strobingn.wildlifefieldops.ai.fieldops.ExclusionEstimate
import com.strobingn.wildlifefieldops.ai.fieldops.JobStatusPipeline
import com.strobingn.wildlifefieldops.ai.fieldops.OnMyWay
import com.strobingn.wildlifefieldops.ai.fieldops.PaymentLedger
import com.strobingn.wildlifefieldops.ai.fieldops.RepeatCustomerHistory
import com.strobingn.wildlifefieldops.ai.fieldops.SignatureRules
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.pricing.ExclusionPointRecord
import com.strobingn.wildlifefieldops.pricing.JobPaymentRecord
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.Money
import com.strobingn.wildlifefieldops.pricing.PaymentMethod
import com.strobingn.wildlifefieldops.pricing.isManual
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.ErrorRed
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PaperWhite
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.util.SignatureInk
import com.strobingn.wildlifefieldops.util.WildlifeWhispererReceiptPdf
import java.util.UUID

@Composable
fun JobStatusPipelineCard(
    job: Job,
    onSetStatus: (JobStatus) -> Unit
) {
    val hint = JobStatusPipeline.suggest(job)
    BatchCard(title = "Status") {
        Text(
            "Set the status yourself. A suggestion never changes it until you tap it.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            JobStatusPipeline.stages.forEach { status ->
                FilterChip(
                    selected = job.status == status ||
                        (status == JobStatus.LEAD && job.status == JobStatus.PENDING) ||
                        (status == JobStatus.CLOSED && job.status == JobStatus.COMPLETED),
                    onClick = { onSetStatus(status) },
                    label = { Text(JobStatusPipeline.label(status)) }
                )
            }
        }
        if (hint != null) {
            TextButton(onClick = { onSetStatus(hint) }) {
                Text(
                    "Suggested: ${JobStatusPipeline.label(hint)}. Tap to set it.",
                    color = PrimaryGreen
                )
            }
        }
    }
}

@Composable
fun JobReachCustomerCard(name: String, phone: String) {
    val context = LocalContext.current
    var eta by remember(phone) { mutableStateOf("") }
    BatchCard(title = "Call or text") {
        Text(
            if (phone.isBlank()) "Add a phone number above to call or text." else phone,
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { tapToCall(context, phone) },
                enabled = phone.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) {
                androidx.compose.material3.Icon(Icons.Default.Call, contentDescription = null)
                Text("Call")
            }
            OutlinedButton(
                onClick = { tapToText(context, phone) },
                enabled = phone.isNotBlank()
            ) {
                androidx.compose.material3.Icon(Icons.Default.Sms, contentDescription = null)
                Text("Text")
            }
        }
        OutlinedTextField(
            value = eta,
            onValueChange = { eta = it },
            label = { Text("ETA (typed)") },
            placeholder = { Text("example: 20 minutes") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Text(
            "On my way opens your messaging app with the text filled in. Nothing is sent until you send it.",
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = {
                val body = OnMyWay.message(name, eta)
                openSmsDraft(context, phone, body)
            },
            enabled = phone.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("On my way") }
    }
}

@Composable
fun RepeatCustomerHistoryCard(
    current: Job,
    jobs: List<Job>,
    onOpen: (String) -> Unit
) {
    val rows = remember(current.id, current.customerId, current.address, jobs) {
        RepeatCustomerHistory.rows(current, jobs)
    }
    BatchCard(title = "Past jobs") {
        if (rows.isEmpty()) {
            Text(
                "No other jobs for this customer or address yet.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            rows.forEach { row ->
                HistoryLine(row, onOpen)
            }
        }
    }
}

@Composable
private fun HistoryLine(row: CustomerHistoryRow, onOpen: (String) -> Unit) {
    val day = if (row.dateMillis > 0L) PaymentLedger.formatDay(row.dateMillis) else ""
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(row.jobId) }
            .padding(vertical = 6.dp)
    ) {
        Text(
            listOf(day, row.species.ifBlank { "Species not set" }, row.statusLabel).filter { it.isNotBlank() }
                .joinToString(" · "),
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        Text(Money.formatUsd(row.amount), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun JobPaymentsCard(job: Job, onSave: (JobPricing) -> Unit) {
    val context = LocalContext.current
    val total = PaymentLedger.invoiceTotal(job)
    var method by remember(job.id) { mutableStateOf(PaymentMethod.CASH) }
    var checkNumber by remember(job.id) { mutableStateOf("") }
    var amountText by remember(job.id) { mutableStateOf("") }
    var dateText by remember(job.id) { mutableStateOf(PaymentLedger.formatDay(System.currentTimeMillis())) }
    var note by remember(job.id) { mutableStateOf("") }
    BatchCard(title = "Payments") {
        Text(
            "Paid ${Money.formatUsd(PaymentLedger.totalPaid(job.pricing.payments))} · Balance due ${Money.formatUsd(PaymentLedger.balanceDue(total, job.pricing.payments))}",
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        job.pricing.payments.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    listOf(
                        PaymentMethod.label(row.method),
                        row.checkNumber.takeIf { it.isNotBlank() }?.let { "#$it" },
                        PaymentLedger.formatDay(row.paidAt),
                        Money.formatUsd(row.amount)
                    ).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    onSave(PaymentLedger.apply(job.pricing, PaymentLedger.remove(job.pricing.payments, row.id)))
                }) { Text("Delete", color = ErrorRed) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            PaymentMethod.all.forEach { code ->
                FilterChip(
                    selected = method == code,
                    onClick = { method = code },
                    label = { Text(PaymentMethod.label(code)) }
                )
            }
        }
        OutlinedTextField(
            value = checkNumber,
            onValueChange = { checkNumber = it },
            label = { Text("Check #") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        OutlinedTextField(
            value = dateText,
            onValueChange = { dateText = it },
            label = { Text("Date (yyyy-MM-dd)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = {
                val row = JobPaymentRecord(
                    id = UUID.randomUUID().toString(),
                    method = method,
                    checkNumber = checkNumber,
                    amount = amountText.toDoubleOrNull() ?: 0.0,
                    paidAt = PaymentLedger.parseDay(dateText),
                    note = note
                )
                onSave(PaymentLedger.apply(job.pricing, PaymentLedger.upsert(job.pricing.payments, row)))
                amountText = ""
                checkNumber = ""
                note = ""
            },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Record payment") }
        OutlinedButton(
            onClick = {
                val path = WildlifeWhispererReceiptPdf.generate(context, job, total, job.pricing.payments)
                com.strobingn.wildlifefieldops.util.WildlifeWhispererContractPdf.share(context, path, "Share receipt")
            },
            enabled = job.pricing.payments.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Share receipt PDF") }
    }
}

@Composable
fun JobExclusionCard(job: Job, onSave: (JobPricing) -> Unit) {
    var location by remember(job.id) { mutableStateOf("") }
    var size by remember(job.id) { mutableStateOf("") }
    var material by remember(job.id) { mutableStateOf("") }
    var photo by remember(job.id) { mutableStateOf("") }
    var priceText by remember(job.id) { mutableStateOf("") }
    BatchCard(title = "Exclusion / repair") {
        Text(
            "Each opening becomes an editable seal-up line on the estimate.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        job.pricing.exclusionPoints.forEach { point ->
            ExclusionEditor(point, job, onSave)
        }
        OutlinedTextField(location, { location = it }, label = { Text("Location") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(size, { size = it }, label = { Text("Size") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(material, { material = it }, label = { Text("Material") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(photo, { photo = it }, label = { Text("Photo path") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(
            priceText,
            { priceText = it },
            label = { Text("Price") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        Button(
            onClick = {
                val point = ExclusionPointRecord(
                    id = UUID.randomUUID().toString(),
                    location = location,
                    size = size,
                    material = material,
                    photoPath = photo,
                    photoManual = photo.isBlank(),
                    unitPrice = priceText.toDoubleOrNull() ?: 0.0
                )
                val pricing = ExclusionEstimate.pushToEstimate(
                    job.pricing.copy(exclusionPoints = job.pricing.exclusionPoints + point)
                )
                onSave(pricing)
                location = ""
                size = ""
                material = ""
                photo = ""
                priceText = ""
            },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add opening") }
    }
}

@Composable
private fun ExclusionEditor(point: ExclusionPointRecord, job: Job, onSave: (JobPricing) -> Unit) {
    var description by remember(point.id, point.description, point.descriptionManual) {
        mutableStateOf(ExclusionEstimate.lineDescription(point))
    }
    var price by remember(point.id, point.unitPrice) {
        mutableStateOf(if (point.unitPrice == 0.0) "" else point.unitPrice.toString())
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            listOf(point.size, point.location, point.material).filter { it.isNotBlank() }.joinToString(" · ")
                .ifBlank { "Opening" },
            color = TextPrimary,
            fontWeight = FontWeight.Medium
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Estimate line") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = price,
            onValueChange = { price = it },
            label = { Text("Price") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        Row {
            TextButton(onClick = {
                val updated = point.copy(
                    description = description,
                    descriptionManual = true,
                    unitPrice = price.toDoubleOrNull() ?: 0.0
                )
                val points = job.pricing.exclusionPoints.map { if (it.id == point.id) updated else it }
                onSave(ExclusionEstimate.pushToEstimate(job.pricing.copy(exclusionPoints = points)))
            }) { Text("Save line", color = PrimaryGreen) }
            TextButton(onClick = {
                val points = job.pricing.exclusionPoints.filterNot { it.id == point.id }
                onSave(ExclusionEstimate.pushToEstimate(job.pricing.copy(exclusionPoints = points)))
            }) { Text("Delete", color = ErrorRed) }
        }
    }
}

@Composable
fun JobSignatureCard(job: Job, customerName: String, onSave: (JobPricing) -> Unit) {
    var document by remember(job.id) { mutableStateOf(SignatureRules.ESTIMATE) }
    val record = SignatureRules.find(job.pricing, document)
    val manual = job.pricing.isManual(SignatureRules.manualKey(document))
    var name by remember(job.id, document, record?.signerName, manual) {
        mutableStateOf(SignatureRules.editorName(record, customerName, manual))
    }
    var ink by remember(job.id, document, record?.pngBase64) { mutableStateOf(record?.pngBase64.orEmpty()) }
    BatchCard(title = "Customer signature") {
        Text(
            "Draw on the pad or type a name. The name, ink, and time are embedded in the estimate or contract PDF.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = document == SignatureRules.ESTIMATE,
                onClick = { document = SignatureRules.ESTIMATE },
                label = { Text("Estimate") }
            )
            FilterChip(
                selected = document == SignatureRules.CONTRACT,
                onClick = { document = SignatureRules.CONTRACT },
                label = { Text("Contract") }
            )
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Signer name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        SignatureCanvas(
            onInk = { bitmap -> ink = SignatureInk.encodePng(bitmap) }
        )
        if (ink.isNotBlank()) {
            val preview = remember(ink) { SignatureInk.decodePng(ink) }
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "Saved signature",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(PaperWhite)
                )
            }
        }
        Row {
            TextButton(onClick = { ink = "" }) { Text("Clear ink", color = TextSecondary) }
            TextButton(onClick = {
                val saved = SignatureRules.save(
                    pricing = job.pricing,
                    document = document,
                    signerName = name,
                    signedAt = System.currentTimeMillis(),
                    pngBase64 = ink
                )
                onSave(saved)
            }) { Text("Save signature", color = PrimaryGreen) }
        }
        val caption = SignatureRules.embedCaption(SignatureRules.find(job.pricing, document))
        if (caption.isNotBlank()) {
            Text("On the PDF: $caption", color = TextTertiary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SignatureCanvas(onInk: (Bitmap) -> Unit) {
    val density = LocalDensity.current.density
    val strokes = remember { mutableStateOf(listOf<List<Offset>>()) }
    var current by remember { mutableStateOf(listOf<Offset>()) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .background(PaperWhite, RoundedCornerShape(8.dp))
            .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset -> current = listOf(offset) },
                    onDragEnd = {
                        if (current.size > 1) {
                            strokes.value = strokes.value + listOf(current)
                            onInk(renderSignature(strokes.value, size.width, size.height, density))
                        }
                        current = emptyList()
                    },
                    onDrag = { change, _ ->
                        current = current + change.position
                        change.consume()
                    }
                )
            }
    ) {
        val paintColor = Color(0xFF1A1A1A)
        (strokes.value + listOf(current)).forEach { stroke ->
            if (stroke.size > 1) {
                for (i in 1 until stroke.size) {
                    drawLine(paintColor, stroke[i - 1], stroke[i], strokeWidth = 3.dp.toPx())
                }
            }
        }
    }
}

private fun renderSignature(strokes: List<List<Offset>>, width: Int, height: Int, density: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(26, 26, 26)
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { stroke ->
        if (stroke.size < 2) return@forEach
        val path = Path()
        path.moveTo(stroke.first().x, stroke.first().y)
        stroke.drop(1).forEach { path.lineTo(it.x, it.y) }
        canvas.drawPath(path, paint)
    }
    return bitmap
}

@Composable
private fun BatchCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

private fun openSmsDraft(context: Context, phone: String, body: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${phone.trim()}")).apply {
        putExtra("sms_body", body)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}
