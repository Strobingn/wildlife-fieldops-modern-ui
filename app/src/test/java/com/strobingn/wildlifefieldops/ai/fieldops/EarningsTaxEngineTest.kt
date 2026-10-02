package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.pricing.JobPricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class EarningsTaxEngineTest {

    @Test
    fun adjustmentsAddToPaidAndTaxAndManualOverrideWins() {
        val day = cal(2026, Calendar.APRIL, 10)
        val invoice = Invoice(
            id = "inv1",
            jobId = "j1",
            issueDate = day,
            status = InvoiceStatus.PAID,
            totalAmount = 100.0,
            amountPaid = 100.0,
            subtotal = 92.5,
            taxAmount = 7.5
        )
        val job = Job(
            id = "j1",
            title = "Cornwall raccoon",
            county = "Orange",
            state = "NY",
            status = JobStatus.PAID,
            completedDate = day,
            estimatedValue = 200.0,
            pricing = JobPricing(taxRatePercent = 8.125, taxAmountOverride = 7.5)
        )
        val adj = listOf(
            EarningsAdjustment(id = "a1", date = day, kind = EarningsAdjKind.PAID, amount = 25.0, county = "Orange", taxable = true),
            EarningsAdjustment(id = "a2", date = day, kind = EarningsAdjKind.TAX, amount = 2.0, county = "Orange")
        )
        val (start, end) = NySalesTaxPeriods.monthBounds(day)
        val snap = EarningsTaxEngine.snapshot(listOf(invoice), listOf(job), adj, emptyList(), start, end, NySalesTaxPeriods.Grain.MONTH)
        assertEquals(125.0, snap.paid, 0.01)
        assertEquals(9.5, snap.taxCollected, 0.01)
        assertTrue(snap.byCounty.any { it.county.contains("Orange") && it.taxCollected >= 9.5 })

        val locked = EarningsPeriodOverride(grain = "MONTH", startMs = start, paid = 50.0, taxCollected = 1.0)
        val over = EarningsTaxEngine.snapshot(listOf(invoice), listOf(job), adj, listOf(locked), start, end, NySalesTaxPeriods.Grain.MONTH)
        assertEquals(50.0, over.paid, 0.001)
        assertEquals(1.0, over.taxCollected, 0.001)
        assertTrue(over.overrideApplied)
    }

    @Test
    fun ledgerJobIsNotEstimatedIncome() {
        val day = cal(2026, Calendar.APRIL, 10)
        val ledger = OpsLedger.newJob().copy(estimatedValue = 9999.0, completedDate = day)
        val (start, end) = NySalesTaxPeriods.monthBounds(day)
        val snap = EarningsTaxEngine.snapshot(emptyList(), listOf(ledger), emptyList(), emptyList(), start, end)
        assertEquals(0.0, snap.estimated, 0.001)
        assertEquals(0, snap.jobCount)
    }

    private fun cal(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(year, month, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
