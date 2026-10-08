package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.dp
import com.strobingn.wildlifefieldops.MoreShellActions
import com.strobingn.wildlifefieldops.MoreShellHeader
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.syncSnapshot
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every block that left Home is still on the screen that now owns it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w480dp-h2000dp-xhdpi")
class MovedHomeItemsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun jobFlagsRecentJobsNextStepsAndRemindersAreOnJobs() {
        composeRule.setContent {
            WildlifeFieldOpsTheme {
                Box(Modifier.size(411.dp, 1600.dp)) {
                    JobListScreen(
                        onNavigateToJobDetail = {},
                        onNavigateToJobForm = {},
                        onNavigateToDictate = {},
                        onBack = {},
                        showBack = false,
                        preview = JobListPreview(
                            jobs = listOf(sampleJob()),
                            recentJobs = listOf(sampleJob()),
                            scheduledCount = 2,
                            inProgressCount = 1,
                            completedCount = 3,
                            dueNextSteps = listOf(sampleJob()),
                            reminders = listOf(
                                com.strobingn.wildlifefieldops.data.model.Reminder(
                                    id = "rem-1",
                                    title = "Call Willow Properties"
                                )
                            )
                        )
                    )
                }
            }
        }
        listOf(
            "Scheduled",
            "In progress",
            "Completed",
            "2",
            "1",
            "3",
            "Recent jobs",
            "Bat exclusion",
            "View all",
            "Next steps due",
            "Pull the one-way door",
            "Reminders",
            "Call Willow Properties"
        ).forEach { label ->
            composeRule.onAllNodesWithText(label)[0].assertIsDisplayed()
        }
    }

    @Test
    fun customerCountIsOnCustomers() {
        composeRule.setContent {
            WildlifeFieldOpsTheme {
                Box(Modifier.size(411.dp, 800.dp)) {
                    CustomerListScreen(
                        onNavigateToCustomerForm = {},
                        onBack = {},
                        showBack = false,
                        preview = CustomerListPreview(
                            customers = listOf(
                                Customer(id = "c1", firstName = "Pat", lastName = "Lee", phone = "845-555-0100")
                            ),
                            customerCount = 12
                        )
                    )
                }
            }
        }
        composeRule.onAllNodesWithText("Customers")[0].assertIsDisplayed()
        composeRule.onNodeWithText("12").assertIsDisplayed()
        composeRule.onNodeWithText("Pat Lee").assertIsDisplayed()
    }

    @Test
    fun inspectionAndFollowUpCountsAreOnInspections() {
        composeRule.setContent {
            WildlifeFieldOpsTheme {
                Box(Modifier.size(411.dp, 900.dp)) {
                    InspectionListScreen(
                        onNavigateToInspectionDetail = {},
                        onNavigateToInspectionForm = {},
                        onBack = {},
                        showBack = false,
                        preview = InspectionListPreview(
                            inspections = listOf(
                                Inspection(
                                    id = "insp-1",
                                    customerName = "Willow Properties",
                                    followUpRequired = true
                                )
                            ),
                            inspectionCount = 4,
                            followUpCount = 2,
                            initialTab = 1
                        )
                    )
                }
            }
        }
        composeRule.onAllNodesWithText("Inspections")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Follow-ups")[0].assertIsDisplayed()
        composeRule.onNodeWithText("4").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithText("Willow Properties").assertIsDisplayed()
        // Report counts live on the Reports tab, beside the Scheduled tab.
        composeRule.onNodeWithText("Scheduled (0)").assertIsDisplayed()
        composeRule.onNodeWithText("Reports (4)").assertIsDisplayed()
    }

    @Test
    fun scheduledInspectionsOpenFirstOnTheirOwnTab() {
        composeRule.setContent {
            WildlifeFieldOpsTheme {
                Box(Modifier.size(411.dp, 900.dp)) {
                    InspectionListScreen(
                        onNavigateToInspectionDetail = {},
                        onNavigateToInspectionForm = {},
                        onBack = {},
                        showBack = false,
                        preview = InspectionListPreview(
                            inspections = emptyList(),
                            scheduled = listOf(
                                Job(
                                    id = "insp-job",
                                    title = "Attic noise inspection",
                                    customerName = "Dana Reyes",
                                    status = JobStatus.INSPECTION,
                                    scheduledDate = null
                                )
                            )
                        )
                    )
                }
            }
        }
        composeRule.onNodeWithText("Scheduled (1)").assertIsDisplayed()
        composeRule.onNodeWithText("No time set (1)").assertIsDisplayed()
        composeRule.onNodeWithText("Attic noise inspection").assertIsDisplayed()
        composeRule.onNodeWithText("Dana Reyes").assertIsDisplayed()
        composeRule.onNodeWithText("Need a decision").assertIsDisplayed()
    }

    @Test
    fun quickActionsGridIsOnMore() {
        val sync = syncSnapshot(isSyncing = false, failed = false, pending = 0, failureDetail = null)
        composeRule.setContent {
            WildlifeFieldOpsTheme {
                Box(Modifier.size(411.dp, 900.dp).fillMaxSize()) {
                    MoreScreen(
                        onOpen = {},
                        header = { MoreShellHeader(sync) },
                        primaryActions = { MoreShellActions(onNewJob = {}, onDictate = {}) },
                        onNewJob = {},
                        onDictate = {},
                        onSchedule = {},
                        onMap = {},
                        onInspect = {},
                        onRoutes = {},
                        onReports = {}
                    )
                }
            }
        }
        listOf(
            "Quick actions",
            "New Job",
            "Dictate job",
            "Schedule",
            "Map",
            "Inspect",
            "Routes",
            "Reports"
        ).forEach { label ->
            assertTrue(label, composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
            composeRule.onAllNodesWithText(label)[0].assertIsDisplayed()
        }
    }

    private fun sampleJob() = Job(
        id = "job-scheduled",
        title = "Bat exclusion",
        customerName = "Willow Properties",
        address = "210 Willow Avenue, Cornwall",
        status = JobStatus.SCHEDULED,
        nextStep = "Pull the one-way door",
        nextStepDueAt = System.currentTimeMillis()
    )
}
