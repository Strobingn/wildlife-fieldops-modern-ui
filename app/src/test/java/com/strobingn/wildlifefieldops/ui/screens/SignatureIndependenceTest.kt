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
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.strobingn.wildlifefieldops.ai.fieldops.SignatureRules
import com.strobingn.wildlifefieldops.ai.fieldops.TechSignatureRules
import com.strobingn.wildlifefieldops.ai.fieldops.TechnicianSignature
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.ui.theme.BackgroundDark
import com.strobingn.wildlifefieldops.ui.theme.WildlifeFieldOpsTheme
import com.strobingn.wildlifefieldops.util.DocumentKind
import com.strobingn.wildlifefieldops.util.StandardDocuments
import com.strobingn.wildlifefieldops.util.StandardJobPacket
import com.strobingn.wildlifefieldops.util.TechSignatureDefaults
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

/** Sir 2026-10-13: "It's saving my signature as the customers." */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h1100dp-xhdpi")
class SignatureIndependenceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val saved = TechnicianSignature("SIRINK", "Dirk Diggler", 1L)

    @Test fun invoiceSignButtonIsTechnicianAndRoutesOnlyThere() {
        assertTrue(SignaturePadTarget.SIGN_BUTTON.technician)
        assertEquals(Pair(null, "SIR"), SignaturePadTarget.route(SignaturePadTarget.SIGN_BUTTON, "SIR", null, null))
        assertEquals(Pair("CUST", "SIR"), SignaturePadTarget.route(SignaturePadTarget.CUSTOMER, "CUST", null, "SIR"))
        assertEquals(Pair("CUST", "SIR2"), SignaturePadTarget.route(SignaturePadTarget.TECHNICIAN, "SIR2", "CUST", "SIR"))
    }

    @Test fun savedSignatureNeverFillsCustomer() {
        val p = TechSignatureRules.save(JobPricing(), SignatureRules.ESTIMATE, "Dirk", 2L, "SIRINK")
        assertNull(SignatureRules.find(p, SignatureRules.ESTIMATE))
        assertTrue(p.customerSignatures.isEmpty())
        val packet = TechSignatureDefaults.apply(StandardJobPacket(job = Job(id = "j")), saved)
        assertEquals("", packet.customerSignatureBase64)
        assertEquals("", packet.signerName)
        val ops = StandardDocuments.build(DocumentKind.INVOICE, packet).layout().pages.flatMap { it.ops }
        assertTrue(ops.none { it.kind == "image" && it.tag == "customer" })
        assertTrue(ops.any { it.kind == "image" && it.tag == "company" })
    }

    @Test fun jobCardSaveKeepsSlotsApart() {
        var out: JobPricing? = null
        val job = Job(id = "j1", customerName = "Pat Morales", pricing = JobPricing())
        composeRule.setContent {
            WildlifeFieldOpsTheme(darkTheme = false) {
                Box(Modifier.width(411.dp).background(BackgroundDark).padding(12.dp)) {
                    JobSignatureCard(job = job, customerName = job.customerName, onSave = { out = it }, savedSignature = saved)
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save signatures").performClick()
        composeRule.waitForIdle()
        val p = out!!
        assertEquals("SIRINK", TechSignatureRules.find(p, SignatureRules.ESTIMATE)?.pngBase64)
        assertEquals("", SignatureRules.find(p, SignatureRules.ESTIMATE)?.pngBase64.orEmpty())
        val dir = File("/tmp/shots/pr-signature-fix").apply { mkdirs() }
        val bmp = composeRule.runOnIdle { composeRule.activity.window.decorView.findSigFixCompose()!!.drawToBitmap() }
        FileOutputStream(File(dir, "job-card-after-save.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

private fun View.findSigFixCompose(): View? {
    if (javaClass.name.contains("AndroidComposeView")) return this
    if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findSigFixCompose()?.let { return it }
    return null
}
