package com.strobingn.wildlifefieldops.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.widget.Toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strobingn.wildlifefieldops.util.ContractDocumentType
import com.strobingn.wildlifefieldops.util.WildlifeWhispererContractPdf
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.*
import com.strobingn.wildlifefieldops.pricing.EstimateInvoiceCarry
import com.strobingn.wildlifefieldops.pricing.InvoicePricingInputs
import com.strobingn.wildlifefieldops.pricing.Money
import com.strobingn.wildlifefieldops.pricing.MoneyField
import com.strobingn.wildlifefieldops.pricing.PricingCalculator
import com.strobingn.wildlifefieldops.pricing.calculatedTotal
import com.strobingn.wildlifefieldops.pricing.effectiveTotal
import com.strobingn.wildlifefieldops.ui.components.CompactOverridableAmount
import com.strobingn.wildlifefieldops.ui.components.OverridableAmountField
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.CountyTaxState
import com.strobingn.wildlifefieldops.ui.viewmodel.InvoiceViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceScreen(
    jobId: String,
    onBack: () -> Unit,
    jobsViewModel: JobsViewModel = hiltViewModel(),
    invoiceViewModel: InvoiceViewModel = hiltViewModel()
) {
    val job by jobsViewModel.getJobById(jobId).collectAsState(initial = null)
    val context = LocalContext.current
    val countyTaxState by invoiceViewModel.countyTaxState.collectAsState()
    val existingInvoices by invoiceViewModel.getInvoicesByJob(jobId).collectAsState(initial = null)

    // Trigger county lookup once the job is loaded.
    LaunchedEffect(job?.id) {
        job?.let { invoiceViewModel.resolveCountyTax(it) }
    }

    var lineItems by remember { mutableStateOf<List<InvoiceLineItem>>(emptyList()) }
    var taxRate by remember { mutableStateOf("8.0") }
    var taxRateManual by remember { mutableStateOf(false) }
    var seededFromEstimate by remember { mutableStateOf(false) }
    var sessionDirty by remember { mutableStateOf(false) }
    var appliedKey by remember { mutableStateOf<String?>(null) }
    var boundInvoiceId by remember { mutableStateOf<String?>(null) }
    var discountPercent by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf(EstimateInvoiceCarry.DEFAULT_TERMS) }
    var subtotalOverride by remember { mutableStateOf<Double?>(null) }
    var taxAmountOverride by remember { mutableStateOf<Double?>(null) }
    var discountAmountOverride by remember { mutableStateOf<Double?>(null) }
    var totalOverride by remember { mutableStateOf<Double?>(null) }
    var pdfPath by remember { mutableStateOf("") }
    var showPdfShare by remember { mutableStateOf(false) }
    var pdfBusy by remember { mutableStateOf(false) }
    val pdfScope = rememberCoroutineScope()
    var showSignaturePad by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }
    var showNoEstimate by remember { mutableStateOf(false) }
    var technicianSignature by remember { mutableStateOf<Bitmap?>(null) }
    var customerSignature by remember { mutableStateOf<Bitmap?>(null) }

    fun touch() {
        sessionDirty = true
    }

    fun applyForm(form: EstimateInvoiceCarry.InvoiceFormState, fromEstimate: Boolean) {
        lineItems = form.lineItems
        taxRate = EstimateInvoiceCarry.formatNumber(form.taxRate)
        taxRateManual = form.taxRateManual
        discountPercent = EstimateInvoiceCarry.formatNumber(form.discountPercent)
        notes = form.notes
        terms = form.terms
        subtotalOverride = form.subtotalOverride
        taxAmountOverride = form.taxAmountOverride
        discountAmountOverride = form.discountAmountOverride
        totalOverride = form.totalOverride
        seededFromEstimate = fromEstimate
    }

    fun currentForm(): EstimateInvoiceCarry.InvoiceFormState = EstimateInvoiceCarry.InvoiceFormState(
        lineItems = lineItems,
        taxRate = taxRate.toDoubleOrNull() ?: 0.0,
        taxRateManual = taxRateManual,
        discountPercent = discountPercent.toDoubleOrNull() ?: 0.0,
        notes = notes,
        terms = terms,
        subtotalOverride = subtotalOverride,
        taxAmountOverride = taxAmountOverride,
        discountAmountOverride = discountAmountOverride,
        totalOverride = totalOverride,
        manuallyEdited = sessionDirty
    )

    LaunchedEffect(jobId) {
        sessionDirty = false
        appliedKey = null
        boundInvoiceId = null
        seededFromEstimate = false
    }

    LaunchedEffect(job, existingInvoices, sessionDirty) {
        if (sessionDirty) return@LaunchedEffect
        val current = job ?: return@LaunchedEffect
        val invoices = existingInvoices ?: return@LaunchedEffect
        val saved = boundInvoiceId?.let { id -> invoices.find { it.id == id } }
            ?: invoices.maxByOrNull { it.updatedAt }
        val plan = EstimateInvoiceCarry.resolveOpen(current.pricing, current.estimatedValue, saved)
        if (plan.existingId != null) boundInvoiceId = plan.existingId
        if (plan.contentKey == appliedKey) return@LaunchedEffect
        applyForm(plan.form, fromEstimate = plan.kind == EstimateInvoiceCarry.OpenKind.ESTIMATE)
        appliedKey = plan.contentKey
        if (plan.kind == EstimateInvoiceCarry.OpenKind.ESTIMATE && plan.existingId != null) {
            invoiceViewModel.refreshUntouchedFromEstimate(current.id, plan.existingId)
        }
    }

    // County fills tax only before an estimate or a typed rate is on the form.
    LaunchedEffect(countyTaxState, seededFromEstimate, taxRateManual) {
        if (taxRateManual || seededFromEstimate || sessionDirty) return@LaunchedEffect
        if (countyTaxState is CountyTaxState.Resolved) {
            val rate = (countyTaxState as CountyTaxState.Resolved).ratePercent
            taxRate = EstimateInvoiceCarry.formatNumber(rate)
        }
    }

    fun estimateWorksheet(): com.strobingn.wildlifefieldops.pricing.JobPricing? {
        val current = job ?: return null
        return EstimateInvoiceCarry.worksheetForCarry(current.pricing, current.estimatedValue)
    }

    fun applyEstimateCopy(choice: EstimateInvoiceCarry.CopyChoice) {
        val worksheet = estimateWorksheet() ?: return
        val incoming = EstimateInvoiceCarry.lineItems(worksheet)
        lineItems = EstimateInvoiceCarry.applyCopy(lineItems, incoming, choice)
        touch()
        invoiceViewModel.saveEditorInvoice(
            jobId = jobId,
            existingId = boundInvoiceId,
            form = currentForm(),
            manuallyEdited = true,
            markJobInvoiced = false
        )
    }

    val priced = PricingCalculator.computeInvoice(
        InvoicePricingInputs(
            lineItems = lineItems,
            taxRatePercent = taxRate.toDoubleOrNull() ?: 0.0,
            discountPercent = discountPercent.toDoubleOrNull() ?: 0.0,
            subtotalOverride = subtotalOverride,
            discountAmountOverride = discountAmountOverride,
            taxAmountOverride = taxAmountOverride,
            totalOverride = totalOverride
        )
    )
    val subtotal = priced.subtotal.effective
    val discountAmount = priced.discountAmount.effective
    val taxAmount = priced.taxAmount.effective
    val total = priced.total.effective

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Invoice", color = TextPrimary) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Job info header
            job?.let { currentJob ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Invoice For", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(currentJob.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (currentJob.customerName.isNotBlank()) {
                            Text(currentJob.customerName, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                        }
                        Text(currentJob.address, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                    }
                }
            }

            // Line Items
            Card(
                colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Line Items", style = MaterialTheme.typography.titleSmall, color = TextPrimary, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val worksheet = estimateWorksheet()
                            if (worksheet == null) {
                                showNoEstimate = true
                            } else if (EstimateInvoiceCarry.needsCopyConfirm(lineItems)) {
                                showCopyDialog = true
                            } else {
                                applyEstimateCopy(EstimateInvoiceCarry.CopyChoice.REPLACE)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                    ) {
                        Text("Copy from estimate")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    // Headers
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Description", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        Text("Qty", modifier = Modifier.weight(0.5f), style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                        Text("Price", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = TextTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        Text("Total", modifier = Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = TextTertiary, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                    }

                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = DividerDark)

                    lineItems.forEachIndexed { index, item ->
                        InvoiceLineItemRow(
                            item = item,
                            onUpdate = { updated ->
                                touch()
                                lineItems = lineItems.toMutableList().apply { set(index, updated) }
                            },
                            onRemove = {
                                touch()
                                lineItems = lineItems.toMutableList().apply { removeAt(index) }
                            }
                        )
                    }

                    // Add line item button
                    TextButton(
                        onClick = {
                            touch()
                            lineItems = lineItems + InvoiceLineItem(description = "", quantity = 1.0, unit = "ea", unitPrice = 0.0)
                        },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = PrimaryGreen, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Line Item", color = PrimaryGreen)
                    }
                }
            }

            // Tax & Discount
            Card(
                colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        InvoiceField("Tax %", taxRate, {
                            touch()
                            taxRate = it.filter { c -> c.isDigit() || c == '.' }
                            taxRateManual = true
                        }, Modifier.weight(1f))
                        InvoiceField("Discount %", discountPercent, {
                            touch()
                            discountPercent = it.filter { c -> c.isDigit() || c == '.' }
                        }, Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    CountyTaxLabel(
                        state = countyTaxState,
                        onRefresh = { job?.let { invoiceViewModel.refreshCountyTax(it) } }
                    )
                }
            }

            // Notes & Terms
            Card(
                colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = {
                            touch()
                            notes = it
                        },
                        label = { Text("Invoice Notes") },
                        colors = invoiceFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = terms,
                        onValueChange = {
                            touch()
                            terms = it
                        },
                        label = { Text("Payment Terms") },
                        colors = invoiceFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 2
                    )
                }
            }

            // Totals
            Card(
                colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverridableAmountField(
                        label = "Subtotal",
                        field = priced.subtotal,
                        onOverride = {
                            touch()
                            subtotalOverride = Money.round(it)
                        },
                        onReset = {
                            touch()
                            subtotalOverride = null
                        }
                    )
                    OverridableAmountField(
                        label = "Discount $",
                        field = priced.discountAmount,
                        onOverride = {
                            touch()
                            discountAmountOverride = Money.round(it)
                        },
                        onReset = {
                            touch()
                            discountAmountOverride = null
                        }
                    )
                    OverridableAmountField(
                        label = "Tax $",
                        field = priced.taxAmount,
                        onOverride = {
                            touch()
                            taxAmountOverride = Money.round(it)
                        },
                        onReset = {
                            touch()
                            taxAmountOverride = null
                        }
                    )
                    Divider(modifier = Modifier.padding(vertical = 4.dp), color = BorderDark)
                    OverridableAmountField(
                        label = "TOTAL",
                        field = priced.total,
                        onOverride = {
                            touch()
                            totalOverride = Money.round(it)
                        },
                        onReset = {
                            touch()
                            totalOverride = null
                        },
                        emphasized = true
                    )
                    val recorded = job?.pricing?.payments.orEmpty()
                    val balance = com.strobingn.wildlifefieldops.ai.fieldops.PaymentLedger.balanceDue(
                        priced.total.effective,
                        recorded
                    )
                    Text(
                        "Paid ${com.strobingn.wildlifefieldops.pricing.Money.formatUsd(com.strobingn.wildlifefieldops.ai.fieldops.PaymentLedger.totalPaid(recorded))} · Balance due ${com.strobingn.wildlifefieldops.pricing.Money.formatUsd(balance)}",
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    job?.let { current ->
                        OutlinedButton(
                            onClick = {
                                val path = com.strobingn.wildlifefieldops.util.WildlifeWhispererReceiptPdf.generate(
                                    context,
                                    current,
                                    priced.total.effective,
                                    recorded
                                )
                                WildlifeWhispererContractPdf.share(context, path, "Share receipt")
                            },
                            enabled = recorded.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Share receipt PDF") }
                    }
                }
            }

            // Signature Section
            if (technicianSignature != null || customerSignature != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BackgroundCard),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        technicianSignature?.let {
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Technician", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.foundation.Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Technician Signature",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                )
                            }
                        }
                        customerSignature?.let {
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Customer", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Spacer(modifier = Modifier.height(4.dp))
                                androidx.compose.foundation.Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "Customer Signature",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showSignaturePad = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = OnPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Draw, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sign", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        job?.takeIf { !pdfBusy }?.let { currentJob ->
                            val contractSig = com.strobingn.wildlifefieldops.ai.fieldops.SignatureRules
                                .find(currentJob.pricing, com.strobingn.wildlifefieldops.ai.fieldops.SignatureRules.CONTRACT)
                                ?: com.strobingn.wildlifefieldops.ai.fieldops.SignatureRules
                                    .find(currentJob.pricing, com.strobingn.wildlifefieldops.ai.fieldops.SignatureRules.ESTIMATE)
                            val saved = existingInvoices?.let { list ->
                                boundInvoiceId?.let { id -> list.find { it.id == id } }
                                    ?: list.maxByOrNull { it.updatedAt }
                            }
                            pdfBusy = true
                            pdfScope.launch {
                                try {
                                    pdfPath = withContext(Dispatchers.IO) {
                                        generateInvoicePDF(
                                        context = context,
                                        job = currentJob,
                                        lineItems = lineItems,
                                        subtotal = subtotal,
                                        taxRate = taxRate.toDoubleOrNull() ?: 0.0,
                                        taxAmount = taxAmount,
                                        discountAmount = discountAmount,
                                        total = total,
                                        notes = notes,
                                        terms = terms,
                                        technicianSignature = technicianSignature,
                                        customerSignature = customerSignature
                                            ?: com.strobingn.wildlifefieldops.util.SignatureInk.decodePng(contractSig?.pngBase64.orEmpty()),
                                        customerSignerName = contractSig?.signerName.orEmpty(),
                                        customerSignedAtMillis = contractSig?.signedAt,
                                        balanceDue = com.strobingn.wildlifefieldops.ai.fieldops.PaymentLedger.balanceDue(
                                            total,
                                            currentJob.pricing.payments
                                        ),
                                        documentNumber = saved?.invoiceNumber.orEmpty(),
                                        dueDateMillis = saved?.dueDate?.takeIf { it > 0L },
                                        invoiceDateMillis = saved?.issueDate?.takeIf { it > 0L },
                                        amountPaid = com.strobingn.wildlifefieldops.ai.fieldops.PaymentLedger.totalPaid(
                                            currentJob.pricing.payments
                                        ),
                                        payments = currentJob.pricing.payments,
                                            customerEmail = saved?.customerEmail.orEmpty()
                                        )
                                    }
                                    showPdfShare = true
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (t: Throwable) {
                                    Toast.makeText(
                                        context,
                                        "Could not create the PDF: ${t.message ?: t.javaClass.simpleName}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } finally {
                                    pdfBusy = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Generate PDF", fontWeight = FontWeight.Bold)
                }
            }

            // Save to database
            Button(
                onClick = {
                    touch()
                    invoiceViewModel.saveEditorInvoice(
                        jobId = jobId,
                        existingId = boundInvoiceId,
                        form = currentForm(),
                        manuallyEdited = true,
                        markJobInvoiced = true
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple, contentColor = OnPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save Invoice to Database", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Signature Pad Dialog
    if (showSignaturePad) {
        SignaturePadDialog(
            onDismiss = { showSignaturePad = false },
            onSave = { bitmap ->
                if (technicianSignature == null) {
                    technicianSignature = bitmap
                } else {
                    customerSignature = bitmap
                }
                showSignaturePad = false
            }
        )
    }

    // PDF Share Dialog
    if (showPdfShare && pdfPath.isNotBlank()) {
        PdfShareDialog(
            pdfPath = pdfPath,
            onDismiss = { showPdfShare = false },
            onShare = { sharePDF(context, pdfPath) },
            onView = { viewPDF(context, pdfPath) },
            onPrint = { WildlifeWhispererContractPdf.print(context, pdfPath, "Invoice") },
            onEmail = {
                com.strobingn.wildlifefieldops.util.StandardDocumentPdf.emailWithPdf(
                    context,
                    pdfPath,
                    "",
                    "Invoice",
                    "Invoice attached.",
                    "Email invoice"
                )
            }
        )
    }

    if (showCopyDialog) {
        AlertDialog(
            onDismissRequest = { showCopyDialog = false },
            title = { Text("Copy from estimate", color = TextPrimary) },
            text = {
                Text(
                    "This invoice already has line items. Replace them, or add estimate lines that are not already here.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        showCopyDialog = false
                        applyEstimateCopy(EstimateInvoiceCarry.CopyChoice.REPLACE)
                    }) { Text("Replace", color = TextPrimary) }
                    TextButton(onClick = {
                        showCopyDialog = false
                        applyEstimateCopy(EstimateInvoiceCarry.CopyChoice.ADD)
                    }) { Text("Add", color = TextPrimary) }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCopyDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BackgroundCard
        )
    }

    if (showNoEstimate) {
        AlertDialog(
            onDismissRequest = { showNoEstimate = false },
            title = { Text("Copy from estimate", color = TextPrimary) },
            text = { Text("This job has no estimate yet.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { showNoEstimate = false }) {
                    Text("OK", color = TextPrimary)
                }
            },
            containerColor = BackgroundCard
        )
    }
}

/**
 * Small informational row shown beneath the Tax % field.
 * Displays the resolved county name and rate, a loading indicator, or a hint to
 * set the rate manually when county resolution failed.
 */
@Composable
private fun CountyTaxLabel(
    state: CountyTaxState,
    onRefresh: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (state) {
            is CountyTaxState.Idle -> Unit

            is CountyTaxState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                    color = TextSecondary
                )
                Text(
                    "Resolving county…",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            is CountyTaxState.Resolved -> {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    state.displayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = PrimaryGreen
                )
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh county",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            is CountyTaxState.Unknown -> {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "County unknown — set tax % manually",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Retry county lookup",
                        tint = TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceLineItemRow(
    item: InvoiceLineItem,
    onUpdate: (InvoiceLineItem) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        OutlinedTextField(
            value = item.description,
            onValueChange = { onUpdate(item.copy(description = it)) },
            placeholder = { Text("Description", color = TextTertiary) },
            colors = invoiceFieldColors(),
            modifier = Modifier.weight(2f),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp)
        )
        OutlinedTextField(
            value = item.quantity.toString(),
            onValueChange = {
                it.toDoubleOrNull()?.let { q -> onUpdate(item.copy(quantity = q)) }
            },
            colors = invoiceFieldColors(),
            modifier = Modifier.weight(0.5f),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        OutlinedTextField(
            value = item.unitPrice.toString(),
            onValueChange = {
                it.toDoubleOrNull()?.let { p -> onUpdate(item.copy(unitPrice = p)) }
            },
            colors = invoiceFieldColors(),
            modifier = Modifier.weight(0.7f),
            shape = RoundedCornerShape(8.dp),
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        CompactOverridableAmount(
            field = MoneyField(item.calculatedTotal(), item.totalOverride),
            onOverride = { onUpdate(item.copy(totalOverride = Money.round(it), total = Money.round(it))) },
            onReset = { onUpdate(item.copy(totalOverride = null, total = item.calculatedTotal())) },
            modifier = Modifier.weight(0.8f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = ErrorRed.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun InvoiceField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        colors = invoiceFieldColors(),
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(12.dp),
        singleLine = true
    )
}

@Composable
private fun InvoiceTotalRow(label: String, amount: Double, color: Color = TextSecondary) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = color)
        Text("$${String.format("%.2f", amount)}", style = MaterialTheme.typography.bodySmall, color = if (amount < 0) SuccessGreen else TextPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun invoiceFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = BackgroundDark,
    unfocusedContainerColor = BackgroundDark
)

private fun generateInvoicePDF(
    context: Context,
    job: Job,
    lineItems: List<InvoiceLineItem>,
    subtotal: Double,
    taxRate: Double,
    taxAmount: Double,
    discountAmount: Double,
    total: Double,
    notes: String,
    terms: String,
    technicianSignature: Bitmap?,
    customerSignature: Bitmap?,
    customerSignerName: String = "",
    customerSignedAtMillis: Long? = null,
    balanceDue: Double? = null,
    documentNumber: String = "",
    dueDateMillis: Long? = null,
    invoiceDateMillis: Long? = null,
    amountPaid: Double? = null,
    payments: List<com.strobingn.wildlifefieldops.pricing.JobPaymentRecord> = emptyList(),
    customerEmail: String = ""
): String {
    return WildlifeWhispererContractPdf.generate(
        context = context,
        documentType = ContractDocumentType.INVOICE,
        job = job,
        lineItems = lineItems,
        subtotal = subtotal,
        taxRate = taxRate,
        taxAmount = taxAmount,
        discountAmount = discountAmount,
        total = total,
        notes = notes,
        terms = terms,
        technicianSignature = technicianSignature,
        customerSignature = customerSignature,
        customerSignerName = customerSignerName,
        customerSignedAtMillis = customerSignedAtMillis,
        balanceDue = balanceDue,
        documentNumber = documentNumber,
        dueDateMillis = dueDateMillis,
        invoiceDateMillis = invoiceDateMillis,
        amountPaid = amountPaid,
        payments = payments,
        customerEmail = customerEmail
    )
}

private fun sharePDF(context: Context, path: String) {
    WildlifeWhispererContractPdf.share(context, path, "Share Invoice")
}

private fun viewPDF(context: Context, path: String) {
    WildlifeWhispererContractPdf.view(context, path)
}

@Composable
private fun SignaturePadDialog(onDismiss: () -> Unit, onSave: (Bitmap) -> Unit) {
    val density = LocalContext.current.resources.displayMetrics.density
    // Store touch points as line segments: each segment is (x1, y1, x2, y2)
    val lineSegments = remember { mutableStateListOf<android.graphics.PointF>() }
    var currentStart by remember { mutableStateOf<android.graphics.PointF?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign Here", color = TextPrimary) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentStart = android.graphics.PointF(offset.x, offset.y)
                                },
                                onDrag = { change, _ ->
                                    val end = android.graphics.PointF(change.position.x, change.position.y)
                                    val start = currentStart ?: end
                                    lineSegments.add(start)
                                    lineSegments.add(end)
                                    currentStart = end
                                },
                                onDragEnd = {
                                    currentStart = null
                                }
                            )
                        }
                ) {
                    // Draw line segments as continuous paths
                    val path = androidx.compose.ui.graphics.Path()
                    for (i in 0 until lineSegments.size step 2) {
                        val start = lineSegments.getOrNull(i) ?: continue
                        val end = lineSegments.getOrNull(i + 1) ?: continue
                        path.moveTo(start.x, start.y)
                        path.lineTo(end.x, end.y)
                    }
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 3f,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                }
                if (lineSegments.isEmpty()) {
                    Text(
                        "Sign with your finger",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color(0xFF616161)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Render line segments directly to Android Bitmap
                    val widthPx = (600f * density).toInt().coerceAtLeast(1)
                    val heightPx = (200f * density).toInt().coerceAtLeast(1)
                    val bm = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    val androidCanvas = android.graphics.Canvas(bm)
                    androidCanvas.drawColor(AndroidColor.WHITE)
                    val paint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        color = AndroidColor.BLACK
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 3f * density
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    val androidPath = android.graphics.Path()
                    for (i in 0 until lineSegments.size step 2) {
                        val start = lineSegments.getOrNull(i) ?: continue
                        val end = lineSegments.getOrNull(i + 1) ?: continue
                        // Scale coordinates from dp to pixels
                        val x1 = start.x * density
                        val y1 = start.y * density
                        val x2 = end.x * density
                        val y2 = end.y * density
                        androidPath.moveTo(x1, y1)
                        androidPath.lineTo(x2, y2)
                    }
                    androidCanvas.drawPath(androidPath, paint)
                    onSave(bm)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) {
                Text("Save Signature", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { lineSegments.clear() }) {
                Text("Clear", color = TextSecondary)
            }
        },
        containerColor = BackgroundCard
    )
}

@Composable
private fun PdfShareDialog(
    pdfPath: String,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onView: () -> Unit,
    onPrint: () -> Unit,
    onEmail: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invoice Generated", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfPagePreview(pdfPath)
                Text("PDF saved. Share, print, or email this invoice.", color = TextSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onEmail) { Text("Email", color = TextPrimary) }
                TextButton(onClick = onPrint) { Text("Print", color = TextPrimary) }
                TextButton(onClick = onView) { Text("View PDF", color = TextPrimary) }
            }
        },
        containerColor = BackgroundCard
    )
}
