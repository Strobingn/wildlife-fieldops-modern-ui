package com.strobingn.wildlifefieldops.ai.accuracy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAccuracyLedgerTest {

    @Test
    fun onlyBlankToFilledCountsAsAFill() {
        val filled = AiAccuracyLedger.filledFields(
            before = mapOf("Title" to "", "Notes" to "typed by Sir", "Address" to ""),
            after = mapOf("Title" to "Raccoon in attic", "Notes" to "typed by Sir", "Address" to "")
        )
        assertEquals(mapOf("Title" to "Raccoon in attic"), filled)
    }

    @Test
    fun savedValuesPairWithFillsAndRankMostChanged() {
        var log = emptyList<AiFillRecord>()
        log = AiAccuracyLedger.recordFill(log, "Dictation", "s1", "Title", "Racoon attic", 1)
        log = AiAccuracyLedger.recordFill(log, "Dictation", "s1", "Address", "12 Main St", 1)
        log = AiAccuracyLedger.recordSaved(log, "s1", mapOf("Title" to "Raccoon in attic", "Address" to "12 Main St"), 2)
        log = AiAccuracyLedger.recordFill(log, "Text import", "s2", "Title", "Bat", 3)
        log = AiAccuracyLedger.recordSaved(log, "s2", mapOf("Title" to ""), 4)
        val stats = AiAccuracyLedger.summary(log)
        assertEquals("Title", stats.first().field)
        assertEquals(2, stats.first().changed)
        assertEquals(2, stats.first().saved)
        assertEquals(listOf("Dictation", "Text import"), stats.first().sources)
        val address = stats.first { it.field == "Address" }
        assertEquals(0, address.changed)
        assertTrue(address.examples.isEmpty())
    }

    @Test
    fun unsavedSessionsStayPendingAndWhitespaceIsNotAChange() {
        var log = AiAccuracyLedger.recordFill(emptyList(), "Suggest", "s1", "Weather advice", "Check early", 1)
        assertTrue(AiAccuracyLedger.summary(log).isEmpty())
        log = AiAccuracyLedger.recordSaved(log, "other", mapOf("Weather advice" to "x"), 2)
        assertTrue(AiAccuracyLedger.summary(log).isEmpty())
        log = AiAccuracyLedger.recordSaved(log, "s1", mapOf("Weather advice" to "  check   early "), 3)
        assertEquals(0, AiAccuracyLedger.summary(log).single().changed)
    }
}
