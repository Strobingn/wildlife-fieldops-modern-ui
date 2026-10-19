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
    var shareWarning by remember { mutableStateOf<List<String>?>(null) }
    var sharePricing by remember { mutableStateOf<JobPricing?>(null) }

    fun pending(): JobPricing = SignatureCardSave.pending(
        pricing = job.pricing, document = document,
        customerName = name, customerInk = ink,
        techName = techName, techInk = techInk,
        now = System.currentTimeMillis(),
        // An auto-filled Settings signature on an existing record keeps that record's first stamp.
        techStamp = techResolved.signedAt.takeIf { techResolved.fromSaved && techRecord != null && it > 0L && techInk == techResolved.pngBase64 }
    )
    /** Share saves pending ink first, so the PDF's ink and dates come from the same saved records. */
    fun startShare() {
        val next = pending()
        if (next != job.pricing) onSave(next)
        sharePricing = next
    }

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
                    padKey = "$document#$customerPad".hashCode(),
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
                    padKey = "$document#$techPad".hashCode(),
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
                TextButton(onClick = { onSave(pending()) }) { Text("Save signatures", color = PrimaryGreen) }
                TextButton(onClick = {
                    val missing = SignatureShareGuard.missing(customerInk = ink, technicianInk = techInk)
                    if (missing.isNotEmpty()) { shareWarning = missing; return@TextButton }
                    startShare()
                }) { Text("Share contract PDF", color = TextPrimary) }
            }
            shareWarning?.let { missing ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { shareWarning = null },
                    title = { Text(missing.joinToString("\n"), color = TextPrimary) },
                    text = { Text("That signature area will be left blank on the PDF.", color = TextSecondary) },
                    confirmButton = { TextButton(onClick = { shareWarning = null; startShare() }) { Text("Share anyway", color = TextPrimary) } },
                    dismissButton = { TextButton(onClick = { shareWarning = null }) { Text("Cancel", color = TextPrimary) } }
                )
            }
            sharePricing?.let { pricing ->
                androidx.compose.runtime.LaunchedEffect(pricing) {
                    sharePricing = null
                    val result = com.strobingn.wildlifefieldops.pricing.PricingCalculator.compute(pricing)
                    val items = com.strobingn.wildlifefieldops.pricing.EstimateInvoiceCarry.lineItems(pricing, result)
                    val sig = SignatureCardSave.forPdf(pricing, document, saved)
                    runCatching {
                        val path = com.strobingn.wildlifefieldops.util.WildlifeWhispererContractPdf.generate(
                            context = context,
                            documentType = com.strobingn.wildlifefieldops.util.ContractDocumentType.CONTRACT,
                            job = job.copy(pricing = pricing),
                            lineItems = items,
                            subtotal = result.subtotal.effective,
                            taxRate = pricing.taxRatePercent,
                            taxAmount = result.taxAmount.effective,
                            discountAmount = result.discountAmount.effective,
                            total = result.total.effective,
                            notes = listOf(pricing.notes, pricing.rationale).filter { it.isNotBlank() }.joinToString("\n"),
                            customerSignature = SignatureInk.decodePng(sig.customerInk),
                            customerSignerName = sig.customerName.ifBlank { name },
                            customerSignedAtMillis = sig.customerSignedAt,
                            technicianSignature = SignatureInk.decodePng(sig.techInk),
                            technicianSignerName = sig.techName,
                            technicianSignedAtMillis = sig.techSignedAt,
                            technicianSignatureCleared = sig.techCleared
                        )
                        com.strobingn.wildlifefieldops.util.WildlifeWhispererContractPdf.share(context, path, "Share contract")
                    }.onFailure {
                        android.widget.Toast.makeText(context, "Couldn't create the PDF: ${it.message.orEmpty()}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
            val caption = SignatureShareGuard.caption(SignatureRules.find(job.pricing, document), TechSignatureRules.find(job.pricing, document))
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
        SignatureCanvas(
            onInk = { bmp: Bitmap -> onInk(SignatureInk.encodePng(bmp)) },
            resetKey = padKey,
            height = padHeight,
            tag = "pad-$title",
            // The pad shows exactly the ink that will be saved and printed (one copy, no stale strokes).
            savedInk = ink
        )
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


/** Share checks and the "On the PDF:" line: a person is listed only once their drawn ink is saved. */
object SignatureShareGuard {
    fun missing(customerInk: String, technicianInk: String): List<String> = buildList {
        if (customerInk.isBlank()) add("Customer hasn't signed yet")
        if (technicianInk.isBlank()) add("Technician hasn't signed yet")
    }

    fun caption(
        customer: com.strobingn.wildlifefieldops.pricing.CustomerSignatureRecord?,
        technician: com.strobingn.wildlifefieldops.pricing.CustomerSignatureRecord?
    ): String = listOfNotNull(
        customer?.takeIf { it.pngBase64.isNotBlank() }?.let { listOf("Customer", it.signerName).filter { s -> s.isNotBlank() }.joinToString(" ") + " · ${SignatureRules.formatStamp(it.signedAt)}" },
        technician?.takeIf { it.pngBase64.isNotBlank() && !it.cleared }?.let { listOf("Technician", it.signerName).filter { s -> s.isNotBlank() }.joinToString(" ") + " · ${SignatureRules.formatStamp(it.signedAt)}" }
    ).joinToString("; ")
}

/** Save and Share use the same rules, so the shared PDF always matches what was saved. */
object SignatureCardSave {
    fun pending(
        pricing: JobPricing,
        document: String,
        customerName: String,
        customerInk: String,
        techName: String,
        techInk: String,
        now: Long,
        techStamp: Long? = null
    ): JobPricing {
        val record = SignatureRules.find(pricing, document)
        val techRecord = TechSignatureRules.find(pricing, document)
        // Adding the technician signature to an already customer-signed document keeps the
        // customer's ink, name and signed date exactly as they were.
        val customerUnchanged = record != null && record.signerName == customerName.trim() && record.pngBase64 == customerInk
        // No ink and nothing saved before: don't stamp a name/date as if the customer signed.
        val customerSkip = customerInk.isBlank() && (record == null || record.pngBase64.isBlank())
        val withCustomer = if (customerUnchanged || customerSkip) pricing else SignatureRules.save(pricing, document, customerName, now, customerInk)
        val techUnchanged = techRecord != null && !techRecord.cleared && techRecord.signerName == techName.trim() && techRecord.pngBase64 == techInk
        val techSkip = techInk.isBlank() && (techRecord == null || techRecord.pngBase64.isBlank())
        return if (techSkip || techUnchanged) withCustomer else TechSignatureRules.save(withCustomer, document, techName, techStamp ?: now, techInk)
    }

    data class PdfSignatures(
        val customerInk: String,
        val customerName: String,
        val customerSignedAt: Long?,
        val techInk: String,
        val techName: String,
        val techSignedAt: Long?,
        val techCleared: Boolean
    )

    /** Ink and date always come from the same saved record. */
    fun forPdf(pricing: JobPricing, document: String, saved: TechnicianSignature, now: Long = System.currentTimeMillis()): PdfSignatures {
        // Typed-only records keep their name and date; their area just has no ink.
        val c = SignatureRules.find(pricing, document)
        val t = TechSignatureRules.resolve(pricing, document, saved, now)
        return PdfSignatures(
            customerInk = c?.pngBase64.orEmpty(),
            customerName = c?.signerName.orEmpty(),
            customerSignedAt = c?.signedAt?.takeIf { it > 0L },
            techInk = t.pngBase64,
            techName = t.name,
            techSignedAt = t.signedAt.takeIf { it > 0L && !t.cleared },
            techCleared = t.cleared || (!t.hasInk && t.name.isBlank())
        )
    }
}
