package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import java.util.Calendar

enum class TrapDueState {
    OVERDUE,
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

    fun dayEnd(now: Long): Long = dayStart(now) + DAY_MS

    fun dueState(nextCheckDate: Long?, now: Long): TrapDueState {
        if (nextCheckDate == null) return TrapDueState.UNSET
        val start = dayStart(now)
        val end = start + DAY_MS
        return when {
            nextCheckDate < start -> TrapDueState.OVERDUE
            nextCheckDate < end -> TrapDueState.DUE_TODAY
            else -> TrapDueState.UPCOMING
        }
    }

    fun isActive(status: TrapStatus): Boolean =
        status != TrapStatus.REMOVED && status != TrapStatus.DISABLED

    /**
     * Today's board: overdue first, then due today. Removed/disabled traps stay off the list.
     */
    fun todaysList(traps: List<TrapLog>, now: Long = System.currentTimeMillis()): List<TrapCheckItem> {
        return traps
            .filter { isActive(it.status) }
            .map { TrapCheckItem(it, dueState(it.nextCheckDate, now)) }
            .filter { it.dueState == TrapDueState.OVERDUE || it.dueState == TrapDueState.DUE_TODAY }
            .sortedWith(
                compareBy<TrapCheckItem> { if (it.dueState == TrapDueState.OVERDUE) 0 else 1 }
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
