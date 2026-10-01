package com.strobingn.wildlifefieldops.pricing

import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.remote.toLocal
import com.strobingn.wildlifefieldops.data.remote.toRemoteDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PricingCalculatorTest {

    private fun worksheet(
        hours: Double = 2.0,
        rate: Double = 85.0,
        materialsQty: Double = 1.0,
        materialsPrice: Double = 40.0,
        equipment: Double = 10.0,
        permit: Double = 5.0,
        disposal: Double = 7.0,
        miles: Double = 20.0,
        mileRate: Double = 0.65,
        tax: Double = 8.125,
        discount: Double = 0.0
    ) = JobPricing(
        laborHours = hours,
        laborRate = rate,
        materialsQty = materialsQty,
        materialsPrice = materialsPrice,
        equipmentCost = equipment,
        permitCost = permit,
        disposalCost = disposal,
        mileage = miles,
        mileageRate = mileRate,
        taxRatePercent = tax,
        discountPercent = discount
    )

    @Test
    fun `auto-sum labor materials mileage fees tax`() {
        val result = PricingCalculator.compute(worksheet())
        assertEquals(170.00, result.laborTotal.calculated, 0.0)
        assertEquals(40.00, result.materialsTotal.calculated, 0.0)
        assertEquals(13.00, result.mileageTotal.calculated, 0.0)
        assertEquals(10.00, result.equipmentTotal.effective, 0.0)
        assertEquals(5.00, result.permitTotal.effective, 0.0)
        assertEquals(7.00, result.disposalTotal.effective, 0.0)
        assertEquals(245.00, result.subtotal.effective, 0.0)
        assertEquals(19.91, result.taxAmount.effective, 0.0) // 245 * 8.125%
        assertEquals(264.91, result.total.effective, 0.0)
        assertFalse(result.total.isOverridden)
    }

    @Test
    fun `discount reduces taxable base before tax`() {
        val result = PricingCalculator.compute(worksheet(discount = 10.0))
        assertEquals(245.00, result.subtotal.effective, 0.0)
        assertEquals(24.50, result.discountAmount.effective, 0.0)
        assertEquals(17.92, result.taxAmount.effective, 0.0) // 220.50 * 8.125%
        assertEquals(238.42, result.total.effective, 0.0)
    }

    @Test
    fun `rounding uses half-up cents not float drift`() {
        // 0.1 + 0.2 style: 3 × $0.10 must be $0.30, not 0.30000000004
        val result = PricingCalculator.compute(
            JobPricing(
                laborHours = 0.0,
                laborRate = 0.0,
                materialsQty = 3.0,
                materialsPrice = 0.10,
                taxRatePercent = 0.0
            )
        )
        assertEquals(0.30, result.materialsTotal.effective, 0.0)
        assertEquals(0.30, result.subtotal.effective, 0.0)
        assertEquals(0.30, result.total.effective, 0.0)
        assertTrue(Money.equals(0.1 + 0.2, 0.3) || result.total.effective == 0.30)
        assertEquals("0.30", Money.format(0.1 + 0.2))
    }

    @Test
    fun `total override survives recalculation when hours change`() {
        val locked = worksheet().copy(totalOverride = 500.00)
        val afterHours = locked.copy(laborHours = 8.0)
        val before = PricingCalculator.compute(locked)
        val after = PricingCalculator.compute(afterHours)
        assertEquals(500.00, after.total.effective, 0.0)
        assertTrue(after.total.isOverridden)
        assertNotEquals(before.total.calculated, after.total.calculated)
        assertEquals(500.00, after.total.override!!, 0.0)
        assertEquals(Money.minus(500.00, after.total.calculated), after.total.difference, 0.0)
    }

    @Test
    fun `AI fill updates inputs but does not clear overrides`() {
        val locked = worksheet().copy(
            totalOverride = 850.00,
            subtotalOverride = 700.00,
            taxAmountOverride = 50.00,
            laborTotalOverride = 200.00,
            taxRateManual = true,
            taxRatePercent = 7.0
        )
        val filled = PricingCalculator.applyAiInputs(
            current = locked,
            laborHours = 4.0,
            laborRate = 90.0,
            materialsCost = 120.0,
            equipmentCost = 15.0,
            permitCost = 0.0,
            disposalCost = 0.0,
            mileage = 12.0,
            mileageRate = 0.67,
            taxRatePercent = 8.125,
            discountPercent = 0.0,
            rationale = "AI",
            notes = "notes"
        )
        assertEquals(4.0, filled.laborHours, 0.0)
        assertEquals(90.0, filled.laborRate, 0.0)
        assertEquals(120.0, filled.materialsPrice, 0.0)
        assertEquals(12.0, filled.mileage, 0.0)
        assertEquals(850.00, filled.totalOverride)
        assertEquals(700.00, filled.subtotalOverride)
        assertEquals(50.00, filled.taxAmountOverride)
        assertEquals(200.00, filled.laborTotalOverride)
        assertEquals(7.0, filled.taxRatePercent, 0.0)
        assertTrue(filled.taxRateManual)

        val result = PricingCalculator.compute(filled)
        assertEquals(850.00, result.total.effective, 0.0)
        assertEquals(700.00, result.subtotal.effective, 0.0)
        assertEquals(50.00, result.taxAmount.effective, 0.0)
        assertEquals(200.00, result.laborTotal.effective, 0.0)
    }

    @Test
    fun `AI fill may update tax percent when not manual`() {
        val open = worksheet().copy(taxRateManual = false, taxRatePercent = 8.125)
        val filled = PricingCalculator.applyAiInputs(
            current = open,
            laborHours = 2.0,
            laborRate = 85.0,
            materialsCost = 0.0,
            equipmentCost = 0.0,
            permitCost = 0.0,
            disposalCost = 0.0,
            mileage = 0.0,
            mileageRate = 0.65,
            taxRatePercent = 8.375,
            discountPercent = 0.0,
            rationale = "",
            notes = ""
        )
        assertEquals(8.375, filled.taxRatePercent, 0.0)
    }

    @Test
    fun `reset total returns to calculated`() {
        val locked = worksheet().copy(totalOverride = 999.00)
        val reset = PricingCalculator.resetTotal(locked)
        assertNull(reset.totalOverride)
        val result = PricingCalculator.compute(reset)
        assertFalse(result.total.isOverridden)
        assertEquals(result.total.calculated, result.total.effective, 0.0)
        assertEquals(264.91, result.total.effective, 0.0)
    }

    @Test
    fun `county tax does not overwrite a manual tax percent`() {
        val manual = worksheet().copy(taxRateManual = true, taxRatePercent = 4.0)
        val after = PricingCalculator.applyCountyTaxRate(manual, 8.125)
        assertEquals(4.0, after.taxRatePercent, 0.0)
    }

    @Test
    fun `county tax fills when operator has not locked tax percent`() {
        val open = worksheet().copy(taxRateManual = false, taxRatePercent = 8.0)
        val after = PricingCalculator.applyCountyTaxRate(open, 8.375)
        assertEquals(8.375, after.taxRatePercent, 0.0)
    }

    @Test
    fun `job form typed total becomes an override used as estimated value`() {
        val withTyped = PricingCalculator.withTypedJobTotal(worksheet(), 800.0)
        val result = PricingCalculator.compute(withTyped)
        assertEquals(800.00, result.total.effective, 0.0)
        assertTrue(result.total.isOverridden)
        val cleared = PricingCalculator.withTypedJobTotal(withTyped, null)
        assertNull(cleared.totalOverride)
    }

    @Test
    fun `job form save of the calculated total does not invent an override`() {
        val ws = worksheet()
        val calc = PricingCalculator.compute(ws).total.effective
        val same = PricingCalculator.withTypedJobTotal(ws, calc)
        assertNull(same.totalOverride)
    }

    @Test
    fun `invoice line override wins and PDF totals use effective values`() {
        val lines = listOf(
            InvoiceLineItem(description = "Labor", quantity = 2.0, unitPrice = 85.0),
            InvoiceLineItem(
                description = "Materials",
                quantity = 3.0,
                unitPrice = 40.0,
                totalOverride = 100.00
            )
        )
        val computed = PricingCalculator.computeInvoice(
            InvoicePricingInputs(
                lineItems = lines,
                taxRatePercent = 8.125,
                discountPercent = 0.0
            )
        )
        assertEquals(170.00, lines[0].effectiveTotal(), 0.0)
        assertEquals(100.00, lines[1].effectiveTotal(), 0.0)
        assertEquals(120.00, lines[1].calculatedTotal(), 0.0)
        assertEquals(270.00, computed.subtotal.effective, 0.0)

        val withTotalLock = PricingCalculator.computeInvoice(
            InvoicePricingInputs(
                lineItems = lines,
                taxRatePercent = 8.125,
                discountPercent = 0.0,
                totalOverride = 250.00
            )
        )
        val pdf = withTotalLock.effectiveContractTotals()
        assertEquals(270.00, pdf.subtotal, 0.0)
        assertEquals(21.94, pdf.taxAmount, 0.0)
        assertEquals(250.00, pdf.total, 0.0)
        assertEquals(250.00, withTotalLock.total.effective, 0.0)
        assertTrue(withTotalLock.total.isOverridden)
        assertEquals(Money.minus(250.00, withTotalLock.total.calculated), withTotalLock.total.difference, 0.0)
    }

    @Test
    fun `invoice subtotal override survives line edits`() {
        val lines = listOf(
            InvoiceLineItem(description = "A", quantity = 1.0, unitPrice = 100.0)
        )
        val locked = InvoicePricingInputs(
            lineItems = lines,
            taxRatePercent = 0.0,
            discountPercent = 0.0,
            subtotalOverride = 500.00
        )
        val afterEdit = locked.copy(
            lineItems = listOf(
                InvoiceLineItem(description = "A", quantity = 4.0, unitPrice = 100.0)
            )
        )
        val result = PricingCalculator.computeInvoice(afterEdit)
        assertEquals(400.00, result.subtotal.calculated, 0.0)
        assertEquals(500.00, result.subtotal.effective, 0.0)
        assertEquals(500.00, result.total.effective, 0.0)
    }

    @Test
    fun `pricingForEditor uses stored estimated value as total override`() {
        val editor = PricingCalculator.pricingForEditor(JobPricing(), estimatedValue = 425.50)
        assertEquals(425.50, editor.totalOverride)
        assertEquals(425.50, PricingCalculator.compute(editor).total.effective, 0.0)
    }

    @Test
    fun `empty worksheet round-trips through JSON without breaking old jobs`() {
        val encoded = PricingJson.encode(JobPricing())
        val decoded = PricingJson.decode(encoded)
        assertTrue(decoded.isEmptyWorksheet())
        assertTrue(PricingJson.decode(null).isEmptyWorksheet())
        assertTrue(PricingJson.decode("{}").isEmptyWorksheet())
        assertTrue(PricingJson.decode("{\"laborHours\":3.5}").laborHours == 3.5)
    }

    @Test
    fun `usd format does not use kotlin dollar interpolation`() {
        assertEquals("$10.00", Money.formatUsd(10.0))
        assertEquals("+$5.00", Money.formatSignedUsd(5.0))
        assertEquals("$-2.50", Money.formatSignedUsd(-2.5))
    }

    @Test
    fun `supabase job dto round-trips override as the effective estimate`() {
        val job = Job(
            title = "Squirrel exclusion",
            estimatedValue = 850.0,
            pricing = JobPricing(
                laborHours = 4.0,
                laborRate = 85.0,
                totalOverride = 850.0
            )
        )
        val remote = job.toRemoteDto()
        assertEquals(850.0, remote.estimate)
        assertEquals(850.0, remote.pricing.totalOverride)
        assertEquals(340.0, remote.subtotal)
        val local = remote.toLocal()
        assertEquals(850.0, local.pricing.totalOverride)
        assertEquals(850.0, local.estimatedValue, 0.0)
        assertEquals(4.0, local.pricing.laborHours, 0.0)
        val pdf = PricingCalculator.compute(local.pricing).effectiveContractTotals()
        assertEquals(850.0, pdf.total, 0.0)
    }

    @Test
    fun photoLineItemsAddToSubtotalAndStayEditable() {
        val line = InvoiceLineItem(description = "Soffit close-up", quantity = 4.0, unit = "lf", unitPrice = 24.0)
        val result = PricingCalculator.compute(
            JobPricing(
                laborHours = 0.0,
                laborRate = 0.0,
                taxRatePercent = 0.0,
                photoLineItems = listOf(line)
            )
        )
        assertEquals(96.00, result.subtotal.effective, 0.0)
        assertEquals(96.00, result.total.effective, 0.0)
        assertTrue(JobPricing(confirmedSpecies = "raccoon", nextStep = "Check traps").isEmptyWorksheet())
        assertTrue(
            JobPricing(
                weatherTrapAdvice = "Check after rain",
                followUpKind = "TRAP_PULL",
                trapRecords = listOf(SyncedTrapRecord(id = "t1", trapId = "Deck"))
            ).isEmptyWorksheet()
        )
        assertFalse(JobPricing(photoLineItems = listOf(line)).isEmptyWorksheet())
    }
}
