package com.strobingn.wildlifefieldops.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the typed New Job path. Home previously opened [Screen.JobDictate]
 * (voice/AI only). If any primary entry point points at voice again, this fails.
 */
class ManualJobEntryTest {

    @Test
    fun createRouteIsBlankUnifiedJobForm() {
        assertEquals("job_form/new", Screen.JobForm.createRoute())
        assertEquals("job_form/new", ManualJobEntry.createRoute())
        assertTrue(ManualJobEntry.isManualCreateRoute(ManualJobEntry.createRoute()))
        assertFalse(ManualJobEntry.isVoiceOnlyCreateRoute(ManualJobEntry.createRoute()))
    }

    @Test
    fun everyPrimaryEntryPointOpensTheBlankFormNotVoice() {
        ManualJobEntry.EntryPoint.entries.forEach { point ->
            val route = ManualJobEntry.destination(point)
            assertEquals(point.name, "job_form/new", route)
            assertTrue(point.name, ManualJobEntry.isManualCreateRoute(route))
            assertFalse(point.name, ManualJobEntry.isVoiceOnlyCreateRoute(route))
        }
    }

    @Test
    fun voiceDictateStaysAHelperRouteNotTheCreateRoute() {
        assertEquals("job_dictate", Screen.JobDictate.route)
        assertTrue(ManualJobEntry.isVoiceOnlyCreateRoute(Screen.JobDictate.route))
        assertFalse(ManualJobEntry.isManualCreateRoute(Screen.JobDictate.route))
    }

    @Test
    fun blankFormListsEveryFieldSirTypesByHand() {
        val fields = ManualJobEntry.typedFields
        listOf(
            "customer name",
            "phone",
            "email",
            "service address",
            "billing address",
            "species",
            "problem",
            "notes",
            "date/time",
            "price lines",
            "totals"
        ).forEach { field ->
            assertTrue(field, fields.contains(field))
        }
        assertEquals(ManualJobEntry.ACTION_LABEL, "New Job")
    }
}
