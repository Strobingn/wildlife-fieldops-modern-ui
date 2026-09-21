package com.strobingn.wildlifefieldops.ai.asr

/**
 * Structural and light-semantic gate for ASR output.
 *
 * ## Fail-closed contract
 * Any check that cannot be evaluated returns [ValidationResult.Failure].
 * An empty transcript is NEVER accepted as [ValidationResult.Ok] unless the
 * audio is independently classified as silence by [isSilentAudio].
 *
 * ## Checks performed (in order)
 *  1. Silence exemption — legitimately empty only when audio is near-silent.
 *  2. Null guard — null [TranscriptAttempt.rawText] means the process crashed.
 *  3. Non-empty + non-whitespace after trimming.
 *  4. Token-spam detection — extreme repetition of the same token flags a
 *     model repetition loop (e.g. hallucinating the same word hundreds of times).
 *  5. Duration heuristic — very few characters relative to audio length is
 *     suspicious for non-trivial audio (≥ 5 s).
 *
 * ## Silence detection
 * [isSilentAudio] is currently a stub that always returns false (non-silent).
 * This is the safe fail-closed default: empty transcripts on non-silent audio
 * are never accepted.
 * TODO(energy-threshold): replace with a real energy-threshold classifier.
 */
interface TranscriptValidator {

    /** Validate [attempt] against [audio]. */
    fun validate(audio: AudioArtifact, attempt: TranscriptAttempt): ValidationResult

    /**
     * Silence / near-silence classifier for [audio].
     *
     * TODO(energy-threshold): replace stub with real energy classifier.
     *   Suggested approach: compute RMS over PCM16 samples; treat audio as
     *   silent when RMS ≤ −40 dBFS (i.e. RMS ≤ 0.01 on a 0..1 normalised scale).
     *   For now this stub always returns false (non-silent), which is the
     *   fail-closed safe default.
     */
    fun isSilentAudio(audio: AudioArtifact): Boolean

    sealed class ValidationResult {
        /** Transcript passed all checks; [validatedText] is trimmed and safe to commit. */
        data class Ok(val validatedText: String) : ValidationResult()

        /** Transcript failed at least one check; [reason] describes the first failure. */
        data class Failure(val reason: String) : ValidationResult()
    }

    companion object {
        /** Default production implementation with standard thresholds. */
        val Default: TranscriptValidator = DefaultTranscriptValidator()
    }
}

/**
 * Default production implementation of [TranscriptValidator].
 *
 * Thresholds:
 *  - [MAX_REPEAT_FRACTION]: fraction of tokens that may be a single repeated token.
 *  - [MIN_CHARS_PER_SECOND]: minimum characters-per-second for non-trivial audio.
 *  - [MIN_DURATION_FOR_HEURISTIC_MS]: heuristic only applied when audio is at least this long.
 */
internal class DefaultTranscriptValidator(
    private val maxRepeatFraction: Double = MAX_REPEAT_FRACTION,
    private val minCharsPerSecond: Double = MIN_CHARS_PER_SECOND,
    private val minDurationForHeuristicMs: Long = MIN_DURATION_FOR_HEURISTIC_MS,
) : TranscriptValidator {

    companion object {
        /** If one token makes up more than this fraction of all tokens, flag as spam. */
        internal const val MAX_REPEAT_FRACTION = 0.6

        /** Minimum chars/second expected for meaningful speech (≥ minDurationForHeuristicMs). */
        internal const val MIN_CHARS_PER_SECOND = 0.5

        /** Apply duration heuristic only when audio is at least this long. */
        internal const val MIN_DURATION_FOR_HEURISTIC_MS = 5_000L

        /** Minimum token count before the spam check is applied (avoids false positives). */
        private const val MIN_TOKENS_FOR_SPAM_CHECK = 4
    }

    override fun validate(audio: AudioArtifact, attempt: TranscriptAttempt): TranscriptValidator.ValidationResult {
        // ── 1. Silence exemption ────────────────────────────────────────────
        // An empty transcript is VALID only when the audio is classified as silence.
        if (isSilentAudio(audio)) {
            return TranscriptValidator.ValidationResult.Ok(validatedText = "")
        }

        // ── 2. Null guard ───────────────────────────────────────────────────
        val raw = attempt.rawText
        if (raw == null) {
            return TranscriptValidator.ValidationResult.Failure(
                reason = "rawText is null — process likely crashed (exitOk=${attempt.exitOk})"
            )
        }

        val trimmed = raw.trim()

        // ── 3. Non-empty / non-whitespace ───────────────────────────────────
        if (trimmed.isEmpty()) {
            val hint = if (attempt.exitOk) " — probable LiteRT-LM issue #3684 (exit 0 but empty)" else ""
            return TranscriptValidator.ValidationResult.Failure(
                reason = "Empty transcript on non-silent audio (exitOk=${attempt.exitOk})$hint"
            )
        }

        // ── 4. Token-spam detection ─────────────────────────────────────────
        val tokens = trimmed.split(Regex("\\s+"))
        if (tokens.size >= MIN_TOKENS_FOR_SPAM_CHECK) {
            val topCount = tokens.groupingBy { it }.eachCount().values.maxOrNull() ?: 0
            if (topCount.toDouble() / tokens.size > maxRepeatFraction) {
                return TranscriptValidator.ValidationResult.Failure(
                    reason = "Extreme token repetition: top token appears $topCount/${tokens.size} times " +
                        "(threshold=${maxRepeatFraction})"
                )
            }
        }

        // ── 5. Duration heuristic ───────────────────────────────────────────
        if (audio.durationMs >= minDurationForHeuristicMs) {
            val audioSeconds = audio.durationMs / 1_000.0
            val charsPerSecond = trimmed.length.toDouble() / audioSeconds
            if (charsPerSecond < minCharsPerSecond) {
                return TranscriptValidator.ValidationResult.Failure(
                    reason = "Transcript too sparse for audio duration: " +
                        "${"%.2f".format(charsPerSecond)} chars/s " +
                        "(min=$minCharsPerSecond) for ${audio.durationMs} ms audio"
                )
            }
        }

        return TranscriptValidator.ValidationResult.Ok(validatedText = trimmed)
    }

    /**
     * Stub — always returns false (non-silent).
     * TODO(energy-threshold): implement real RMS energy classifier.
     */
    override fun isSilentAudio(audio: AudioArtifact): Boolean = false
}
