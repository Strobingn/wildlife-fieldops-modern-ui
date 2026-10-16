package com.strobingn.wildlifefieldops.pricing

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.Job
import java.util.Locale
import java.util.UUID

/**
 * Turns a job's estimate worksheet into the invoice editor's starting point.
 *
 * Manual invoices are never refilled. A field the operator cleared stays
 * blank (or a typed 0.00 stays 0.00). An invoice that was never manually
 * edited still follows the latest estimate.
 */
object EstimateInvoiceCarry {

    const val DEFAULT_TERMS =
        "Payment due within 30 days. Late payments subject to 1.5% monthly service charge."

    enum class OpenKind { ESTIMATE, MANUAL, BLANK }

    enum class CopyChoice { REPLACE, ADD, CANCEL }

    data class InvoiceFormState(
        val lineItems: List<InvoiceLineItem> = emptyList(),
        val taxRate: Double = 0.0,
        val taxRateManual: Boolean = false,
        val discountPercent: Double = 0.0,
        val notes: String = "",
        val terms: String = DEFAULT_TERMS,
        val subtotalOverride: Double? = null,
        val taxAmountOverride: Double? = null,
        val discountAmountOverride: Double? = null,
        val totalOverride: Double? = null,
        val manuallyEdited: Boolean = false
    )

    data class OpenPlan(
        val kind: OpenKind,
        val form: InvoiceFormState,
        val existingId: String?
    ) {
        val contentKey: String = contentKey(kind, form)
    }

    /** Saved worksheet, or a typed job total treated as the estimate. */
    fun worksheetForCarry(pricing: JobPricing, estimatedValue: Double): JobPricing? {
        if (!pricing.isEmptyWorksheet()) return pricing
        if (estimatedValue > 0.0) return PricingCalculator.pricingForEditor(pricing, estimatedValue)
        return null
    }

    fun resolveOpen(pricing: JobPricing, estimatedValue: Double, saved: Invoice?): OpenPlan {
        if (saved != null && saved.manuallyEdited) {
            return OpenPlan(OpenKind.MANUAL, formFromInvoice(saved), saved.id)
        }
        val worksheet = worksheetForCarry(pricing, estimatedValue)
        if (worksheet != null) {
            return OpenPlan(OpenKind.ESTIMATE, formFromEstimate(worksheet), saved?.id)
        }
        if (saved != null) {
            return OpenPlan(OpenKind.MANUAL, formFromInvoice(saved), saved.id)
        }
        return OpenPlan(OpenKind.BLANK, blankForm(), null)
    }

    fun formFromInvoice(invoice: Invoice): InvoiceFormState = InvoiceFormState(
        lineItems = invoice.lineItems,
        taxRate = invoice.taxRate,
        taxRateManual = invoice.taxRateManual,
        discountPercent = invoice.discountPercent,
        notes = invoice.notes,
        terms = invoice.terms,
        subtotalOverride = invoice.subtotalOverride,
        taxAmountOverride = invoice.taxAmountOverride,
        discountAmountOverride = invoice.discountAmountOverride,
        totalOverride = invoice.totalOverride,
        manuallyEdited = invoice.manuallyEdited
    )

    fun formFromEstimate(pricing: JobPricing): InvoiceFormState {
        val result = PricingCalculator.compute(pricing)
        return InvoiceFormState(
            lineItems = lineItems(pricing, result),
            taxRate = pricing.taxRatePercent,
            taxRateManual = pricing.taxRateManual,
            discountPercent = pricing.discountPercent,
            notes = listOf(pricing.notes, pricing.rationale).filter { it.isNotBlank() }.joinToString("\n"),
            terms = DEFAULT_TERMS,
            subtotalOverride = pricing.subtotalOverride,
            taxAmountOverride = pricing.taxAmountOverride,
            discountAmountOverride = pricing.discountAmountOverride,
            totalOverride = pricing.totalOverride,
            manuallyEdited = false
        )
    }

    fun blankForm(): InvoiceFormState = InvoiceFormState(
        lineItems = listOf(
            InvoiceLineItem(description = "Wildlife Inspection", quantity = 1.0, unit = "ea", unitPrice = 150.0),
            InvoiceLineItem(description = "Live Trapping & Removal", quantity = 1.0, unit = "ea", unitPrice = 350.0),
            InvoiceLineItem(description = "Entry Point Sealing", quantity = 3.0, unit = "ea", unitPrice = 85.0)
        ),
        taxRate = 8.0,
        terms = DEFAULT_TERMS,
        manuallyEdited = false
    )

    fun lineItems(pricing: JobPricing, result: JobPricingResult = PricingCalculator.compute(pricing)): List<InvoiceLineItem> {
        val items = mutableListOf<InvoiceLineItem>()
        pricing.photoLineItems.forEach { item ->
            items += item.copy(id = UUID.randomUUID().toString(), total = item.effectiveTotal())
        }
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
            items += InvoiceLineItem(
                description = "Equipment",
                quantity = 1.0,
                unit = "ea",
                unitPrice = pricing.equipmentCost,
                total = result.equipmentTotal.effective
            )
        }
        if (pricing.permitCost > 0) {
            items += InvoiceLineItem(
                description = "Permits",
                quantity = 1.0,
                unit = "ea",
                unitPrice = pricing.permitCost,
                total = result.permitTotal.effective
            )
        }
        if (pricing.disposalCost > 0) {
            items += InvoiceLineItem(
                description = "Disposal",
                quantity = 1.0,
                unit = "ea",
                unitPrice = pricing.disposalCost,
                total = result.disposalTotal.effective
            )
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
            items += InvoiceLineItem(
                description = pricing.notes.take(80),
                quantity = 1.0,
                unit = "ea",
                unitPrice = result.total.effective,
                total = result.total.effective,
                totalOverride = pricing.totalOverride
            )
        }
        if (items.isEmpty() && pricing.rationale.isNotBlank()) {
            items += InvoiceLineItem(
                description = "Inspection / estimate",
                quantity = 1.0,
                unit = "ea",
                unitPrice = result.total.effective,
                total = result.total.effective,
                totalOverride = pricing.totalOverride
            )
        }
        if (items.isEmpty() && result.total.effective > 0) {
            items += InvoiceLineItem(
                description = "Job total",
                quantity = 1.0,
                unit = "ea",
                unitPrice = result.total.effective,
                total = result.total.effective,
                totalOverride = pricing.totalOverride
            )
        }
        return items
    }

    /** Confirm only when the invoice already has lines. An empty invoice just fills. */
    fun needsCopyConfirm(current: List<InvoiceLineItem>): Boolean = current.isNotEmpty()

    fun applyCopy(
        current: List<InvoiceLineItem>,
        estimateLines: List<InvoiceLineItem>,
        choice: CopyChoice
    ): List<InvoiceLineItem> {
        if (choice == CopyChoice.CANCEL) return current
        val incoming = estimateLines.map { it.copy(id = UUID.randomUUID().toString(), total = it.effectiveTotal()) }
        return when (choice) {
            CopyChoice.REPLACE -> incoming
            CopyChoice.ADD -> {
                val have = current.map { fingerprint(it) }.toSet()
                current + incoming.filter { fingerprint(it) !in have }
            }
            CopyChoice.CANCEL -> current
        }
    }

    fun buildInvoice(
        job: Job,
        form: InvoiceFormState,
        existing: Invoice?,
        invoiceNumber: String,
        now: Long,
        manuallyEdited: Boolean
    ): Invoice {
        val priced = PricingCalculator.computeInvoice(
            InvoicePricingInputs(
                lineItems = form.lineItems,
                taxRatePercent = form.taxRate,
                discountPercent = form.discountPercent,
                subtotalOverride = form.subtotalOverride,
                discountAmountOverride = form.discountAmountOverride,
                taxAmountOverride = form.taxAmountOverride,
                totalOverride = form.totalOverride
            )
        )
        val lines = form.lineItems.map { it.copy(total = it.effectiveTotal()) }
        val paid = existing?.amountPaid ?: 0.0
        val total = priced.total.effective
        return Invoice(
            id = existing?.id ?: UUID.randomUUID().toString(),
            invoiceNumber = existing?.invoiceNumber?.takeIf { it.isNotBlank() } ?: invoiceNumber,
            jobId = job.id,
            customerId = job.customerId.ifBlank { existing?.customerId.orEmpty() },
            customerName = job.customerName.ifBlank { existing?.customerName.orEmpty() },
            customerEmail = existing?.customerEmail.orEmpty(),
            customerAddress = job.address.ifBlank { existing?.customerAddress.orEmpty() },
            issueDate = existing?.issueDate ?: now,
            dueDate = existing?.dueDate ?: (now + 30L * 86_400_000L),
            status = existing?.status ?: InvoiceStatus.DRAFT,
            subtotal = priced.subtotal.effective,
            taxRate = form.taxRate,
            taxAmount = priced.taxAmount.effective,
            discountPercent = form.discountPercent,
            discountAmount = priced.discountAmount.effective,
            totalAmount = total,
            subtotalOverride = form.subtotalOverride,
            taxAmountOverride = form.taxAmountOverride,
            discountAmountOverride = form.discountAmountOverride,
            totalOverride = form.totalOverride,
            taxRateManual = form.taxRateManual,
            amountPaid = paid,
            balanceDue = Money.minus(total, paid).coerceAtLeast(0.0),
            lineItems = lines,
            notes = form.notes,
            terms = form.terms,
            technicianSignature = existing?.technicianSignature.orEmpty(),
            customerSignature = existing?.customerSignature.orEmpty(),
            pdfPath = existing?.pdfPath.orEmpty(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            isSynced = false,
            manuallyEdited = manuallyEdited
        )
    }

    fun sameMoneyContent(left: Invoice, right: Invoice): Boolean {
        if (left.lineItems.size != right.lineItems.size) return false
        if (left.lineItems.map { fingerprint(it) } != right.lineItems.map { fingerprint(it) }) return false
        if (left.taxRateManual != right.taxRateManual) return false
        if (!Money.equals(left.taxRate, right.taxRate)) return false
        if (!Money.equals(left.discountPercent, right.discountPercent)) return false
        if (left.notes != right.notes || left.terms != right.terms) return false
        if (!sameOverride(left.subtotalOverride, right.subtotalOverride)) return false
        if (!sameOverride(left.taxAmountOverride, right.taxAmountOverride)) return false
        if (!sameOverride(left.discountAmountOverride, right.discountAmountOverride)) return false
        if (!sameOverride(left.totalOverride, right.totalOverride)) return false
        return left.manuallyEdited == right.manuallyEdited
    }

    /**
     * Sync may insert a missing invoice or replace an older snapshot.
     * A manual local invoice is never replaced by an untouched remote carry.
     */
    fun shouldReplaceWithRemote(local: Invoice?, remote: SyncedInvoiceRecord): Boolean {
        if (local == null) return true
        if (local.manuallyEdited && !remote.manuallyEdited) return false
        if (!remoteHasBody(remote)) return false
        return remote.updatedAt > local.updatedAt
    }

    fun formatNumber(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return "0"
        val text = String.format(Locale.US, "%.4f", value).trimEnd('0').trimEnd('.')
        return if (text.isEmpty() || text == "-" || text == "-0") "0" else text
    }

    fun fingerprint(item: InvoiceLineItem): String = listOf(
        item.description.trim().lowercase(Locale.US),
        item.unit.trim().lowercase(Locale.US),
        String.format(Locale.US, "%.4f", item.quantity),
        String.format(Locale.US, "%.4f", item.unitPrice),
        item.totalOverride?.let { String.format(Locale.US, "%.4f", it) }.orEmpty()
    ).joinToString("|")

    private fun remoteHasBody(remote: SyncedInvoiceRecord): Boolean =
        remote.lineItems.isNotEmpty() ||
            remote.notes.isNotBlank() ||
            remote.terms.isNotBlank() ||
            remote.taxRateManual ||
            remote.totalOverride != null ||
            remote.subtotalOverride != null ||
            remote.taxAmountOverride != null ||
            remote.discountAmountOverride != null ||
            remote.totalAmount != 0.0 ||
            remote.taxRate != 0.0 ||
            remote.subtotal != 0.0

    private fun sameOverride(left: Double?, right: Double?): Boolean = when {
        left == null && right == null -> true
        left == null || right == null -> false
        else -> Money.equals(left, right)
    }

    private fun contentKey(kind: OpenKind, form: InvoiceFormState): String {
        val lines = form.lineItems.joinToString(";") { fingerprint(it) }
        return listOf(
            kind.name,
            lines,
            formatNumber(form.taxRate),
            form.taxRateManual.toString(),
            formatNumber(form.discountPercent),
            form.notes,
            form.terms,
            form.subtotalOverride?.toString().orEmpty(),
            form.taxAmountOverride?.toString().orEmpty(),
            form.discountAmountOverride?.toString().orEmpty(),
            form.totalOverride?.toString().orEmpty()
        ).joinToString("¦")
    }
}
