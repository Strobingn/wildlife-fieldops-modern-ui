package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class TrapReminderStage { LEAD, DUE }

/** One phone reminder for one trap check: [fireAt] is when it should be posted. */
data class TrapReminder(
    val trap: TrapLog,
    val dueAt: Long,
    val stage: TrapReminderStage,
    val fireAt: Long
) {
    val key: String get() = TrapReminders.key(trap.id, dueAt, stage)
}

/**
 * Due-time math and reminder planning for trap checks. Pure, so it is unit tested.
 * Wording is "Due now" / "Due 3:40 PM". Never "overdue".
 */
object TrapReminders {
    const val HOUR_MS = 3_600_000L
    const val MINUTE_MS = 60_000L
    const val DEFAULT_INTERVAL_HOURS = 24
    const val DEFAULT_LEAD_MINUTES = 120
    const val MAX_INTERVAL_HOURS = 24 * 14
    const val MAX_LEAD_MINUTES = 24 * 60

    fun key(trapRowId: String, dueAt: Long, stage: TrapReminderStage): String =
        "$trapRowId|$dueAt|${stage.name}"

    /** Per-trap interval wins; otherwise the Settings default; otherwise 24 hours. */
    fun intervalHours(trap: TrapLog, defaultHours: Int): Int {
        val chosen = trap.checkIntervalHours?.takeIf { it > 0 }
            ?: defaultHours.takeIf { it > 0 }
            ?: DEFAULT_INTERVAL_HOURS
        return chosen.coerceIn(1, MAX_INTERVAL_HOURS)
    }

    fun nextDueAfterCheck(checkedAt: Long, intervalHours: Int): Long =
        checkedAt + intervalHours.coerceIn(1, MAX_INTERVAL_HOURS) * HOUR_MS

    /** Active traps with a due time get reminders. Pulled or disabled traps never do. */
    fun remindable(trap: TrapLog): Boolean =
        TrapCheckPlanner.isActive(trap.status) && trap.nextCheckDate != null

    fun plan(traps: List<TrapLog>, leadMinutes: Int): List<TrapReminder> {
        val leadMs = leadMinutes.coerceIn(0, MAX_LEAD_MINUTES) * MINUTE_MS
        return traps.filter(::remindable).flatMap { trap ->
            val due = trap.nextCheckDate ?: return@flatMap emptyList()
            buildList {
                if (leadMs > 0) add(TrapReminder(trap, due, TrapReminderStage.LEAD, due - leadMs))
                add(TrapReminder(trap, due, TrapReminderStage.DUE, due))
            }
        }
    }

    private fun stillUseful(r: TrapReminder, now: Long): Boolean =
        !(r.stage == TrapReminderStage.LEAD && now >= r.dueAt)

    /**
     * Reminders to post at [now]. Anything in [sent] is never posted again. A lead
     * reminder is dropped once the check is due; the due reminder covers it.
     */
    fun dueNow(
        traps: List<TrapLog>,
        now: Long,
        leadMinutes: Int,
        sent: Set<String>
    ): List<TrapReminder> = plan(traps, leadMinutes).filter { r ->
        r.fireAt <= now && r.key !in sent && stillUseful(r, now)
    }

    /** Keys to remember after posting. A due reminder also retires its lead reminder. */
    fun keysToMark(posted: List<TrapReminder>): Set<String> = posted.flatMap { r ->
        if (r.stage == TrapReminderStage.DUE) {
            listOf(r.key, key(r.trap.id, r.dueAt, TrapReminderStage.LEAD))
        } else {
            listOf(r.key)
        }
    }.toSet()

    /** Earliest future reminder, for scheduling the next exact wake-up. */
    fun nextFireAt(
        traps: List<TrapLog>,
        now: Long,
        leadMinutes: Int,
        sent: Set<String>
    ): Long? = plan(traps, leadMinutes)
        .filter { it.fireAt > now && it.key !in sent && stillUseful(it, it.fireAt) }
        .minOfOrNull { it.fireAt }

    fun dueText(due: Long?, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        if (due == null) return "No check set"
        if (due <= now) return "Due now"
        val dueAt = Instant.ofEpochMilli(due).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = dueAt.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        val days = ChronoUnit.DAYS.between(today, dueAt.toLocalDate())
        return when {
            days == 0L -> "Due $time"
            days == 1L -> "Due tomorrow $time"
            days < 7L -> "Due " + dueAt.format(DateTimeFormatter.ofPattern("EEE", Locale.US)) + " $time"
            else -> "Due " + dueAt.format(DateTimeFormatter.ofPattern("MMM d", Locale.US)) + " $time"
        }
    }

    fun notificationTitle(r: TrapReminder): String {
        val name = r.trap.trapId.ifBlank { "Trap" }
        return when (r.stage) {
            TrapReminderStage.LEAD -> "Trap check soon: $name"
            TrapReminderStage.DUE -> "Trap check due now: $name"
        }
    }

    fun notificationBody(
        r: TrapReminder,
        jobTitle: String,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): String = listOf(dueText(r.dueAt, now, zone), jobTitle, r.trap.trapLocation)
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

/** Optional note on the big Checked button. */
enum class TrapCheckOutcome(val label: String) {
    NONE("No note"),
    CAUGHT("Caught"),
    RELEASED("Released"),
    EUTHANIZED("Euthanized"),
    REBAITED("Rebaited")
}

object TrapCheckRecorder {

    fun speciesLabel(type: CatchType): String =
        type.name.lowercase(Locale.US).replace('_', ' ').replaceFirstChar { it.titlecase(Locale.US) }

    /**
     * Records a check at [now] and sets the next due time from the trap's interval.
     * A blank note never clears the notes already on the trap.
     */
    fun checked(
        trap: TrapLog,
        outcome: TrapCheckOutcome,
        species: CatchType,
        count: Int,
        note: String,
        now: Long,
        intervalHours: Int
    ): TrapLog {
        val base = trap.copy(
            checkDate = now,
            nextCheckDate = TrapReminders.nextDueAfterCheck(now, intervalHours),
            updatedAt = now,
            isSynced = false
        )
        val withOutcome = when (outcome) {
            TrapCheckOutcome.NONE -> base.copy(actionTaken = "Checked")
            TrapCheckOutcome.CAUGHT -> {
                val caught = if (species == CatchType.NONE) CatchType.OTHER else species
                val n = count.coerceAtLeast(1)
                base.copy(
                    status = TrapStatus.TRIGGERED,
                    catchType = caught,
                    catchCount = n,
                    actionTaken = "Caught ${speciesLabel(caught)} x$n"
                )
            }
            TrapCheckOutcome.RELEASED -> base.copy(disposition = "Released on site", actionTaken = "Released")
            TrapCheckOutcome.EUTHANIZED -> base.copy(disposition = "Euthanized", actionTaken = "Euthanized")
            TrapCheckOutcome.REBAITED -> base.copy(
                status = TrapStatus.SET,
                baitCondition = "Rebaited",
                actionTaken = "Rebaited"
            )
        }
        val trimmed = note.trim()
        return if (trimmed.isEmpty()) withOutcome else withOutcome.copy(conditionNotes = trimmed)
    }

    /** Pulled: the trap is off the line. No next check, so no more reminders. */
    fun pulled(trap: TrapLog, now: Long): TrapLog = trap.copy(
        status = TrapStatus.REMOVED,
        checkDate = now,
        nextCheckDate = null,
        actionTaken = "Pulled",
        updatedAt = now,
        isSynced = false
    )

    fun offersDecEntry(outcome: TrapCheckOutcome): Boolean = outcome == TrapCheckOutcome.CAUGHT
}
