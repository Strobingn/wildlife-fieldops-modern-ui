package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.util.DocumentKind
import com.strobingn.wildlifefieldops.util.StandardDocuments
import com.strobingn.wildlifefieldops.util.StandardJobPacket
import com.strobingn.wildlifefieldops.util.TechSignatureDefaults
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TechSignatureRulesTest {
    private val saved = TechnicianSignature(pngBase64 = "SAVEDPNG", name = "Dirk Diggler", savedAt = 5L)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun newDocumentAutoFillsFromSettings() {
        val r = TechSignatureRules.resolve(JobPricing(), TechSignatureRules.INVOICE, saved, now = 99L)
        assertEquals("SAVEDPNG", r.pngBase64); assertEquals("Dirk Diggler", r.name); assertEquals(99L, r.signedAt)
        assertTrue(r.fromSaved)
    }

    @Test fun noSavedSignatureStaysBlank() {
        val r = TechSignatureRules.resolve(JobPricing(), TechSignatureRules.ESTIMATE, TechnicianSignature())
        assertFalse(r.hasInk); assertEquals("", r.name)
    }

    @Test fun reSignPerDocumentWins() {
        val p = TechSignatureRules.save(JobPricing(), TechSignatureRules.CONTRACT, "Dirk", 7L, "NEWINK")
        val r = TechSignatureRules.resolve(p, TechSignatureRules.CONTRACT, saved)
        assertEquals("NEWINK", r.pngBase64); assertFalse(r.fromSaved)
        // other documents still auto-fill
        assertTrue(TechSignatureRules.resolve(p, TechSignatureRules.ESTIMATE, saved).fromSaved)
    }

    @Test fun clearedStaysBlankThroughSaveReloadSync() {
        val p = TechSignatureRules.clear(JobPricing(), TechSignatureRules.INVOICE)
        // round-trip through the jsonb that syncs as jobs.pricing
        val reloaded = json.decodeFromString<JobPricing>(json.encodeToString(p))
        val r = TechSignatureRules.resolve(reloaded, TechSignatureRules.INVOICE, saved)
        assertTrue(r.cleared); assertFalse(r.hasInk); assertEquals("", r.name)
    }

    @Test fun customerSignatureUntouched() {
        val base = SignatureRules.save(JobPricing(), SignatureRules.ESTIMATE, "Pat Customer", 3L, "CUST")
        val p = TechSignatureRules.save(base, SignatureRules.ESTIMATE, "Dirk", 4L, "TECH")
        assertEquals("CUST", SignatureRules.find(p, SignatureRules.ESTIMATE)?.pngBase64)
        assertEquals("TECH", TechSignatureRules.find(p, SignatureRules.ESTIMATE)?.pngBase64)
    }

    @Test fun oldPricingJsonStillDecodes() {
        val p = json.decodeFromString<JobPricing>("{}")
        assertTrue(p.technicianSignatures.isEmpty())
    }

    @Test fun packetDefaultsFillOnlyEmptyAndRespectCleared() {
        val packet = StandardJobPacket(job = Job(id = "j"), technicianName = "Tech", nowMillis = 1000L)
        val filled = TechSignatureDefaults.apply(packet, saved)
        assertEquals("SAVEDPNG", filled.companySignatureBase64); assertEquals("Dirk Diggler", filled.technicianSignerName)
        assertEquals(1000L, filled.technicianSignedAtMillis)
        val own = TechSignatureDefaults.apply(packet.copy(companySignatureBase64 = "MINE", technicianSignerName = "Me"), saved)
        assertEquals("MINE", own.companySignatureBase64); assertEquals("Me", own.technicianSignerName)
        val cleared = TechSignatureDefaults.apply(packet.copy(technicianSignatureCleared = true), saved)
        assertEquals("", cleared.companySignatureBase64); assertEquals("", cleared.technicianSignerName)
    }

    @Test fun pdfSignatureBlocksSideBySide() {
        val packet = StandardJobPacket(
            job = Job(id = "j", customerName = "Pat"),
            signerName = "Pat Customer", signedAtMillis = 1_790_000_000_000L, customerSignatureBase64 = "C",
            companySignatureBase64 = "T", technicianSignerName = "Dirk Diggler", technicianSignedAtMillis = 1_790_000_000_000L
        )
        listOf(DocumentKind.ESTIMATE, DocumentKind.CONTRACT, DocumentKind.INVOICE).forEach { kind ->
            val ops = StandardDocuments.build(kind, packet).layout().pages.flatMap { it.ops }
            val owner = ops.first { it.kind == "text" && it.text.startsWith("Owner Signature") }
            val tech = ops.first { it.kind == "text" && it.text.startsWith("Technician Signature") }
            assertEquals(kind.name, owner.y, tech.y, 0.01f)
            assertTrue(kind.name, tech.x > owner.x + 100f)
            assertTrue(tech.text.contains("Dirk Diggler"))
            val images = ops.filter { it.kind == "image" && (it.tag == "customer" || it.tag == "company") }
            assertEquals(2, images.size)
            assertEquals(images[0].y, images[1].y, 0.01f)
            assertTrue(ops.count { it.kind == "text" && it.text.startsWith("Date:") && it.y == tech.y } >= 2)
        }
    }

    @Test fun clearedTechnicianBlockIsBlankOnPdf() {
        val packet = StandardJobPacket(job = Job(id = "j"), companySignatureBase64 = "T", technicianSignerName = "Dirk", technicianSignatureCleared = true)
        val ops = StandardDocuments.build(DocumentKind.INVOICE, packet).layout().pages.flatMap { it.ops }
        assertTrue(ops.none { it.kind == "image" && it.tag == "company" })
        assertTrue(ops.any { it.kind == "text" && it.text == "Technician Signature" })
    }
}
