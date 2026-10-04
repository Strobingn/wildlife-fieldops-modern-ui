package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.ModernBottomBar
import com.strobingn.wildlifefieldops.MoreShellActions
import com.strobingn.wildlifefieldops.MoreShellHeader
import com.strobingn.wildlifefieldops.SyncStatusLine
import com.strobingn.wildlifefieldops.navigation.Screen
import com.strobingn.wildlifefieldops.showsFloatingSync
import com.strobingn.wildlifefieldops.syncSnapshot
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
 * Renders the actual More-tab composables (brand header, New Job, Dictate job,
 * Search tools, Today cards, sync, bottom nav) through the Compose view.
 * Pixel copy is unavailable in this JVM, so the bitmap comes from [drawToBitmap].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w480dp-h960dp-xhdpi")
class MoreTabScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = render(dark = false, name = "light")

    @Test
    fun dark() = render(dark = true, name = "dark")

    private fun render(dark: Boolean, name: String) {
        composeRule.setContent {
            Box(Modifier.size(411.dp, 780.dp)) {
                MoreTabRender(dark = dark)
            }
        }
        composeRule.onNodeWithText("Wildlife Whisperer").assertExists()
        composeRule.onNodeWithText("New Job").assertExists()
        composeRule.onNodeWithText("Dictate job").assertExists()
        composeRule.onNodeWithText("Search tools").assertExists()
        composeRule.onNodeWithText("Today").assertExists()
        composeRule.onNodeWithText("Schedule").assertExists()
        composeRule.onNodeWithText("Today's route").assertExists()
        composeRule.onNodeWithText("Synced").assertExists()
        composeRule.waitForIdle()
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
        val prefix = System.getenv("HOME_SHOT_PREFIX") ?: "home"
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

private fun View.findComposeView(): View? {
    if (javaClass.simpleName == "AndroidComposeView") return this
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).findComposeView()?.let { return it }
        }
    }
    return null
}

@Composable
internal fun MoreTabRender(dark: Boolean) {
    WildlifeFieldOpsTheme(darkTheme = dark) {
        val sync = syncSnapshot(isSyncing = false, failed = false, pending = 0, failureDetail = null)
        Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                MoreScreen(
                    onOpen = {},
                    header = { MoreShellHeader(sync) },
                    primaryActions = { MoreShellActions(onNewJob = {}, onDictate = {}) },
                    modifier = Modifier.weight(1f)
                )
                if (showsFloatingSync(Screen.More.route)) {
                    SyncStatusLine(text = sync.label, color = sync.color)
                }
                ModernBottomBar(currentRoute = Screen.More.route, onNavigate = {})
            }
        }
    }
}
