package com.strobingn.wildlifefieldops.ai.camera

/**
 * Tiny device-level check: do CameraX [androidx.camera.core.ImageInfo] timestamps share a
 * timebase with [android.os.SystemClock.elapsedRealtimeNanos]?
 *
 * Method: on the first [maxSamples] analyzed frames, compare consecutive inter-frame deltas
 * of both clocks. If those deltas stay nearly proportional (ratio ≈ 1) and stable, set
 * [timebaseTrusted]=true.
 *
 * **Untrusted means we never subtract the two clocks for pass/fail or latency gates.**
 * [sourceToArrivalMs] is only returned when trusted; otherwise callers must rely solely on
 * elapsedRealtime* fields (see [CaptureFrameTrace]).
 */
class CaptureTimebaseProbe(
    private val maxSamples: Int = 16,
    private val minPairs: Int = 8,
    private val ratioLow: Double = 0.85,
    private val ratioHigh: Double = 1.15,
    private val minDeltaNs: Long = 1_000_000L,
    private val maxDeltaNs: Long = 500_000_000L
) {
    @Volatile
    var timebaseTrusted: Boolean = false
        private set

    @Volatile
    var probeComplete: Boolean = false
        private set

    private val lock = Any()
    private var sampleCount = 0
    private var prevSourceNs = 0L
    private var prevElapsedNs = 0L
    private var goodPairs = 0
    private var badPairs = 0

    /** Call once per analyzed frame (not busy-drops). Thread-safe. */
    fun observe(sourceTimestampNs: Long, arrivalElapsedNs: Long) {
        if (probeComplete) return
        synchronized(lock) {
            if (probeComplete) return
            if (sourceTimestampNs <= 0L || arrivalElapsedNs <= 0L) {
                sampleCount++
                maybeFinalizeLocked()
                return
            }
            if (sampleCount > 0 && prevSourceNs > 0L && prevElapsedNs > 0L) {
                val dSrc = sourceTimestampNs - prevSourceNs
                val dEl = arrivalElapsedNs - prevElapsedNs
                when {
                    dSrc in minDeltaNs..maxDeltaNs && dEl in minDeltaNs..maxDeltaNs -> {
                        val ratio = dSrc.toDouble() / dEl.toDouble()
                        if (ratio in ratioLow..ratioHigh) goodPairs++ else badPairs++
                    }
                    dSrc <= 0L || dEl <= 0L -> badPairs++
                    else -> {
                        // Out-of-band spacing — ignore for ratio but still advance sample count
                    }
                }
            }
            prevSourceNs = sourceTimestampNs
            prevElapsedNs = arrivalElapsedNs
            sampleCount++
            maybeFinalizeLocked()
        }
    }

    private fun maybeFinalizeLocked() {
        val pairs = goodPairs + badPairs
        if (pairs < minPairs && sampleCount < maxSamples) return
        if (pairs < minPairs) {
            timebaseTrusted = false
            probeComplete = true
            return
        }
        val goodFrac = goodPairs.toDouble() / pairs.toDouble()
        timebaseTrusted = goodPairs >= minPairs && badPairs <= 1 && goodFrac >= 0.85
        probeComplete = true
    }

    /**
     * Optional sensor→arrival lag in ms.
     * Returns null when untrusted or inputs invalid — never invents a cross-clock subtract.
     */
    fun sourceToArrivalMs(sourceTimestampNs: Long, arrivalElapsedNs: Long): Long? {
        if (!timebaseTrusted) return null
        if (sourceTimestampNs <= 0L || arrivalElapsedNs <= 0L) return null
        return (arrivalElapsedNs - sourceTimestampNs) / 1_000_000L
    }

    fun reset() {
        synchronized(lock) {
            timebaseTrusted = false
            probeComplete = false
            sampleCount = 0
            prevSourceNs = 0L
            prevElapsedNs = 0L
            goodPairs = 0
            badPairs = 0
        }
    }
}
