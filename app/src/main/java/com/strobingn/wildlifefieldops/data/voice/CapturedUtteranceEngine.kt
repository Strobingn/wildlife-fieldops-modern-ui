package com.strobingn.wildlifefieldops.data.voice

import com.strobingn.wildlifefieldops.ai.asr.AudioArtifact
import com.strobingn.wildlifefieldops.ai.asr.InferenceBackend
import com.strobingn.wildlifefieldops.ai.asr.TranscriptAttempt
import com.strobingn.wildlifefieldops.ai.asr.TranscriptionEngine
import java.util.UUID

/**
 * CPU-path [TranscriptionEngine] that feeds a captured on-device utterance
 * (SpeechRecognizer / future Qwen3-ASR) through [com.strobingn.wildlifefieldops.ai.asr.VoiceCaptureOrchestrator].
 *
 * An empty or missing utterance is returned as empty [TranscriptAttempt.rawText]
 * with [TranscriptAttempt.exitOk] = true so the fail-closed validator rejects it
 * (LiteRT-LM #3684-shaped empty success).
 */
class CapturedUtteranceEngine(
    private val capturedText: String?,
    private val clock: () -> Long = System::currentTimeMillis,
) : TranscriptionEngine {

    override fun transcribe(
        audio: AudioArtifact,
        backend: InferenceBackend,
        modelSha256: String,
        runtimeVersion: String,
        deviceKey: String,
        windowConfig: String,
        startedAt: Long,
    ): TranscriptAttempt {
        val finishedAt = clock()
        return TranscriptAttempt(
            attemptId = UUID.randomUUID().toString(),
            audioSha256 = audio.sha256,
            backend = backend,
            modelSha256 = modelSha256,
            runtimeVersion = runtimeVersion,
            deviceKey = deviceKey,
            windowConfig = windowConfig,
            startedAt = startedAt,
            finishedAt = if (finishedAt > startedAt) finishedAt else startedAt + 1,
            exitOk = true,
            rawText = capturedText ?: "",
            error = null,
        )
    }

    companion object {
        const val MODEL_SHA256 = "0000000000000000000000000000000000000000000000000000000000000001"
        const val RUNTIME_VERSION = "on-device-speech-1"
        const val WINDOW_CONFIG = "chunkMs=2000,overlapMs=200"
    }
}
