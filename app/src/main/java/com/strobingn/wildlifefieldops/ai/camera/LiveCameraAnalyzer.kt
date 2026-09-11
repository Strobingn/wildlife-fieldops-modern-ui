package com.strobingn.wildlifefieldops.ai.camera

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Live [ImageAnalysis.Analyzer] designed for [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST].
 *
 * Single-flight: while ML Kit is busy, later frames are dropped with [CaptureFrameTrace.droppedReason]
 * so overlays never bind to the wrong frame. Latency uses elapsedRealtime only;
 * [CaptureFrameTrace.sourceTimestampNs] is retained for later timebase validation.
 */
class LiveCameraAnalyzer(
    private val onGuidance: (CaptureGuidance) -> Unit,
    private val onTrace: ((CaptureFrameTrace) -> Unit)? = null,
    private val maxResultAgeBudgetMs: Long = 250L
) : ImageAnalysis.Analyzer {

    private val busy = AtomicBoolean(false)
    private val frameSeq = AtomicLong(0L)
    private val policy = CaptureGuidancePolicy()
    private val wildlifeHints = setOf(
        "raccoon", "bat", "squirrel", "opossum", "snake", "bird", "rodent",
        "animal", "mammal", "wildlife", "hole", "nest", "cat", "dog"
    )

    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    @Volatile var lastResultAgeMs: Long = -1L
        private set
    @Volatile var lastDropReason: String? = null
        private set
    @Volatile var framesSeen: Long = 0L
        private set
    @Volatile var framesDropped: Long = 0L
        private set

    fun reset() {
        policy.reset()
        framesSeen = 0L
        framesDropped = 0L
        lastDropReason = null
        lastResultAgeMs = -1L
    }

    fun close() {
        try {
            labeler.close()
        } catch (_: Exception) {
        }
    }

    override fun analyze(image: ImageProxy) {
        val arrival = SystemClock.elapsedRealtimeNanos()
        val frameId = frameSeq.incrementAndGet()
        framesSeen = frameId
        val sourceTs = image.imageInfo.timestamp

        if (!busy.compareAndSet(false, true)) {
            framesDropped++
            lastDropReason = "ANALYZER_BUSY"
            onTrace?.invoke(
                CaptureFrameTrace(
                    frameId = frameId,
                    sourceTimestampNs = sourceTs,
                    analyzerArrivalElapsedNs = arrival,
                    droppedReason = "ANALYZER_BUSY"
                )
            )
            image.close()
            return
        }

        val media = image.image
        if (media == null) {
            busy.set(false)
            image.close()
            return
        }

        val analysisStart = SystemClock.elapsedRealtimeNanos()
        val lumaSignals = LumaQualityProbe.probe(image)
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)

        labeler.process(input)
            .addOnSuccessListener { labels ->
                val analysisEnd = SystemClock.elapsedRealtimeNanos()
                val hints = labels
                    .filter { it.confidence >= 0.5f }
                    .map { it.text.lowercase() }
                    .filter { h -> wildlifeHints.any { h.contains(it) } }
                    .distinct()
                    .take(4)
                // Coverage proxy from label confidence until a dedicated detector bbox path is added
                val coverage = when {
                    hints.isNotEmpty() -> 0.12f
                    labels.any { it.confidence >= 0.65f } -> 0.06f
                    else -> 0f
                }
                val signals = lumaSignals.copy(
                    subjectCoverage = coverage,
                    labelHints = hints
                )
                val nowMs = SystemClock.elapsedRealtime()
                val (action, reason) = policy.evaluate(signals, nowMs)
                val committed = SystemClock.elapsedRealtimeNanos()
                val trace = CaptureFrameTrace(
                    frameId = frameId,
                    sourceTimestampNs = sourceTs,
                    analyzerArrivalElapsedNs = arrival,
                    analysisStartElapsedNs = analysisStart,
                    analysisEndElapsedNs = analysisEnd,
                    resultCommittedElapsedNs = committed
                )
                lastResultAgeMs = trace.resultAgeFromArrivalMs
                lastDropReason = null
                onTrace?.invoke(trace)
                if (trace.resultAgeFromArrivalMs > maxResultAgeBudgetMs) {
                    Log.w(
                        TAG,
                        "frame=$frameId resultAge=${trace.resultAgeFromArrivalMs}ms exceeds ${maxResultAgeBudgetMs}ms budget"
                    )
                }
                onGuidance(
                    CaptureGuidance(
                        action = action,
                        reasonCode = reason,
                        userMessage = policy.userMessage(action),
                        signals = signals,
                        frameId = frameId,
                        resultAgeFromArrivalMs = trace.resultAgeFromArrivalMs,
                        analysisDurationMs = trace.analysisDurationMs
                    )
                )
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "live analyze failed frame=$frameId", e)
                // Still coach from luma so the HUD stays useful offline
                val nowMs = SystemClock.elapsedRealtime()
                val (action, reason) = policy.evaluate(lumaSignals, nowMs)
                val committed = SystemClock.elapsedRealtimeNanos()
                onTrace?.invoke(
                    CaptureFrameTrace(
                        frameId = frameId,
                        sourceTimestampNs = sourceTs,
                        analyzerArrivalElapsedNs = arrival,
                        analysisStartElapsedNs = analysisStart,
                        analysisEndElapsedNs = committed,
                        resultCommittedElapsedNs = committed,
                        droppedReason = "ML_FAILURE"
                    )
                )
                onGuidance(
                    CaptureGuidance(
                        action = action,
                        reasonCode = reason,
                        userMessage = policy.userMessage(action),
                        signals = lumaSignals,
                        frameId = frameId,
                        resultAgeFromArrivalMs = (committed - arrival) / 1_000_000L,
                        analysisDurationMs = (committed - analysisStart) / 1_000_000L
                    )
                )
            }
            .addOnCompleteListener {
                busy.set(false)
                image.close()
            }
    }

    companion object {
        private const val TAG = "LiveCameraAnalyzer"
    }
}
