package com.strobingn.wildlifefieldops.ai.fieldops

import java.util.Calendar

/**
 * New York State sales-tax quarterly filing periods (ST-100), not calendar quarters:
 * Mar–May, Jun–Aug, Sep–Nov, Dec–Feb. Quarterly returns are due the 20th of the
 * month after the period ends (Jun 20, Sep 20, Dec 20, Mar 20).
 *
 * https://www.tax.ny.gov/pubs_and_bulls/tg_bulletins/st/filing_requirements.htm
 */
data class NySalesQuarter(
    val label: String,
    val startMs: Long,
    val endMs: Long,
    val dueMs: Long,
    val dueLabel: String
)

object NySalesTaxPeriods {

    enum class Grain { DAY, WEEK, MONTH, YEAR, QUARTER }

    fun monthBounds(now: Long): Pair<Long, Long> {
        val start = calendar(now).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            startOfDay()
        }.timeInMillis
        val end = calendar(start).apply { add(Calendar.MONTH, 1) }.timeInMillis
        return start to end
    }

    fun yearBounds(now: Long): Pair<Long, Long> {
        val start = calendar(now).apply {
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            startOfDay()
        }.timeInMillis
        val end = calendar(start).apply { add(Calendar.YEAR, 1) }.timeInMillis
        return start to end
    }

    fun bounds(grain: Grain, now: Long): Pair<Long, Long> = when (grain) {
        Grain.DAY -> EarningsRollup.dayBounds(now)
        Grain.WEEK -> EarningsRollup.weekBounds(now)
        Grain.MONTH -> monthBounds(now)
        Grain.YEAR -> yearBounds(now)
        Grain.QUARTER -> {
            val q = quarterContaining(now)
            q.startMs to q.endMs
        }
    }

    fun quarterContaining(now: Long): NySalesQuarter {
        val cal = calendar(now)
        val month = cal.get(Calendar.MONTH)
        val year = cal.get(Calendar.YEAR)
        return when (month) {
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY ->
                quarter(year, Calendar.MARCH, Calendar.JUNE, 20, "Mar–May", year)
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST ->
                quarter(year, Calendar.JUNE, Calendar.SEPTEMBER, 20, "Jun–Aug", year)
            Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER ->
                quarter(year, Calendar.SEPTEMBER, Calendar.DECEMBER, 20, "Sep–Nov", year)
            else -> {
                val startYear = if (month == Calendar.DECEMBER) year else year - 1
                quarter(startYear, Calendar.DECEMBER, Calendar.MARCH, 20, "Dec–Feb", startYear + 1)
            }
        }
    }

    fun quartersOverlapping(start: Long, end: Long): List<NySalesQuarter> {
        val out = LinkedHashSet<NySalesQuarter>()
        var cursor = start
        while (cursor < end) {
            val q = quarterContaining(cursor)
            out.add(q)
            cursor = q.endMs
        }
        if (end > start) out.add(quarterContaining(end - 1))
        return out.sortedBy { it.startMs }
    }

    fun quartersForCalendarYear(year: Int): List<NySalesQuarter> {
        val march = calendar(0).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, Calendar.MARCH)
            set(Calendar.DAY_OF_MONTH, 15)
            startOfDay()
        }.timeInMillis
        val first = quarterContaining(march)
        return listOf(
            quarterContaining(first.startMs - 1L),
            first,
            quarterContaining(calendar(first.endMs).apply { add(Calendar.DATE, 15) }.timeInMillis),
            quarterContaining(calendar(first.endMs).apply { add(Calendar.MONTH, 4) }.timeInMillis)
        )
    }

    private fun quarter(
        startYear: Int,
        startMonth: Int,
        dueMonth: Int,
        dueDay: Int,
        label: String,
        dueYear: Int
    ): NySalesQuarter {
        val start = calendar(0).apply {
            set(Calendar.YEAR, startYear)
            set(Calendar.MONTH, startMonth)
            set(Calendar.DAY_OF_MONTH, 1)
            startOfDay()
        }.timeInMillis
        val end = calendar(start).apply { add(Calendar.MONTH, 3) }.timeInMillis
        val due = calendar(0).apply {
            set(Calendar.YEAR, dueYear)
            set(Calendar.MONTH, dueMonth)
            set(Calendar.DAY_OF_MONTH, dueDay)
            startOfDay()
        }.timeInMillis
        val dueCal = calendar(due)
        val dueLabel = String.format(
            java.util.Locale.US,
            "%s %d",
            dueCal.getDisplayName(Calendar.MONTH, Calendar.SHORT, java.util.Locale.US),
            dueCal.get(Calendar.DAY_OF_MONTH)
        )
        return NySalesQuarter(
            label = "$label $startYear",
            startMs = start,
            endMs = end,
            dueMs = due,
            dueLabel = "$dueLabel $dueYear"
        )
    }

    private fun calendar(now: Long): Calendar = Calendar.getInstance().apply { timeInMillis = now }

    private fun Calendar.startOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
