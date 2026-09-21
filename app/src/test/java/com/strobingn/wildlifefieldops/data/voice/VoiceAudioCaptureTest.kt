package com.strobingn.wildlifefieldops.data.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VoiceAudioCaptureTest {

    @Test
    fun writeWav_includesHeaderAndPcm() {
        val file = File.createTempFile("voice-capture", ".wav")
        val pcm = ByteArray(3_200) { index -> (index % 127).toByte() }
        try {
            VoiceAudioCapture.writeWav(file, pcm)
            assertTrue(file.length() >= 44L + pcm.size)
            val bytes = file.readBytes()
            assertEquals('R'.code.toByte(), bytes[0])
            assertEquals('I'.code.toByte(), bytes[1])
            assertEquals('F'.code.toByte(), bytes[2])
            assertEquals('F'.code.toByte(), bytes[3])
            val hash = VoiceAudioCapture.sha256Hex(file)
            assertEquals(64, hash.length)
            assertEquals(hash, VoiceAudioCapture.sha256Hex(file))
        } finally {
            file.delete()
        }
    }
}
