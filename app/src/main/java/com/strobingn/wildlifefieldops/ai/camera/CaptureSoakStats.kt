package com.strobingn.wildlifefieldops.ai.camera

import android.util.Log

/**
 * Rolling soak ring (~[capacity] success+drop traces) for live capture HUD + Log.i summaries.
 */
class CaptureSoakStats(
    private val capacity: Int = 120,
    private val logEvery: Int = 30
) {
    data class Snapshot(
        val n: Int,
        val drops: Int,
        val dropPct: Float,
        val ageP50: Long,
        val ageP95: Long,
        val analP50: Long,
        val analP95: Long,
        val queueP50: Long,
        val queueP95: Long
    )

    private val lock = Any()
    private val ring = ArrayDeque<CaptureFrameTrace>(capacity)
    private var sinceLog = 0

    fun record(trace: CaptureFrameTrace) {
        synchronized(lock) {
            if (ring.size >= capacity) ring.removeFirst()
            ring.addLast(trace)
            sinceLog++
            if (sinceLog >= logEvery) {
                sinceLog = 0
                Log.i(TAG, compactLineLocked())
            }
        }
    }

    fun snapshot(): Snapshot = synchronized(lock) { snapshotLocked() }

    fun compactLine(): String = synchronized(lock) { compactLineLocked() }

    fun reset() {
        synchronized(lock) {
            ring.clear()
            sinceLog = 0
        }
    }

    private fun compactLineLocked(): String {
        val s = snapshotLocked()
        return "soak n=${s.n} drop=${"%.0f".format(s.dropPct)}% " +
            "ageP50/P95=${s.ageP50}/${s.ageP95} " +
            "analP50/P95=${s.analP50}/${s.analP95} " +
            "qP50/P95=${s.queueP50}/${s.queueP95}"
    }

    private fun snapshotLocked(): Snapshot {
        val n = ring.size
        if (n == 0) {
            return Snapshot(0, 0, 0f, -1, -1, -1, -1, -1, -1)
        }
        val drops = ring.count { it.droppedReason != null }
        val dropPct = 100f * drops / n
        val ages = ring.map { it.resultAgeFromArrivalMs }.filter { it >= 0 }
        val anals = ring.map { it.analysisDurationMs }.filter { it >= 0 }
        val queues = ring.map { it.queueWaitBeforeAnalysisMs }.filter { it >= 0 }
        return Snapshot(
            n = n,
            drops = drops,
            dropPct = dropPct,
            ageP50 = percentile(ages, 0.50),
            ageP95 = percentile(ages, 0.95),
            analP50 = percentile(anals, 0.50),
            analP95 = percentile(anals, 0.95),
            queueP50 = percentile(queues, 0.50),
            queueP95 = percentile(queues, 0.95)
        )
    }

    private fun percentile(values: List<Long>, p: Double): Long {
        if (values.isEmpty()) return -1L
        val sorted = values.sorted()
        val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }

    companion object {
        private const val TAG = "CaptureSoak"
    }
}
