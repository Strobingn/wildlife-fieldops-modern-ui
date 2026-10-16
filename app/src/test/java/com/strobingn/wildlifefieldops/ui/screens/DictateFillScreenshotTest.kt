package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.data.remote.JobIntakeParser
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import androidx.compose.foundation.background
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Dictate job review after Sir's sample sentence (rule-based fill, AI unavailable). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1600dp-xhdpi")
class DictateFillScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sample =
        "New job for Maria Lopez at 22 Oak Street Newburgh New York 12550, phone 845 555 0199, " +
            "raccoon in the attic, schedule for tomorrow, high priority"

    @Test fun dictateFilledLight() = shot(dark = false)
    @Test fun dictateFilledDark() = shot(dark = true)

    private fun shot(dark: Boolean) {
        val draft = JobIntakeParser.heuristicFill(sample)!!
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 1500.dp).background(BackgroundDark)) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Transcript: $sample", style = MaterialTheme.typography.bodySmall)
                        Text(JobIntakeParser.NOTICE_RULES, color = PrimaryGreen, style = MaterialTheme.typography.labelSmall)
                        DictateReviewFields(draft) { _, _ -> }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Maria Lopez").assertExists()
        composeRule.onNodeWithText("845-555-0199").assertExists()
        composeRule.onNodeWithText("12550").assertExists()
        val bitmap = composeRule.runOnIdle {
            composeRule.activity.window.decorView.findCompose()!!.drawToBitmap()
        }
        val colors = mutableSetOf<Int>()
        for (y in 0 until bitmap.height step 16) for (x in 0 until bitmap.width step 16) colors += bitmap.getPixel(x, y)
        assertTrue("render is blank", colors.size > 4)
        val dir = File("/tmp/shots/pr-dictate-fill").apply { mkdirs() }
        FileOutputStream(File(dir, "dictate-filled-${if (dark) "dark" else "light"}.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

private fun View.findCompose(): View? {
    if (javaClass.name.contains("AndroidComposeView")) return this
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findCompose()?.let { return it }
    return null
}
