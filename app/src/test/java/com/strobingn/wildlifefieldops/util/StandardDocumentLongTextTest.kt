package com.strobingn.wildlifefieldops.util

import org.junit.Assert.assertTrue
import org.junit.Test

class StandardDocumentLongTextTest {

    /** The wrapper used to copy the remaining text once per character: a long note froze the PDF. */
    @Test(timeout = 30_000)
    fun veryLongNoteLaysOutQuickly() {
        val doc = StandardDocument(
            kind = DocumentKind.INSPECTION,
            profile = BusinessProfile.defaults(),
            notes = "word ".repeat(20_000)
        )
        assertTrue(doc.layout().pages.size > 1)
    }

    @Test(timeout = 30_000)
    fun unbrokenHugeTokenLaysOutQuickly() {
        val doc = StandardDocument(
            kind = DocumentKind.INSPECTION,
            profile = BusinessProfile.defaults(),
            notes = "x".repeat(30_000)
        )
        assertTrue(doc.layout().pages.isNotEmpty())
    }

    @Test
    fun emptyDocumentStillHasAPage() {
        val doc = StandardDocument(kind = DocumentKind.INSPECTION, profile = BusinessProfile.defaults())
        assertTrue(doc.layout().pages.isNotEmpty())
    }
}
