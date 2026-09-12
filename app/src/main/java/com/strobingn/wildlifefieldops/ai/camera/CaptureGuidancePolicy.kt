package com.strobingn.wildlifefieldops.ai.camera

/**
 * Deterministic capture acceptance / coaching policy (not VLM-owned).
 * Uses hysteresis + minimum dwell so guidance does not oscillate.
 */
class CaptureGuidancePolicy(
    private val darkLumaEnter: Float = 42f,
    private val darkLumaExit: Float = 55f,
    private val brightLumaEnter: Float = 230f,
    private val brightLumaExit: Float = 220f,
    private val blurSharpEnter: Float = 18f,
    private val blurSharpExit: Float = 28f,
    private val coverageEnter: Float = 0.04f,
    private val coverageExit: Float = 0.08f,
    private val minDwellMs: Long = 450L
) {
    private var current: CaptureGuidanceAction = CaptureGuidanceAction.WAIT
    private var sinceMs: Long = 0L

    fun evaluate(signals: CaptureQualitySignals, nowMs: Long): Pair<CaptureGuidanceAction, String> {
        val candidate = propose(signals)
        if (candidate == current) {
            return current to reasonFor(current, signals)
        }
        if (sinceMs == 0L) sinceMs = nowMs
        if (nowMs - sinceMs < minDwellMs && current != CaptureGuidanceAction.WAIT) {
            return current to reasonFor(current, signals)
        }
        // Exit hysteresis: only leave ACCEPT when signals clearly degrade
        if (current == CaptureGuidanceAction.ACCEPT && candidate != CaptureGuidanceAction.ACCEPT) {
            val stillOk = signals.meanLuma in darkLumaExit..brightLumaExit &&
                signals.sharpness >= blurSharpEnter &&
                signals.subjectCoverage >= coverageEnter
            if (stillOk) return current to reasonFor(current, signals)
        }
        current = candidate
        sinceMs = nowMs
        return current to reasonFor(current, signals)
    }

    fun reset() {
        current = CaptureGuidanceAction.WAIT
        sinceMs = 0L
    }

    private fun propose(s: CaptureQualitySignals): CaptureGuidanceAction = when {
        s.meanLuma < darkLumaEnter || s.meanLuma > brightLumaEnter -> CaptureGuidanceAction.IMPROVE_LIGHTING
        s.sharpness < blurSharpEnter -> CaptureGuidanceAction.HOLD_STEADY
        s.subjectCoverage in 0.001f..<coverageEnter -> CaptureGuidanceAction.MOVE_CLOSER
        s.subjectCoverage <= 0f && s.labelHints.isEmpty() -> CaptureGuidanceAction.REFRAME
        s.meanLuma >= darkLumaExit &&
            s.meanLuma <= brightLumaExit &&
            s.sharpness >= blurSharpExit &&
            (s.subjectCoverage >= coverageExit || s.labelHints.isNotEmpty()) -> CaptureGuidanceAction.ACCEPT
        else -> CaptureGuidanceAction.WAIT
    }

    private fun reasonFor(action: CaptureGuidanceAction, s: CaptureQualitySignals): String = when (action) {
        CaptureGuidanceAction.IMPROVE_LIGHTING ->
            if (s.meanLuma < darkLumaEnter) "DARK_LUMA" else "BRIGHT_LUMA"
        CaptureGuidanceAction.HOLD_STEADY -> "LOW_SHARPNESS"
        CaptureGuidanceAction.MOVE_CLOSER -> "LOW_SUBJECT_COVERAGE"
        CaptureGuidanceAction.REFRAME -> "NO_SUBJECT"
        CaptureGuidanceAction.ACCEPT -> "QUALITY_OK"
        CaptureGuidanceAction.WAIT -> "STABILIZING"
    }

    fun userMessage(action: CaptureGuidanceAction): String = when (action) {
        CaptureGuidanceAction.ACCEPT -> "Frame looks good — capture when ready"
        CaptureGuidanceAction.HOLD_STEADY -> "Hold steady — reduce motion blur"
        CaptureGuidanceAction.MOVE_CLOSER -> "Move closer to the subject"
        CaptureGuidanceAction.REFRAME -> "Point at the animal or entry point"
        CaptureGuidanceAction.IMPROVE_LIGHTING -> "Improve lighting or avoid harsh glare"
        CaptureGuidanceAction.WAIT -> "Analyzing…"
    }
}
