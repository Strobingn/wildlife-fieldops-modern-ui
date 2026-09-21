package com.strobingn.wildlifefieldops.data.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records 16 kHz mono PCM16 to a WAV under [rootDir]/voice so the original
 * utterance is retained even when transcription fails.
 */
class VoiceAudioCapture(
    private val rootDir: File,
) {
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)
    private val pcm = ByteArrayOutputStream()
    private var startedAtMs: Long = 0L

    data class CaptureResult(
        val file: File,
        val sha256: String,
        val durationMs: Long,
        val uri: String,
    )

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running.get()) return false
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) return false
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                minBuf * 2,
            )
        } catch (_: Exception) {
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        pcm.reset()
        recorder = record
        startedAtMs = System.currentTimeMillis()
        running.set(true)
        record.startRecording()
        worker = Thread({
            val buf = ByteArray(minBuf)
            while (running.get()) {
                val n = try {
                    record.read(buf, 0, buf.size)
                } catch (_: Exception) {
                    -1
                }
                if (n > 0) {
                    synchronized(pcm) { pcm.write(buf, 0, n) }
                }
            }
        }, "voice-audio-capture").also { it.start() }
        return true
    }

    fun stop(): CaptureResult? {
        if (!running.getAndSet(false)) return null
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }
        try {
            worker?.join(1_500)
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
        worker = null
        val bytes = synchronized(pcm) { pcm.toByteArray() }
        pcm.reset()
        val durationMs = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(
            ((bytes.size / 2L) * 1000L) / SAMPLE_RATE
        ).coerceAtLeast(1L)
        if (bytes.isEmpty()) return null
        val dir = File(rootDir, "voice").apply { mkdirs() }
        val file = File(dir, "voice-${UUID.randomUUID()}.wav")
        writeWav(file, bytes)
        return CaptureResult(
            file = file,
            sha256 = sha256Hex(file),
            durationMs = durationMs,
            uri = file.absolutePath,
        )
    }

    fun cancel() {
        stop()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8_192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    digest.update(buf, 0, n)
                }
            }
            return digest.digest().joinToString("") { b -> "%02x".format(b) }
        }

        fun writeWav(file: File, pcm: ByteArray, sampleRate: Int = SAMPLE_RATE) {
            val channels = 1
            val bits = 16
            val byteRate = sampleRate * channels * bits / 8
            val dataSize = pcm.size
            FileOutputStream(file).use { out ->
                out.write("RIFF".toByteArray())
                out.write(intLe(36 + dataSize))
                out.write("WAVE".toByteArray())
                out.write("fmt ".toByteArray())
                out.write(intLe(16))
                out.write(shortLe(1))
                out.write(shortLe(channels))
                out.write(intLe(sampleRate))
                out.write(intLe(byteRate))
                out.write(shortLe(channels * bits / 8))
                out.write(shortLe(bits))
                out.write("data".toByteArray())
                out.write(intLe(dataSize))
                out.write(pcm)
            }
        }

        private fun intLe(value: Int): ByteArray = byteArrayOf(
            (value and 0xff).toByte(),
            (value shr 8 and 0xff).toByte(),
            (value shr 16 and 0xff).toByte(),
            (value shr 24 and 0xff).toByte(),
        )

        private fun shortLe(value: Int): ByteArray = byteArrayOf(
            (value and 0xff).toByte(),
            (value shr 8 and 0xff).toByte(),
        )
    }
}
