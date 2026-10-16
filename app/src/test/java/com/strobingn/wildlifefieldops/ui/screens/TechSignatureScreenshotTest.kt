package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.ai.fieldops.SignatureRules
import com.strobingn.wildlifefieldops.ai.fieldops.TechnicianSignature
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import com.strobingn.wildlifefieldops.util.CityIslandSample
import com.strobingn.wildlifefieldops.util.DocumentKind
import com.strobingn.wildlifefieldops.util.SignatureInk
import com.strobingn.wildlifefieldops.util.StandardDocuments
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Technician signature beside the customer's: capture card, Settings, and the PDF. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1100dp-xhdpi")
class TechSignatureScreenshotTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val dir = File(System.getenv("FIELDOPS_SHOT_DIR")?.takeIf { it.isNotBlank() } ?: "/tmp/shots/pr-tech-signature")

    private fun ink(seed: Int): String {
        val bmp = Bitmap.createBitmap(600, 160, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(android.graphics.Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(26, 26, 26); style = Paint.Style.STROKE; strokeWidth = 6f; strokeCap = Paint.Cap.ROUND }
        val path = Path().apply {
            moveTo(30f, 110f)
            var x = 30f
            for (i in 0 until 9) {
                cubicTo(x + 20f, 20f + (i * seed % 30), x + 40f, 150f, x + 60f, 90f - (i % 3) * 15f); x += 60f
            }
        }
        c.drawPath(path, p)
        c.drawLine(40f, 135f, 540f, 128f, p)
        return SignatureInk.encodePng(bmp)
    }

    private val sirInk by lazy { ink(7) }
    private val custInk by lazy { ink(13) }

    @Test fun captureCard() {
        val saved = TechnicianSignature(sirInk, "Dirk Diggler", 1_790_000_000_000L)
        val pricing = SignatureRules.save(JobPricing(), SignatureRules.ESTIMATE, "Pat Morales", 1_790_000_000_000L, custInk)
        val job = Job(id = "j1", customerName = "Pat Morales", pricing = pricing)
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = false) {
                Box(Modifier.width(411.dp).background(BackgroundDark).padding(12.dp)) {
                    JobSignatureCard(job = job, customerName = job.customerName, onSave = {}, savedSignature = saved)
                }
            }
        }
        settle()
        composeRule.onNodeWithText("Customer signature").assertExists()
        composeRule.onNodeWithText("Technician signature").assertExists()
        composeRule.onNodeWithText("From your saved signature").assertExists()
        save("signature-capture")
    }

    @Test fun settingsSavedSignature() {
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = false) {
                Column(Modifier.width(411.dp).background(BackgroundDark).padding(12.dp)) {
                    TechSignatureSettingsSection(initial = TechnicianSignature(sirInk, "Dirk Diggler", 1_790_000_000_000L))
                }
            }
        }
        settle()
        composeRule.onNodeWithText("Your signature").assertExists()
        composeRule.onNodeWithText("Save my signature").assertExists()
        assertTrue(composeRule.onAllNodesWithText("Saved").fetchSemanticsNodes().isNotEmpty())
        save("settings-saved-signature")
    }

    /** Layout ops + both signature images, rendered to PDF and PNG by tools/render-standard-document-*.java. */
    @Test fun pdfBothSignatures() {
        val packet = CityIslandSample.packet().copy(
            signerName = "Pat Morales", signedAtMillis = 1_790_000_000_000L, customerSignatureBase64 = custInk,
            companySignatureBase64 = sirInk, technicianSignerName = "Dirk Diggler", technicianSignedAtMillis = 1_790_000_000_000L
        )
        val doc = StandardDocuments.build(DocumentKind.INVOICE, packet)
        val images = mapOf("customer" to custInk, "company" to sirInk)
        val out = File(dir, "pdf").apply { mkdirs() }
        val dump = File(out, "layout.txt")
        dump.writeText(doc.layout().pages.mapIndexed { i, page ->
            val name = if (i == 0) "tech-signature-invoice" else "tech-signature-invoice-p${i + 1}"
            buildString {
                append("PAGE\t$name\t${doc.title}\t${page.width}\t${page.height}\n")
                page.ops.forEach { op ->
                    when (op.kind) {
                        "text" -> append("text\t${op.text.replace("\t", " ").replace("\n", " ")}\t${op.x}\t${op.y}\t${op.size}\t${if (op.bold) 1 else 0}\t${if (op.italic) 1 else 0}\t${op.align}\t${op.color}\n")
                        "line" -> append("line\t${op.x}\t${op.y}\t${op.x2}\t${op.y2}\t${op.size}\t${op.color}\n")
                        "rect", "fillrect" -> append("${op.kind}\t${op.x}\t${op.y}\t${op.x2}\t${op.y2}\t${op.color}\n")
                        "logo" -> append("logo\t${op.x}\t${op.y}\t${op.x2}\n")
                        "image" -> images[op.tag]?.let { append("sigimg\t${op.x}\t${op.y}\t${op.x2}\t${op.y2}\t$it\n") }
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
        out.listFiles().orEmpty().filter { it.name.endsWith(".pdf") }.forEach { assertTrue(String(it.readBytes(), Charsets.ISO_8859_1).contains("/Sg")) }
        assertTrue(File(out, "tech-signature-invoice.png").length() > 1000)
    }

    private fun locate(relative: String): File =
        listOf(File(relative), File("../$relative")).firstOrNull { it.exists() } ?: File(relative)

    private fun run(vararg cmd: String) {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val text = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { text }
    }

    private fun settle() {
        composeRule.waitForIdle(); composeRule.mainClock.advanceTimeBy(500); composeRule.waitForIdle()
    }

    private fun save(name: String) {
        val bmp = composeRule.runOnIdle { composeRule.activity.window.decorView.findComposeView()!!.drawToBitmap() }
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private fun View.findComposeView(): View? {
    if (javaClass.name.contains("AndroidComposeView")) return this
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findComposeView()?.let { return it }
    return null
}
