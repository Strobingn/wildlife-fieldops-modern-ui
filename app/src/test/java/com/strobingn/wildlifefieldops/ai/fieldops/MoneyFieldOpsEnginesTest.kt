package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PricingJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyFieldOpsEnginesTest {

    @Test
    fun timerStartStopAccumulates() {
        val t0 = 1_000_000L
        val started = JobTimer.start(JobTimerState(), t0)
        assertTrue(started.running)
        val stopped = JobTimer.stop(started, t0 + 30 * 60_000L)
        assertFalse(stopped.running)
        assertEquals(30L, stopped.displayedMinutes(t0 + 30 * 60_000L))
        assertEquals("0:45", JobTimer.format(JobTimer.withTypedMinutes(stopped, 45).elapsedMs))
    }

    @Test
    fun profitUsesPaidAndOverrides() {
        val result = JobProfit.compute(
            ProfitInput(
                quoted = 900.0,
                paid = 800.0,
                materialsCost = 120.0,
                laborHours = 2.0,
                laborRate = 85.0,
                laborCostOverride = 150.0,
                expenses = 20.0,
                mileageCost = 10.0
            )
        )
        assertEquals(800.0, result.revenue, 0.001)
        assertEquals(150.0, result.laborCost, 0.001)
        assertEquals(300.0, result.totalCost, 0.001)
        assertEquals(500.0, result.profit, 0.001)
    }

    @Test
    fun mileageYearCsv() {
        val jan = MileageTaxLog.yearStart(2026) + 5 * 86_400_000L
        val rows = listOf(
            MileageLogEntry(id = "1", date = jan, miles = 12.0, purpose = "Trap check", jobTitle = "Oak St", rate = 0.70)
        )
        val csv = MileageTaxLog.toCsv(MileageTaxLog.forYear(rows, 2026))
        assertTrue(csv.contains("12.0"))
        assertTrue(csv.contains("Oak St"))
        assertEquals(12.0, MileageTaxLog.totalMiles(rows), 0.0)
    }

    @Test
    fun earningsPaidVsInvoicedVsEstimated() {
        val (start, end) = EarningsRollup.dayBounds(1_700_000_000_000L)
        val mid = start + 3_600_000L
        val invoices = listOf(
            Invoice(status = InvoiceStatus.PAID, totalAmount = 200.0, amountPaid = 200.0, issueDate = mid),
            Invoice(status = InvoiceStatus.SENT, totalAmount = 150.0, issueDate = mid)
        )
        val jobs = listOf(Job(estimatedValue = 400.0, createdAt = mid))
        val window = EarningsRollup.summarize(invoices, jobs, start, end)
        assertEquals(200.0, window.paid, 0.0)
        assertEquals(350.0, window.invoiced, 0.0)
        assertEquals(400.0, window.estimated, 0.0)
    }

    @Test
    fun overdueInvoiceDraftMentionsBalance() {
        val inv = Invoice(
            invoiceNumber = "INV-9",
            customerName = "Pat",
            totalAmount = 250.0,
            balanceDue = 250.0,
            dueDate = 1L,
            status = InvoiceStatus.SENT
        )
        assertTrue(InvoiceReminder.isOverdue(inv, now = 10_000L))
        val draft = InvoiceReminder.draft(inv)
        assertTrue(draft.body.contains("INV-9"))
        assertTrue(draft.body.contains("Pat"))
        val paid = InvoiceReminder.withStatus(inv, InvoiceStatus.PAID)
        assertEquals(InvoiceStatus.PAID, paid.status)
        assertEquals(0.0, paid.balanceDue, 0.0)
    }

    @Test
    fun moneyExtrasStayInsidePricingAndDoNotForceWorksheet() {
        val pricing = JobPricing(
            timerElapsedMs = 3_600_000L,
            materialsCostActual = 40.0,
            mileageLogs = listOf(MileageLogEntry(id = "m1", miles = 8.0, purpose = "Site")),
            paidAmount = 100.0
        )
        val decoded = PricingJson.decode(PricingJson.encode(pricing))
        assertEquals(3_600_000L, decoded.timerElapsedMs)
        assertEquals(8.0, decoded.mileageLogs.first().miles, 0.0)
        assertTrue(decoded.isEmptyWorksheet())
    }
}
