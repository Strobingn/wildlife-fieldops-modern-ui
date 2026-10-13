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
import androidx.compose.ui.test.onAllNodesWithText
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

/** Sir 2026-10-13 5:05 PM: "I've signed it 10 times it's not sticking." */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1100dp-xhdpi")
class TechSignatureSticksTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dir = File("/tmp/shots/pr-tech-sig-sticks")
    private val profile = BusinessProfile.defaults().copy(ownerName = "Dirk Diggler")
    private val noSaved = TechnicianSignature()
    private val customerInk by lazy {
        val b = Bitmap.createBitmap(300, 80, Bitmap.Config.ARGB_8888); b.eraseColor(android.graphics.Color.WHITE)
        val c = android.graphics.Canvas(b); val p = android.graphics.Paint().apply { strokeWidth = 4f; color = android.graphics.Color.BLACK }
        c.drawLine(10f, 60f, 290f, 20f, p); c.drawLine(10f, 20f, 200f, 70f, p)
        SignatureInk.encodePng(b)
    }

    private fun sign(tag: String) {
        composeRule.onNodeWithTag(tag).performTouchInput {
            swipe(Offset(30f, height * 0.7f), Offset(width * 0.5f, height * 0.3f), 250)
            swipe(Offset(width * 0.5f, height * 0.3f), Offset(width - 30f, height * 0.65f), 250)
        }
        composeRule.waitForIdle()
    }

    /** Card -> chip -> sign technician -> Save -> JSON round trip (sync/reload) -> PDF. */
    private fun cardFlow(document: String, chip: String?, clearFirst: Boolean): JobPricing {
        val start = SignatureRules.save(JobPricing(), document, "Malky Unger", 1_791_900_000_000L, customerInk)
        var job by mutableStateOf(Job(id = "j-$document", customerName = "Malky Unger", pricing = start))
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = false) {
                Box(Modifier.width(411.dp).background(BackgroundDark).padding(12.dp)) {
                    JobSignatureCard(job = job, customerName = job.customerName, savedSignature = noSaved, onSave = { next ->
                        job = job.copy(pricing = json.decodeFromString(json.encodeToString(next)))
                    })
                }
            }
        }
        composeRule.waitForIdle()
        chip?.let { composeRule.onNodeWithText(it).performClick(); composeRule.waitForIdle() }
        if (clearFirst) {
            // Clear persists immediately (key change) — then sign again: must still stick.
            composeRule.onNodeWithTag("pad-Technician signature").assertExists()
            composeRule.onAllNodesWithText("Clear")[1].performClick()
            composeRule.waitForIdle()
        }
        sign("pad-Technician signature")
        composeRule.onNodeWithText("Save signatures").performClick()
        composeRule.waitForIdle()
        // second round after Save (state was re-keyed): re-sign and save again
        sign("pad-Technician signature")
        composeRule.onNodeWithText("Save signatures").performClick()
        composeRule.waitForIdle()
        val tech = TechSignatureRules.find(job.pricing, document)
        assertTrue("$document tech ink saved", tech != null && tech.pngBase64.isNotBlank())
        assertEquals("$document customer kept", customerInk, SignatureRules.find(job.pricing, document)?.pngBase64)
        if (document == SignatureRules.CONTRACT) save("contract-card-after-save")
        return job.pricing
    }

    private fun assertPdf(kind: DocumentKind, pricing: JobPricing, document: String, saved: TechnicianSignature = noSaved): StandardJobPacket {
        val r = TechSignatureRules.resolve(pricing, document, saved)
        val cust = SignatureRules.find(pricing, document)
        val packet = TechSignatureDefaults.apply(
            CityIslandSample.packet().copy(
                profile = profile,
                signerName = cust?.signerName.orEmpty(), signedAtMillis = cust?.signedAt, customerSignatureBase64 = cust?.pngBase64.orEmpty(),
                companySignatureBase64 = r.pngBase64, technicianSignerName = r.name,
                technicianSignedAtMillis = r.signedAt.takeIf { it > 0L }, technicianSignatureCleared = r.cleared,
                inspection = if (kind == DocumentKind.INSPECTION) com.strobingn.wildlifefieldops.util.InspectionReportFields(customerName = "Malky Unger") else null
            ),
            saved
        )
        val ops = StandardDocuments.build(kind, packet).layout().pages.flatMap { it.ops }
        assertTrue("$kind tech ink on PDF", ops.any { it.kind == "image" && it.tag == "company" })
        val techText = ops.first { it.kind == "text" && it.text.startsWith("Technician Signature") }
        assertTrue("$kind tech name: ${techText.text}", techText.text.contains("Dirk Diggler"))
        assertTrue("$kind tech date", ops.any { it.kind == "text" && it.y == techText.y && it.text.matches(Regex("Date: \\d{2}/\\d{2}/\\d{4}")) })
        return packet
    }

    @Test fun contractTechSignatureSticks() {
        val p = cardFlow(SignatureRules.CONTRACT, "Contract", clearFirst = true)
        val packet = assertPdf(DocumentKind.CONTRACT, p, SignatureRules.CONTRACT)
        render(packet)
    }

    @Test fun estimateTechSignatureSticks() = assertPdf(DocumentKind.ESTIMATE, cardFlow(SignatureRules.ESTIMATE, null, false), SignatureRules.ESTIMATE).let { }

    @Test fun inspectionTechSignatureSticks() =
        assertPdf(DocumentKind.INSPECTION, cardFlow(TechSignatureRules.INSPECTION, "Inspection", false), TechSignatureRules.INSPECTION).let { }

    @Test fun invoiceTechnicianSignsSticks() {
        val (_, tech) = SignaturePadTarget.route(SignaturePadTarget.SIGN_BUTTON, "INK", null, null)
        var p = TechSignatureRules.save(JobPricing(), TechSignatureRules.INVOICE, "", 1_791_900_000_000L, customerInk.takeIf { tech != null }!!)
        p = json.decodeFromString(json.encodeToString(p))
        assertPdf(DocumentKind.INVOICE, p, TechSignatureRules.INVOICE)
    }

    @Test fun settingsSavedSignatureReachesPdf() {
        val saved = TechnicianSignature(customerInk, "Dirk Diggler", 5L)
        assertPdf(DocumentKind.CONTRACT, JobPricing(), SignatureRules.CONTRACT, saved)
    }

    private fun render(packet: StandardJobPacket) {
        val doc = StandardDocuments.build(DocumentKind.CONTRACT, packet)
        val out = File(dir, "pdf").apply { mkdirs() }
        val images = mapOf("customer" to packet.customerSignatureBase64, "company" to packet.companySignatureBase64)
        val dump = File(out, "layout.txt")
        dump.writeText(doc.layout().pages.mapIndexed { i, page ->
            val name = if (i == 0) "contract-both-signatures" else "contract-both-signatures-p${i + 1}"
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
        val logo = locate("app/src/main/assets/wildlife_whisperer_logo.png.b64")
        val classes = File(out, "classes").apply { mkdirs() }
        val home = System.getProperty("java.home")
        run("$home/bin/javac", "-d", classes.absolutePath, locate("tools/render-standard-document-pages.java").absolutePath, locate("tools/render-standard-document-pdf.java").absolutePath)
        run("$home/bin/java", "-cp", classes.absolutePath, "render_standard_document_pdf", dump.absolutePath, logo.absolutePath, out.absolutePath)
        run("$home/bin/java", "-cp", classes.absolutePath, "render_standard_document_pages", dump.absolutePath, logo.absolutePath, out.absolutePath)
    }

    private fun locate(relative: String): File = listOf(File(relative), File("../$relative")).firstOrNull { it.exists() } ?: File(relative)
    private fun run(vararg cmd: String) {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val t = p.inputStream.bufferedReader().readText()
        check(p.waitFor() == 0) { t }
    }
    private fun save(name: String) {
        val bmp = composeRule.runOnIdle { composeRule.activity.window.decorView.findSticksCompose()!!.drawToBitmap() }
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private fun View.findSticksCompose(): View? {
    if (javaClass.name.contains("AndroidComposeView")) return this
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findSticksCompose()?.let { return it }
    return null
}
