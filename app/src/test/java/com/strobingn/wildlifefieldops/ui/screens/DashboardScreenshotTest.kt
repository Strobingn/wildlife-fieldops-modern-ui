package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.SyncSnapshot
import com.strobingn.wildlifefieldops.SyncStatusLine
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckItem
import com.strobingn.wildlifefieldops.ai.fieldops.TrapDueState
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.model.Reminder
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import com.strobingn.wildlifefieldops.data.remote.WeatherSnapshot
import com.strobingn.wildlifefieldops.navigation.Screen
import com.strobingn.wildlifefieldops.showsFloatingSync
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import com.strobingn.wildlifefieldops.ui.viewmodel.DashboardStats
import com.strobingn.wildlifefieldops.ui.viewmodel.WeatherUiState
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
 * Renders the actual Home tab ([DashboardScreen]) with sample jobs.
 * Pixel copy is unavailable here, so the bitmap comes from [drawToBitmap].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w480dp-h1800dp-xhdpi")
class DashboardScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = render(dark = false, name = "light")

    @Test
    fun dark() = render(dark = true, name = "dark")

    private fun render(dark: Boolean, name: String) {
        composeRule.mainClock.autoAdvance = true
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                val sync = SyncSnapshot("Synced", TextSecondary)
                Box(Modifier.size(411.dp, 1680.dp)) {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            DashboardScreen(
                                onNavigateToJobs = {},
                                onNavigateToInspections = {},
                                onNavigateToSchedule = {},
                                onNavigateToJobDetail = {},
                                onNavigateToJobForm = {},
                                onNavigateToMap = {},
                                onNavigateToRoutes = {},
                                onNavigateToSettings = {},
                                onNavigateToAI = {},
                                onNavigateToTrapChecks = {},
                                onNavigateToDictate = {},
                                onNavigateToTodayRoute = {},
                                onOpenDrawer = {},
                                preview = sampleHome(sync)
                            )
                        }
                        if (showsFloatingSync(Screen.Dashboard.route)) {
                            SyncStatusLine(text = sync.label, color = sync.color)
                        }
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(2_500)
        composeRule.waitForIdle()
        listOf("New Job", "Dictate job", "In progress", "Scheduled", "Completed", "Bat exclusion").forEach { label ->
            assertTrue(label, composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
        val bitmap = composeRule.runOnIdle {
            val compose = composeRule.activity.window.decorView.findComposeView()
                ?: error("AndroidComposeView not in the hierarchy")
            compose.drawToBitmap()
        }
        val colors = mutableSetOf<Int>()
        val step = 32
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                colors += bitmap.getPixel(x, y)
            }
        }
        assertTrue("render is blank", colors.size > 4)
        val prefix = System.getenv("HOME_SHOT_PREFIX") ?: "dashboard"
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

private fun sampleHome(sync: SyncSnapshot): DashboardPreview {
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
        status = JobStatus.COMPLETED,
        nextStep = "Pull the one-way door",
        nextStepDueAt = System.currentTimeMillis()
    )
    return DashboardPreview(
        stats = DashboardStats(
            totalJobs = 6,
            scheduledJobs = 2,
            inProgressJobs = 1,
            completedJobs = 3,
            totalCustomers = 12,
            totalInspections = 4,
            followUpRequired = 2,
            todayJobs = 3
        ),
        recentJobs = listOf(scheduled, inProgress, completed),
        todayOnSchedule = listOf(scheduled, inProgress, completed),
        reminders = listOf(
            Reminder(id = "rem-1", title = "Call Willow Properties", dueDate = System.currentTimeMillis())
        ),
        dueNextSteps = listOf(completed),
        dueTrapChecks = listOf(
            TrapCheckItem(
                trap = TrapLog(
                    id = "trap-1",
                    jobId = "job-progress",
                    trapId = "Cage 4",
                    trapLocation = "Attic hatch",
                    status = TrapStatus.SET
                ),
                dueState = TrapDueState.DUE_TODAY,
                jobTitle = "Squirrel one-way door"
            )
        ),
        weather = WeatherUiState.Ready(
            snap = WeatherSnapshot(
                tempF = 62,
                condition = "Clouds",
                description = "Broken clouds",
                humidity = 55,
                windMph = 8f,
                summaryLine = "62°F, broken clouds"
            ),
            placeLabel = "Cornwall, NY"
        ),
        sync = sync,
        greeting = "Good morning",
        todayLabel = "Sunday, Oct 4"
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
