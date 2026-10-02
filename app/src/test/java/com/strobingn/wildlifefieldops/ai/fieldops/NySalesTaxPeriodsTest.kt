package com.strobingn.wildlifefieldops.ai.fieldops

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class NySalesTaxPeriodsTest {

    @Test
    fun quarterlyPeriodsMatchSt100() {
        val may = cal(2026, Calendar.MAY, 10)
        val q = NySalesTaxPeriods.quarterContaining(may)
        assertEquals("Mar–May 2026", q.label)
        assertEquals(cal(2026, Calendar.JUNE, 20), q.dueMs)
        assertTrue(may in q.startMs until q.endMs)

        val july = cal(2026, Calendar.JULY, 1)
        val summer = NySalesTaxPeriods.quarterContaining(july)
        assertEquals("Jun–Aug 2026", summer.label)
        assertEquals(cal(2026, Calendar.SEPTEMBER, 20), summer.dueMs)

        val oct = cal(2026, Calendar.OCTOBER, 2)
        val fall = NySalesTaxPeriods.quarterContaining(oct)
        assertEquals("Sep–Nov 2026", fall.label)
        assertEquals(cal(2026, Calendar.DECEMBER, 20), fall.dueMs)

        val jan = cal(2026, Calendar.JANUARY, 15)
        val winter = NySalesTaxPeriods.quarterContaining(jan)
        assertEquals("Dec–Feb 2025", winter.label)
        assertEquals(cal(2026, Calendar.MARCH, 20), winter.dueMs)
        assertTrue(cal(2025, Calendar.DECEMBER, 1) in winter.startMs until winter.endMs)
    }

    @Test
    fun monthAndYearBounds() {
        val mid = cal(2026, Calendar.APRIL, 18)
        val (ms, me) = NySalesTaxPeriods.monthBounds(mid)
        assertEquals(cal(2026, Calendar.APRIL, 1), ms)
        assertEquals(cal(2026, Calendar.MAY, 1), me)
        val (ys, ye) = NySalesTaxPeriods.yearBounds(mid)
        assertEquals(cal(2026, Calendar.JANUARY, 1), ys)
        assertEquals(cal(2027, Calendar.JANUARY, 1), ye)
    }

    @Test
    fun calendarYearHasFourNyQuarters() {
        val qs = NySalesTaxPeriods.quartersForCalendarYear(2026)
        assertEquals(4, qs.size)
        assertTrue(qs.any { it.label.startsWith("Dec–Feb") })
        assertTrue(qs.any { it.label.startsWith("Mar–May") })
        assertTrue(qs.any { it.label.startsWith("Jun–Aug") })
        assertTrue(qs.any { it.label.startsWith("Sep–Nov") })
    }

    private fun cal(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
