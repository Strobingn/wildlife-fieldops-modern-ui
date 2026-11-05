package com.strobingn.wildlifefieldops.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZelleHeaderTest {
    private val zelleText = "Zelle: ${WildlifeWhispererIdentity.ZELLE}"

    private fun firstPageText(kind: DocumentKind, profile: BusinessProfile): List<DrawOp> =
        StandardDocument(kind = kind, profile = profile).layout().pages.first().ops.filter { it.kind == "text" }

    @Test
    fun defaultProfileCarriesZelleButPlainHeaderLinesDoNot() {
        val defaults = BusinessProfileResolve.fromStored(emptyMap())
        assertEquals(WildlifeWhispererIdentity.ZELLE, defaults.zelle)
        assertEquals(WildlifeWhispererIdentity.ZELLE, BusinessProfile.defaults().zelle)
        assertEquals(zelleText, defaults.zelleLine())
        assertFalse(defaults.headerLines().any { it.startsWith("Zelle") })
    }

    @Test
    fun zelleRowSitsDirectlyUnderTheEmailLine() {
        val rows = BusinessProfile.defaults().headerRows(includeZelle = true)
        val contact = rows.indexOfFirst { it.text.contains(WildlifeWhispererIdentity.EMAIL) }
        val zelle = rows.indexOfFirst { it.style == HeaderRowStyle.ZELLE }
        assertTrue(contact >= 0)
        assertEquals(contact + 1, zelle)
        assertEquals(zelleText, rows[zelle].text)
    }

    @Test
    fun estimateInspectionAndInvoicePrintZelleLargerAndBoldUnderEmail() {
        val profile = BusinessProfile.defaults()
        listOf(DocumentKind.ESTIMATE, DocumentKind.INSPECTION, DocumentKind.INVOICE).forEach { kind ->
            val ops = firstPageText(kind, profile)
            val zelle = ops.single { it.text == zelleText }
            val email = ops.first { it.text.contains(WildlifeWhispererIdentity.EMAIL) && it.text != zelleText }
            assertTrue("$kind Zelle bold", zelle.bold)
            assertEquals("$kind Zelle ink", "ink", zelle.color)
            assertTrue("$kind Zelle larger than email", zelle.size > email.size)
            assertEquals(StandardDocumentLayout.ZELLE_TEXT, zelle.size)
            assertTrue("$kind Zelle under email", zelle.y > email.y)
            assertEquals("$kind same column as email", email.x, zelle.x)
        }
    }

    @Test
    fun otherDocumentKindsDoNotPrintZelle() {
        val profile = BusinessProfile.defaults()
        DocumentKind.entries
            .filterNot { it in StandardDocumentLayout.ZELLE_KINDS }
            .forEach { kind ->
                assertFalse(kind.name, firstPageText(kind, profile).any { it.text.startsWith("Zelle") })
            }
    }

    @Test
    fun clearedZelleLeavesTheLineOff() {
        val cleared = BusinessProfileResolve.fromStored(mapOf(BusinessProfileResolve.KEY_ZELLE to ""))
        assertEquals("", cleared.zelle)
        assertNull(cleared.zelleLine())
        assertFalse(firstPageText(DocumentKind.INVOICE, cleared).any { it.text.startsWith("Zelle") })
    }

    @Test
    fun customZelleRecipientFromSettingsIsPrinted() {
        val custom = BusinessProfileResolve.fromStored(mapOf(BusinessProfileResolve.KEY_ZELLE to "(845) 751-8448"))
        val ops = firstPageText(DocumentKind.ESTIMATE, custom)
        assertTrue(ops.any { it.text == "Zelle: (845) 751-8448" && it.bold })
    }

    @Test
    fun defaultZelleFitsOnOneHeaderLine() {
        val ops = firstPageText(DocumentKind.INVOICE, BusinessProfile.defaults())
        assertEquals(1, ops.count { it.text.contains("Austin@Wildlifewhispererllc.com") })
    }
}
