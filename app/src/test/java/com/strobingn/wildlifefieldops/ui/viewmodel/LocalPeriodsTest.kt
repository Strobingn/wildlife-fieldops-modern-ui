package com.strobingn.wildlifefieldops.ui.viewmodel

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPeriodsTest {

    private val ny = TimeZone.getTimeZone("America/New_York")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(ny).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun eveningInNewYorkIsStillToday() {
        // 9 PM EDT on Oct 8 is already Oct 9 in UTC. Epoch maths flipped "today" here.
        val now = at(2026, Calendar.OCTOBER, 8, 21)
        val start = LocalPeriods.dayStart(now, ny)
        assertEquals(at(2026, Calendar.OCTOBER, 8, 0), start)
        assertTrue(LocalPeriods.isToday(at(2026, Calendar.OCTOBER, 8, 9), now, ny))
        assertFalse(LocalPeriods.isToday(at(2026, Calendar.OCTOBER, 9, 9), now, ny))
    }

    @Test
    fun nextDayStartIsExclusiveEnd() {
        val now = at(2026, Calendar.OCTOBER, 8, 12)
        val end = LocalPeriods.nextDayStart(now, ny)
        assertEquals(at(2026, Calendar.OCTOBER, 9, 0), end)
        assertFalse(LocalPeriods.isToday(end, now, ny))
        assertTrue(LocalPeriods.isToday(end - 1, now, ny))
    }

    @Test
    fun dstChangeDayHasTwentyFiveHours() {
        // Fall back on 2026-11-01 in New York.
        val now = at(2026, Calendar.NOVEMBER, 1, 12)
        val length = LocalPeriods.nextDayStart(now, ny) - LocalPeriods.dayStart(now, ny)
        assertEquals(25L * 3_600_000L, length)
    }

    @Test
    fun monthStartIsFirstOfCalendarMonth() {
        val now = at(2026, Calendar.OCTOBER, 28, 15)
        assertEquals(at(2026, Calendar.OCTOBER, 1, 0), LocalPeriods.monthStart(now, ny))
    }

    @Test
    fun nullMillisIsNeverToday() {
        assertFalse(LocalPeriods.isToday(null, at(2026, Calendar.OCTOBER, 8, 12), ny))
    }
}
