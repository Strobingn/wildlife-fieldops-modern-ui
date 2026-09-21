package com.strobingn.wildlifefieldops.ai.asr

/**
 * Immutable record of a captured audio artifact.
 *
 * Rules:
 *  - Never mutated after creation.
 *  - [sha256] is the authoritative content address; [uri] may be a content-URI
 *    or an absolute filesystem path and should be treated as opaque by consumers.
 *  - [capturedAt] is the device-local capture clock (epoch-ms), NOT a server timestamp.
 *  - [durationMs] must be > 0; a zero-duration artifact cannot produce a valid transcript.
 *
 * The audio file identified by [uri] / [sha256] must exist on storage before any
 * [DerivedTranscript] can be committed for it. The [VoiceCaptureOrchestrator] enforces
 * this invariant at runtime.
 */
data class AudioArtifact(
    /** Content-URI or absolute path to the audio file on-device. */
    val uri: String,

    /** SHA-256 hex digest (lowercase) of the raw audio bytes. */
    val sha256: String,

    /** Duration of the audio in milliseconds. Must be > 0. */
    val durationMs: Long,

    /** Device-local capture timestamp (epoch-ms). */
    val capturedAt: Long,
) {
    init {
        require(uri.isNotBlank()) { "AudioArtifact.uri must not be blank" }
        require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) {
            "AudioArtifact.sha256 must be a 64-char hex string, got: '${sha256.take(16)}…'"
        }
        require(durationMs > 0) {
            "AudioArtifact.durationMs must be > 0, was $durationMs"
        }
    }
}
