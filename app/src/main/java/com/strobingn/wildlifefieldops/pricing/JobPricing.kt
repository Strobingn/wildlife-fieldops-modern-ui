package com.strobingn.wildlifefieldops.pricing

import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import kotlinx.serialization.Serializable

/**
 * Persisted estimate / job worksheet. Input fields are always operator-editable.
 * `*Override` values lock the corresponding *computed* field so auto-sum, AI fill,
 * and county tax cannot silently replace what the owner typed.
 */
@Serializable
data class JobPricing(
    val laborHours: Double = 0.0,
    val laborRate: Double = 85.0,
    val laborTotalOverride: Double? = null,
    val materialsQty: Double = 0.0,
    val materialsPrice: Double = 0.0,
    val materialsTotalOverride: Double? = null,
    val equipmentCost: Double = 0.0,
    val permitCost: Double = 0.0,
    val disposalCost: Double = 0.0,
    val mileage: Double = 0.0,
    val mileageRate: Double = 0.65,
    val mileageTotalOverride: Double? = null,
    val taxRatePercent: Double = 8.125,
    /** True once the operator types Tax % — county lookup / AI must not clobber it. */
    val taxRateManual: Boolean = false,
    val discountPercent: Double = 0.0,
    val subtotalOverride: Double? = null,
    val discountAmountOverride: Double? = null,
    val taxAmountOverride: Double? = null,
    val totalOverride: Double? = null,
    val notes: String = "",
    val rationale: String = ""
) {
    fun isEmptyWorksheet(): Boolean =
        laborHours == 0.0 &&
            materialsQty == 0.0 &&
            materialsPrice == 0.0 &&
            equipmentCost == 0.0 &&
            permitCost == 0.0 &&
            disposalCost == 0.0 &&
            mileage == 0.0 &&
            discountPercent == 0.0 &&
            laborTotalOverride == null &&
            materialsTotalOverride == null &&
            mileageTotalOverride == null &&
            subtotalOverride == null &&
            discountAmountOverride == null &&
            taxAmountOverride == null &&
            totalOverride == null &&
            notes.isBlank() &&
            rationale.isBlank() &&
            !taxRateManual
}

data class JobPricingResult(
    val laborTotal: MoneyField,
    val materialsTotal: MoneyField,
    val equipmentTotal: MoneyField,
    val permitTotal: MoneyField,
    val disposalTotal: MoneyField,
    val mileageTotal: MoneyField,
    val subtotal: MoneyField,
    val discountAmount: MoneyField,
    val taxAmount: MoneyField,
    val total: MoneyField
) {
    /** Amounts PDFs / invoices / sync must print — override if set, else calculated. */
    fun effectiveContractTotals(): ContractTotals = ContractTotals(
        subtotal = subtotal.effective,
        discountAmount = discountAmount.effective,
        taxAmount = taxAmount.effective,
        total = total.effective,
        taxRatePercent = 0.0
    )
}

data class InvoicePricingResult(
    val lineEffectiveTotals: List<Double>,
    val subtotal: MoneyField,
    val discountAmount: MoneyField,
    val taxAmount: MoneyField,
    val total: MoneyField
) {
    fun effectiveContractTotals(): ContractTotals = ContractTotals(
        subtotal = subtotal.effective,
        discountAmount = discountAmount.effective,
        taxAmount = taxAmount.effective,
        total = total.effective,
        taxRatePercent = 0.0
    )
}

data class ContractTotals(
    val subtotal: Double,
    val discountAmount: Double,
    val taxAmount: Double,
    val total: Double,
    val taxRatePercent: Double
)

data class InvoicePricingInputs(
    val lineItems: List<InvoiceLineItem>,
    val taxRatePercent: Double,
    val discountPercent: Double,
    val subtotalOverride: Double? = null,
    val discountAmountOverride: Double? = null,
    val taxAmountOverride: Double? = null,
    val totalOverride: Double? = null
)

object PricingCalculator {

    fun starterWorksheet(): JobPricing = JobPricing(
        laborHours = 2.0,
        laborRate = 85.0,
        materialsQty = 1.0,
        mileageRate = 0.65,
        taxRatePercent = 8.125
    )

    /**
     * Editor hydrate: restore a saved worksheet; otherwise treat a stored job
     * estimated value as a total override; otherwise show starter labor defaults.
     */
    fun pricingForEditor(saved: JobPricing, estimatedValue: Double): JobPricing {
        if (!saved.isEmptyWorksheet()) return saved
        if (estimatedValue > 0.0) {
            return JobPricing(
                laborRate = 85.0,
                mileageRate = 0.65,
                taxRatePercent = 8.125,
                totalOverride = Money.round(estimatedValue)
            )
        }
        return starterWorksheet()
    }

    fun compute(pricing: JobPricing): JobPricingResult {
        val laborCalc = Money.times(pricing.laborHours, pricing.laborRate)
        val labor = MoneyField(laborCalc, pricing.laborTotalOverride?.let { Money.round(it) })

        val materialsCalc = Money.times(pricing.materialsQty, pricing.materialsPrice)
        val materials = MoneyField(materialsCalc, pricing.materialsTotalOverride?.let { Money.round(it) })

        val equipment = MoneyField(Money.round(pricing.equipmentCost), null)
        val permit = MoneyField(Money.round(pricing.permitCost), null)
        val disposal = MoneyField(Money.round(pricing.disposalCost), null)

        val mileageCalc = Money.times(pricing.mileage, pricing.mileageRate)
        val mileage = MoneyField(mileageCalc, pricing.mileageTotalOverride?.let { Money.round(it) })

        val subtotalCalc = Money.plus(
            labor.effective,
            materials.effective,
            equipment.effective,
            permit.effective,
            disposal.effective,
            mileage.effective
        )
        val subtotal = MoneyField(subtotalCalc, pricing.subtotalOverride?.let { Money.round(it) })

        val discountCalc = Money.percentOf(subtotal.effective, pricing.discountPercent)
        val discount = MoneyField(discountCalc, pricing.discountAmountOverride?.let { Money.round(it) })

        val taxable = Money.minus(subtotal.effective, discount.effective)
        val taxCalc = Money.percentOf(taxable, pricing.taxRatePercent)
        val tax = MoneyField(taxCalc, pricing.taxAmountOverride?.let { Money.round(it) })

        val totalCalc = Money.plus(taxable, tax.effective)
        val total = MoneyField(totalCalc, pricing.totalOverride?.let { Money.round(it) })

        return JobPricingResult(
            laborTotal = labor,
            materialsTotal = materials,
            equipmentTotal = equipment,
            permitTotal = permit,
            disposalTotal = disposal,
            mileageTotal = mileage,
            subtotal = subtotal,
            discountAmount = discount,
            taxAmount = tax,
            total = total
        )
    }

    fun computeInvoice(inputs: InvoicePricingInputs): InvoicePricingResult {
        val lineTotals = inputs.lineItems.map { it.effectiveTotal() }
        val subtotalCalc = lineTotals.fold(0.0) { acc, v -> Money.plus(acc, v) }
        val subtotal = MoneyField(subtotalCalc, inputs.subtotalOverride?.let { Money.round(it) })

        val discountCalc = Money.percentOf(subtotal.effective, inputs.discountPercent)
        val discount = MoneyField(discountCalc, inputs.discountAmountOverride?.let { Money.round(it) })

        val taxable = Money.minus(subtotal.effective, discount.effective)
        val taxCalc = Money.percentOf(taxable, inputs.taxRatePercent)
        val tax = MoneyField(taxCalc, inputs.taxAmountOverride?.let { Money.round(it) })

        val totalCalc = Money.plus(taxable, tax.effective)
        val total = MoneyField(totalCalc, inputs.totalOverride?.let { Money.round(it) })

        return InvoicePricingResult(
            lineEffectiveTotals = lineTotals,
            subtotal = subtotal,
            discountAmount = discount,
            taxAmount = tax,
            total = total
        )
    }

    /**
     * AI / distance-matrix fill updates inputs only. Computed-field overrides and a
     * manual tax-rate lock are preserved. Mileage and tax percent from the caller
     * (measured miles + shop/county rate) still apply unless tax % is locked.
     */
    fun applyAiInputs(
        current: JobPricing,
        laborHours: Double,
        laborRate: Double,
        materialsCost: Double,
        equipmentCost: Double,
        permitCost: Double,
        disposalCost: Double,
        mileage: Double,
        mileageRate: Double,
        taxRatePercent: Double,
        discountPercent: Double,
        rationale: String,
        notes: String
    ): JobPricing {
        val materialsLocked = current.materialsTotalOverride != null
        return current.copy(
            laborHours = laborHours,
            laborRate = laborRate,
            materialsQty = if (materialsLocked) current.materialsQty else 1.0,
            materialsPrice = if (materialsLocked) current.materialsPrice else materialsCost,
            equipmentCost = equipmentCost,
            permitCost = permitCost,
            disposalCost = disposalCost,
            mileage = mileage,
            mileageRate = mileageRate,
            taxRatePercent = if (current.taxRateManual) current.taxRatePercent else taxRatePercent,
            discountPercent = discountPercent,
            rationale = rationale,
            notes = notes
        )
    }

    fun applyCountyTaxRate(current: JobPricing, ratePercent: Double): JobPricing {
        if (current.taxRateManual) return current
        return current.copy(taxRatePercent = ratePercent)
    }

    /**
     * Job-form estimated-value box. Typing a number that differs from the
     * calculated total locks [JobPricing.totalOverride]. Saving the same value
     * as the current calculated total does not invent a lock. Clearing the box
     * drops the lock.
     */
    fun withTypedJobTotal(current: JobPricing, typedTotal: Double?): JobPricing {
        if (typedTotal == null || typedTotal == 0.0) {
            return current.copy(totalOverride = null)
        }
        val rounded = Money.round(typedTotal)
        if (current.totalOverride != null) {
            return current.copy(totalOverride = rounded)
        }
        val calculated = compute(current).total.calculated
        return if (Money.equals(rounded, calculated)) current
        else current.copy(totalOverride = rounded)
    }

    fun resetLaborTotal(p: JobPricing) = p.copy(laborTotalOverride = null)
    fun resetMaterialsTotal(p: JobPricing) = p.copy(materialsTotalOverride = null)
    fun resetMileageTotal(p: JobPricing) = p.copy(mileageTotalOverride = null)
    fun resetSubtotal(p: JobPricing) = p.copy(subtotalOverride = null)
    fun resetDiscountAmount(p: JobPricing) = p.copy(discountAmountOverride = null)
    fun resetTaxAmount(p: JobPricing) = p.copy(taxAmountOverride = null)
    fun resetTotal(p: JobPricing) = p.copy(totalOverride = null)
}

fun InvoiceLineItem.calculatedTotal(): Double = Money.times(quantity, unitPrice)

fun InvoiceLineItem.effectiveTotal(): Double {
    val locked = totalOverride
    return if (locked != null) Money.round(locked) else calculatedTotal()
}
