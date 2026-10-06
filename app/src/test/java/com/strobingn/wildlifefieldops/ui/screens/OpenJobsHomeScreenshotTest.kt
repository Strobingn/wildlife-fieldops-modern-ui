package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.SyncSnapshot
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
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
import java.util.Calendar

/**
 * Bottom of Home with the Open jobs section, light and dark.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h780dp-xhdpi")
class OpenJobsHomeScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h2400dp-xhdpi")
    fun seeAllLinkCapsAtEightAndReportsTheTotal() {
        var opened = 0
        val jobs = (1..9).map { index ->
            Job(
                id = "cap-$index",
                customerName = "Customer $index",
                address = "$index Oak St",
                status = JobStatus.SCHEDULED,
                scheduledDate = index * 3_600_000L
            )
        }
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = false) {
                Box(Modifier.size(411.dp, 2200.dp)) {
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
                        onSeeAllOpenJobs = { opened += 1 },
                        preview = homeWithOpenJobs(OpenHomeJobs.list(jobs))
                    )
                }
            }
        }
        composeRule.onNodeWithText("See all open jobs (9)").assertIsDisplayed()
        composeRule.onNodeWithTag("home-open-jobs-count").assertTextEquals("9")
        assertEquals(8, composeRule.onAllNodesWithTag("home-open-job").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Customer 9").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("See all open jobs (9)").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun light() = render(dark = false, name = "home-open-jobs-light")

    @Test
    fun dark() = render(dark = true, name = "home-open-jobs-dark")

    private fun render(dark: Boolean, name: String) {
        val open = sampleOpenJobs()
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 740.dp)) {
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
                        preview = homeWithOpenJobs(open)
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Open jobs").performScrollTo()
        val scrollable = composeRule.onNode(hasScrollAction())
        repeat(6) {
            scrollable.performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
                scrollBy(0f, 600f)
            }
            composeRule.waitForIdle()
        }
        composeRule.onNodeWithText("Open jobs").assertIsDisplayed()
        composeRule.onNodeWithText("Pat Lee").assertIsDisplayed()
        composeRule.onNodeWithText("Willow Properties").assertIsDisplayed()
        composeRule.onNodeWithText("Ada Brooks").assertIsDisplayed()
        composeRule.onNodeWithText("In progress").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Scheduled").fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, composeRule.onAllNodesWithText("Hudson Valley Customer").fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("$", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("overdue", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        val fabNodes = composeRule.onAllNodesWithTag("home-floating-create").fetchSemanticsNodes()
        if (fabNodes.isNotEmpty()) {
            val fabTop = fabNodes.first().boundsInRoot.top
            val lastRow = composeRule.onNodeWithText("4 River Road, Newburgh").fetchSemanticsNode().boundsInRoot
            assertFalse(
                "last open job overlaps the floating buttons",
                lastRow.bottom > fabTop + 1f && lastRow.top < fabNodes.first().boundsInRoot.bottom
            )
            assertTrue(lastRow.bottom <= fabTop + 1f)
        }
        saveShot(name)
    }

    private fun saveShot(name: String) {
        val bitmap = composeRule.runOnIdle {
            val compose = composeRule.activity.window.decorView.findShotComposeView()
                ?: error("AndroidComposeView not in the hierarchy")
            compose.drawToBitmap()
        }
        val dir = File("/opt/cursor/artifacts")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}

private fun homeWithOpenJobs(open: List<Job>): DashboardPreview {
    val sync = SyncSnapshot("Synced", TextSecondary)
    return DashboardPreview(
        stats = DashboardStats(todayJobs = 1, scheduledJobs = 2, inProgressJobs = 1, completedJobs = 1),
        recentJobs = emptyList(),
        todayOnSchedule = emptyList(),
        openJobs = open,
        reminders = emptyList(),
        dueNextSteps = emptyList(),
        dueTrapChecks = emptyList(),
        weather = WeatherUiState.Unavailable("Shop weather is off for this preview."),
        sync = sync,
        greeting = "Good morning",
        todayLabel = "Tuesday, Oct 6",
        isLoading = false
    )
}

private fun sampleOpenJobs(): List<Job> {
    fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val soon = Job(
        id = "open-soon",
        customerName = "Pat Lee",
        address = "12 Oak St, Cornwall",
        status = JobStatus.IN_PROGRESS,
        scheduledDate = at(6, 9, 0),
        estimatedValue = 450.0
    )
    val later = Job(
        id = "open-later",
        customerName = "Willow Properties",
        address = "210 Willow Avenue, Cornwall",
        status = JobStatus.SCHEDULED,
        scheduledDate = at(6, 14, 30)
    )
    val next = Job(
        id = "open-next",
        customerName = "Ada Brooks",
        address = "4 River Road, Newburgh",
        status = JobStatus.SCHEDULED,
        scheduledDate = at(8, 11, 0)
    )
    val done = Job(
        id = "open-done",
        customerName = "Hudson Valley Customer",
        address = "9 Pine Ave",
        status = JobStatus.COMPLETED,
        scheduledDate = at(5, 8, 0),
        estimatedValue = 900.0
    )
    return OpenHomeJobs.list(listOf(next, done, later, soon))
}

private fun View.findShotComposeView(): View? {
    if (javaClass.simpleName == "AndroidComposeView") return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findShotComposeView()?.let { return it }
        }
    }
    return null
}
