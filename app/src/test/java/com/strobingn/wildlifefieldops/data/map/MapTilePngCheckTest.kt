package com.strobingn.wildlifefieldops.data.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTilePngCheckTest {

    @Test
    fun pngSignatureIsAccepted() {
        val png = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D
        )
        assertTrue(MapTileCache.looksLikePng(png))
    }

    @Test
    fun captivePortalHtmlIsRejected() {
        val html = "<html><body>Sign in to Wi-Fi</body></html>".toByteArray()
        assertFalse(MapTileCache.looksLikePng(html))
        assertFalse(MapTileCache.looksLikePng(ByteArray(0)))
    }
}
