package com.strobingn.wildlifefieldops.ai.local

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LlmJsonSalvageTest {

    private val truncated = """
        ```json
        {"species": "raccoon, squirrel", "serviceType": "Raccoon Removal & Exclusion", "priority": "HIGH",
         "notes": "Raccoon activity at the soffit. Seal the 4 in gap and \"set\" a cage trap near the chim
    """.trimIndent()

    @Test
    fun extractsCompleteFieldsFromTruncatedOutput() {
        val json = LlmJsonSalvage.extractObjectText(truncated)
        assertEquals("raccoon, squirrel", LlmJsonSalvage.extractString(json, "species"))
        assertEquals("Raccoon Removal & Exclusion", LlmJsonSalvage.extractString(json, "serviceType"))
        assertEquals("HIGH", LlmJsonSalvage.extractString(json, "priority"))
    }

    @Test
    fun truncatedStringIsMarkedWithEllipsisAndUnescaped() {
        val json = LlmJsonSalvage.extractObjectText(truncated)
        val notes = LlmJsonSalvage.extractString(json, "notes")!!
        assertTrue(notes.startsWith("Raccoon activity at the soffit."))
        assertTrue(notes.contains("\"set\""))
        assertTrue(notes.endsWith("…"))
    }

    @Test
    fun closedStringHasNoEllipsis() {
        assertEquals("a b", LlmJsonSalvage.extractString("""{"notes": "a b"}""", "notes"))
    }

    @Test
    fun missingOrBlankStringIsNull() {
        assertNull(LlmJsonSalvage.extractString("""{"notes": ""}""", "notes"))
        assertNull(LlmJsonSalvage.extractString("""{"x": "y"}""", "notes"))
    }

    @Test
    fun numberAtEndOfTruncatedTextIsIgnored() {
        assertNull(LlmJsonSalvage.extractNumber("""{"estimatedPriceHigh": 12""", "estimatedPriceHigh"))
        assertEquals(
            450.0,
            LlmJsonSalvage.extractNumber("""{"estimatedPriceLow": 450, "x": 1}""", "estimatedPriceLow")!!,
            0.0
        )
        assertEquals(
            1200.5,
            LlmJsonSalvage.extractNumber("""{"estimatedPriceHigh": 1200.5}""", "estimatedPriceHigh")!!,
            0.0
        )
    }

    @Test
    fun listDropsCutOffLastItem() {
        val json = """{"recommendedActions": ["Seal gap", "Install one-way door", "Check att"""
        assertEquals(
            listOf("Seal gap", "Install one-way door"),
            LlmJsonSalvage.extractStringList(json, "recommendedActions")
        )
    }

    @Test
    fun listReadsCompleteArray() {
        val json = """{"complianceFlags": ["a", "b\"c"], "other": 1}"""
        assertEquals(listOf("a", "b\"c"), LlmJsonSalvage.extractStringList(json, "complianceFlags"))
        assertTrue(LlmJsonSalvage.extractStringList(json, "missing").isEmpty())
    }

    @Test
    fun extractObjectTextStripsFencesAndProse() {
        assertEquals(
            """{"a": 1}""",
            LlmJsonSalvage.extractObjectText("Here you go:\n```json\n{\"a\": 1}\n```\nThanks")
        )
        assertEquals("""{"a": 1}""", LlmJsonSalvage.extractObjectText("""{"a": 1}"""))
    }

    @Test
    fun looksLikeJsonDistinguishesProse() {
        assertTrue(LlmJsonSalvage.looksLikeJson(truncated))
        assertTrue(LlmJsonSalvage.looksLikeJson("{\"notes\": \"x"))
        assertFalse(LlmJsonSalvage.looksLikeJson("Seal the gap and set a trap."))
    }

    @Test
    fun unescapeHandlesUnicodeAndNewlines() {
        assertEquals("a\nbé", LlmJsonSalvage.unescape("a\\nb\\u00e9"))
    }

    @Test
    fun hardTimeoutReturnsNullWhenBlockOutlivesTimeout() {
        runBlocking {
            val started = System.currentTimeMillis()
            val result = HardTimeout.run(100) {
                delay(5_000)
                "late"
            }
            assertNull(result)
            assertTrue(System.currentTimeMillis() - started < 3_000)
        }
    }

    @Test
    fun hardTimeoutReturnsValueAndPropagatesFailure() {
        runBlocking {
            assertEquals("ok", HardTimeout.run(2_000) { "ok" })
            try {
                HardTimeout.run<String>(2_000) { throw IllegalStateException("boom") }
                fail("expected exception")
            } catch (e: IllegalStateException) {
                assertEquals("boom", e.message)
            }
        }
    }
}
