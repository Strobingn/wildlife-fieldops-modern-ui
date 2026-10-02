package com.strobingn.wildlifefieldops.data.remote

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictationJobFillTest {

    private val transcript =
        "Urgent raccoon in the attic for Pat Lee at 12 Oak Street Cornwall. Need a trap check."

    @Test
    fun heuristicFillIsInstantAndSaveableBeforeAiReturns() {
        val start = System.currentTimeMillis()
        val draft = JobIntakeParser.heuristicFill(transcript)
        val elapsed = System.currentTimeMillis() - start
        assertTrue("heuristic took ${elapsed}ms", elapsed < 1_000L)
        assertNotNull(draft)
        assertTrue(JobIntakeParser.canSave(draft))
        assertTrue(JobIntakeParser.canSave(draft) && elapsed < 1_000L)
        assertEquals("Pat Lee", draft!!.customerName)
        assertEquals("URGENT", draft.priority)
    }

    @Test
    fun saveIsBlockedOnlyWhenDraftIsEmptyNotWhileAiRuns() {
        assertFalse(JobIntakeParser.canSave(null))
        assertFalse(JobIntakeParser.canSave(JobIntakeDraft()))
        val heuristic = JobIntakeParser.heuristicFill(transcript)
        assertTrue(JobIntakeParser.canSave(heuristic))
    }

    @Test
    fun lateAiNeverOverwritesTypedOrClearedFields() {
        val heuristic = JobIntakeParser.heuristicFill(transcript)!!
        val typed = heuristic.copy(customerName = "Sir typed this", address = "")
        val edited = setOf(IntakeField.CUSTOMER, IntakeField.ADDRESS)
        val lateAi = JobIntakeDraft(
            title = "Cloud title",
            customerName = "AI Pat",
            address = "99 Cloud Lane",
            type = "Exclusion",
            priority = "LOW",
            description = "from cloud",
            notes = "AI notes"
        )
        val merged = JobIntakeParser.merge(typed, lateAi, edited)
        assertEquals("Sir typed this", merged.customerName)
        assertEquals("", merged.address)
        assertEquals(typed.title, merged.title)
        assertEquals(typed.type, merged.type)
        assertEquals(typed.priority, merged.priority)
        assertEquals("AI notes", merged.notes)
    }

    @Test
    fun lateAiFillsOnlyEmptyUneditedFields() {
        val current = JobIntakeDraft(
            title = "Raccoon Removal — Pat Lee",
            customerName = "Pat Lee",
            address = "",
            type = "Raccoon Removal",
            priority = "URGENT",
            description = transcript,
            notes = ""
        )
        val lateAi = JobIntakeDraft(
            title = "ignored title",
            customerName = "ignored customer",
            address = "12 Oak Street Cornwall",
            type = "ignored type",
            priority = "LOW",
            description = "ignored description",
            notes = "Bring one-way door"
        )
        val merged = JobIntakeParser.merge(current, lateAi, editedFields = emptySet())
        assertEquals("Raccoon Removal — Pat Lee", merged.title)
        assertEquals("Pat Lee", merged.customerName)
        assertEquals("12 Oak Street Cornwall", merged.address)
        assertEquals("Raccoon Removal", merged.type)
        assertEquals("URGENT", merged.priority)
        assertEquals(transcript, merged.description)
        assertEquals("Bring one-way door", merged.notes)
    }

    @Test
    fun refineTimesOutWithoutBlockingHeuristic() = runBlocking {
        val start = System.currentTimeMillis()
        val result = JobIntakeParser.refine(
            transcript = transcript,
            localReady = true,
            generateLocal = { _, _ ->
                delay(30_000)
                """{"title":"too late"}"""
            },
            cloudConfigured = false,
            completeCloud = { _, _ -> null to null },
            providerLabel = "xAI",
            localDisplayName = "local",
            skipLocalIfCloud = false,
            timeoutMs = 150L
        )
        val elapsed = System.currentTimeMillis() - start
        assertTrue("refine timeout took ${elapsed}ms", elapsed < 2_000L)
        assertNull(result.draft)
        assertTrue(result.error.orEmpty().contains("timed out"))
        assertTrue(JobIntakeParser.canSave(JobIntakeParser.heuristicFill(transcript)))
    }

    @Test
    fun skipLocalLlmWhenCloudIsConfigured() = runBlocking {
        var localCalled = false
        val json = """
            {"title":"Cloud job","customerName":"Pat","address":"1 Oak","type":"Removal",
            "priority":"HIGH","description":"from cloud","notes":"n"}
        """.trimIndent()
        val result = JobIntakeParser.refine(
            transcript = transcript,
            localReady = true,
            generateLocal = { _, _ ->
                localCalled = true
                null
            },
            cloudConfigured = true,
            completeCloud = { _, _ -> json to null },
            providerLabel = "xAI",
            localDisplayName = "local",
            skipLocalIfCloud = true,
            timeoutMs = 2_000L
        )
        assertFalse(localCalled)
        assertEquals("Cloud job", result.draft?.title)
        assertTrue(result.sourceLabel.contains("Cloud"))
    }
}
