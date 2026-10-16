package com.strobingn.wildlifefieldops.pricing

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyReviewFixesTest {

    @Test
    fun halfCentMileageRateIsNotRoundedBeforeMultiplying() {
        // 100 miles at 65.5 cents is $65.50. Rounding the rate to 0.66 first gave $66.00.
        assertEquals(65.50, Money.times(100.0, 0.655), 0.0)
        // 3 ft at $1.495 is $4.485 -> $4.49 (rate rounded first gave $4.50).
        assertEquals(4.49, Money.times(3.0, 1.495), 0.0)
        // Two-decimal prices are unchanged.
        assertEquals(170.0, Money.times(2.0, 85.0), 0.0)
    }

    @Test
    fun discountOverOneHundredPercentCannotMakeNegativeTotals() {
        val pricing = JobPricing(
            laborHours = 1.0,
            laborRate = 100.0,
            taxRatePercent = 8.0,
            discountPercent = 150.0
        )
        val result = PricingCalculator.compute(pricing)
        assertEquals(100.0, result.subtotal.effective, 0.0)
        assertEquals(100.0, result.discountAmount.effective, 0.0)
        assertEquals(0.0, result.taxAmount.effective, 0.0)
        assertEquals(0.0, result.total.effective, 0.0)

        val invoice = PricingCalculator.computeInvoice(
            InvoicePricingInputs(
                lineItems = listOf(InvoiceLineItem(description = "Work", quantity = 1.0, unitPrice = 100.0)),
                taxRatePercent = 8.0,
                discountPercent = 150.0
            )
        )
        assertEquals(0.0, invoice.taxAmount.effective, 0.0)
        assertEquals(0.0, invoice.total.effective, 0.0)
    }

    @Test
    fun savedInvoiceBalanceHasNoBinaryDrift() {
        val form = EstimateInvoiceCarry.InvoiceFormState(
            lineItems = listOf(InvoiceLineItem(description = "Work", quantity = 1.0, unitPrice = 0.3))
        )
        val existing = Invoice(amountPaid = 0.1)
        val built = EstimateInvoiceCarry.buildInvoice(
            job = Job(),
            form = form,
            existing = existing,
            invoiceNumber = "INV-1",
            now = 1_000L,
            manuallyEdited = false
        )
        assertEquals(0.3, built.totalAmount, 0.0)
        // 0.3 - 0.1 is 0.19999999999999998 in raw doubles.
        assertEquals(0.2, built.balanceDue, 0.0)
    }
}
