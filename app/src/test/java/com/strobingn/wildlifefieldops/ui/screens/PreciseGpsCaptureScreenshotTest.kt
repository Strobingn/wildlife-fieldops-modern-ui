package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.location.AveragedFix
import com.strobingn.wildlifefieldops.location.CaptureReadout
import com.strobingn.wildlifefieldops.location.PreciseGpsMath
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h700dp-xhdpi")
class PreciseGpsCaptureScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test fun collectingLight() = sheet(false, collecting(), null, "precise-gps-collecting-light")

    @Test fun collectingDark() = sheet(true, collecting(), null, "precise-gps-collecting-dark")

    @Test fun lockedLight() = sheet(false, locked(), null, "precise-gps-locked-light")

    @Test fun lockedDark() = sheet(true, locked(), null, "precise-gps-locked-dark")

    @Test fun gpsOffLight() = sheet(false, idle(), PreciseGpsMath.GPS_OFF, "precise-gps-off-light")

    private fun collecting() = CaptureReadout(
        elapsedMs = 18_000,
        averaged = null,
        latestAccuracyMeters = 22.0,
        satellites = 6,
        acceptedCount = 0,
        rejectedCount = 3,
        autoFinish = false,
        progress = PreciseGpsMath.progress(18_000)
    )

    private fun locked() = CaptureReadout(
        elapsedMs = 22_000,
        averaged = AveragedFix(41.4401234, -74.0123456, 2.4, 8),
        latestAccuracyMeters = 3.1,
        satellites = 11,
        acceptedCount = 8,
        rejectedCount = 2,
        autoFinish = false,
        progress = PreciseGpsMath.progress(22_000)
    )

    private fun idle() = CaptureReadout(
        elapsedMs = 0,
        averaged = null,
        latestAccuracyMeters = null,
        satellites = null,
        acceptedCount = 0,
        rejectedCount = 0,
        autoFinish = false,
        progress = 0f
    )

    private fun sheet(dark: Boolean, readout: CaptureReadout, message: String?, name: String) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(
                    Modifier
                        .size(411.dp, 640.dp)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                        .padding(24.dp)
                ) {
                    Column {
                        PreciseGpsCaptureBody(
                            readout = readout,
                            message = message,
                            onUseNow = {},
                            onCancel = {}
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save trap spot").assertIsDisplayed()
        composeRule.onNodeWithText("Use now").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        if (message != null) composeRule.onNodeWithText(message).assertIsDisplayed()
        save(name)
    }

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
