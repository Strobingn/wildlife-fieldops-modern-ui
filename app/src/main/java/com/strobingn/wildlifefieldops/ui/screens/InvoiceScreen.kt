package com.strobingn.wildlifefieldops.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
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
import androidx.core.content.FileProvider
import com.strobingn.wildlifefieldops.util.ContractDocumentType
import com.strobingn.wildlifefieldops.util.WildlifeWhispererContractPdf
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.data.model.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.CountyTaxState
import com.strobingn.wildlifefieldops.ui.viewmodel.InvoiceViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
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

    // Trigger county lookup once the job is loaded.
    LaunchedEffect(job) {
        job?.let { invoiceViewModel.resolveCountyTax(it) }
    }

    var lineItems by remember { mutableStateOf(listOf(
        InvoiceLineItem(description = "Wildlife Inspection", quantity = 1.0, unit = "ea", unitPrice = 150.0),
        InvoiceLineItem(description = "Live Trapping & Removal", quantity = 1.0, unit = "ea", unitPrice = 350.0),
        InvoiceLineItem(description = "Entry Point Sealing", quantity = 3.0, unit = "ea", unitPrice = 85.0)
    )) }
    var taxRate by remember { mutableStateOf("8.0") }

    // Auto-fill tax rate when county resolves (only overwrite if the user hasn't
    // manually changed the field away from the default "8.0" sentinel).
    LaunchedEffect(countyTaxState) {
        if (countyTaxState is CountyTaxState.Resolved) {
            val rate = (countyTaxState as CountyTaxState.Resolved).ratePercent
            taxRate = rate.toBigDecimal().stripTrailingZeros().toPlainString()
        }
    }
    var discountPercent by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf("Payment due within 30 days. Late payments subject to 1.5% monthly service charge.") }
    var pdfPath by remember { mutableStateOf("") }
    var showPdfShare by remember { mutableStateOf(false) }
    var showSignaturePad by remember { mutableStateOf(false) }
    var technicianSignature by remember { mutableStateOf<Bitmap?>(null) }
    var customerSignature by remember { mutableStateOf<Bitmap?>(null) }

    val subtotal = lineItems.sumOf { it.calculateTotal() }
    val discountAmount = subtotal * ((discountPercent.toDoubleOrNull() ?: 0.0) / 100.0)
    val taxableAmount = subtotal - discountAmount
    val taxAmount = taxableAmount * ((taxRate.toDoubleOrNull() ?: 0.0) / 100.0)
    val total = taxableAmount + taxAmount

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
                                lineItems = lineItems.toMutableList().apply { set(index, updated) }
                            },
                            onRemove = {
                                lineItems = lineItems.toMutableList().apply { removeAt(index) }
                            }
                        )
                    }

                    // Add line item button
                    TextButton(
                        onClick = {
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
                        InvoiceField("Tax %", taxRate, { taxRate = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f))
                        InvoiceField("Discount %", discountPercent, { discountPercent = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.weight(1f))
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
                        onValueChange = { notes = it },
                        label = { Text("Invoice Notes") },
                        colors = invoiceFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = terms,
                        onValueChange = { terms = it },
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
                Column(modifier = Modifier.padding(20.dp)) {
                    InvoiceTotalRow("Subtotal", subtotal)
                    if (discountAmount > 0) InvoiceTotalRow("Discount (${discountPercent}%)", -discountAmount, SuccessGreen)
                    InvoiceTotalRow("Tax (${taxRate}%)", taxAmount)
                    Divider(modifier = Modifier.padding(vertical = 8.dp), color = BorderDark)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("TOTAL", style = MaterialTheme.typography.titleLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text("$${String.format("%.2f", total)}", style = MaterialTheme.typography.headlineSmall, color = PrimaryGreen, fontWeight = FontWeight.Bold)
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
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Draw, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sign", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        job?.let { currentJob ->
                            pdfPath = generateInvoicePDF(
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
                            )
                            showPdfShare = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.White),
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
                    invoiceViewModel.generateInvoiceFromJob(
                        jobId = jobId,
                        lineItems = lineItems,
                        taxRate = taxRate.toDoubleOrNull() ?: 0.0,
                        discountAmount = discountAmount,
                        notes = notes,
                        terms = terms
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple),
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
            onView = { viewPDF(context, pdfPath) }
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
        Text(
            "$${String.format("%.0f", item.calculateTotal())}",
            modifier = Modifier.weight(0.6f),
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            textAlign = androidx.compose.ui.text.style.TextAlign.End
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
    customerSignature: Bitmap?
): String {
    // terms retained for call-site compatibility; acceptance blurb lives in shared contract PDF
    @Suppress("UNUSED_PARAMETER")
    val _terms = terms
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
        technicianSignature = technicianSignature,
        customerSignature = customerSignature
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
                        color = Color.Gray.copy(alpha = 0.5f)
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
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.White)
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
private fun PdfShareDialog(pdfPath: String, onDismiss: () -> Unit, onShare: () -> Unit, onView: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invoice Generated", color = TextPrimary) },
        text = {
            Text("PDF saved successfully. What would you like to do?", color = TextSecondary)
        },
        confirmButton = {
            Button(
                onClick = onShare,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.White)
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onView) {
                Text("View PDF", color = AccentBlue)
            }
        },
        containerColor = BackgroundCard
    )
}
