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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.ai.fieldops.SignatureRules
import com.strobingn.wildlifefieldops.ai.fieldops.TechSignatureRules
import com.strobingn.wildlifefieldops.ai.fieldops.TechnicianSignature
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PricingJson
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import com.strobingn.wildlifefieldops.util.BusinessProfile
import com.strobingn.wildlifefieldops.util.CityIslandSample
import com.strobingn.wildlifefieldops.util.DocumentKind
import com.strobingn.wildlifefieldops.util.SignatureInk
import com.strobingn.wildlifefieldops.util.StandardDocuments
import com.strobingn.wildlifefieldops.util.StandardJobPacket
import com.strobingn.wildlifefieldops.util.TechSignatureDefaults
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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

/** Sir 2026-10-14: "There is no copy of the actual signature." Real pad -> Save -> reload -> PDF. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1100dp-xhdpi")
class RealInkTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val upload = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private val dir = File("/tmp/shots/pr-real-ink")

    private fun draw(tag: String, wave: Float) {
        composeRule.onNodeWithTag(tag).performTouchInput {
            swipe(Offset(25f, height * 0.75f), Offset(width * 0.35f, height * wave), 200)
            swipe(Offset(width * 0.35f, height * wave), Offset(width * 0.6f, height * 0.7f), 200)
            swipe(Offset(width * 0.6f, height * 0.7f), Offset(width - 25f, height * 0.25f), 200)
        }
        composeRule.waitForIdle()
    }

    @Test fun drawnInkReachesEveryPdfAndChipSwitchDoesNotFakeIt() {
        var job by mutableStateOf(Job(id = "j", customerName = "Malky Unger", pricing = JobPricing()))
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = false) {
                Box(Modifier.width(411.dp).background(BackgroundDark).padding(12.dp)) {
                    JobSignatureCard(job = job, customerName = job.customerName, savedSignature = TechnicianSignature(), onSave = { next ->
                        job = job.copy(pricing = PricingJson.decode(upload.encodeToString(next)))
                    })
                }
            }
        }
        composeRule.waitForIdle()
        // Sign on Estimate, then switch to Contract WITHOUT signing there: Save must not stamp a fake signed record.
        draw("pad-Customer signature", 0.2f); draw("pad-Technician signature", 0.3f)
        composeRule.onNodeWithText("Save signatures").performClick(); composeRule.waitForIdle()
        composeRule.onNodeWithText("Contract").performClick(); composeRule.waitForIdle()
        composeRule.onNodeWithText("Save signatures").performClick(); composeRule.waitForIdle()
        assertEquals(null, SignatureRules.find(job.pricing, SignatureRules.CONTRACT))
        assertEquals(null, TechSignatureRules.find(job.pricing, SignatureRules.CONTRACT))
        // Now both sign on Contract.
        draw("pad-Customer signature", 0.15f); draw("pad-Technician signature", 0.35f)
        composeRule.onNodeWithText("Save signatures").performClick(); composeRule.waitForIdle()
        save("contract-pads-after-save")
        composeRule.onNodeWithText("Customer Malky Unger", substring = true).assertExists()
        composeRule.onNodeWithText("; Technician", substring = true).assertExists()

        val profile = BusinessProfile.defaults().copy(ownerName = "Austin Luker")
        listOf(
            DocumentKind.ESTIMATE to SignatureRules.ESTIMATE,
            DocumentKind.CONTRACT to SignatureRules.CONTRACT
        ).forEach { (kind, doc) ->
            val cust = SignatureRules.find(job.pricing, doc)!!
            val tech = TechSignatureRules.find(job.pricing, doc)!!
            listOf(cust.pngBase64, tech.pngBase64).forEach { png ->
                val bmp = SignatureInk.decodePng(png)!!
                assertTrue("$kind ink has size", bmp.width > 50 && bmp.height > 20)
                var dark = 0
                for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) if (android.graphics.Color.red(bmp.getPixel(x, y)) < 100) dark++
                assertTrue("$kind ink has strokes ($dark)", dark > 20)
            }
            val packet = TechSignatureDefaults.apply(
                CityIslandSample.packet().copy(
                    profile = profile, signerName = cust.signerName, signedAtMillis = cust.signedAt, customerSignatureBase64 = cust.pngBase64,
                    companySignatureBase64 = tech.pngBase64, technicianSignerName = tech.signerName, technicianSignedAtMillis = tech.signedAt
                ), TechnicianSignature()
            )
            val ops = StandardDocuments.build(kind, packet).layout().pages.flatMap { it.ops }
            assertTrue(ops.any { it.kind == "image" && it.tag == "customer" })
            assertTrue(ops.any { it.kind == "image" && it.tag == "company" })
            if (kind == DocumentKind.CONTRACT) render(packet)
        }
    }

    @Test fun noInkMeansBlankAreaNotCursiveName() {
        val packet = StandardJobPacket(job = Job(id = "j"), signerName = "Malky Unger", signedAtMillis = 1L, technicianSignerName = "Austin Luker", technicianSignedAtMillis = 1L)
        listOf(DocumentKind.ESTIMATE, DocumentKind.CONTRACT, DocumentKind.INVOICE).forEach { kind ->
            val ops = StandardDocuments.build(kind, packet).layout().pages.flatMap { it.ops }
            assertTrue(ops.none { it.kind == "text" && it.italic && (it.text == "Malky Unger" || it.text == "Austin Luker") })
            assertTrue(ops.none { it.kind == "image" && (it.tag == "customer" || it.tag == "company") })
        }
    }

    @Test fun shareWarnsAndCaptionListsOnlyInkedSigners() {
        assertEquals(listOf("Customer hasn't signed yet", "Technician hasn't signed yet"), SignatureShareGuard.missing("", ""))
        assertTrue(SignatureShareGuard.missing("A", "B").isEmpty())
        val c = com.strobingn.wildlifefieldops.pricing.CustomerSignatureRecord(document = "contract", signerName = "Malky Unger", signedAt = 1L, pngBase64 = "")
        val t = c.copy(signerName = "Austin Luker", pngBase64 = "INK")
        val cap = SignatureShareGuard.caption(c, t)
        assertTrue(cap, !cap.contains("Malky") && cap.contains("Technician Austin Luker"))
    }

    private fun render(packet: StandardJobPacket) {
        // Preview render needs tools/ and a JDK javac; without them skip the render, not the assertions.
        if (!RenderTools.available()) { println("RealInkTest: tools/ or javac missing, PDF preview render skipped"); return }
        val doc = StandardDocuments.build(DocumentKind.CONTRACT, packet)
        val out = File(dir, "pdf").apply { mkdirs() }
        val images = mapOf("customer" to packet.customerSignatureBase64, "company" to packet.companySignatureBase64)
        val dump = File(out, "layout.txt")
        dump.writeText(doc.layout().pages.mapIndexed { i, page ->
            val name = if (i == 0) "contract-real-ink" else "contract-real-ink-p${i + 1}"
            buildString {
                append("PAGE\t$name\t${doc.title}\t${page.width}\t${page.height}\n")
                page.ops.forEach { op ->
                    when (op.kind) {
                        "text" -> append("text\t${op.text.replace("\t", " ").replace("\n", " ")}\t${op.x}\t${op.y}\t${op.size}\t${if (op.bold) 1 else 0}\t${if (op.italic) 1 else 0}\t${op.align}\t${op.color}\n")
                        "line" -> append("line\t${op.x}\t${op.y}\t${op.x2}\t${op.y2}\t${op.size}\t${op.color}\n")
                        "rect", "fillrect" -> append("${op.kind}\t${op.x}\t${op.y}\t${op.x2}\t${op.y2}\t${op.color}\n")
                        "logo" -> append("logo\t${op.x}\t${op.y}\t${op.x2}\n")
                        "image" -> images[op.tag]?.takeIf { it.isNotBlank() }?.let { append("sigimg\t${op.x}\t${op.y}\t${op.x2}\t${op.y2}\t$it\n") }
                    }
                }
            }
        }.joinToString("\n"))
        val logo = listOf(File("app/src/main/assets/wildlife_whisperer_logo.png.b64"), File("../app/src/main/assets/wildlife_whisperer_logo.png.b64"), File("src/main/assets/wildlife_whisperer_logo.png.b64")).firstOrNull { it.exists() } ?: File("x")
        RenderTools.render(dump, logo, out)
    }

    private fun save(name: String) {
        val bmp = composeRule.runOnIdle { composeRule.activity.window.decorView.findRealInkCompose()!!.drawToBitmap() }
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private fun View.findRealInkCompose(): View? {
    if (javaClass.name.contains("AndroidComposeView")) return this
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findRealInkCompose()?.let { return it }
    return null
}

/** Runs the tools/ preview renderers; [available] is false when tools/ or javac is missing (CI-safe). */
internal object RenderTools {
    private fun tool(n: String) = listOf(File("tools/$n"), File("../tools/$n")).firstOrNull { it.exists() }
    private val home: String get() = System.getProperty("java.home").orEmpty()
    private fun bin(n: String) = File(home, "bin/$n")

    fun available(): Boolean =
        tool("render-standard-document-pages.java") != null && tool("render-standard-document-pdf.java") != null &&
            bin("javac").canExecute() && bin("java").canExecute()

    fun render(dump: File, logo: File, out: File) {
        val classes = File(out, "classes").apply { mkdirs() }
        run(bin("javac").path, "-d", classes.absolutePath, tool("render-standard-document-pages.java")!!.absolutePath, tool("render-standard-document-pdf.java")!!.absolutePath)
        run(bin("java").path, "-cp", classes.absolutePath, "render_standard_document_pdf", dump.absolutePath, logo.absolutePath, out.absolutePath)
        run(bin("java").path, "-cp", classes.absolutePath, "render_standard_document_pages", dump.absolutePath, logo.absolutePath, out.absolutePath)
    }

    private fun run(vararg cmd: String) {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val t = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { t }
    }
}
