package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ai.fieldops.EstimateLineSuggester
import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.effectiveTotal
import com.strobingn.wildlifefieldops.ui.components.AiRuntimeBadge
import com.strobingn.wildlifefieldops.pricing.Money
import com.strobingn.wildlifefieldops.pricing.PricingCalculator
import com.strobingn.wildlifefieldops.ui.components.DecimalField
import com.strobingn.wildlifefieldops.ui.components.OverridableAmountField
import com.strobingn.wildlifefieldops.ui.theme.AccentBlue
import com.strobingn.wildlifefieldops.ui.theme.AccentPurple
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.ui.viewmodel.CountyTaxState
import com.strobingn.wildlifefieldops.ui.viewmodel.InvoiceViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.JobAiViewModel
import com.strobingn.wildlifefieldops.ui.viewmodel.JobsViewModel
import com.strobingn.wildlifefieldops.util.ContractDocumentType
import com.strobingn.wildlifefieldops.util.WildlifeWhispererContractPdf
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EstimateScreen(
    jobId: String,
    autoDraft: Boolean = false,
    onBack: () -> Unit,
    jobsViewModel: JobsViewModel = hiltViewModel(),
    jobAiViewModel: JobAiViewModel = hiltViewModel(),
    invoiceViewModel: InvoiceViewModel = hiltViewModel()
) {
    val job by jobsViewModel.getJobById(jobId).collectAsState(initial = null)
    val draft by jobAiViewModel.estimateDraft.collectAsState()
    val estimateLoading by jobAiViewModel.estimateLoading.collectAsState()
    val aiMessage by jobAiViewModel.message.collectAsState()
    val photoLinesLoading by jobAiViewModel.photoLinesLoading.collectAsState()
    val suggestedLines by jobAiViewModel.suggestedLines.collectAsState()
    val countyTaxState by invoiceViewModel.countyTaxState.collectAsState()
    var autoDraftFired by remember { mutableStateOf(false) }

    var pricing by remember { mutableStateOf(PricingCalculator.starterWorksheet()) }
    var hydrated by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var laborHoursText by remember { mutableStateOf("2.0") }
    var laborRateText by remember { mutableStateOf("85.00") }
    var materialsQtyText by remember { mutableStateOf("1") }
    var materialsPriceText by remember { mutableStateOf("0.00") }
    var equipmentText by remember { mutableStateOf("0.00") }
    var permitText by remember { mutableStateOf("0.00") }
    var disposalText by remember { mutableStateOf("0.00") }
    var mileageText by remember { mutableStateOf("0") }
    var mileageRateText by remember { mutableStateOf("0.65") }
    var taxRateText by remember { mutableStateOf("8.125") }
    var discountPercentText by remember { mutableStateOf("0") }

    fun applyPricing(next: JobPricing, markDirty: Boolean = true) {
        pricing = next
        if (markDirty) dirty = true
    }

    fun syncInputTexts(p: JobPricing) {
        laborHoursText = formatNum(p.laborHours)
        laborRateText = formatNum(p.laborRate)
        materialsQtyText = formatNum(p.materialsQty)
        materialsPriceText = formatNum(p.materialsPrice)
        equipmentText = formatNum(p.equipmentCost)
        permitText = formatNum(p.permitCost)
        disposalText = formatNum(p.disposalCost)
        mileageText = formatNum(p.mileage)
        mileageRateText = formatNum(p.mileageRate)
        taxRateText = formatNum(p.taxRatePercent)
        discountPercentText = formatNum(p.discountPercent)
    }

    LaunchedEffect(job, autoDraft) {
        val current = job
        if (autoDraft && !autoDraftFired && current != null && !estimateLoading) {
            autoDraftFired = true
            jobAiViewModel.draftEstimate(current)
        }
    }

    LaunchedEffect(job?.id) {
        hydrated = false
        dirty = false
    }

    LaunchedEffect(job, hydrated) {
        val current = job ?: return@LaunchedEffect
        if (hydrated) return@LaunchedEffect
        val loaded = PricingCalculator.pricingForEditor(current.pricing, current.estimatedValue)
        pricing = loaded
        syncInputTexts(loaded)
        hydrated = true
        invoiceViewModel.resolveCountyTax(current)
    }

    LaunchedEffect(draft, hydrated) {
        val d = draft ?: return@LaunchedEffect
        if (!hydrated) return@LaunchedEffect
        val filled = PricingCalculator.applyAiInputs(
            current = pricing,
            laborHours = d.laborHours,
            laborRate = d.laborRate,
            materialsCost = d.materialsCost,
            equipmentCost = d.equipmentCost,
            permitCost = d.permitCost,
            disposalCost = d.disposalCost,
            mileage = d.mileage,
            mileageRate = d.mileageRate,
            taxRatePercent = d.taxRate,
            discountPercent = d.discountPercent,
            rationale = d.rationale,
            notes = d.lineItemNotes
        )
        applyPricing(filled)
        syncInputTexts(filled)
    }

    LaunchedEffect(countyTaxState, hydrated) {
        if (!hydrated) return@LaunchedEffect
        val resolved = countyTaxState as? CountyTaxState.Resolved ?: return@LaunchedEffect
        val next = PricingCalculator.applyCountyTaxRate(pricing, resolved.ratePercent)
        if (next.taxRatePercent != pricing.taxRatePercent) {
            applyPricing(next)
            taxRateText = formatNum(next.taxRatePercent)
        }
    }

    LaunchedEffect(pricing, dirty, hydrated) {
        if (!hydrated || !dirty) return@LaunchedEffect
        delay(500)
        jobsViewModel.saveJobPricing(jobId, pricing)
    }

    val result = PricingCalculator.compute(pricing)
    val context = LocalContext.current
    var pdfPath by remember { mutableStateOf("") }
    var showPdfShare by remember { mutableStateOf(false) }

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
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            job?.let {
                Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(it.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(it.customerName, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        if (it.address.isNotBlank()) Text(it.address, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        Text(
                            "Every amount is editable. Auto-sum keeps running; a manual lock stays until you reset it.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            Button(
                onClick = { job?.let { jobAiViewModel.draftEstimate(it) } },
                enabled = job != null && !estimateLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentPurple, contentColor = OnPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (estimateLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = OnPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Looking up miles + drafting…")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI draft from job notes", fontWeight = FontWeight.SemiBold)
                }
            }
            if (!aiMessage.isNullOrBlank()) Text(aiMessage!!, style = MaterialTheme.typography.labelMedium, color = PrimaryGreen)
            AiRuntimeBadge(
                status = jobAiViewModel.runtimeStatus,
                lastUsed = job?.aiRuntime.orEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { job?.let { jobAiViewModel.suggestPhotoLineItems(it) } },
                enabled = job != null && !photoLinesLoading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (photoLinesLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = OnPrimary, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reading photos + notes…")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Suggest lines from photos", fontWeight = FontWeight.SemiBold)
                }
            }
            if (suggestedLines.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        val incoming = jobAiViewModel.consumeSuggestedLines()
                        applyPricing(
                            pricing.copy(
                                photoLineItems = EstimateLineSuggester.merge(pricing.photoLineItems, incoming, replace = false)
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen)
                ) {
                    Text("Add ${suggestedLines.size} suggested lines (keeps yours)")
                }
            }
            OutlinedButton(
                onClick = {
                    applyPricing(
                        pricing.copy(
                            photoLineItems = pricing.photoLineItems + InvoiceLineItem(
                                description = "",
                                quantity = 1.0,
                                unit = "ea",
                                unitPrice = 0.0,
                                total = 0.0
                            )
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen)
            ) {
                Text("Add line by hand")
            }
            EstimateSection("Photo / exclusion lines") {
                Text(
                    "Every qty and price is yours. AI only adds suggestions — it never blocks save.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (pricing.photoLineItems.isEmpty()) {
                    Text(
                        "No lines yet. Tap Add line by hand or keep using the labor / materials boxes below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                pricing.photoLineItems.forEachIndexed { index, item ->
                        OutlinedTextField(
                            value = item.description,
                            onValueChange = { text ->
                                applyPricing(pricing.copy(photoLineItems = pricing.photoLineItems.toMutableList().also {
                                    it[index] = item.copy(description = text)
                                }))
                            },
                            label = { Text("Line ${index + 1}") },
                            colors = com.strobingn.wildlifefieldops.ui.components.pricingFieldColors(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DecimalField("Qty", formatNum(item.quantity), {
                                val qty = it.toDoubleOrNull() ?: 0.0
                                applyPricing(pricing.copy(photoLineItems = pricing.photoLineItems.toMutableList().also { list ->
                                    list[index] = item.copy(quantity = qty, total = qty * item.unitPrice)
                                }))
                            }, Modifier.weight(1f))
                            DecimalField("Unit $", formatNum(item.unitPrice), {
                                val price = it.toDoubleOrNull() ?: 0.0
                                applyPricing(pricing.copy(photoLineItems = pricing.photoLineItems.toMutableList().also { list ->
                                    list[index] = item.copy(unitPrice = price, total = item.quantity * price)
                                }))
                            }, Modifier.weight(1f))
                        }
                        Text(
                            "${item.unit} · ${com.strobingn.wildlifefieldops.pricing.Money.formatUsd(item.effectiveTotal())}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                        TextButton(onClick = {
                            applyPricing(pricing.copy(photoLineItems = pricing.photoLineItems.filterIndexed { i, _ -> i != index }))
                        }) {
                            Text("Remove line", color = TextSecondary)
                        }
                    }
                }
            }
            if (pricing.rationale.isNotBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Draft rationale", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(pricing.rationale, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    }
                }
            }
            EstimateSection("Labor") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField("Hours", laborHoursText, {
                        laborHoursText = it
                        applyPricing(pricing.copy(laborHours = it.toDoubleOrNull() ?: 0.0))
                    }, Modifier.weight(1f))
                    DecimalField("Rate/hr", laborRateText, {
                        laborRateText = it
                        applyPricing(pricing.copy(laborRate = it.toDoubleOrNull() ?: 0.0))
                    }, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OverridableAmountField(
                    label = "Labor total",
                    field = result.laborTotal,
                    onOverride = { applyPricing(pricing.copy(laborTotalOverride = Money.round(it))) },
                    onReset = { applyPricing(PricingCalculator.resetLaborTotal(pricing)) }
                )
            }
            EstimateSection("Materials") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField("Qty", materialsQtyText, {
                        materialsQtyText = it
                        applyPricing(pricing.copy(materialsQty = it.toDoubleOrNull() ?: 0.0))
                    }, Modifier.weight(1f))
                    DecimalField("Unit price", materialsPriceText, {
                        materialsPriceText = it
                        applyPricing(pricing.copy(materialsPrice = it.toDoubleOrNull() ?: 0.0))
                    }, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OverridableAmountField(
                    label = "Materials total",
                    field = result.materialsTotal,
                    onOverride = { applyPricing(pricing.copy(materialsTotalOverride = Money.round(it))) },
                    onReset = { applyPricing(PricingCalculator.resetMaterialsTotal(pricing)) }
                )
            }
            EstimateSection("Other Costs") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField("Equipment", equipmentText, {
                        equipmentText = it
                        applyPricing(pricing.copy(equipmentCost = it.toDoubleOrNull() ?: 0.0))
                    }, Modifier.weight(1f))
                    DecimalField("Permits", permitText, {
                        permitText = it
                        applyPricing(pricing.copy(permitCost = it.toDoubleOrNull() ?: 0.0))
                    }, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                DecimalField("Disposal", disposalText, {
                    disposalText = it
                    applyPricing(pricing.copy(disposalCost = it.toDoubleOrNull() ?: 0.0))
                }, Modifier.fillMaxWidth())
            }
            EstimateSection("Mileage") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField("Miles", mileageText, {
                        mileageText = it
                        applyPricing(pricing.copy(mileage = it.toDoubleOrNull() ?: 0.0))
                    }, Modifier.weight(1f))
                    DecimalField("Rate/mi", mileageRateText, {
                        mileageRateText = it
                        applyPricing(pricing.copy(mileageRate = it.toDoubleOrNull() ?: 0.0))
                    }, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OverridableAmountField(
                    label = "Mileage total",
                    field = result.mileageTotal,
                    onOverride = { applyPricing(pricing.copy(mileageTotalOverride = Money.round(it))) },
                    onReset = { applyPricing(PricingCalculator.resetMileageTotal(pricing)) }
                )
            }
            EstimateSection("Adjustments") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DecimalField("Tax %", taxRateText, {
                        taxRateText = it
                        applyPricing(
                            pricing.copy(
                                taxRatePercent = it.toDoubleOrNull() ?: 0.0,
                                taxRateManual = true
                            )
                        )
                    }, Modifier.weight(1f))
                    DecimalField("Discount %", discountPercentText, {
                        discountPercentText = it
                        applyPricing(pricing.copy(discountPercent = it.toDoubleOrNull() ?: 0.0))
                    }, Modifier.weight(1f))
                }
                val county = countyTaxState
                if (county is CountyTaxState.Resolved) {
                    Text(
                        county.displayLabel + if (pricing.taxRateManual) " · tax % locked" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pricing.taxRateManual) TextTertiary else PrimaryGreen,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = PrimaryGreen.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverridableAmountField(
                        label = "Subtotal",
                        field = result.subtotal,
                        onOverride = { applyPricing(pricing.copy(subtotalOverride = Money.round(it))) },
                        onReset = { applyPricing(PricingCalculator.resetSubtotal(pricing)) }
                    )
                    OverridableAmountField(
                        label = "Discount $",
                        field = result.discountAmount,
                        onOverride = { applyPricing(pricing.copy(discountAmountOverride = Money.round(it))) },
                        onReset = { applyPricing(PricingCalculator.resetDiscountAmount(pricing)) }
                    )
                    OverridableAmountField(
                        label = "Tax $",
                        field = result.taxAmount,
                        onOverride = { applyPricing(pricing.copy(taxAmountOverride = Money.round(it))) },
                        onReset = { applyPricing(PricingCalculator.resetTaxAmount(pricing)) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = BorderDark)
                    OverridableAmountField(
                        label = "TOTAL",
                        field = result.total,
                        onOverride = { applyPricing(pricing.copy(totalOverride = Money.round(it))) },
                        onReset = { applyPricing(PricingCalculator.resetTotal(pricing)) },
                        emphasized = true
                    )
                }
            }
            job?.let { j ->
                OutlinedButton(
                    onClick = {
                        dirty = true
                        jobsViewModel.saveJobPricing(j.id, pricing)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save job pricing")
                }
                Button(
                    onClick = {
                        jobsViewModel.saveJobPricing(j.id, pricing)
                        val items = buildEstimateLineItems(pricing, result)
                        pdfPath = WildlifeWhispererContractPdf.generate(
                            context = context,
                            documentType = ContractDocumentType.ESTIMATE,
                            job = j.copy(estimatedValue = result.total.effective, pricing = pricing),
                            lineItems = items,
                            subtotal = result.subtotal.effective,
                            taxRate = pricing.taxRatePercent,
                            taxAmount = result.taxAmount.effective,
                            discountAmount = result.discountAmount.effective,
                            total = result.total.effective,
                            notes = listOf(pricing.notes, pricing.rationale).filter { it.isNotBlank() }.joinToString("\n")
                        )
                        showPdfShare = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Generate contract PDF", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showPdfShare && pdfPath.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { showPdfShare = false },
            title = { Text("Estimate PDF ready", color = TextPrimary) },
            text = { Text("PDF saved. Share or view the Wildlife Whisperer service contract.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { WildlifeWhispererContractPdf.share(context, pdfPath, "Share Estimate") }) {
                    Text("Share", color = PrimaryGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    WildlifeWhispererContractPdf.view(context, pdfPath)
                    showPdfShare = false
                }) {
                    Text("View PDF", color = AccentBlue)
                }
            },
            containerColor = BackgroundCard
        )
    }
}

internal fun buildEstimateLineItems(
    pricing: JobPricing,
    result: com.strobingn.wildlifefieldops.pricing.JobPricingResult
): List<InvoiceLineItem> {
    val items = mutableListOf<InvoiceLineItem>()
    if (pricing.laborHours > 0 || result.laborTotal.effective > 0) {
        items += InvoiceLineItem(
            description = "Labor / Trap Service",
            quantity = pricing.laborHours,
            unit = "hr",
            unitPrice = pricing.laborRate,
            total = result.laborTotal.effective,
            totalOverride = pricing.laborTotalOverride
        )
    }
    if (pricing.materialsQty > 0 || pricing.materialsPrice > 0 || result.materialsTotal.effective > 0) {
        items += InvoiceLineItem(
            description = "Materials / Exclusion & Repairs",
            quantity = pricing.materialsQty,
            unit = "ea",
            unitPrice = pricing.materialsPrice,
            total = result.materialsTotal.effective,
            totalOverride = pricing.materialsTotalOverride
        )
    }
    if (pricing.equipmentCost > 0) {
        items += InvoiceLineItem(description = "Equipment", quantity = 1.0, unit = "ea", unitPrice = pricing.equipmentCost, total = result.equipmentTotal.effective)
    }
    if (pricing.permitCost > 0) {
        items += InvoiceLineItem(description = "Permits", quantity = 1.0, unit = "ea", unitPrice = pricing.permitCost, total = result.permitTotal.effective)
    }
    if (pricing.disposalCost > 0) {
        items += InvoiceLineItem(description = "Disposal", quantity = 1.0, unit = "ea", unitPrice = pricing.disposalCost, total = result.disposalTotal.effective)
    }
    if (pricing.mileage > 0 || result.mileageTotal.effective > 0) {
        items += InvoiceLineItem(
            description = "Mileage",
            quantity = pricing.mileage,
            unit = "mi",
            unitPrice = pricing.mileageRate,
            total = result.mileageTotal.effective,
            totalOverride = pricing.mileageTotalOverride
        )
    }
    if (items.isEmpty() && pricing.notes.isNotBlank()) {
        items += InvoiceLineItem(description = pricing.notes.take(80), quantity = 1.0, unit = "ea", unitPrice = result.total.effective, total = result.total.effective, totalOverride = pricing.totalOverride)
    }
    if (items.isEmpty() && pricing.rationale.isNotBlank()) {
        items += InvoiceLineItem(description = "Inspection / estimate", quantity = 1.0, unit = "ea", unitPrice = result.total.effective, total = result.total.effective, totalOverride = pricing.totalOverride)
    }
    if (items.isEmpty() && result.total.effective > 0) {
        items += InvoiceLineItem(description = "Job total", quantity = 1.0, unit = "ea", unitPrice = result.total.effective, total = result.total.effective, totalOverride = pricing.totalOverride)
    }
    return items
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
