package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.ModernBottomBar
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.remote.TextMessageImport
import com.strobingn.wildlifefieldops.navigation.Screen
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
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
 * Light and dark renders of the share-in review form and the bottom bar.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h820dp-xhdpi")
class TextImportScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sample =
        "Hi this is Pat Lee, 12 Oak St Cornwall NY 12518, 845-555-0142, raccoons in my attic"

    @Test
    fun shareReviewLight() = review(dark = false, name = "share-in-review-light")

    @Test
    fun shareReviewDark() = review(dark = true, name = "share-in-review-dark")

    @Test
    fun bottomBarLight() = bar(dark = false, name = "bottom-bar-light")

    @Test
    fun bottomBarDark() = bar(dark = true, name = "bottom-bar-dark")

    private fun review(dark: Boolean, name: String) {
        val fields = TextMessageImport.parse(sample)
        var saved = false
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 780.dp)) {
                    ShareInReviewForm(
                        fields = fields,
                        matches = listOf(
                            Customer(
                                id = "cust-pat",
                                firstName = "Pat",
                                lastName = "Lee",
                                phone = "845-555-0142",
                                address = "12 Oak St",
                                city = "Cornwall",
                                state = "NY",
                                zipCode = "12518"
                            )
                        ),
                        onBack = {},
                        onSave = { saved = true }
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Review this text").assertIsDisplayed()
        composeRule.onNodeWithText("Use existing customer").assertIsDisplayed()
        composeRule.onNodeWithText("Create new").assertIsDisplayed()
        composeRule.onNodeWithText("Pat Lee").assertIsDisplayed()
        composeRule.onNodeWithText("845-555-0142").assertIsDisplayed()
        composeRule.onNodeWithText("Create Job").performScrollTo().assertIsDisplayed()
        assertFalse(saved)
        saveShot(name)
        assertFalse(saved)
    }

    private fun bar(dark: Boolean, name: String) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.fillMaxWidth()) {
                    ModernBottomBar(
                        currentRoute = Screen.InspectionList.route,
                        onNavigate = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        listOf("Home", "Jobs", "Inspections", "Customers", "More").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
            composeRule.onNode(hasContentDescription("nav-$label-overflow-false-lines-1", substring = true))
                .assertIsDisplayed()
        }
        saveShot(name)
    }

    private fun saveShot(name: String) {
        val bitmap = composeRule.runOnIdle {
            val compose = composeRule.activity.window.decorView.findComposeView()
                ?: error("AndroidComposeView not in the hierarchy")
            compose.drawToBitmap()
        }
        val colors = mutableSetOf<Int>()
        val step = 16
        for (y in 0 until bitmap.height step step) {
            for (x in 0 until bitmap.width step step) {
                colors += bitmap.getPixel(x, y)
            }
        }
        assertTrue("render is blank", colors.size > 4)
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
