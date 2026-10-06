package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TrapRemindersTest {
    private val zone = ZoneId.of("America/New_York")
    private fun at(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()
    private val hour = TrapReminders.HOUR_MS

    private fun trap(next: Long?, status: TrapStatus = TrapStatus.SET, interval: Int? = null) = TrapLog(
        id = "t1", jobId = "j1", trapId = "Cage 1", status = status, nextCheckDate = next, checkIntervalHours = interval
    )

    @Test
    fun defaultIntervalIs24HoursAndPerTrapWins() {
        assertEquals(24, TrapReminders.intervalHours(trap(null), 0))
        assertEquals(12, TrapReminders.intervalHours(trap(null), 12))
        assertEquals(6, TrapReminders.intervalHours(trap(null, interval = 6), 12))
        assertEquals(TrapReminders.MAX_INTERVAL_HOURS, TrapReminders.intervalHours(trap(null, interval = 10_000), 24))
        val checked = at("2026-10-06T07:40")
        assertEquals(at("2026-10-07T07:40"), TrapReminders.nextDueAfterCheck(checked, 24))
        assertEquals(at("2026-10-06T15:40"), TrapReminders.nextDueAfterCheck(checked, 8))
    }

    @Test
    fun dueTextNeverSaysOverdue() {
        val now = at("2026-10-06T12:00")
        assertEquals("Due now", TrapReminders.dueText(now, now, zone))
        assertEquals("Due now", TrapReminders.dueText(now - 5 * hour, now, zone))
        assertEquals("Due 3:40 PM", TrapReminders.dueText(at("2026-10-06T15:40"), now, zone))
        assertEquals("Due tomorrow 7:05 AM", TrapReminders.dueText(at("2026-10-07T07:05"), now, zone))
        assertEquals("Due Fri 9:00 AM", TrapReminders.dueText(at("2026-10-09T09:00"), now, zone))
        assertEquals("No check set", TrapReminders.dueText(null, now, zone))
        listOf(now - 48 * hour, now, now + hour).forEach {
            assertFalse(TrapReminders.dueText(it, now, zone).contains("overdue", ignoreCase = true))
        }
        assertEquals("Due now", TrapCheckPlanner.dueLabel(TrapDueState.DUE_NOW))
    }

    @Test
    fun leadReminderTwoHoursBeforeThenDueReminder() {
        val due = at("2026-10-06T15:40")
        val traps = listOf(trap(due))
        assertTrue(TrapReminders.dueNow(traps, due - 3 * hour, 120, emptySet()).isEmpty())
        val lead = TrapReminders.dueNow(traps, due - 2 * hour, 120, emptySet())
        assertEquals(listOf(TrapReminderStage.LEAD), lead.map { it.stage })
        val sent = TrapReminders.keysToMark(lead)
        assertTrue(TrapReminders.dueNow(traps, due - hour, 120, sent).isEmpty())
        assertEquals(due, TrapReminders.nextFireAt(traps, due - hour, 120, sent))
        val atDue = TrapReminders.dueNow(traps, due, 120, sent)
        assertEquals(listOf(TrapReminderStage.DUE), atDue.map { it.stage })
        val allSent = sent + TrapReminders.keysToMark(atDue)
        assertTrue(TrapReminders.dueNow(traps, due + hour, 120, allSent).isEmpty())
        assertNull(TrapReminders.nextFireAt(traps, due + hour, 120, allSent))
    }

    @Test
    fun missedLeadIsDroppedOnceDueSoOnlyOneAlertPosts() {
        val due = at("2026-10-06T15:40")
        val posted = TrapReminders.dueNow(listOf(trap(due)), due + 10 * 60_000L, 120, emptySet())
        assertEquals(listOf(TrapReminderStage.DUE), posted.map { it.stage })
        val sent = TrapReminders.keysToMark(posted)
        assertTrue(TrapReminders.key("t1", due, TrapReminderStage.LEAD) in sent)
    }

    @Test
    fun checkingMovesTheDueTimeSoNewRemindersAreFresh() {
        val due = at("2026-10-06T15:40")
        val sent = TrapReminders.keysToMark(TrapReminders.dueNow(listOf(trap(due)), due, 120, emptySet()))
        val checked = TrapCheckRecorder.checked(
            trap(due), TrapCheckOutcome.NONE, CatchType.NONE, 0, "", now = due + hour, intervalHours = 24
        )
        assertEquals(due + 25 * hour, checked.nextCheckDate)
        assertTrue(TrapReminders.dueNow(listOf(checked), due + 2 * hour, 120, sent).isEmpty())
        assertEquals(1, TrapReminders.dueNow(listOf(checked), due + 23 * hour, 120, sent).size)
    }

    @Test
    fun pulledOrDisabledTrapsNeverRemind() {
        val due = at("2026-10-06T15:40")
        val pulled = TrapCheckRecorder.pulled(trap(due), due - hour)
        assertEquals(TrapStatus.REMOVED, pulled.status)
        assertNull(pulled.nextCheckDate)
        assertTrue(TrapReminders.dueNow(listOf(pulled, trap(due, TrapStatus.DISABLED)), due + hour, 120, emptySet()).isEmpty())
    }

    @Test
    fun zeroLeadMeansOnlyTheDueReminder() {
        val due = at("2026-10-06T15:40")
        assertEquals(listOf(TrapReminderStage.DUE), TrapReminders.plan(listOf(trap(due)), 0).map { it.stage })
    }

    @Test
    fun checkedOutcomesFillOnlyTheirFields() {
        val now = at("2026-10-06T08:00")
        val base = trap(now).copy(conditionNotes = "by the shed")
        val caught = TrapCheckRecorder.checked(base, TrapCheckOutcome.CAUGHT, CatchType.RACCOON, 0, "", now, 24)
        assertEquals(TrapStatus.TRIGGERED, caught.status)
        assertEquals(CatchType.RACCOON, caught.catchType)
        assertEquals(1, caught.catchCount)
        assertEquals("by the shed", caught.conditionNotes)
        assertEquals(now, caught.checkDate)
        assertTrue(TrapCheckRecorder.offersDecEntry(TrapCheckOutcome.CAUGHT))
        assertFalse(TrapCheckRecorder.offersDecEntry(TrapCheckOutcome.NONE))
        val rebaited = TrapCheckRecorder.checked(base, TrapCheckOutcome.REBAITED, CatchType.NONE, 0, "fresh marshmallow", now, 24)
        assertEquals(TrapStatus.SET, rebaited.status)
        assertEquals("fresh marshmallow", rebaited.conditionNotes)
        val released = TrapCheckRecorder.checked(base, TrapCheckOutcome.RELEASED, CatchType.NONE, 0, "", now, 24)
        assertEquals("Released on site", released.disposition)
    }
}
