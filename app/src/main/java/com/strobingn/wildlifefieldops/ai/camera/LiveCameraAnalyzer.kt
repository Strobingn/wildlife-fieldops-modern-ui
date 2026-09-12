package com.strobingn.wildlifefieldops.ai.camera

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * Live [ImageAnalysis.Analyzer] designed for [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST].
 *
 * Single-flight: while ML Kit is busy, later frames are dropped with [CaptureFrameTrace.droppedReason]
 * so overlays never bind to the wrong frame. Latency uses elapsedRealtime only;
 * [CaptureFrameTrace.sourceTimestampNs] is retained for later timebase validation.
 *
 * Merges on-device [CustomEvidenceModel] TFLite hits with ML Kit + lexicon.
 */
class LiveCameraAnalyzer(
    context: Context,
    private val onGuidance: (CaptureGuidance) -> Unit,
    private val onTrace: ((CaptureFrameTrace) -> Unit)? = null,
    private val maxResultAgeBudgetMs: Long = 250L
) : ImageAnalysis.Analyzer {

    private val appContext = context.applicationContext
    private val busy = AtomicBoolean(false)
    private val frameSeq = AtomicLong(0L)
    private val policy = CaptureGuidancePolicy()
    private val wildlifeHints = setOf(
        "raccoon", "bat", "squirrel", "opossum", "snake", "bird", "rodent",
        "animal", "mammal", "wildlife", "hole", "nest", "cat", "dog",
        "skunk", "groundhog", "coyote", "fox", "beaver", "pigeon", "goose",
        "trap", "cage", "netting", "chimney", "vent", "ladder", "mesh"
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
        try {
            CustomEvidenceModel.close()
        } catch (_: Exception) {
        }
    }

    override fun analyze(image: ImageProxy) {
        val arrival = SystemClock.elapsedRealtimeNanos()
        val frameId = frameSeq.incrementAndGet()
        framesSeen = frameId
        val sourceTs = try {
            image.imageInfo.timestamp
        } catch (_: Throwable) {
            0L
        }

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

        try {
            val media = image.image
            if (media == null) {
                busy.set(false)
                image.close()
                return
            }

            val analysisStart = SystemClock.elapsedRealtimeNanos()
            val lumaSignals = LumaQualityProbe.probe(image)

            // Snapshot a downscaled RGB bitmap for TFLite before ImageProxy is closed.
            val tfliteBitmap = try {
                scaleForModel(image.toBitmap())
            } catch (t: Throwable) {
                Log.d(TAG, "bitmap extract skipped frame=$frameId: ${t.message}")
                null
            }

            val input = try {
                InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
            } catch (t: Throwable) {
                Log.w(TAG, "InputImage build failed frame=$frameId", t)
                recycleQuietly(tfliteBitmap)
                busy.set(false)
                image.close()
                return
            }

            labeler.process(input)
            .addOnSuccessListener { labels ->
                val analysisEnd = SystemClock.elapsedRealtimeNanos()
                val custom = try {
                    CustomEvidenceModel.tryInfer(
                        context = appContext,
                        bitmap = tfliteBitmap,
                        labelHints = labels.map { it.text }
                    )
                } catch (_: Throwable) {
                    emptyList()
                } finally {
                    recycleQuietly(tfliteBitmap)
                }
                val evidence = WildlifeEvidenceDetector.detect(
                    labels = labels,
                    customHits = custom
                )
                val hints = (
                    evidence.species.map { it.label } +
                        evidence.entries.map { it.label } +
                        evidence.damage.map { it.label } +
                        evidence.equipment.map { it.label } +
                        labels.filter { it.confidence >= 0.5f }.map { it.text.lowercase() }
                            .filter { h -> wildlifeHints.any { h.contains(it) } }
                    ).distinct().take(8)
                val coverage = when {
                    evidence.entries.isNotEmpty() || evidence.species.isNotEmpty() -> 0.14f
                    evidence.equipment.isNotEmpty() -> 0.13f
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
                        analysisDurationMs = trace.analysisDurationMs,
                        evidenceSummary = evidence.topSummary,
                        evidenceSpecies = evidence.species.map { it.label },
                        evidenceEntries = evidence.entries.map { it.label },
                        evidenceEquipment = evidence.equipment.map { it.label },
                        evidenceDamage = evidence.damage.map { it.label }
                    )
                )
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "live analyze failed frame=$frameId", e)
                recycleQuietly(tfliteBitmap)
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
        } catch (t: Throwable) {
            Log.w(TAG, "analyze crashed frame=$frameId", t)
            lastDropReason = "ANALYZE_THROW"
            framesDropped++
            busy.set(false)
            try {
                image.close()
            } catch (_: Throwable) {
            }
            try {
                val now = SystemClock.elapsedRealtimeNanos()
                onGuidance(
                    CaptureGuidance(
                        action = CaptureGuidanceAction.WAIT,
                        reasonCode = "ANALYZE_THROW",
                        userMessage = "Analyzer recovering…",
                        signals = CaptureQualitySignals(0f, 0f, 0f),
                        frameId = frameId,
                        resultAgeFromArrivalMs = (now - arrival) / 1_000_000L,
                        analysisDurationMs = -1L
                    )
                )
            } catch (_: Throwable) {
            }
        }
    }

    private fun scaleForModel(src: Bitmap, maxSide: Int = 320): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxSide) return src
        val scale = maxSide.toFloat() / longest.toFloat()
        val w = (src.width * scale).toInt().coerceAtLeast(1)
        val h = (src.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, w, h, true)
        if (scaled !== src) {
            try { src.recycle() } catch (_: Throwable) {}
        }
        return scaled
    }

    private fun recycleQuietly(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        try {
            bitmap.recycle()
        } catch (_: Throwable) {
        }
    }

    companion object {
        private const val TAG = "LiveCameraAnalyzer"
    }
}
