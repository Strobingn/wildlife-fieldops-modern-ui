package com.strobingn.wildlifefieldops.data.repository

import com.strobingn.wildlifefieldops.ai.fieldops.SignatureRules
import com.strobingn.wildlifefieldops.ai.fieldops.TechSignatureRules
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPaymentRecord
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.MoneyInput
import com.strobingn.wildlifefieldops.pricing.PricingCalculator
import com.strobingn.wildlifefieldops.pricing.PricingJson
import com.strobingn.wildlifefieldops.util.DocumentKind
import com.strobingn.wildlifefieldops.util.StandardDocuments
import com.strobingn.wildlifefieldops.util.StandardJobPacket
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditRegressionTest {
    /** Same settings as the live upload (encodeDefaults = false). */
    private val upload = Json { ignoreUnknownKeys = true; encodeDefaults = false; explicitNulls = false }

    @Test fun dataVersionAlwaysUploadedAndOldJsonStillReads() {
        val keys = upload.parseToJsonElement(upload.encodeToString(JobPricing())).jsonObject.keys
        assertTrue("dataVersion in upload: $keys", "dataVersion" in keys)
        // Jobs without a precise trap spot stay at 2 so older builds keep uploading them.
        assertEquals(2, PricingJson.decode("""{"laborHours":2.0}""").dataVersion)
        assertEquals(2, JobPricing().dataVersion)
        assertEquals(DataVersionGate.TRAP_GPS, DataVersionGate.CURRENT)
        assertFalse(DataVersionGate.isNewer(JobPricing().dataVersion))
    }

    @Test fun newerDataPausesUploadsOlderDoesNot() {
        assertFalse(DataVersionGate.observe(listOf(1, DataVersionGate.CURRENT)))
        assertTrue(DataVersionGate.observe(listOf(DataVersionGate.CURRENT, DataVersionGate.CURRENT + 1)))
        assertTrue(DataVersionGate.blocked.value)
        assertFalse(DataVersionGate.observe(listOf(DataVersionGate.CURRENT)))
        assertFalse(DataVersionGate.blocked.value)
    }

    @Test fun techSignatureAndPaymentsSurviveUploadReload() {
        var p = TechSignatureRules.save(JobPricing(), SignatureRules.CONTRACT, "Dirk Diggler", 1_791_900_000_000L, "INK")
        p = p.copy(payments = listOf(JobPaymentRecord(id = "d", amount = 500.0, paidAt = 1L)))
        val back = PricingJson.decode(upload.encodeToString(p))
        assertEquals("INK", TechSignatureRules.find(back, SignatureRules.CONTRACT)?.pngBase64)
        assertEquals(500.0, back.payments.single().amount, 0.0)
    }

    @Test fun invoiceCustomerSignatureSavedReloadedOnPdf() {
        val p = SignatureRules.save(JobPricing(), TechSignatureRules.INVOICE, "Malky Unger", 1_791_900_000_000L, "CUST")
        val back = PricingJson.decode(upload.encodeToString(p))
        val rec = SignatureRules.find(back, TechSignatureRules.INVOICE)!!
        val packet = StandardJobPacket(job = Job(id = "j"), signerName = rec.signerName, signedAtMillis = rec.signedAt, customerSignatureBase64 = rec.pngBase64)
        val ops = StandardDocuments.build(DocumentKind.INVOICE, packet).layout().pages.flatMap { it.ops }
        assertTrue(ops.any { it.kind == "image" && it.tag == "customer" })
        assertTrue(ops.any { it.kind == "text" && it.text == "Owner Signature: Malky Unger" })
    }

    @Test fun typedMoneyWithDollarAndCommaPricesTheEstimate() {
        val p = JobPricing(laborHours = MoneyInput.parse("2")!!, laborRate = MoneyInput.parse("$1,085.00")!!)
        val back = PricingJson.decode(upload.encodeToString(p))
        assertEquals(2170.0, PricingCalculator.compute(back).subtotal.effective, 0.01)
    }
}
