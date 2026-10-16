package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.ModernBottomBar
import com.strobingn.wildlifefieldops.ai.fieldops.SalesTaxYear
import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.navigation.Screen
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import org.junit.Assert.assertEquals
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

/** Six-tab bottom bar (Tax added, nothing removed) and the Tax tab opening on the yearly card. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h900dp-xhdpi")
class TaxTabScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun at(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m, d, 12, 0) }.timeInMillis
    private fun inv(id: String, job: String, date: Long, sub: Double, tax: Double) = Invoice(
        id = id, jobId = job, issueDate = date, createdAt = date, status = InvoiceStatus.PAID,
        subtotal = sub, taxAmount = tax, totalAmount = sub + tax, amountPaid = sub + tax
    )
    private val summary = SalesTaxYear.summarize(
        listOf(
            inv("1", "b", at(2026, Calendar.APRIL, 10), 400.0, 35.50),
            inv("2", "o", at(2026, Calendar.JULY, 2), 1000.0, 81.25),
            inv("3", "x", at(2026, Calendar.OCTOBER, 5), 250.0, 0.0)
        ),
        listOf(Job(id = "b", county = "Bronx", state = "NY"), Job(id = "o", county = "Orange", state = "NY"), Job(id = "x", county = "Orange", state = "NY")),
        emptyList(), emptyList(), SalesTaxYear.Kind.NYS_TAX_YEAR, 2026
    )

    @Test fun bottomBarLight() = bar(false, 411)
    @Test fun bottomBarDark() = bar(true, 411)
    @Test fun bottomBarNarrowLight() = bar(false, 360)
    @Test fun taxTabLight() = taxTab(false)
    @Test fun taxTabDark() = taxTab(true)

    private fun assertSixLabelsOnOneLine() {
        listOf("Home", "Jobs", "Inspections", "Customers", "Tax", "More").forEach { t ->
            composeRule.onNodeWithContentDescription("nav-$t-overflow-false-lines-1", useUnmergedTree = true).assertExists()
        }
        assertEquals(0, composeRule.onAllNodesWithContentDescription("overflow-true", substring = true, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    private fun bar(dark: Boolean, widthDp: Int) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(widthDp.dp, 96.dp).background(BackgroundDark)) {
                    ModernBottomBar(currentRoute = Screen.EarningsTax.route, onNavigate = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        assertSixLabelsOnOneLine()
        save("bottom-bar-${widthDp}dp-${if (dark) "dark" else "light"}")
    }

    private fun taxTab(dark: Boolean) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 900.dp)) {
                    Scaffold(
                        containerColor = BackgroundDark,
                        bottomBar = { ModernBottomBar(currentRoute = Screen.EarningsTax.route, onNavigate = {}) }
                    ) { padding ->
                        Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
                            Text("Earnings & sales tax", color = TextPrimary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
                            SalesTaxYearCard(summary, {}, {}, { _, _ -> }, {}, {})
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        assertSixLabelsOnOneLine()
        save("tax-tab-${if (dark) "dark" else "light"}")
    }

    private fun save(name: String) {
        val bmp = composeRule.runOnIdle { composeRule.activity.window.decorView.findCompose()!!.drawToBitmap() }
        val colors = mutableSetOf<Int>()
        for (y in 0 until bmp.height step 8) for (x in 0 until bmp.width step 8) colors += bmp.getPixel(x, y)
        assertTrue(colors.size > 4)
        val dir = File("/tmp/shots/pr-tax-tab").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private fun View.findCompose(): View? {
    if (javaClass.name.contains("AndroidComposeView")) return this
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findCompose()?.let { return it }
    return null
}
