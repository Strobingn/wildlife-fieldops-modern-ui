package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.SyncSnapshot
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.remote.WeatherSnapshot
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import com.strobingn.wildlifefieldops.ui.viewmodel.DashboardStats
import com.strobingn.wildlifefieldops.ui.viewmodel.WeatherUiState
import com.strobingn.wildlifefieldops.weather.WeatherAlertEngine
import com.strobingn.wildlifefieldops.weather.WeatherAlertSettings
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w480dp-h1600dp-xhdpi")
class WeatherAlertsScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeLight() = home(dark = false, name = "weather-home-light")

    @Test
    fun homeDark() = home(dark = true, name = "weather-home-dark")

    @Test
    fun jobRowLight() = jobRow(dark = false, name = "weather-job-row-light")

    @Test
    fun jobRowDark() = jobRow(dark = true, name = "weather-job-row-dark")

    @Test
    fun settingsLight() = settings(dark = false, name = "weather-settings-light")

    @Test
    fun settingsDark() = settings(dark = true, name = "weather-settings-dark")

    private fun home(dark: Boolean, name: String) {
        val alerts = sampleAlerts()
        val fetched = LocalDateTime.parse("2026-10-07T15:12")
            .atZone(WeatherAlertEngine.ZONE)
            .toInstant()
            .toEpochMilli()
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 980.dp)) {
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
                        preview = DashboardPreview(
                            stats = DashboardStats(
                                totalJobs = 3,
                                scheduledJobs = 1,
                                inProgressJobs = 1,
                                completedJobs = 1,
                                totalCustomers = 4,
                                totalInspections = 1,
                                followUpRequired = 0,
                                todayJobs = 0
                            ),
                            recentJobs = emptyList(),
                            todayOnSchedule = emptyList(),
                            reminders = emptyList(),
                            dueNextSteps = emptyList(),
                            dueTrapChecks = emptyList(),
                            weather = WeatherUiState.Ready(
                                snap = WeatherSnapshot(
                                    tempF = 62,
                                    condition = "Clouds",
                                    description = "broken clouds",
                                    humidity = 55,
                                    windMph = 8f,
                                    summaryLine = "62°F Clouds"
                                ),
                                placeLabel = "Cornwall, NY"
                            ),
                            sync = SyncSnapshot("Synced", TextSecondary),
                            greeting = "Good morning",
                            todayLabel = "Wednesday, Oct 7",
                            weatherAlerts = alerts,
                            weatherAlertsFetchedAt = fetched,
                            weatherAlertsExpanded = true
                        )
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Gusts 38 mph, steady 18 mph, Wed 2–6 PM").assertIsDisplayed()
        composeRule.onNodeWithText("Official NWS alert: Wind Advisory in effect, Wed 2–6 PM").assertIsDisplayed()
        composeRule.onNodeWithText("Heavy rain 0.45 in Wed 3–4 PM").assertIsDisplayed()
        composeRule.onNodeWithText("Snow 0.4 in Thu 6–9 AM").assertIsDisplayed()
        save(name)
    }

    private fun jobRow(dark: Boolean, name: String) {
        val job = Job(
            id = "job-wind",
            title = "Attic raccoon",
            customerName = "Willow Properties",
            address = "210 Willow Avenue, Cornwall",
            status = JobStatus.SCHEDULED,
            scheduledDate = System.currentTimeMillis()
        )
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 720.dp)) {
                    JobListScreen(
                        onNavigateToJobDetail = {},
                        onNavigateToJobForm = {},
                        onNavigateToDictate = {},
                        onBack = {},
                        showBack = false,
                        preview = JobListPreview(
                            jobs = listOf(job),
                            recentJobs = listOf(job),
                            scheduledCount = 1,
                            inProgressCount = 0,
                            completedCount = 0,
                            weatherChips = mapOf(job.id to "Gusts 38 mph, steady 18 mph")
                        )
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Gusts 38 mph, steady 18 mph")[0].assertIsDisplayed()
        composeRule.onAllNodesWithText("Attic raccoon")[0].assertIsDisplayed()
        save(name)
    }

    private fun settings(dark: Boolean, name: String) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(
                    Modifier
                        .size(411.dp, 1180.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(16.dp)
                ) {
                    WeatherAlertsSettingsBody(
                        settings = WeatherAlertSettings(),
                        statusLine = "Last checked Wed 3:12 PM.",
                        checking = false,
                        onRain = {},
                        onHeavyRain = {},
                        onHighWind = {},
                        onSnow = {},
                        onNotifications = {},
                        onWind = {},
                        onHeavyHour = {},
                        onHeavyDay = {},
                        onCheckNow = {},
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Weather alerts").assertIsDisplayed()
        composeRule.onNodeWithText("Check now").assertIsDisplayed()
        composeRule.onNodeWithText("Wind limit (mph)").assertIsDisplayed()
        composeRule.onNodeWithText("Last checked Wed 3:12 PM.").assertIsDisplayed()
        save(name)
    }

    private fun sampleAlerts(): List<com.strobingn.wildlifefieldops.weather.WeatherAlert> {
        val now = LocalDateTime.parse("2026-10-07T12:00")
            .atZone(WeatherAlertEngine.ZONE)
            .toInstant()
            .toEpochMilli()
        return WeatherAlertEngine.evaluate(
            hourly = listOf(
                com.strobingn.wildlifefieldops.weather.HourlyWeather(
                    startMillis = hour("2026-10-07T14:00"),
                    windGustsMph = 32.0,
                    windSpeedMph = 12.0
                ),
                com.strobingn.wildlifefieldops.weather.HourlyWeather(
                    startMillis = hour("2026-10-07T15:00"),
                    windGustsMph = 38.0,
                    windSpeedMph = 18.0,
                    rainInches = 0.45,
                    precipitationInches = 0.45,
                    precipitationProbability = 80
                ),
                com.strobingn.wildlifefieldops.weather.HourlyWeather(
                    startMillis = hour("2026-10-07T16:00"),
                    windGustsMph = 34.0,
                    windSpeedMph = 12.0
                ),
                com.strobingn.wildlifefieldops.weather.HourlyWeather(
                    startMillis = hour("2026-10-07T17:00"),
                    windGustsMph = 31.0,
                    windSpeedMph = 12.0
                ),
                com.strobingn.wildlifefieldops.weather.HourlyWeather(
                    startMillis = hour("2026-10-08T06:00"),
                    snowfallInches = 0.4
                ),
                com.strobingn.wildlifefieldops.weather.HourlyWeather(
                    startMillis = hour("2026-10-08T07:00"),
                    snowfallInches = 0.2
                ),
                com.strobingn.wildlifefieldops.weather.HourlyWeather(
                    startMillis = hour("2026-10-08T08:00"),
                    snowfallInches = 0.1
                )
            ),
            nws = listOf(
                com.strobingn.wildlifefieldops.weather.NwsActiveAlert(
                    id = "wind-advisory",
                    event = "Wind Advisory",
                    headline = "Wind Advisory in effect",
                    onsetMillis = hour("2026-10-07T14:00"),
                    endsMillis = hour("2026-10-07T18:00")
                )
            ),
            nowMillis = now
        )
    }

    private fun hour(local: String): Long =
        LocalDateTime.parse(local).atZone(WeatherAlertEngine.ZONE).toInstant().toEpochMilli()

    private fun save(name: String) {
        val bitmap = composeRule.runOnIdle {
            val compose = composeRule.activity.window.decorView.findComposeView()
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

private fun View.findComposeView(): View? {
    if (javaClass.simpleName == "AndroidComposeView") return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findComposeView()?.let { return it }
        }
    }
    return null
}
