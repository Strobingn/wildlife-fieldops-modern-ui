package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.ai.fieldops.PaymentLedger
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import com.strobingn.wildlifefieldops.util.CityIslandSample
import com.strobingn.wildlifefieldops.util.DocumentKind
import com.strobingn.wildlifefieldops.util.StandardDocuments
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Sir 2026-10-13: "I enter the deposit and it says zero." */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1600dp-xhdpi")
class PaymentFixTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val dir = File("/tmp/shots/pr-payment-fix")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun parsesWhatSirTypes() {
        assertEquals(500.0, PaymentLedger.parseAmount("$500")!!, 0.0)
        assertEquals(1250.5, PaymentLedger.parseAmount("1,250.50")!!, 0.0)
        assertEquals(1000.0, PaymentLedger.parseAmount(" $ 1,000 ")!!, 0.0)
        assertEquals(300.0, PaymentLedger.parseAmount("300")!!, 0.0)
        assertEquals(0.75, PaymentLedger.parseAmount(".75")!!, 0.0)
        assertNull(PaymentLedger.parseAmount(""))
        assertNull(PaymentLedger.parseAmount("abc"))
        assertNull(PaymentLedger.parseAmount("0"))
    }

    @Test fun depositEnteredSavedReloadedSyncedReducesBalance() {
        val start = Job(id = "j1", customerName = "Pat Morales", estimatedValue = 1500.0, pricing = JobPricing())
        var job by mutableStateOf(start)
        var saves = 0
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = false) {
                Box(Modifier.width(411.dp).background(BackgroundDark).padding(12.dp)) {
                    JobPaymentsCard(job = job, onSave = { next ->
                        saves++
                        // save -> pricing jsonb (what syncs as jobs.pricing) -> reload
                        val synced = json.decodeFromString<JobPricing>(json.encodeToString(next))
                        job = job.copy(pricing = synced)
                    })
                }
            }
        }
        composeRule.waitForIdle()
        val totalBefore = PaymentLedger.invoiceTotal(job)
        composeRule.onNode(hasSetTextAction() and hasText("Amount")).performTextInput("$1,250.50")
        composeRule.onNodeWithText("Record payment", substring = true).performClick()
        composeRule.waitForIdle()
        assertEquals(1, saves)
        assertEquals(1250.5, PaymentLedger.totalPaid(job.pricing.payments), 0.0)
        assertEquals(1250.5, job.pricing.paidAmount, 0.0)
        assertTrue(PaymentLedger.balanceDue(totalBefore, job.pricing.payments) < totalBefore || totalBefore == 0.0)
        composeRule.onNodeWithText("Paid " + com.strobingn.wildlifefieldops.pricing.Money.formatUsd(1250.5), substring = true).assertExists()
        save("deposit-after-save-reload")

        // a bad entry is not saved as $0
        composeRule.onNode(hasSetTextAction() and hasText("Amount")).performTextInput("abc")
        composeRule.onNodeWithText("Record payment", substring = true).performClick()
        composeRule.waitForIdle()
        assertEquals(1, saves)
    }

    @Test fun invoicePdfShowsDepositAndBalance() {
        val base = CityIslandSample.packet()
        val deposit = com.strobingn.wildlifefieldops.pricing.JobPaymentRecord(id = "d1", method = "check", checkNumber = "1042", amount = 500.0, paidAt = 1_791_900_000_000L, note = "Deposit")
        val packet = base.copy(payments = listOf(deposit), amountPaid = 500.0, balanceDue = (base.total - 500.0).coerceAtLeast(0.0))
        val doc = StandardDocuments.build(DocumentKind.INVOICE, packet)
        val text = doc.layout().pages.flatMap { it.ops }.filter { it.kind == "text" }.joinToString("\n") { it.text }
        assertTrue(text, text.contains("500.00"))
        val out = File(dir, "pdf").apply { mkdirs() }
        val dump = File(out, "layout.txt")
        dump.writeText(doc.layout().pages.mapIndexed { i, page ->
            val name = if (i == 0) "invoice-deposit" else "invoice-deposit-p${i + 1}"
            buildString {
                append("PAGE\t$name\t${doc.title}\t${page.width}\t${page.height}\n")
                page.ops.forEach { op ->
                    when (op.kind) {
                        "text" -> append("text\t${op.text.replace("\t", " ").replace("\n", " ")}\t${op.x}\t${op.y}\t${op.size}\t${if (op.bold) 1 else 0}\t${if (op.italic) 1 else 0}\t${op.align}\t${op.color}\n")
                        "line" -> append("line\t${op.x}\t${op.y}\t${op.x2}\t${op.y2}\t${op.size}\t${op.color}\n")
                        "rect", "fillrect" -> append("${op.kind}\t${op.x}\t${op.y}\t${op.x2}\t${op.y2}\t${op.color}\n")
                        "logo" -> append("logo\t${op.x}\t${op.y}\t${op.x2}\n")
                    }
                }
            }
        }.joinToString("\n"))
        val logo = locate("app/src/main/assets/wildlife_whisperer_logo.png.b64")
        val classes = File(out, "classes").apply { mkdirs() }
        val home = System.getProperty("java.home")
        run("$home/bin/javac", "-d", classes.absolutePath, locate("tools/render-standard-document-pages.java").absolutePath, locate("tools/render-standard-document-pdf.java").absolutePath)
        run("$home/bin/java", "-cp", classes.absolutePath, "render_standard_document_pdf", dump.absolutePath, logo.absolutePath, out.absolutePath)
        run("$home/bin/java", "-cp", classes.absolutePath, "render_standard_document_pages", dump.absolutePath, logo.absolutePath, out.absolutePath)
        assertTrue(File(out, "invoice-deposit.png").length() > 1000)
    }

    private fun locate(relative: String): File = listOf(File(relative), File("../$relative")).firstOrNull { it.exists() } ?: File(relative)
    private fun run(vararg cmd: String) {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val t = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { t }
    }
    private fun save(name: String) {
        val bmp = composeRule.runOnIdle { composeRule.activity.window.decorView.findPayCompose()!!.drawToBitmap() }
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private fun View.findPayCompose(): View? {
    if (javaClass.name.contains("AndroidComposeView")) return this
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findPayCompose()?.let { return it }
    return null
}
