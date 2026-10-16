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
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.ai.fieldops.SalesTaxYear
import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
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
import java.util.Calendar

/** Top of the Tax tab: yearly NY sales tax total, quarters, jurisdictions. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1600dp-xhdpi")
class SalesTaxScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun at(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m, d, 12, 0) }.timeInMillis

    private fun inv(id: String, job: String, date: Long, sub: Double, tax: Double) = Invoice(
        id = id, jobId = job, issueDate = date, createdAt = date, status = InvoiceStatus.PAID,
        subtotal = sub, taxAmount = tax, totalAmount = sub + tax, amountPaid = sub + tax
    )

    private val summary = SalesTaxYear.summarize(
        invoices = listOf(
            inv("1", "b", at(2026, Calendar.APRIL, 10), 400.0, 35.50),
            inv("2", "o", at(2026, Calendar.JULY, 2), 1000.0, 81.25),
            inv("3", "x", at(2026, Calendar.OCTOBER, 5), 250.0, 0.0),
            inv("4", "o", at(2027, Calendar.JANUARY, 20), 200.0, 16.25)
        ),
        jobs = listOf(
            Job(id = "b", county = "Bronx", state = "NY"),
            Job(id = "o", county = "Orange", state = "NY"),
            Job(id = "x", county = "Orange", state = "NY")
        ),
        adjustments = emptyList(),
        overrides = emptyList(),
        kind = SalesTaxYear.Kind.NYS_TAX_YEAR,
        year = 2026
    )

    @Test fun taxTabLight() = shot(false)
    @Test fun taxTabDark() = shot(true)

    private fun shot(dark: Boolean) {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = dark) {
                Box(Modifier.size(411.dp, 1500.dp).background(BackgroundDark)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Earnings & sales tax", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                        SalesTaxYearCard(summary, {}, {}, { _, _ -> }, {}, {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("NY sales tax for the year").assertExists()
        composeRule.onNodeWithText("Bronx County  ·  8.875%").assertExists()
        val bmp = composeRule.runOnIdle { composeRule.activity.window.decorView.findCompose()!!.drawToBitmap() }
        val colors = mutableSetOf<Int>()
        for (y in 0 until bmp.height step 16) for (x in 0 until bmp.width step 16) colors += bmp.getPixel(x, y)
        assertTrue(colors.size > 4)
        val dir = File("/tmp/shots/pr-sales-tax").apply { mkdirs() }
        FileOutputStream(File(dir, "tax-tab-${if (dark) "dark" else "light"}.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private fun View.findCompose(): View? {
    if (javaClass.name.contains("AndroidComposeView")) return this
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findCompose()?.let { return it }
    return null
}
