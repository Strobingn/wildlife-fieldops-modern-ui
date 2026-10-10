package com.strobingn.wildlifefieldops.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DictationJobParserTest {

    private val sirSample =
        "New job for Maria Lopez at 22 Oak Street Newburgh New York 12550, phone 845 555 0199, " +
            "raccoon in the attic, schedule for tomorrow, high priority"

    private fun assertMaria(d: JobIntakeDraft) {
        assertEquals("Maria Lopez", d.customerName)
        assertEquals("845-555-0199", d.phone)
        assertEquals("22 Oak Street", d.address)
        assertEquals("Newburgh", d.city)
        assertEquals("NY", d.state)
        assertEquals("12550", d.zip)
        assertEquals("Raccoon", d.species)
        assertEquals("Raccoon Removal", d.type)
        assertEquals("Raccoon Removal — Maria Lopez", d.title)
        assertEquals("Raccoon in the attic", d.description)
        assertEquals("HIGH", d.priority)
        assertEquals("Scheduled", d.status)
        assertEquals("tomorrow", d.scheduleDay)
        assertEquals("", d.notes)
    }

    @Test
    fun sirsSentenceFillsEveryField() = assertMaria(DictationJobParser.parse(sirSample)!!)

    @Test
    fun lowercaseUnpunctuatedSpeechFillsEveryField() {
        val spoken = sirSample.lowercase().replace(",", "")
        assertMaria(DictationJobParser.parse(spoken)!!)
    }

    @Test
    fun transcriptIsNeverDumpedIntoDescriptionOrNotes() {
        val d = JobIntakeParser.heuristicFill(sirSample)!!
        assertFalse(d.description.contains("845"))
        assertFalse(d.description.contains("Oak Street"))
        assertTrue(d.description.length < 60)
        assertEquals("", d.notes)
    }

    @Test
    fun emailAndStatusAndStateCode() {
        val d = DictationJobParser.parse(
            "customer is pat lee 12 main st cornwall ny 12518 email pat at gmail dot com bats in the eaves job is in progress"
        )!!
        assertEquals("Pat Lee", d.customerName)
        assertEquals("pat@gmail.com", d.email)
        assertEquals("12 Main St", d.address)
        assertEquals("Cornwall", d.city)
        assertEquals("NY", d.state)
        assertEquals("12518", d.zip)
        assertEquals("Bat", d.species)
        assertEquals("In progress", d.status)
        assertEquals("MEDIUM", d.priority)
    }

    @Test
    fun scheduleTomorrowIsNineAmNextDay() {
        val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 10, 15, 30) }
        val at = DictationJobParser.scheduleMillis("tomorrow", now)!!
        val c = Calendar.getInstance().apply { timeInMillis = at }
        assertEquals(11, c.get(Calendar.DAY_OF_MONTH))
        assertEquals(9, c.get(Calendar.HOUR_OF_DAY))
        assertNull(DictationJobParser.scheduleMillis(""))
    }

    @Test
    fun aiImprovesRuleGuessesButNeverTypedOrClearedFields() {
        val rules = JobIntakeParser.heuristicFill(sirSample)!!
        val current = rules.copy(customerName = "Sir typed", email = "")
        val edited = setOf(IntakeField.CUSTOMER, IntakeField.EMAIL)
        val ai = rules.copy(
            customerName = "AI name", email = "ai@x.com", description = "Raccoon in attic, entry at soffit",
            city = "Newburgh City"
        )
        val merged = JobIntakeParser.merge(current, ai, edited, replaceable = rules)
        assertEquals("Sir typed", merged.customerName)
        assertEquals("", merged.email)
        assertEquals("Raccoon in attic, entry at soffit", merged.description)
        assertEquals("Newburgh City", merged.city)
    }

    @Test
    fun cloudTimeoutFallsBackToOnDeviceWithNotice() = runBlocking {
        val r = JobIntakeParser.refine(
            transcript = sirSample,
            localReady = true,
            generateLocal = { _, _ -> """{"customerName":"Maria Lopez","phone":"845-555-0199","status":"Scheduled"}""" },
            cloudConfigured = true,
            completeCloud = { _, _ -> Thread.sleep(2_000); "{}" to null },
            providerLabel = "Grok",
            localDisplayName = "Gemma",
            cloudTimeoutMs = 200
        )
        assertNotNull(r.draft)
        assertEquals("Maria Lopez", r.draft!!.customerName)
        assertEquals(JobIntakeParser.NOTICE_LOCAL, r.notice)
    }

    @Test
    fun noAiAtAllGivesShortNoticeAndNoDraft() = runBlocking {
        val r = JobIntakeParser.refine(
            transcript = sirSample,
            localReady = false,
            generateLocal = { _, _ -> null },
            cloudConfigured = true,
            completeCloud = { _, _ -> null to "HTTP 500" },
            providerLabel = "Grok",
            localDisplayName = "Gemma"
        )
        assertNull(r.draft)
        assertEquals(JobIntakeParser.NOTICE_RULES, r.notice)
        assertTrue(r.notice!!.length < 120)
    }

    @Test
    fun cloudAnswerUsedFirst() = runBlocking {
        var localCalled = false
        val r = JobIntakeParser.refine(
            transcript = sirSample,
            localReady = true,
            generateLocal = { _, _ -> localCalled = true; delay(1); null },
            cloudConfigured = true,
            completeCloud = { _, _ -> """{"customerName":"Maria Lopez","zip":"12550","status":"weird"}""" to null },
            providerLabel = "Grok",
            localDisplayName = "Gemma"
        )
        assertEquals("12550", r.draft!!.zip)
        assertEquals("", r.draft!!.status)
        assertFalse(localCalled)
        assertNull(r.notice)
    }
}
