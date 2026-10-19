package com.strobingn.wildlifefieldops.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import com.strobingn.wildlifefieldops.pricing.CustomerSignatureRecord
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PricingJson
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import com.strobingn.wildlifefieldops.util.BusinessProfile
import com.strobingn.wildlifefieldops.util.CityIslandSample
import com.strobingn.wildlifefieldops.util.DocumentKind
import com.strobingn.wildlifefieldops.util.DrawOp
import com.strobingn.wildlifefieldops.util.PageLayout
import com.strobingn.wildlifefieldops.util.SignatureInk
import com.strobingn.wildlifefieldops.util.StandardDocumentPdf
import com.strobingn.wildlifefieldops.util.StandardDocuments
import com.strobingn.wildlifefieldops.util.StandardJobPacket
import com.strobingn.wildlifefieldops.util.TechSignatureDefaults
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/** Kashi's #116 review notes: stamp once, no stretch, Share saves first, CI-safe renders. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1100dp-xhdpi")
class SigFollowupsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val upload = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }
    private val dir = File("/tmp/shots/pr-sig-followups")

    /** A wide (4:1) Settings signature with a circle, so stretching would turn it into an oval. */
    private fun wideSignature(): String {
        val bmp = Bitmap.createBitmap(400, 100, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); c.drawColor(Color.WHITE)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 5f }
        c.drawCircle(50f, 50f, 40f, p)
        c.drawLine(110f, 80f, 200f, 20f, p); c.drawLine(200f, 20f, 290f, 80f, p); c.drawLine(290f, 80f, 390f, 30f, p)
        return SignatureInk.encodePng(bmp)
    }

    private fun reload(p: JobPricing): JobPricing = PricingJson.decode(upload.encodeToString(p))

    @Test fun autoFilledTechStampIsKeptAcrossReloads() {
        val saved = TechnicianSignature(pngBase64 = wideSignature(), name = "Austin Luker", savedAt = 5L)
        val t0 = 1_791_990_604_511L // Oct 14, 2026 11:10 AM ET
        // Existing record with a name/date but no ink (the 2.9.5 bug): keeps its own first stamp.
        val typed = TechSignatureRules.save(JobPricing(), SignatureRules.CONTRACT, "Austin Luker", t0, "")
        assertEquals(t0, TechSignatureRules.resolve(typed, SignatureRules.CONTRACT, saved, now = t0 + 99_000).signedAt)
        val persisted = reload(TechSignatureRules.persistAutoFill(typed, SignatureRules.CONTRACT, saved, now = t0 + 99_000)!!)
        val rec = TechSignatureRules.find(persisted, SignatureRules.CONTRACT)!!
        assertEquals(t0, rec.signedAt); assertEquals(saved.pngBase64, rec.pngBase64)
        // Persisted once: nothing more to persist, and later reloads keep the same time.
        assertEquals(null, TechSignatureRules.persistAutoFill(persisted, SignatureRules.CONTRACT, saved, now = t0 + 999_000))
        assertEquals(t0, TechSignatureRules.resolve(reload(persisted), SignatureRules.CONTRACT, saved, now = t0 + 999_000).signedAt)
        // A never-stamped record gets now() once, then keeps it.
        val unstamped = JobPricing(technicianSignatures = listOf(CustomerSignatureRecord(document = "contract", signerName = "Austin Luker", signedAt = 0L, pngBase64 = "")))
        val once = reload(TechSignatureRules.persistAutoFill(unstamped, "contract", saved, now = 777L)!!)
        assertEquals(777L, TechSignatureRules.resolve(once, "contract", saved, now = 888L).signedAt)
        // Cleared stays cleared.
        assertEquals(null, TechSignatureRules.persistAutoFill(TechSignatureRules.clear(JobPricing(), "contract"), "contract", saved))
    }

    @Test fun fitKeepsAspectAndCenters() {
        val b = SignatureFit.fit(400, 100, 200f, 110f)
        assertEquals(SignatureFit.Box(0, 30, 200, 50), b)
        val tall = SignatureFit.fit(100, 100, 300f, 100f)
        assertEquals(SignatureFit.Box(100, 0, 100, 100), tall)
    }

    @Test fun pdfImageIsNotStretched() {
        val ink = SignatureInk.decodePng(wideSignature())!!
        val page = PageLayout(300, 200, listOf(DrawOp("image", x = 10f, y = 10f, x2 = 200f, y2 = 120f, tag = "company")))
        val out = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        Canvas(out).also { it.drawColor(Color.WHITE); StandardDocumentPdf.drawTagged(it, page, "company", ink) }
        var maxY = 0; var maxX = 0
        for (y in 0 until 200) for (x in 0 until 300) if (Color.red(out.getPixel(x, y)) < 128) { if (y > maxY) maxY = y; if (x > maxX) maxX = x }
        // 4:1 ink into a 200x120 box -> 200x50, not 200x120.
        assertTrue("height $maxY", maxY in 45..62)
        assertTrue("width $maxX", maxX in 190..212)
        dir.mkdirs(); FileOutputStream(File(dir, "pdf-image-fit.png")).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun shareSavesPendingInkSoInkAndDatesAgree() {
        val saved = TechnicianSignature(pngBase64 = wideSignature(), name = "Austin Luker", savedAt = 5L)
        val t0 = 1_791_990_604_511L
        // Sir's job: customer typed-only record (no ink) and tech typed-only record on Contract.
        val start = TechSignatureRules.save(
            SignatureRules.save(JobPricing(), SignatureRules.CONTRACT, "Malky Unger", 1_791_908_752_169L, ""),
            SignatureRules.CONTRACT, "Austin Luker", t0, ""
        )
        var job by mutableStateOf(Job(id = "j", customerName = "Malky Unger", pricing = reload(start)))
        var saves = 0
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = false) {
                Box(Modifier.width(411.dp).background(BackgroundDark).padding(12.dp)) {
                    JobSignatureCard(job = job, customerName = job.customerName, savedSignature = saved, onSave = { next ->
                        saves++; job = job.copy(pricing = reload(next))
                    })
                }
            }
        }
        composeRule.onNodeWithText("Contract").performClick(); composeRule.waitForIdle()
        // Opening Contract persisted the auto-filled tech signature once, with its first stamp.
        val tech = TechSignatureRules.find(job.pricing, SignatureRules.CONTRACT)!!
        assertEquals(t0, tech.signedAt); assertEquals(saved.pngBase64, tech.pngBase64)
        save("contract-reloaded-tech-fit")
        // Customer signs but does NOT tap Save; Share must save that ink first.
        val before = System.currentTimeMillis()
        composeRule.onNodeWithTag("pad-Customer signature").performTouchInput {
            swipe(Offset(25f, height * 0.75f), Offset(width * 0.35f, height * 0.2f), 200)
            swipe(Offset(width * 0.35f, height * 0.2f), Offset(width * 0.6f, height * 0.7f), 200)
            swipe(Offset(width * 0.6f, height * 0.7f), Offset(width - 25f, height * 0.25f), 200)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Share contract PDF").performClick(); composeRule.waitForIdle()
        val cust = SignatureRules.find(job.pricing, SignatureRules.CONTRACT)!!
        assertTrue("customer ink saved by Share", cust.pngBase64.isNotBlank())
        assertTrue("customer date is the share time", cust.signedAt >= before)
        // Tech date unchanged by the share save.
        assertEquals(t0, TechSignatureRules.find(job.pricing, SignatureRules.CONTRACT)!!.signedAt)
        save("contract-after-share")

        // Real reload -> PDF data: ink and dates from the same saved records.
        val reloaded = reload(job.pricing)
        val sig = SignatureCardSave.forPdf(reloaded, SignatureRules.CONTRACT, saved, now = t0 + 10_000_000)
        assertEquals(cust.pngBase64, sig.customerInk); assertEquals(cust.signedAt, sig.customerSignedAt)
        assertEquals(saved.pngBase64, sig.techInk); assertEquals(t0, sig.techSignedAt)
        assertNotNull(SignatureInk.decodePng(sig.customerInk))
        val packet = TechSignatureDefaults.apply(
            CityIslandSample.packet().copy(
                profile = BusinessProfile.defaults().copy(ownerName = "Austin Luker"),
                signerName = sig.customerName, signedAtMillis = sig.customerSignedAt ?: 0L, customerSignatureBase64 = sig.customerInk,
                companySignatureBase64 = sig.techInk, technicianSignerName = sig.techName, technicianSignedAtMillis = sig.techSignedAt ?: 0L
            ), TechnicianSignature()
        )
        val ops = StandardDocuments.build(DocumentKind.CONTRACT, packet).layout().pages.flatMap { it.ops }
        assertTrue(ops.any { it.kind == "image" && it.tag == "customer" })
        assertTrue(ops.any { it.kind == "image" && it.tag == "company" })
        val stamp = SignatureRules.formatStamp(cust.signedAt).substringBefore(' ')
        assertTrue("customer date $stamp on PDF", ops.any { it.kind == "text" && it.text.contains(stamp) })
        assertTrue("tech date on PDF", ops.any { it.kind == "text" && it.text.contains("10/14/2026") })
        renderAndroidPages(packet)
        renderPreview(packet)
    }

    /** The app's own image drawing (StandardDocumentPdf.drawTagged) onto bitmaps, so we can look at it. */
    private fun renderAndroidPages(packet: StandardJobPacket) {
        val pages = StandardDocuments.build(DocumentKind.CONTRACT, packet).layout().pages
        val sigPage = pages.indexOfFirst { p -> p.ops.any { it.kind == "image" && it.tag == "company" } }
        val page = pages[sigPage]
        val out = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out); c.drawColor(Color.WHITE)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        page.ops.forEach { op ->
            when (op.kind) {
                "text" -> { text.textSize = op.size; text.isFakeBoldText = op.bold; c.drawText(op.text, op.x, op.y, text) }
                "line" -> c.drawLine(op.x, op.y, op.x2, op.y2, text)
            }
        }
        StandardDocumentPdf.drawTagged(c, page, "customer", SignatureInk.decodePng(packet.customerSignatureBase64)!!)
        StandardDocumentPdf.drawTagged(c, page, "company", SignatureInk.decodePng(packet.companySignatureBase64)!!)
        File(dir, "pdf").mkdirs()
        FileOutputStream(File(dir, "pdf/contract-android-draw-p${sigPage + 1}.png")).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Full preview via tools/ (skipped when tools/ or javac is missing, so CI can't break). */
    private fun renderPreview(packet: StandardJobPacket) {
        if (!RenderTools.available()) { println("SigFollowupsTest: tools/ or javac missing, preview skipped"); return }
        val doc = StandardDocuments.build(DocumentKind.CONTRACT, packet)
        val out = File(dir, "pdf").apply { mkdirs() }
        val images = mapOf("customer" to packet.customerSignatureBase64, "company" to packet.companySignatureBase64)
        val dump = File(out, "layout.txt")
        dump.writeText(doc.layout().pages.mapIndexed { i, page ->
            val name = if (i == 0) "contract-followups" else "contract-followups-p${i + 1}"
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
        val logo = listOf(File("app/src/main/assets/wildlife_whisperer_logo.png.b64"), File("src/main/assets/wildlife_whisperer_logo.png.b64")).firstOrNull { it.exists() } ?: File("x")
        RenderTools.render(dump, logo, out)
    }

    private fun save(name: String) {
        val bmp = composeRule.runOnIdle { composeRule.activity.window.decorView.findFollowupCompose()!!.drawToBitmap() }
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private fun View.findFollowupCompose(): View? {
    if (javaClass.name.contains("AndroidComposeView")) return this
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findFollowupCompose()?.let { return it }
    return null
}
