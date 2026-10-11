package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.tax.NyCountyTaxRates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SalesTaxYearTest {

    private fun at(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance().apply { clear(); set(y, m, d, 12, 0, 0) }.timeInMillis

    private val bronxJob = Job(id = "j-bronx", title = "Bronx bats", county = "Bronx", state = "NY", status = JobStatus.COMPLETED)
    private val orangeJob = Job(id = "j-orange", title = "Cornwall raccoon", county = "Orange", state = "NY", status = JobStatus.COMPLETED)
    private val exemptJob = Job(id = "j-exempt", title = "Exempt", county = "Orange", state = "NY", status = JobStatus.COMPLETED)

    private fun invoice(id: String, jobId: String, date: Long, subtotal: Double, tax: Double, rate: Double) = Invoice(
        id = id, jobId = jobId, issueDate = date, createdAt = date, status = InvoiceStatus.PAID,
        subtotal = subtotal, taxRate = rate, taxAmount = tax, totalAmount = subtotal + tax, amountPaid = subtotal + tax
    )

    private val invoices = listOf(
        invoice("i1", "j-bronx", at(2026, Calendar.APRIL, 10), 400.0, 35.50, 8.875),   // Mar–May
        invoice("i2", "j-orange", at(2026, Calendar.JULY, 2), 1000.0, 81.25, 8.125),   // Jun–Aug
        invoice("i3", "j-exempt", at(2026, Calendar.OCTOBER, 5), 250.0, 0.0, 0.0),     // Sep–Nov, non-taxable
        invoice("i4", "j-orange", at(2027, Calendar.JANUARY, 20), 200.0, 16.25, 8.125), // Dec–Feb
        invoice("i5", "j-orange", at(2026, Calendar.FEBRUARY, 10), 999.0, 81.17, 8.125) // previous NYS year
    )
    private val jobs = listOf(bronxJob, orangeJob, exemptJob)

    @Test
    fun bronxIs8875() {
        assertEquals(8.875, NyCountyTaxRates.taxRatePercentForCounty("Bronx")!!, 0.0001)
        assertEquals(8.875, NyCountyTaxRates.taxRatePercentForCounty("Kings County")!!, 0.0001)
        assertEquals(8.125, NyCountyTaxRates.taxRatePercentForCounty("Orange")!!, 0.0001)
    }

    @Test
    fun nysTaxYearGrandTotal() {
        val s = SalesTaxYear.summarize(invoices, jobs, emptyList(), emptyList(), SalesTaxYear.Kind.NYS_TAX_YEAR, 2026)
        assertEquals(1600.0, s.total.taxable, 0.001)
        assertEquals(133.0, s.total.taxCollected, 0.001)
        assertEquals(250.0, s.total.nontaxable, 0.001)
    }

    @Test
    fun quartersAddUpToTheYear() {
        val s = SalesTaxYear.summarize(invoices, jobs, emptyList(), emptyList(), SalesTaxYear.Kind.NYS_TAX_YEAR, 2026)
        assertEquals(listOf("Mar–May 2026", "Jun–Aug 2026", "Sep–Nov 2026", "Dec–Feb 2026"), s.segments.map { it.label })
        assertEquals(s.total.taxCollected, s.segments.sumOf { it.snapshot.taxCollected }, 0.001)
        assertEquals(s.total.taxable, s.segments.sumOf { it.snapshot.taxable }, 0.001)
        assertEquals(35.50, s.segments[0].snapshot.taxCollected, 0.001)
        assertEquals(16.25, s.segments[3].snapshot.taxCollected, 0.001)
    }

    @Test
    fun jurisdictionRowsCarryRate() {
        val s = SalesTaxYear.summarize(invoices, jobs, emptyList(), emptyList(), SalesTaxYear.Kind.NYS_TAX_YEAR, 2026)
        val bronx = s.byJurisdiction.first { it.county == "Bronx County" }
        assertEquals(8.875, bronx.ratePercent, 0.0001)
        assertEquals(400.0, bronx.taxable, 0.001)
        assertEquals(35.50, bronx.taxCollected, 0.001)
        val orange = s.byJurisdiction.first { it.county == "Orange County" }
        assertEquals(8.125, orange.ratePercent, 0.0001)
        assertEquals(1200.0, orange.taxable, 0.001)
        assertEquals(250.0, orange.nontaxable, 0.001)
    }

    @Test
    fun calendarYearClipsQuarters() {
        val s = SalesTaxYear.summarize(invoices, jobs, emptyList(), emptyList(), SalesTaxYear.Kind.CALENDAR, 2026)
        // Feb 2026 + Apr + Jul + Oct, not Jan 2027.
        assertEquals(81.17 + 35.50 + 81.25, s.total.taxCollected, 0.001)
        assertEquals(5, s.segments.size)
        assertTrue(s.segments.first().label.contains("part in 2026"))
        assertEquals(s.total.taxCollected, s.segments.sumOf { it.snapshot.taxCollected }, 0.001)
    }

    @Test
    fun manualLockWinsAndBlankUnlockReturnsCalculated() {
        val (start, _) = SalesTaxYear.bounds(SalesTaxYear.Kind.NYS_TAX_YEAR, 2026)
        val base = EarningsPeriodOverride(grain = SalesTaxYear.overrideGrain.name, startMs = start)
        val locked = EarningsTaxEngine.applyLock(base, "taxCollected", "140.00")!!
        val s = SalesTaxYear.summarize(invoices, jobs, emptyList(), listOf(locked), SalesTaxYear.Kind.NYS_TAX_YEAR, 2026)
        assertEquals(140.0, s.total.taxCollected, 0.001)
        assertEquals(133.0, s.calculated.taxCollected, 0.001)
        assertEquals(1600.0, s.total.taxable, 0.001)
        val unlocked = EarningsTaxEngine.applyLock(locked, "taxCollected", "")!!
        val s2 = SalesTaxYear.summarize(invoices, jobs, emptyList(), listOf(unlocked), SalesTaxYear.Kind.NYS_TAX_YEAR, 2026)
        assertEquals(133.0, s2.total.taxCollected, 0.001)
    }

    @Test
    fun handAdjustmentCounts() {
        val adj = EarningsAdjustment(date = at(2026, Calendar.MAY, 1), kind = EarningsAdjKind.PAID, amount = 100.0, county = "Orange", taxable = true)
        val tax = EarningsAdjustment(date = at(2026, Calendar.MAY, 1), kind = EarningsAdjKind.TAX, amount = 8.13, county = "Orange")
        val s = SalesTaxYear.summarize(invoices, jobs, listOf(adj, tax), emptyList(), SalesTaxYear.Kind.NYS_TAX_YEAR, 2026)
        assertEquals(1700.0, s.total.taxable, 0.001)
        assertEquals(141.13, s.total.taxCollected, 0.001)
    }

    @Test
    fun currentYearAndCsv() {
        assertEquals(2026, SalesTaxYear.currentYear(SalesTaxYear.Kind.NYS_TAX_YEAR, at(2026, Calendar.OCTOBER, 11)))
        assertEquals(2025, SalesTaxYear.currentYear(SalesTaxYear.Kind.NYS_TAX_YEAR, at(2026, Calendar.FEBRUARY, 11)))
        val s = SalesTaxYear.summarize(invoices, jobs, emptyList(), emptyList(), SalesTaxYear.Kind.NYS_TAX_YEAR, 2026)
        val csv = SalesTaxYear.csv(s)
        assertTrue(csv.contains("Year total,1600.00,133.00,250.00"))
        assertTrue(csv.contains("Bronx County,8.875,400.00,35.50,0.00"))
        assertTrue(csv.contains("Mar–May 2026 (due Jun 20 2026)"))
    }
}
