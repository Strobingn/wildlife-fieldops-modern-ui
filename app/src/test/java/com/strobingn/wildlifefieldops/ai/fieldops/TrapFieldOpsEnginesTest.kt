package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PricingJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TrapFieldOpsEnginesTest {

    private val noon: Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 1, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun dueTodayAndOverdueSortOverdueFirst() {
        val start = TrapCheckPlanner.dayStart(noon)
        val overdue = TrapLog(id = "o", trapId = "A", nextCheckDate = start - 1_000L, status = TrapStatus.SET)
        val due = TrapLog(id = "d", trapId = "B", nextCheckDate = start + 3_600_000L, status = TrapStatus.EMPTY)
        val later = TrapLog(id = "l", trapId = "C", nextCheckDate = start + TrapCheckPlanner.DAY_MS + 1, status = TrapStatus.SET)
        val pulled = TrapLog(id = "p", trapId = "D", nextCheckDate = start, status = TrapStatus.REMOVED)
        val list = TrapCheckPlanner.todaysList(listOf(later, due, overdue, pulled), noon)
        assertEquals(listOf("o", "d"), list.map { it.trap.id })
        assertEquals(TrapDueState.OVERDUE, list[0].dueState)
        assertEquals(TrapDueState.DUE_TODAY, list[1].dueState)
    }

    @Test
    fun nextCheckIs24HoursUnlessPulled() {
        assertEquals(noon + TrapCheckPlanner.DAY_MS, TrapCheckPlanner.nextCheckAfter(TrapStatus.SET, noon))
        assertEquals(null, TrapCheckPlanner.nextCheckAfter(TrapStatus.REMOVED, noon))
    }

    @Test
    fun weatherAdviceFlagsStormAsUnsafe() {
        val draft = WeatherTrapAdvice.suggest(
            WeatherAdviceInput(condition = "Thunderstorm", description = "severe", trapCount = 2)
        )
        assertTrue(draft.skipUnsafe)
        assertTrue(draft.text.contains("storm", ignoreCase = true))
        assertTrue(draft.text.contains("2 traps"))
    }

    @Test
    fun weatherAdviceHeatMentionsShade() {
        val draft = WeatherTrapAdvice.suggest(
            WeatherAdviceInput(condition = "Clear", tempF = 94, hasCatch = true, species = "raccoon")
        )
        assertTrue(draft.text.contains("Heat", ignoreCase = true) || draft.text.contains("shade", ignoreCase = true))
        assertTrue(draft.text.contains("DEC") || draft.text.contains("catch", ignoreCase = true))
    }

    @Test
    fun decCsvIncludesRequiredColumnsAndEscapesCommas() {
        val trap = TrapLog(
            jobId = "j1",
            trapLocation = "Deck, south corner",
            catchType = CatchType.RACCOON,
            catchCount = 1,
            disposition = "Released on site",
            method = "Live cage trap",
            checkDate = noon,
            conditionNotes = "Healthy adult"
        )
        val csv = DecLogExporter.toCsv(
            DecLogExporter.rowsFromTraps(listOf(trap), mapOf("j1" to Job(id = "j1", title = "Oak St raccoon")))
        )
        assertTrue(csv.startsWith(DecLogExporter.HEADER))
        assertTrue(csv.contains("Raccoon"))
        assertTrue(csv.contains("\"Deck, south corner\""))
        assertTrue(csv.contains("Released on site"))
        assertTrue(csv.contains("Live cage trap"))
        assertTrue(csv.contains("Oak St raccoon"))
    }

    @Test
    fun emptyCatchWithoutDispositionIsNotExported() {
        val empty = TrapLog(status = TrapStatus.SET, catchType = CatchType.NONE)
        assertTrue(DecLogExporter.rowsFromTraps(listOf(empty)).isEmpty())
    }

    @Test
    fun followUpSuggestsTrapPullWhenSetsAreActive() {
        val draft = FollowUpPlanner.suggest(
            FollowUpInput(status = "COMPLETED", species = "raccoon", hasActiveTraps = true, now = noon)
        )
        assertEquals(FollowUpKind.TRAP_PULL, draft.kind)
        assertEquals(noon + 86_400_000L, draft.dueAt)
        assertTrue(draft.notes.contains("DEC") || draft.notes.contains("trap", ignoreCase = true))
    }

    @Test
    fun followUpWarrantyDefault() {
        val draft = FollowUpPlanner.suggest(
            FollowUpInput(status = "COMPLETED", species = "starling", now = noon, completedAt = noon)
        )
        assertEquals(FollowUpKind.WARRANTY, draft.kind)
        assertEquals(noon + 14 * 86_400_000L, draft.dueAt)
    }

    @Test
    fun operatorWinsKeepsTypedAdvice() {
        assertEquals("Sir's rewrite", OperatorWins.text("Sir's rewrite", "AI weather blurb", replace = false))
        assertEquals("AI weather blurb", OperatorWins.text("", "AI weather blurb", replace = false))
    }

    @Test
    fun trapRecordsRoundTripInsidePricingJson() {
        val record = TrapLog(
            id = "t1",
            jobId = "j1",
            trapId = "Deck-1",
            trapLocation = "Under deck",
            latitude = 41.44,
            longitude = -74.01,
            status = TrapStatus.SET,
            disposition = "Still set",
            method = "Live cage trap",
            nextCheckDate = noon
        ).toSynced()
        val pricing = JobPricing(trapRecords = listOf(record), weatherTrapAdvice = "Check after the rain")
        val decoded = PricingJson.decode(PricingJson.encode(pricing))
        assertEquals(1, decoded.trapRecords.size)
        assertEquals("Deck-1", decoded.trapRecords[0].trapId)
        assertEquals("Check after the rain", decoded.weatherTrapAdvice)
        assertTrue(decoded.isEmptyWorksheet())
        assertFalse("confirmed_species" == "trap_logs")
    }
}
