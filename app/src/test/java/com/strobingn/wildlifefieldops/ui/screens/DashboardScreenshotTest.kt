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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.swipeUp
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
@Config(sdk = [35], application = Application::class, qualifiers = "w480dp-h900dp-xhdpi")
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
                // Phone content height: short enough that always-on FABs would cover Today.
                Box(Modifier.size(411.dp, 520.dp)) {
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
                                preview = emptyTodayHome(sync)
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
        assertTopDoesNotCoverToday()
        saveShot(name, "top")
        scrollHomeToEnd()
        composeRule.waitForIdle()
        saveShot(name, "bottom")
        assertBottomClearsFloatingActions()
    }

    private fun assertTopDoesNotCoverToday() {
        assertEquals(0, composeRule.onAllNodesWithTag("home-floating-create").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("home-create-bar").assertIsDisplayed()
        composeRule.onNodeWithText("0 jobs scheduled").assertIsDisplayed()
        val protected = listOf("Schedule", "Today's route").flatMap { label ->
            val nodes = composeRule.onAllNodesWithText(label).fetchSemanticsNodes()
            assertTrue("$label missing at the top", nodes.isNotEmpty())
            nodes.map { it.boundsInRoot }
        }
        listOf("New Job", "Dictate job").forEach { label ->
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().forEach { node ->
                protected.forEach { target ->
                    assertFalse(
                        "$label overlaps a Today action at the top",
                        overlaps(node.boundsInRoot, target)
                    )
                }
            }
        }
    }

    private fun assertBottomClearsFloatingActions() {
        val createCount = composeRule.onAllNodesWithTag("home-create-bar").fetchSemanticsNodes().size
        val fabCount = composeRule.onAllNodesWithTag("home-floating-create").fetchSemanticsNodes().size
        if (createCount != 0 || fabCount != 1) {
            File("/tmp/home-bottom.txt").writeText(
                "create=$createCount fab=$fabCount\n" + composeRule.onRoot(useUnmergedTree = true).printToString()
            )
        }
        assertEquals("create bar still composed after scrolling to the end", 0, createCount)
        assertEquals("floating actions missing after the in-page buttons scroll off", 1, fabCount)
        composeRule.onNodeWithTag("home-floating-create").assertIsDisplayed()
        composeRule.onNodeWithText("Shop weather").assertIsDisplayed()
        val weather = composeRule.onNodeWithText("Shop weather").fetchSemanticsNode().boundsInRoot
        val fab = composeRule.onNodeWithTag("home-floating-create").fetchSemanticsNode().boundsInRoot
        val dictate = composeRule.onAllNodesWithText("Dictate job", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }
        val newJob = composeRule.onAllNodesWithText("New Job", useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue("Dictate job missing at the bottom: $dictate", dictate.isNotEmpty())
        assertTrue("New Job missing at the bottom: $newJob", newJob.isNotEmpty())
        assertFalse(
            "Shop weather $weather is behind floating actions $fab",
            overlaps(weather, fab)
        )
        assertTrue(
            "weather bottom ${weather.bottom} should sit above fab top ${fab.top}",
            weather.bottom <= fab.top + 1f
        )
    }

    private fun scrollHomeToEnd() {
        val scrollable = composeRule.onNode(hasScrollAction())
        repeat(8) {
            scrollable.performTouchInput { swipeUp() }
            composeRule.waitForIdle()
        }
        repeat(8) {
            scrollable.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
                scrollBy(0f, 800f)
            }
            composeRule.waitForIdle()
        }
    }

    private fun saveShot(name: String, position: String) {
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
        val prefix = System.getenv("HOME_SHOT_PREFIX") ?: "dashboard"
        val dirs = listOfNotNull(
            File("build/screenshots"),
            System.getenv("HOME_SHOT_DIR")?.let { File(it) }
        )
        dirs.forEach { dir ->
            dir.mkdirs()
            FileOutputStream(File(dir, "$prefix-$name-$position.png")).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }
}

private fun overlaps(a: Rect, b: Rect): Boolean {
    if (a.right <= b.left || b.right <= a.left) return false
    if (a.bottom <= b.top || b.bottom <= a.top) return false
    return true
}

private fun emptyTodayHome(sync: SyncSnapshot): DashboardPreview {
    val home = sampleHome(sync)
    return home.copy(
        stats = home.stats.copy(todayJobs = 0),
        todayOnSchedule = emptyList()
    )
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
