package com.strobingn.wildlifefieldops.ai.camera

/**
 * Per-frame capture-clock trace for live guidance.
 *
 * [sourceTimestampNs] is CameraX [androidx.camera.core.ImageInfo.getTimestamp] and
 * must NOT be subtracted from [android.os.SystemClock.elapsedRealtimeNanos] until a
 * device-level timebase check confirms they share a clock (Sept 11 brief /
 * [CaptureTimebaseProbe]).
 *
 * Wall-clock latency uses only elapsedRealtime* fields. When [timebaseTrusted] is false,
 * [sourceToArrivalMs] stays null — we never subtract clocks for pass/fail.
 */
data class CaptureFrameTrace(
    val frameId: Long,
    val sourceTimestampNs: Long,
    val analyzerArrivalElapsedNs: Long,
    val analysisStartElapsedNs: Long = 0L,
    val analysisEndElapsedNs: Long = 0L,
    val resultCommittedElapsedNs: Long = 0L,
    /** Stamped when guidance is applied on the main/Compose thread. */
    val overlayDrawnElapsedNs: Long = 0L,
    val droppedReason: String? = null,
    val timebaseTrusted: Boolean = false,
    /** Only set when [timebaseTrusted]; otherwise null (never invent cross-clock lag). */
    val sourceToArrivalMs: Long? = null
) {
    val analysisDurationMs: Long
        get() = if (analysisStartElapsedNs > 0 && analysisEndElapsedNs >= analysisStartElapsedNs) {
            (analysisEndElapsedNs - analysisStartElapsedNs) / 1_000_000L
        } else {
            -1L
        }

    /** Age from analyzer arrival to overlay commit (excludes unknown sensor delay). */
    val resultAgeFromArrivalMs: Long
        get() = if (analyzerArrivalElapsedNs > 0 && resultCommittedElapsedNs >= analyzerArrivalElapsedNs) {
            (resultCommittedElapsedNs - analyzerArrivalElapsedNs) / 1_000_000L
        } else {
            -1L
        }

    val queueWaitBeforeAnalysisMs: Long
        get() = if (analyzerArrivalElapsedNs > 0 && analysisStartElapsedNs >= analyzerArrivalElapsedNs) {
            (analysisStartElapsedNs - analyzerArrivalElapsedNs) / 1_000_000L
        } else {
            -1L
        }

    /** Committed → drawn on main thread. */
    val resultToOverlayMs: Long
        get() = if (resultCommittedElapsedNs > 0 && overlayDrawnElapsedNs >= resultCommittedElapsedNs) {
            (overlayDrawnElapsedNs - resultCommittedElapsedNs) / 1_000_000L
        } else {
            -1L
        }

    /** Full arrival → drawn age (elapsedRealtime only). */
    val arrivalToOverlayMs: Long
        get() = if (analyzerArrivalElapsedNs > 0 && overlayDrawnElapsedNs >= analyzerArrivalElapsedNs) {
            (overlayDrawnElapsedNs - analyzerArrivalElapsedNs) / 1_000_000L
        } else {
            -1L
        }
}

enum class CaptureGuidanceAction {
    ACCEPT,
    HOLD_STEADY,
    MOVE_CLOSER,
    REFRAME,
    IMPROVE_LIGHTING,
    WAIT
}

data class CaptureQualitySignals(
    val meanLuma: Float,
    /** Higher = sharper (Laplacian variance on luma subsample). */
    val sharpness: Float,
    val subjectCoverage: Float,
    val labelHints: List<String> = emptyList()
)

data class CaptureGuidance(
    val action: CaptureGuidanceAction,
    val reasonCode: String,
    val userMessage: String,
    val signals: CaptureQualitySignals,
    val frameId: Long,
    val resultAgeFromArrivalMs: Long,
    val analysisDurationMs: Long,
    val evidenceSummary: String = "",
    val evidenceSpecies: List<String> = emptyList(),
    val evidenceEntries: List<String> = emptyList(),
    val evidenceEquipment: List<String> = emptyList(),
    val evidenceDamage: List<String> = emptyList(),
    val analyzerArrivalElapsedNs: Long = 0L,
    val resultCommittedElapsedNs: Long = 0L,
    val overlayDrawnElapsedNs: Long = 0L,
    val resultToOverlayMs: Long = -1L,
    val arrivalToOverlayMs: Long = -1L,
    val timebaseTrusted: Boolean = false,
    val sourceToArrivalMs: Long? = null
)
