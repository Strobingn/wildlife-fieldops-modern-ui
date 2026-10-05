package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.model.Reminder
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the Jobs tab through the Compose view. Pixel copy is unavailable
 * in this JVM, so the bitmap comes from [drawToBitmap].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w480dp-h2000dp-xhdpi")
class JobsScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = render(dark = false, name = "light")

    @Test
    fun dark() = render(dark = true, name = "dark")

    private fun render(dark: Boolean, name: String) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 1800.dp)) {
                    JobListScreen(
                        onNavigateToJobDetail = {},
                        onNavigateToJobForm = {},
                        onNavigateToDictate = {},
                        onBack = {},
                        showBack = false,
                        preview = sampleJobs()
                    )
                }
            }
        }
        composeRule.waitForIdle()
        listOf(
            "Jobs",
            "Dictate job",
            "New Job",
            "Scheduled",
            "In progress",
            "Completed",
            "Recent jobs",
            "Bat exclusion",
            "Next steps due",
            "Reminders",
            "Call Willow Properties"
        ).forEach { label ->
            assertTrue(label, composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
        val bitmap = composeRule.runOnIdle {
            val compose = composeRule.activity.window.decorView.findComposeView()
                ?: error("AndroidComposeView not in the hierarchy")
            compose.drawToBitmap()
        }
        val colors = mutableSetOf<Int>()
        val step = 24
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                colors += bitmap.getPixel(x, y)
            }
        }
        assertTrue("render is blank", colors.size > 4)
        val prefix = System.getenv("JOBS_SHOT_PREFIX") ?: "jobs"
        val dirs = listOfNotNull(
            File("build/screenshots"),
            System.getenv("HOME_SHOT_DIR")?.let { File(it) }
        )
        dirs.forEach { dir ->
            dir.mkdirs()
            FileOutputStream(File(dir, "$prefix-$name.png")).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }
}

private fun sampleJobs(): JobListPreview {
    val scheduled = Job(
        id = "job-scheduled",
        title = "Bat exclusion",
        customerName = "Willow Properties",
        address = "210 Willow Avenue, Cornwall",
        status = JobStatus.SCHEDULED,
        scheduledDate = System.currentTimeMillis()
    )
    val inProgress = Job(
        id = "job-progress",
        title = "Squirrel one-way door",
        customerName = "Pat Lee",
        address = "12 Oak Street",
        status = JobStatus.IN_PROGRESS
    )
    val completed = Job(
        id = "job-done",
        title = "Raccoon attic check",
        customerName = "Hudson Valley Customer",
        address = "4 River Road",
        status = JobStatus.COMPLETED
    )
    val withStep = completed.copy(nextStep = "Pull the one-way door", nextStepDueAt = System.currentTimeMillis())
    return JobListPreview(
        jobs = listOf(scheduled, inProgress, withStep),
        recentJobs = listOf(scheduled, inProgress, withStep),
        scheduledCount = 2,
        inProgressCount = 1,
        completedCount = 3,
        dueNextSteps = listOf(withStep),
        reminders = listOf(Reminder(id = "rem-1", title = "Call Willow Properties"))
    )
}

private fun View.findComposeView(): View? {
    if (javaClass.simpleName == "AndroidComposeView") return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findComposeView()?.let { return it }
        }
    }
    return null
}
