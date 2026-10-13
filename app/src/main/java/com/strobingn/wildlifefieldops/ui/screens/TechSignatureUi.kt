package com.strobingn.wildlifefieldops.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.ai.fieldops.SignatureRules
import com.strobingn.wildlifefieldops.ai.fieldops.TechSignatureRules
import com.strobingn.wildlifefieldops.ai.fieldops.TechnicianSignature
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.isManual
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.PaperWhite
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.util.SignatureInk
import com.strobingn.wildlifefieldops.util.TechnicianSignatureStore

/**
 * Customer and technician signatures side by side for the estimate,
 * contract and inspection report. The technician box starts with Sir's
 * Settings signature; he can re-sign or clear it per document.
 */
@Composable
fun JobSignatureCard(
    job: Job,
    customerName: String,
    onSave: (JobPricing) -> Unit,
    savedSignature: TechnicianSignature? = null
) {
    val context = LocalContext.current
    val saved = savedSignature ?: remember { TechnicianSignatureStore.load(context) }
    var document by remember(job.id) { mutableStateOf(SignatureRules.ESTIMATE) }
    val record = SignatureRules.find(job.pricing, document)
    val manual = job.pricing.isManual(SignatureRules.manualKey(document))
    var name by remember(job.id, document, record?.signerName, manual) {
        mutableStateOf(SignatureRules.editorName(record, customerName, manual))
    }
    var ink by remember(job.id, document, record?.pngBase64) { mutableStateOf(record?.pngBase64.orEmpty()) }
    var customerPad by remember { mutableIntStateOf(0) }

    val techRecord = TechSignatureRules.find(job.pricing, document)
    val techResolved = TechSignatureRules.resolve(techRecord, saved)
    var techName by remember(job.id, document, techRecord, saved) { mutableStateOf(techResolved.name) }
    var techInk by remember(job.id, document, techRecord, saved) { mutableStateOf(techResolved.pngBase64) }
    var techPad by remember { mutableIntStateOf(0) }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Signatures", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Customer and technician sign side by side. Names, ink, and dates go on the PDF.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SignatureRules.ESTIMATE to "Estimate",
                    SignatureRules.CONTRACT to "Contract",
                    TechSignatureRules.INSPECTION to "Inspection"
                ).forEach { (doc, label) ->
                    FilterChip(selected = document == doc, onClick = { document = doc }, label = { Text(label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SignatureBox(
                    title = "Customer signature",
                    name = name,
                    onName = { name = it },
                    ink = ink,
                    padKey = customerPad,
                    onInk = { ink = it },
                    onClear = { ink = ""; customerPad++ },
                    dateText = SignatureRules.formatStamp(record?.signedAt ?: 0L).substringBefore(' '),
                    modifier = Modifier.weight(1f)
                )
                SignatureBox(
                    title = "Technician signature",
                    name = techName,
                    onName = { techName = it },
                    ink = techInk,
                    padKey = techPad,
                    onInk = { techInk = it },
                    onClear = {
                        techInk = ""
                        techName = ""
                        techPad++
                        // A cleared technician box stays blank through save, reload and sync.
                        onSave(TechSignatureRules.clear(job.pricing, document))
                    },
                    dateText = when {
                        techResolved.cleared -> ""
                        else -> SignatureRules.formatStamp(techResolved.signedAt).substringBefore(' ')
                    },
                    note = when {
                        techResolved.fromSaved -> "From your saved signature"
                        techResolved.cleared -> "Cleared for this document"
                        else -> ""
                    },
                    extra = if (saved.isSet && techInk != saved.pngBase64) {
                        { TextButton(onClick = { techInk = saved.pngBase64; techName = saved.name.ifBlank { techName } }) { Text("Use saved", color = TextPrimary) } }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }
            Row {
                TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    val withCustomer = SignatureRules.save(job.pricing, document, name, now, ink)
                    onSave(TechSignatureRules.save(withCustomer, document, techName, now, techInk))
                }) { Text("Save signatures", color = PrimaryGreen) }
                TextButton(onClick = {
                    val pricing = job.pricing
                    val result = com.strobingn.wildlifefieldops.pricing.PricingCalculator.compute(pricing)
                    val items = com.strobingn.wildlifefieldops.pricing.EstimateInvoiceCarry.lineItems(pricing, result)
                    val savedCustomer = SignatureRules.find(pricing, document)
                    val path = com.strobingn.wildlifefieldops.util.WildlifeWhispererContractPdf.generate(
                        context = context,
                        documentType = com.strobingn.wildlifefieldops.util.ContractDocumentType.CONTRACT,
                        job = job,
                        lineItems = items,
                        subtotal = result.subtotal.effective,
                        taxRate = pricing.taxRatePercent,
                        taxAmount = result.taxAmount.effective,
                        discountAmount = result.discountAmount.effective,
                        total = result.total.effective,
                        notes = listOf(pricing.notes, pricing.rationale).filter { it.isNotBlank() }.joinToString("\n"),
                        customerSignature = SignatureInk.decodePng(savedCustomer?.pngBase64?.ifBlank { ink }.orEmpty()),
                        customerSignerName = name.ifBlank { savedCustomer?.signerName.orEmpty() },
                        customerSignedAtMillis = savedCustomer?.signedAt,
                        technicianSignature = SignatureInk.decodePng(techInk),
                        technicianSignerName = techName,
                        technicianSignedAtMillis = techResolved.signedAt.takeIf { it > 0L },
                        technicianSignatureCleared = techInk.isBlank() && techName.isBlank()
                    )
                    com.strobingn.wildlifefieldops.util.WildlifeWhispererContractPdf.share(context, path, "Share contract")
                }) { Text("Share contract PDF", color = TextPrimary) }
            }
            val caption = SignatureRules.embedCaption(SignatureRules.find(job.pricing, document))
            if (caption.isNotBlank()) {
                Text("On the PDF: $caption", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun SignatureBox(
    title: String,
    name: String,
    onName: (String) -> Unit,
    ink: String,
    padKey: Int,
    onInk: (String) -> Unit,
    onClear: () -> Unit,
    dateText: String,
    modifier: Modifier = Modifier,
    note: String = "",
    padHeight: Dp = 110.dp,
    extra: (@Composable () -> Unit)? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        SignatureCanvas(onInk = { bmp: Bitmap -> onInk(SignatureInk.encodePng(bmp)) }, resetKey = padKey, height = padHeight)
        if (ink.isNotBlank()) {
            val preview = remember(ink) { SignatureInk.decodePng(ink) }
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = "$title saved",
                    modifier = Modifier.fillMaxWidth().height(48.dp).background(PaperWhite)
                )
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = onName,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (dateText.isBlank()) "Date: —" else "Date: $dateText",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClear) { Text("Clear", color = TextPrimary) }
        }
        if (note.isNotBlank()) Text(note, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        extra?.invoke()
    }
}

/** Settings: draw and save Sir's signature once. */
@Composable
fun TechSignatureSettingsSection(initial: TechnicianSignature? = null) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(initial ?: TechnicianSignatureStore.load(context)) }
    var name by remember { mutableStateOf(saved.name) }
    var ink by remember { mutableStateOf(saved.pngBase64) }
    var pad by remember { mutableIntStateOf(0) }
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Your signature", color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Draw it once. It fills the technician box on new estimates, contracts, invoices and inspection reports. You can re-sign or clear it on any document.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            SignatureBox(
                title = "Technician signature",
                name = name,
                onName = { name = it },
                ink = ink,
                padKey = pad,
                onInk = { ink = it },
                onClear = { ink = ""; pad++ },
                dateText = SignatureRules.formatStamp(saved.savedAt).substringBefore(' '),
                note = if (saved.pngBase64.isNotBlank()) "Saved" else "Not saved yet",
                padHeight = 140.dp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    TechnicianSignatureStore.save(context, ink, name)
                    saved = TechnicianSignatureStore.load(context)
                }) { Text("Save my signature", color = TextPrimary) }
                OutlinedButton(onClick = {
                    TechnicianSignatureStore.clear(context)
                    saved = TechnicianSignature()
                    ink = ""
                    name = ""
                    pad++
                }) { Text("Remove saved", color = TextPrimary) }
            }
        }
    }
}
