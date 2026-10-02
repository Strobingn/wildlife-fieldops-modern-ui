package com.strobingn.wildlifefieldops.navigation

import com.strobingn.wildlifefieldops.data.remote.JobIntakeParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * #71 hid voice create by pointing every primary New Job control at the blank
 * form. This guards that Home still exposes both entry points and that
 * [Screen.JobDictate] still records → fills → saves with a typed fallback.
 */
class VoiceJobEntryTest {

    @Test
    fun homeExposesNewJobAndDictateJob() {
        val home = readAppSource("ui/screens/DashboardScreen.kt")
        assertTrue(home.contains("ManualJobEntry.ACTION_LABEL"))
        assertTrue(home.contains("VoiceJobEntry.ACTION_LABEL"))
        assertTrue(home.contains("onNavigateToJobForm"))
        assertTrue(home.contains("onNavigateToDictate"))
        assertTrue(home.contains("Icons.Default.Mic"))
        assertTrue(home.contains("Icons.Default.Add"))
        assertTrue(home.contains("168.dp"))
        assertFalse(home.contains("onNavigateToJobForm = onNavigateToDictate"))
    }

    @Test
    fun jobsScreenAndDrawerExposeMic() {
        val jobs = readAppSource("ui/screens/JobListScreen.kt")
        assertTrue(jobs.contains("VoiceJobEntry.ACTION_LABEL"))
        assertTrue(jobs.contains("onNavigateToDictate"))
        assertTrue(jobs.contains("Icons.Default.Mic"))
        val activity = readAppSource("MainActivity.kt")
        assertTrue(activity.contains("VoiceJobEntry.createRoute()"))
        assertTrue(activity.contains("VoiceJobEntry.ACTION_LABEL"))
        assertTrue(activity.contains("VoiceJobEntry.manualFallbackRoute()"))
        assertTrue(activity.contains("Icons.Default.Mic"))
        assertTrue(activity.contains("launchSingleTop = true"))
        assertTrue(activity.contains("popUpTo(Screen.JobDictate.route)"))
    }

    @Test
    fun voiceRouteStillExistsAndFallsBackToBlankForm() {
        assertEquals("job_dictate", Screen.JobDictate.route)
        assertEquals("job_dictate", VoiceJobEntry.createRoute())
        assertTrue(VoiceJobEntry.isVoiceCreateRoute(Screen.JobDictate.route))
        assertEquals("job_form/new", VoiceJobEntry.manualFallbackRoute())
        assertEquals("Dictate job", VoiceJobEntry.ACTION_LABEL)
        assertEquals("Type manually", VoiceJobEntry.TYPE_MANUALLY_LABEL)
        assertEquals("New Job", ManualJobEntry.ACTION_LABEL)
        ManualJobEntry.EntryPoint.entries.forEach { point ->
            assertEquals(point.name, "job_form/new", ManualJobEntry.destination(point))
            assertFalse(point.name, ManualJobEntry.isVoiceOnlyCreateRoute(ManualJobEntry.destination(point)))
        }
        val dictate = readAppSource("ui/screens/JobDictateScreen.kt")
        assertTrue(dictate.contains("JobVoiceIntakePanel"))
        assertTrue(dictate.contains("fillJobFromDictation"))
        assertTrue(dictate.contains("saveJobWithSchedule"))
        assertTrue(dictate.contains("VoiceJobEntry.TYPE_MANUALLY_LABEL"))
        assertTrue(dictate.contains("onTypeManually"))
    }

    @Test
    fun heuristicFillWorksWithoutCloudSoVoiceSaveHasAPath() = runBlocking {
        val result = JobIntakeParser.parse(
            transcript = "Urgent raccoon in the attic for Pat Lee at 12 Oak Street Cornwall. Need a trap check.",
            localReady = false,
            generateLocal = { _, _ -> null },
            cloudConfigured = false,
            completeCloud = { _, _ -> null to null },
            providerLabel = "xAI",
            localDisplayName = "local",
            notConfiguredMessage = "AI not configured"
        )
        val draft = result.draft
        assertNotNull(draft)
        assertEquals("Raccoon Removal — Pat Lee", draft!!.title)
        assertEquals("Pat Lee", draft.customerName)
        assertTrue(draft.address.contains("12 Oak Street"))
        assertEquals("Raccoon Removal", draft.type)
        assertEquals("URGENT", draft.priority)
        assertTrue(result.sourceLabel.contains("heuristic"))
    }

    private fun readAppSource(relativeUnderJava: String): String {
        val suffix = "src/main/java/com/strobingn/wildlifefieldops/$relativeUnderJava"
        val candidates = listOf(
            File(suffix),
            File("app/$suffix"),
            File("../$suffix")
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("Missing $suffix (cwd=${File(".").canonicalPath})")
        return file.readText()
    }
}
