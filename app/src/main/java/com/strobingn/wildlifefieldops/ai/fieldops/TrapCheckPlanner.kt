package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import java.util.Calendar

enum class TrapDueState {
    /** Due before today. Shown as "Due now". */
    DUE_NOW,
    DUE_TODAY,
    UPCOMING,
    UNSET
}

data class TrapCheckItem(
    val trap: TrapLog,
    val dueState: TrapDueState,
    val jobTitle: String = ""
)

object TrapCheckPlanner {

    const val DAY_MS = 86_400_000L

    fun dayStart(now: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Next local midnight. Not start + 24h: that is wrong on the 23h/25h DST days. */
    fun dayEnd(now: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart(now) }
        cal.add(Calendar.DATE, 1)
        return cal.timeInMillis
    }

    /** Operator-facing label. The stored due state is unchanged. */
    fun dueLabel(state: TrapDueState): String = when (state) {
        TrapDueState.DUE_NOW -> "Due now"
        TrapDueState.DUE_TODAY -> "Due today"
        TrapDueState.UPCOMING -> "Upcoming"
        TrapDueState.UNSET -> "No date"
    }

    fun dueState(nextCheckDate: Long?, now: Long): TrapDueState {
        if (nextCheckDate == null) return TrapDueState.UNSET
        val start = dayStart(now)
        val end = dayEnd(now)
        return when {
            nextCheckDate < start -> TrapDueState.DUE_NOW
            nextCheckDate < end -> TrapDueState.DUE_TODAY
            else -> TrapDueState.UPCOMING
        }
    }

    fun isActive(status: TrapStatus): Boolean =
        status != TrapStatus.REMOVED && status != TrapStatus.DISABLED

    /**
     * Today's board: due now first, then due today. Removed/disabled traps stay off the list.
     */
    fun todaysList(traps: List<TrapLog>, now: Long = System.currentTimeMillis()): List<TrapCheckItem> {
        return traps
            .filter { isActive(it.status) }
            .map { TrapCheckItem(it, dueState(it.nextCheckDate, now)) }
            .filter { it.dueState == TrapDueState.DUE_NOW || it.dueState == TrapDueState.DUE_TODAY }
            .sortedWith(
                compareBy<TrapCheckItem> { if (it.dueState == TrapDueState.DUE_NOW) 0 else 1 }
                    .thenBy { it.trap.nextCheckDate ?: Long.MAX_VALUE }
            )
    }

    fun allScheduled(traps: List<TrapLog>, now: Long = System.currentTimeMillis()): List<TrapCheckItem> {
        return traps
            .map { TrapCheckItem(it, dueState(it.nextCheckDate, now)) }
            .sortedWith(
                compareBy<TrapCheckItem> { it.trap.nextCheckDate ?: Long.MAX_VALUE }
                    .thenByDescending { it.trap.updatedAt }
            )
    }

    /** NY nuisance practice: check set traps within 24 hours. */
    fun nextCheckAfter(status: TrapStatus, now: Long = System.currentTimeMillis()): Long? =
        when (status) {
            TrapStatus.REMOVED, TrapStatus.DISABLED -> null
            else -> now + DAY_MS
        }

    /**
     * Opening Add trap or Log trap check starts at today and the planner's
     * next check. Both stay editable; save stores whatever is in the boxes.
     */
    fun editorDates(status: TrapStatus, now: Long = System.currentTimeMillis()): TrapEditorDates {
        val next = nextCheckAfter(status, now)
        return TrapEditorDates(
            checkDay = FieldDate.formatDay(now),
            nextCheckDay = next?.let { FieldDate.formatDay(it) }.orEmpty()
        )
    }
}

data class TrapEditorDates(
    val checkDay: String,
    val nextCheckDay: String
)
