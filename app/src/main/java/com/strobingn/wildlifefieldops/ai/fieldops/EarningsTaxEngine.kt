package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.pricing.Money
import com.strobingn.wildlifefieldops.pricing.PricingCalculator
import com.strobingn.wildlifefieldops.tax.NyCountyTaxRates
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class EarningsAdjKind { PAID, INVOICED, ESTIMATED, TAX }

@Serializable
data class EarningsAdjustment(
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val kind: EarningsAdjKind = EarningsAdjKind.PAID,
    val amount: Double = 0.0,
    val county: String = "",
    val taxable: Boolean = true,
    val notes: String = ""
)

@Serializable
data class EarningsPeriodOverride(
    val id: String = UUID.randomUUID().toString(),
    val grain: String = NySalesTaxPeriods.Grain.MONTH.name,
    val startMs: Long = 0L,
    val paid: Double? = null,
    val invoiced: Double? = null,
    val estimated: Double? = null,
    val taxCollected: Double? = null,
    val taxable: Double? = null,
    val nontaxable: Double? = null,
    /** Fields locked by Lock, including 0.00. Empty lock is 0, not "use computed". */
    val locked: Set<String> = emptySet()
)

data class CountyTaxRow(
    val county: String,
    val ratePercent: Double,
    val taxable: Double,
    val nontaxable: Double,
    val taxCollected: Double
)

data class EarningsTaxSnapshot(
    val paid: Double,
    val invoiced: Double,
    val estimated: Double,
    val taxable: Double,
    val nontaxable: Double,
    val taxCollected: Double,
    val invoiceCount: Int,
    val jobCount: Int,
    val byCounty: List<CountyTaxRow>,
    val quarter: NySalesQuarter?,
    val overrideApplied: Boolean
)

object EarningsTaxEngine {

    fun snapshot(
        invoices: List<Invoice>,
        jobs: List<Job>,
        adjustments: List<EarningsAdjustment>,
        overrides: List<EarningsPeriodOverride>,
        start: Long,
        end: Long,
        grain: NySalesTaxPeriods.Grain = NySalesTaxPeriods.Grain.MONTH
    ): EarningsTaxSnapshot {
        val workJobs = jobs.filterNot { OpsLedger.isLedger(it) }
        val window = EarningsRollup.summarize(invoices, workJobs, start, end)
        val taxLines = taxLines(invoices, workJobs, start, end)
        val periodAdj = adjustments.filter { it.date in start until end }
        val paid = Money.round(window.paid + sum(periodAdj, EarningsAdjKind.PAID))
        val invoiced = Money.round(window.invoiced + sum(periodAdj, EarningsAdjKind.INVOICED))
        val estimated = Money.round(window.estimated + sum(periodAdj, EarningsAdjKind.ESTIMATED))
        val taxFromAdj = sum(periodAdj, EarningsAdjKind.TAX)
        val taxableAdj = periodAdj.filter { it.kind != EarningsAdjKind.TAX && it.taxable }.sumOf { it.amount }
        val nontaxAdj = periodAdj.filter { it.kind != EarningsAdjKind.TAX && !it.taxable }.sumOf { it.amount }
        var taxable = Money.round(taxLines.sumOf { it.taxable } + taxableAdj)
        var nontaxable = Money.round(taxLines.sumOf { it.nontaxable } + nontaxAdj)
        var taxCollected = Money.round(taxLines.sumOf { it.taxCollected } + taxFromAdj)
        val byCounty = mergeCounty(taxLines, periodAdj)
        val override = overrides.firstOrNull {
            it.grain == grain.name && it.startMs == start
        }
        val applied = override != null && (
            override.locked.isNotEmpty() ||
                override.paid != null || override.invoiced != null || override.estimated != null ||
                override.taxCollected != null || override.taxable != null || override.nontaxable != null
            )
        return EarningsTaxSnapshot(
            paid = pickOverride(override, ManualField.TAX_PAID, override?.paid, paid),
            invoiced = pickOverride(override, ManualField.TAX_INVOICED, override?.invoiced, invoiced),
            estimated = pickOverride(override, ManualField.TAX_ESTIMATED, override?.estimated, estimated),
            taxable = pickOverride(override, ManualField.TAX_TAXABLE, override?.taxable, taxable),
            nontaxable = pickOverride(override, ManualField.TAX_NONTAXABLE, override?.nontaxable, nontaxable),
            taxCollected = pickOverride(override, ManualField.TAX_COLLECTED, override?.taxCollected, taxCollected),
            invoiceCount = window.invoiceCount,
            jobCount = window.jobCount,
            byCounty = byCounty,
            quarter = if (grain == NySalesTaxPeriods.Grain.QUARTER) {
                NySalesTaxPeriods.quarterContaining(start)
            } else {
                NySalesTaxPeriods.quarterContaining(start)
            },
            overrideApplied = applied
        )
    }

    fun csv(
        snapshots: List<Pair<String, EarningsTaxSnapshot>>,
        countyRows: List<CountyTaxRow>
    ): String {
        val header = "Period,Paid,Invoiced,Estimated,Taxable,Non-taxable,Tax collected"
        val body = snapshots.joinToString("\n") { (label, s) ->
            listOf(
                label,
                money(s.paid),
                money(s.invoiced),
                money(s.estimated),
                money(s.taxable),
                money(s.nontaxable),
                money(s.taxCollected)
            ).joinToString(",")
        }
        val countyHeader = "County,Rate %,Taxable,Non-taxable,Tax collected"
        val countyBody = countyRows.joinToString("\n") {
            listOf(
                csvCell(it.county),
                money(it.ratePercent),
                money(it.taxable),
                money(it.nontaxable),
                money(it.taxCollected)
            ).joinToString(",")
        }
        return "$header\n$body\n\n$countyHeader\n$countyBody"
    }

    private fun taxLines(invoices: List<Invoice>, jobs: List<Job>, start: Long, end: Long): List<CountyTaxRow> {
        val jobsById = jobs.associateBy { it.id }
        val fromInvoices = invoices
            .filter { stamp(it) in start until end }
            .filter { it.status != InvoiceStatus.CANCELLED && it.status != InvoiceStatus.DRAFT }
            .map { invoice ->
                val job = jobsById[invoice.jobId]
                val county = job?.county.orEmpty().ifBlank { "Unknown" }
                val rate = NyCountyTaxRates.taxRatePercentForCounty(county, job?.state ?: "NY") ?: 0.0
                val tax = invoice.taxAmountOverride ?: invoice.taxAmount
                val taxableBase = (invoice.subtotal - invoice.discountAmount).coerceAtLeast(0.0)
                val taxable = if (tax > 0.0) taxableBase else 0.0
                val nontaxable = if (tax > 0.0) 0.0 else taxableBase
                CountyTaxRow(NyCountyTaxRates.displayName(county), rate, taxable, nontaxable, tax)
            }
        val invoicedJobIds = invoices.map { it.jobId }.toSet()
        val fromJobs = jobs
            .filter { jobStamp(it) in start until end && it.status != JobStatus.CANCELLED }
            .filter { it.id !in invoicedJobIds }
            .map { job ->
                val quote = PricingCalculator.compute(job.pricing)
                val tax = quote.taxAmount.effective
                val taxableBase = Money.minus(quote.subtotal.effective, quote.discountAmount.effective)
                val county = job.county.orEmpty().ifBlank { "Unknown" }
                val rate = if (job.pricing.taxRateManual) job.pricing.taxRatePercent
                else NyCountyTaxRates.taxRatePercentForCounty(county, job.state ?: "NY") ?: job.pricing.taxRatePercent
                val taxable = if (tax > 0.0) taxableBase else 0.0
                val nontaxable = if (tax > 0.0) 0.0 else taxableBase
                CountyTaxRow(NyCountyTaxRates.displayName(county), rate, taxable, nontaxable, tax)
            }
        return fromInvoices + fromJobs
    }

    private fun mergeCounty(lines: List<CountyTaxRow>, adjustments: List<EarningsAdjustment>): List<CountyTaxRow> {
        val map = linkedMapOf<String, CountyTaxRow>()
        fun put(row: CountyTaxRow) {
            val key = NyCountyTaxRates.displayName(row.county)
            val prev = map[key]
            map[key] = if (prev == null) row.copy(county = key) else prev.copy(
                taxable = Money.round(prev.taxable + row.taxable),
                nontaxable = Money.round(prev.nontaxable + row.nontaxable),
                taxCollected = Money.round(prev.taxCollected + row.taxCollected),
                ratePercent = if (row.ratePercent > 0) row.ratePercent else prev.ratePercent
            )
        }
        lines.forEach(::put)
        adjustments.forEach { adj ->
            val county = adj.county.ifBlank { "Adjustment" }
            when (adj.kind) {
                EarningsAdjKind.TAX -> put(CountyTaxRow(county, 0.0, 0.0, 0.0, adj.amount))
                else -> put(
                    CountyTaxRow(
                        county = county,
                        ratePercent = NyCountyTaxRates.taxRatePercentForCounty(county) ?: 0.0,
                        taxable = if (adj.taxable) adj.amount else 0.0,
                        nontaxable = if (adj.taxable) 0.0 else adj.amount,
                        taxCollected = 0.0
                    )
                )
            }
        }
        return map.values.sortedBy { it.county }
    }

    private fun sum(adj: List<EarningsAdjustment>, kind: EarningsAdjKind): Double =
        adj.filter { it.kind == kind }.sumOf { it.amount }

    private fun stamp(invoice: Invoice): Long = invoice.issueDate.takeIf { it > 0L } ?: invoice.createdAt

    private fun jobStamp(job: Job): Long = job.completedDate ?: job.scheduledDate ?: job.createdAt

    private fun pickOverride(
        override: EarningsPeriodOverride?,
        key: String,
        lockedValue: Double?,
        computed: Double
    ): Double {
        if (override == null) return computed
        if (key in override.locked) return lockedValue ?: 0.0
        return lockedValue ?: computed
    }

    fun parseLock(raw: String): Double = raw.trim().toDoubleOrNull()?.let { Money.round(it) } ?: 0.0

    private fun money(value: Double): String = String.format(java.util.Locale.US, "%.2f", value)

    private fun csvCell(value: String): String {
        val cleaned = value.replace("\r", " ").replace("\n", " ").trim()
        return if (cleaned.contains(',') || cleaned.contains('"')) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else cleaned
    }
}
