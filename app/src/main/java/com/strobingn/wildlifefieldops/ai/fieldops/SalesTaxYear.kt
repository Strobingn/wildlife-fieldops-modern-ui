package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.Money
import java.util.Calendar
import java.util.Locale

/**
 * Automatic yearly New York State sales tax grand total for the Tax tab.
 * Built on [EarningsTaxEngine.snapshot], so per-job and per-invoice tax,
 * hand adjustments and Lock overrides all count the same way.
 *
 * Two year shapes:
 * - [Kind.NYS_TAX_YEAR]: Mar 1 – end of Feb, the NYS sales tax year (annual
 *   ST-101). Its four quarters (Mar–May, Jun–Aug, Sep–Nov, Dec–Feb) add up
 *   exactly to the total.
 * - [Kind.CALENDAR]: Jan 1 – Dec 31. Quarter rows are clipped to the year.
 */
object SalesTaxYear {

    enum class Kind(val label: String) {
        NYS_TAX_YEAR("NYS tax year (Mar–Feb)"),
        CALENDAR("Calendar year (Jan–Dec)")
    }

    data class Segment(
        val label: String,
        val dueLabel: String,
        val startMs: Long,
        val endMs: Long,
        val snapshot: EarningsTaxSnapshot
    )

    data class Summary(
        val kind: Kind,
        val year: Int,
        val label: String,
        val startMs: Long,
        val endMs: Long,
        /** Grand total, with any Lock override for the year applied. */
        val total: EarningsTaxSnapshot,
        /** Totals before Lock overrides, for showing what was calculated. */
        val calculated: EarningsTaxSnapshot,
        val segments: List<Segment>,
        /** Per jurisdiction (county) and rate, for ST-100 / ST-101. */
        val byJurisdiction: List<CountyTaxRow>
    )

    /** Override key: grain YEAR at the year's start (Jan 1 or Mar 1). */
    val overrideGrain = NySalesTaxPeriods.Grain.YEAR

    /** The year whose period contains [now]. Oct 2026 → NYS 2026 (Mar 2026–Feb 2027). */
    fun currentYear(kind: Kind, now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val y = cal.get(Calendar.YEAR)
        return if (kind == Kind.NYS_TAX_YEAR && cal.get(Calendar.MONTH) < Calendar.MARCH) y - 1 else y
    }

    fun bounds(kind: Kind, year: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            clear()
            set(year, if (kind == Kind.NYS_TAX_YEAR) Calendar.MARCH else Calendar.JANUARY, 1, 0, 0, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.YEAR, 1) }.timeInMillis
        return start to end
    }

    fun label(kind: Kind, year: Int): String = when (kind) {
        Kind.NYS_TAX_YEAR -> {
            val feb = Calendar.getInstance().apply { clear(); set(year + 1, Calendar.MARCH, 1); add(Calendar.DATE, -1) }
            "Mar 1, $year – Feb ${feb.get(Calendar.DAY_OF_MONTH)}, ${year + 1}"
        }
        Kind.CALENDAR -> "Jan 1 – Dec 31, $year"
    }

    fun summarize(
        invoices: List<Invoice>,
        jobs: List<Job>,
        adjustments: List<EarningsAdjustment>,
        overrides: List<EarningsPeriodOverride>,
        kind: Kind,
        year: Int
    ): Summary {
        val (start, end) = bounds(kind, year)
        val total = EarningsTaxEngine.snapshot(invoices, jobs, adjustments, overrides, start, end, overrideGrain)
        val calculated = EarningsTaxEngine.snapshot(invoices, jobs, adjustments, emptyList(), start, end, overrideGrain)
        val segments = NySalesTaxPeriods.quartersOverlapping(start, end).map { q ->
            val a = maxOf(q.startMs, start)
            val b = minOf(q.endMs, end)
            val clipped = a != q.startMs || b != q.endMs
            Segment(
                label = if (clipped) "${q.label} (part in $year)" else q.label,
                dueLabel = q.dueLabel,
                startMs = a,
                endMs = b,
                snapshot = EarningsTaxEngine.snapshot(
                    invoices, jobs, adjustments, emptyList(), a, b, NySalesTaxPeriods.Grain.QUARTER
                )
            )
        }
        return Summary(
            kind = kind,
            year = year,
            label = label(kind, year),
            startMs = start,
            endMs = end,
            total = total,
            calculated = calculated,
            segments = segments,
            byJurisdiction = calculated.byCounty
        )
    }

    /** CSV of the yearly summary: totals, quarter subtotals, jurisdictions. */
    fun csv(summary: Summary): String = buildString {
        fun m(v: Double) = String.format(Locale.US, "%.2f", Money.round(v))
        fun cell(s: String) = if (s.contains(',') || s.contains('"')) "\"${s.replace("\"", "\"\"")}\"" else s
        appendLine("NY sales tax summary,${cell(summary.kind.label)},${cell(summary.label)}")
        appendLine()
        appendLine("Line,Taxable sales,Sales tax collected,Non-taxable sales")
        appendLine(listOf("Year total", m(summary.total.taxable), m(summary.total.taxCollected), m(summary.total.nontaxable)).joinToString(","))
        if (summary.total.overrideApplied) {
            appendLine(listOf("Calculated (before manual)", m(summary.calculated.taxable), m(summary.calculated.taxCollected), m(summary.calculated.nontaxable)).joinToString(","))
        }
        summary.segments.forEach { s ->
            appendLine(listOf(cell("${s.label} (due ${s.dueLabel})"), m(s.snapshot.taxable), m(s.snapshot.taxCollected), m(s.snapshot.nontaxable)).joinToString(","))
        }
        appendLine()
        appendLine("Jurisdiction,Rate %,Taxable sales,Sales tax collected,Non-taxable sales")
        summary.byJurisdiction.forEach { r ->
            appendLine(listOf(cell(r.county), String.format(Locale.US, "%.3f", r.ratePercent), m(r.taxable), m(r.taxCollected), m(r.nontaxable)).joinToString(","))
        }
    }.trimEnd()
}
