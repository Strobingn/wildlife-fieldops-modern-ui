package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.SyncSnapshot
import com.strobingn.wildlifefieldops.ai.accuracy.AiAccuracyLedger
import com.strobingn.wildlifefieldops.ai.accuracy.AiFillRecord
import com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckItem
import com.strobingn.wildlifefieldops.ai.fieldops.TrapDueState
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import com.strobingn.wildlifefieldops.data.remote.WeatherSnapshot
import com.strobingn.wildlifefieldops.data.repository.SyncBacklogSnapshot
import com.strobingn.wildlifefieldops.trapreminders.TrapReminderSettings
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import com.strobingn.wildlifefieldops.ui.viewmodel.DashboardStats
import com.strobingn.wildlifefieldops.ui.viewmodel.WeatherUiState
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w480dp-h1600dp-xhdpi")
class TrapChecksScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val zone = ZoneId.systemDefault()
    private fun at(local: String) = LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()
    private val now get() = at("2026-10-06T13:40")

    private fun sampleTraps(): List<TrapCheckItem> = listOf(
        TrapCheckItem(
            TrapLog(id = "t1", jobId = "j1", trapId = "Cage 1", trapLocation = "Back deck", status = TrapStatus.SET,
                checkDate = at("2026-10-05T12:30"), nextCheckDate = at("2026-10-06T12:30")),
            TrapDueState.DUE_TODAY, "Attic raccoon"
        ),
        TrapCheckItem(
            TrapLog(id = "t2", jobId = "j1", trapId = "Cage 2", trapLocation = "Garage", status = TrapStatus.SET,
                checkDate = at("2026-10-05T15:40"), nextCheckDate = at("2026-10-06T15:40"), checkIntervalHours = 24),
            TrapDueState.DUE_TODAY, "Attic raccoon"
        )
    )

    @Test fun homeLight() = home(false, "trap-home-light")
    @Test fun homeDark() = home(true, "trap-home-dark")
    @Test fun cardLight() = card(false, "trap-card-light")
    @Test fun cardDark() = card(true, "trap-card-dark")
    @Test fun checkedDialogLight() = checkedDialog(false, "trap-checked-dialog-light")
    @Test fun checkedDialogDark() = checkedDialog(true, "trap-checked-dialog-dark")
    @Test fun syncStatusLight() = syncStatus(false, "sync-status-light")
    @Test fun syncStatusDark() = syncStatus(true, "sync-status-dark")
    @Test fun aiAccuracyLight() = aiAccuracy(false, "ai-accuracy-light")
    @Test fun aiAccuracyDark() = aiAccuracy(true, "ai-accuracy-dark")
    @Test fun reminderSettingsLight() = reminderSettings(false, "trap-reminder-settings-light")
    @Test fun reminderSettingsDark() = reminderSettings(true, "trap-reminder-settings-dark")

    private fun home(dark: Boolean, name: String) {
        val open = Job(id = "j9", title = "Bat exclusion", customerName = "Hudson Bakery",
            address = "5 River St, Newburgh", status = JobStatus.SCHEDULED)
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 1500.dp)) {
                    DashboardScreen(
                        onNavigateToJobs = {}, onNavigateToInspections = {}, onNavigateToSchedule = {},
                        onNavigateToJobDetail = {}, onNavigateToJobForm = {}, onNavigateToMap = {},
                        onNavigateToRoutes = {}, onNavigateToSettings = {}, onNavigateToAI = {},
                        onNavigateToTrapChecks = {}, onNavigateToDictate = {}, onNavigateToTodayRoute = {},
                        onOpenDrawer = {},
                        preview = DashboardPreview(
                            stats = DashboardStats(totalJobs = 3, scheduledJobs = 2, inProgressJobs = 1, completedJobs = 0,
                                totalCustomers = 4, totalInspections = 1, followUpRequired = 0, todayJobs = 0),
                            recentJobs = emptyList(),
                            todayOnSchedule = emptyList(),
                            openJobs = listOf(open),
                            reminders = emptyList(),
                            dueNextSteps = emptyList(),
                            dueTrapChecks = sampleTraps(),
                            weather = WeatherUiState.Ready(
                                snap = WeatherSnapshot(tempF = 62, condition = "Clouds", description = "broken clouds",
                                    humidity = 55, windMph = 8f, summaryLine = "62°F Clouds"),
                                placeLabel = "Cornwall, NY"
                            ),
                            sync = SyncSnapshot("Synced", TextSecondary),
                            greeting = "Good afternoon",
                            todayLabel = "Tuesday, Oct 6",
                            nowMillis = now
                        )
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Trap checks due today").assertIsDisplayed()
        composeRule.onAllNodesWithText("Due now", substring = true)[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Due 3:40 PM", substring = true)[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("New Job", substring = true)[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Dictate", substring = true)[0].assertIsDisplayed()
        assertFalse(composeRule.onAllNodesWithText("overdue", substring = true, ignoreCase = true)
            .fetchSemanticsNodes().isNotEmpty())
        saveScreen(name)
    }

    private fun card(dark: Boolean, name: String) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Column(
                    Modifier.size(411.dp, 760.dp).background(MaterialTheme.colorScheme.background).padding(16.dp)
                ) {
                    sampleTraps().forEach { item ->
                        TrapCheckCard(item = item, now = now, defaultIntervalHours = 24,
                            onOpenJob = {}, onLog = {}, onChecked = {}, onPulled = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Checked")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Pulled")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Due now", substring = true)[0].assertIsDisplayed()
        saveScreen(name)
    }

    // Robolectric did not go idle with the platform dialog window open, so the dialog body
    // is drawn in a card that matches the dialog (same form composable the app uses).
    private fun checkedDialog(dark: Boolean, name: String) {
        val trap = sampleTraps().first().trap
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(
                    Modifier.size(411.dp, 760.dp).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)).padding(24.dp)
                ) {
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                        color = com.strobingn.wildlifefieldops.ui.theme.BackgroundCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                            androidx.compose.material3.Text(
                                "Checked ${trap.trapId}",
                                style = MaterialTheme.typography.headlineSmall,
                                color = com.strobingn.wildlifefieldops.ui.theme.TextPrimary
                            )
                            TrapCheckedForm(
                                outcome = com.strobingn.wildlifefieldops.ai.fieldops.TrapCheckOutcome.CAUGHT,
                                onOutcome = {},
                                species = com.strobingn.wildlifefieldops.data.model.CatchType.RACCOON,
                                onSpecies = {},
                                count = "1",
                                onCount = {},
                                note = "Adult male, healthy",
                                onNote = {}
                            )
                            androidx.compose.foundation.layout.Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End
                            ) {
                                androidx.compose.material3.TextButton(onClick = {}) {
                                    androidx.compose.material3.Text("Cancel", color = TextSecondary)
                                }
                                androidx.compose.material3.Button(
                                    onClick = {},
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen,
                                        contentColor = com.strobingn.wildlifefieldops.ui.theme.OnPrimary
                                    )
                                ) { androidx.compose.material3.Text("Save check") }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save check").assertIsDisplayed()
        composeRule.onNodeWithText("Caught").assertIsDisplayed()
        saveScreen(name)
    }

    private fun syncStatus(dark: Boolean, name: String) {
        val state = SyncStatusUi(
            lastSuccessAt = at("2026-10-06T12:58"),
            lastErrorAt = at("2026-10-06T13:21"),
            lastError = "Photos: upload timed out (no signal)",
            lastAttemptAt = at("2026-10-06T13:21"),
            lastAttemptOk = false,
            lastMessage = "Sync finished with 1 issue",
            backlog = SyncBacklogSnapshot(
                pendingJobs = 2, pendingCustomers = 1, pendingInspections = 0, pendingObservations = 0,
                pendingEvents = 0, pendingPhotos = 3, failedJobs = 0, failedPhotos = 1,
                recentFailures = listOf("IMG_2031.jpg: upload timed out")
            ),
            connection = "Connected"
        )
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 1300.dp)) {
                    SyncStatusBody(state = state, onBack = {}, onSyncNow = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("sync-status").assertIsDisplayed()
        composeRule.onNodeWithText("Sync now").assertIsDisplayed()
        saveScreen(name)
    }

    private fun aiAccuracy(dark: Boolean, name: String) {
        var log = emptyList<AiFillRecord>()
        log = AiAccuracyLedger.recordFill(log, "Dictation", "s1", "Address", "12 Main Street Cornwall", 1)
        log = AiAccuracyLedger.recordFill(log, "Dictation", "s1", "Title", "Raccoon attic", 1)
        log = AiAccuracyLedger.recordSaved(log, "s1", mapOf("Address" to "12 Main St, Cornwall-on-Hudson", "Title" to "Raccoon attic"), 2)
        log = AiAccuracyLedger.recordFill(log, "Text import", "s2", "Address", "40 Elm", 3)
        log = AiAccuracyLedger.recordSaved(log, "s2", mapOf("Address" to "40 Elm Ave, Newburgh"), 4)
        log = AiAccuracyLedger.recordFill(log, "Suggest", "s3", "Weather advice", "Rain tonight: check traps early.", 5)
        log = AiAccuracyLedger.recordSaved(log, "s3", mapOf("Weather advice" to ""), 6)
        val stats = AiAccuracyLedger.summary(log)
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 1100.dp)) {
                    AiAccuracyBody(stats = stats, pending = 0, onBack = {}, onClear = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Clear log").assertIsDisplayed()
        composeRule.onNodeWithText("Changed 2 of 2").assertIsDisplayed()
        saveScreen(name)
    }

    private fun reminderSettings(dark: Boolean, name: String) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(
                    Modifier.size(411.dp, 760.dp).background(MaterialTheme.colorScheme.background).padding(16.dp)
                ) {
                    TrapReminderSettingsBody(
                        settings = TrapReminderSettings(),
                        onNotifications = {}, onDefaultInterval = {}, onLeadMinutes = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Trap check reminders").assertIsDisplayed()
        composeRule.onNodeWithText("Check every (hours)").assertIsDisplayed()
        composeRule.onNodeWithText("Remind me before (hours)").assertIsDisplayed()
        saveScreen(name)
    }

    private fun saveScreen(name: String) {
        val bitmap = composeRule.runOnIdle {
            val compose = composeRule.activity.window.decorView.findTrapComposeView()
                ?: error("AndroidComposeView not in the hierarchy")
            compose.drawToBitmap()
        }
        write(bitmap, name)
    }

    private fun write(bitmap: Bitmap, name: String) {
        val dir = File("/opt/cursor/artifacts")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}

private fun View.findTrapComposeView(): View? {
    if (javaClass.simpleName == "AndroidComposeView") return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findTrapComposeView()?.let { return it }
        }
    }
    return null
}
