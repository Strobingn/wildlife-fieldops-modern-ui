package com.strobingn.wildlifefieldops.ai.admission

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextMemoryAdmissionTest {

    @After
    fun resetSubsystems() {
        FieldSubsystemPresence.resetForTests()
    }

    @Test
    fun fieldTaskCapStaysAt2048() {
        assertEquals(2048, ContextMemoryAdmission.FIELD_TASK_CONTEXT_CAP)
        assertEquals(2048, ContextBudgetConfig().maxContextTokens)
        assertEquals(0.20, ContextBudgetConfig().minHeadroomFraction, 0.0001)
    }

    @Test
    fun configRejectsContextAboveFieldCapAndWeakenedHeadroom() {
        assertFails { ContextBudgetConfig(maxContextTokens = 4096) }
        assertFails { ContextBudgetConfig(minHeadroomFraction = 0.10) }
    }

    @Test
    fun clampsRequestAboveCapWhenMemoryAllows2048() {
        val decision = ContextMemoryAdmission.evaluate(
            requestedContextTokens = 8192,
            residentContextTokens = 0,
            memory = roomy(),
            modelWeightBytes = 512L * MB,
            modelWeightsResident = false,
        )
        assertEquals(ContextAdmissionAction.INITIALIZE, decision.action)
        assertEquals(2048, decision.grantedContextTokens)
        assertTrue(decision.admitted)
        assertTrue(decision.reasons.any { it.contains("clamped") })
        assertTrue(decision.headroomFraction >= 0.20)
    }

    @Test
    fun deniesInitWhenHeadroomWouldFallBelow20Percent() {
        val decision = ContextMemoryAdmission.evaluate(
            requestedContextTokens = 2048,
            residentContextTokens = 0,
            memory = MemorySnapshot(totalBytes = 8L * GB, availableBytes = 1L * GB),
            modelWeightBytes = 2L * GB,
            modelWeightsResident = false,
        )
        assertEquals(ContextAdmissionAction.DENY, decision.action)
        assertFalse(decision.admitted)
        assertEquals(0, decision.grantedContextTokens)
        assertTrue(decision.headroomFraction < 0.20)
    }

    @Test
    fun shrinksContextToTheLargestStepThatKeepsHeadroom() {
        val decision = ContextMemoryAdmission.evaluate(
            requestedContextTokens = 2048,
            residentContextTokens = 0,
            memory = MemorySnapshot(totalBytes = 5_000_000_000L, availableBytes = 1_502_760_448L),
            modelWeightBytes = 400_000_000L,
            modelWeightsResident = false,
        )
        assertEquals(ContextAdmissionAction.INITIALIZE, decision.action)
        assertEquals(1408, decision.grantedContextTokens)
        assertTrue(decision.headroomFraction >= 0.20)
    }

    @Test
    fun activeSubsystemsCanDenyAContextThatWouldOtherwiseFit() {
        val memory = MemorySnapshot(totalBytes = 8_000_000_000L, availableBytes = 2_730_331_648L)
        val weights = 1_000_000_000L
        val open = ContextMemoryAdmission.evaluate(
            requestedContextTokens = 2048,
            residentContextTokens = 0,
            memory = memory,
            modelWeightBytes = weights,
            modelWeightsResident = false,
        )
        val crowded = ContextMemoryAdmission.evaluate(
            requestedContextTokens = 2048,
            residentContextTokens = 0,
            memory = memory,
            modelWeightBytes = weights,
            modelWeightsResident = false,
            activeSubsystems = setOf(FieldSubsystem.CAMERAX, FieldSubsystem.ARCORE, FieldSubsystem.MAPS),
        )
        assertEquals(2048, open.grantedContextTokens)
        assertEquals(ContextAdmissionAction.DENY, crowded.action)
        assertFalse(crowded.admitted)
    }

    @Test
    fun expansionStopsAtTheBudgetAndHoldingDoesNotGrow() {
        val memory = MemorySnapshot(totalBytes = 4_000_000_000L, availableBytes = 878_644_200L)
        val expanded = ContextMemoryAdmission.evaluate(
            requestedContextTokens = 2048,
            residentContextTokens = 512,
            memory = memory,
            modelWeightBytes = 900_000_000L,
            modelWeightsResident = true,
        )
        assertEquals(ContextAdmissionAction.EXPAND, expanded.action)
        assertEquals(1280, expanded.grantedContextTokens)
        assertTrue(expanded.headroomFraction >= 0.20)

        val held = ContextMemoryAdmission.evaluate(
            requestedContextTokens = 512,
            residentContextTokens = 512,
            memory = memory,
            modelWeightBytes = 900_000_000L,
            modelWeightsResident = true,
        )
        assertEquals(ContextAdmissionAction.HOLD, held.action)
        assertEquals(512, held.grantedContextTokens)
    }

    @Test
    fun sameInputsProduceTheSameDecision() {
        val first = ContextMemoryAdmission.evaluate(
            requestedContextTokens = 2048,
            residentContextTokens = 0,
            memory = roomy(),
            modelWeightBytes = 256L * MB,
            modelWeightsResident = false,
            activeSubsystems = setOf(FieldSubsystem.MAPS),
        )
        val second = ContextMemoryAdmission.evaluate(
            requestedContextTokens = 2048,
            residentContextTokens = 0,
            memory = roomy(),
            modelWeightBytes = 256L * MB,
            modelWeightsResident = false,
            activeSubsystems = setOf(FieldSubsystem.MAPS),
        )
        assertEquals(first, second)
    }

    @Test
    fun compactPromptDropsChatHistoryAndStaysBounded() {
        val chat = "UNIQUE_CHAT_SENTENCE_zxq " + "turn ".repeat(500)
        val prompt = ContextMemoryAdmission.compact(
            StructuredFieldState(
                jobId = "job-14",
                species = "raccoon",
                serviceType = "Raccoon Removal & Exclusion",
                damage = listOf("chew / gnaw damage"),
                entries = listOf("soffit / fascia gap"),
                notes = "Operator notes stay.",
                chatHistory = listOf(chat),
            ),
            maxChars = 400,
        )
        assertTrue(prompt.contains("species: raccoon"))
        assertTrue(prompt.contains("soffit / fascia gap"))
        assertFalse(prompt.contains("UNIQUE_CHAT_SENTENCE_zxq"))
        assertTrue(prompt.length <= 400)
    }

    @Test
    fun subsystemPresenceIsReferenceCounted() {
        FieldSubsystemPresence.resetForTests()
        assertTrue(FieldSubsystemPresence.active().isEmpty())
        FieldSubsystemPresence.acquire(FieldSubsystem.CAMERAX)
        FieldSubsystemPresence.acquire(FieldSubsystem.CAMERAX)
        FieldSubsystemPresence.acquire(FieldSubsystem.MAPS)
        assertEquals(setOf(FieldSubsystem.CAMERAX, FieldSubsystem.MAPS), FieldSubsystemPresence.active())
        FieldSubsystemPresence.release(FieldSubsystem.CAMERAX)
        assertTrue(FieldSubsystem.CAMERAX in FieldSubsystemPresence.active())
        FieldSubsystemPresence.release(FieldSubsystem.CAMERAX)
        FieldSubsystemPresence.release(FieldSubsystem.CAMERAX)
        assertEquals(setOf(FieldSubsystem.MAPS), FieldSubsystemPresence.active())
    }

    private fun roomy() = MemorySnapshot(totalBytes = 8L * GB, availableBytes = 6L * GB)

    private fun assertFails(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("expected IllegalArgumentException")
    }

    private companion object {
        const val MB = 1024L * 1024L
        const val GB = 1024L * 1024L * 1024L
    }
}
