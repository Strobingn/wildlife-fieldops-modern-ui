package com.strobingn.wildlifefieldops.ui.viewmodel

import java.util.Calendar
import java.util.TimeZone

/**
 * Local-time day and month boundaries. Epoch arithmetic such as `now - now % 86400000`
 * gives UTC midnight, which is early evening in New York, so "today" flipped at 7 or 8 PM.
 */
object LocalPeriods {

    fun dayStart(now: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** First millisecond of the next local day (DST-safe, so a day may be 23 or 25 hours). */
    fun nextDayStart(now: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = dayStart(now, zone)
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

    fun monthStart(now: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = dayStart(now, zone)
            set(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis

    fun isToday(millis: Long?, now: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        millis != null && millis >= dayStart(now, zone) && millis < nextDayStart(now, zone)
}
