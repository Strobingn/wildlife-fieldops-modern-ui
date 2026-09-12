package com.strobingn.wildlifefieldops.ai

import android.content.Context
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kotlin.math.sqrt

object ARMeasurementHelper {
    private const val TAG = "ARMeasurementHelper"

    /**
     * Safe ARCore availability probe. Catches Throwable (not just Exception) because
     * missing native libs / absent Play Services for AR often throw UnsatisfiedLinkError
     * or ExceptionInInitializerError — those used to crash Live Capture on ViewModel init.
     */
    fun isARCoreSupported(context: Context): Boolean {
        return try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            availability != null && availability.isSupported
        } catch (t: Throwable) {
            Log.i(TAG, "ARCore not available: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    fun createARSession(context: Context): Session? {
        return try {
            if (!isARCoreSupported(context)) return null
            val session = Session(context)
            val config = Config(session)
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.focusMode = Config.FocusMode.AUTO
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            session.configure(config)
            session
        } catch (t: Throwable) {
            Log.i(TAG, "AR session create failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    data class MeasurementResult(
        val distanceMeters: Float,
        val confidence: Float,
        val planeType: String = "horizontal",
        val notes: String = "AR measured damage/entry point size"
    ) {
        val inches: Float get() = distanceMeters * 39.3701f
        val feetLabel: String
            get() {
                val inchesTotal = inches
                val ft = (inchesTotal / 12f).toInt()
                val inch = inchesTotal % 12f
                return if (ft > 0) String.format("%d ft %.1f in", ft, inch) else String.format("%.1f in", inch)
            }
    }

    /** Successful plane/depth hit used by the two-tap AR measure UI. */
    data class HitSample(
        val pose: Pose,
        val planeType: String,
        val confidence: Float,
        val hitResult: HitResult
    )

    fun distanceMeters(a: Pose, b: Pose): Float {
        val dx = a.tx() - b.tx()
        val dy = a.ty() - b.ty()
        val dz = a.tz() - b.tz()
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun fromPoses(a: Pose, b: Pose, planeType: String = "plane", confidence: Float = 0.8f): MeasurementResult {
        val d = distanceMeters(a, b)
        return MeasurementResult(
            distanceMeters = d,
            confidence = confidence.coerceIn(0.1f, 1f),
            planeType = planeType,
            notes = "AR two-point entry/damage span: ${MeasurementResult(d, confidence).feetLabel}"
        )
    }

    /**
     * Best trackable hit for a screen-space tap (view pixels matching [Session.setDisplayGeometry]).
     * Prefers [Plane] polygon hits, then any trackable with a pose.
     */
    fun hitTestBest(frame: Frame, xPx: Float, yPx: Float): HitSample? {
        return try {
            if (frame.camera.trackingState != TrackingState.TRACKING) return null
            val hits = frame.hitTest(xPx, yPx)
            var bestPlane: HitSample? = null
            var bestAny: HitSample? = null
            for (hit in hits) {
                val trackable = hit.trackable
                if (trackable.trackingState != TrackingState.TRACKING) continue
                if (trackable is Plane) {
                    if (!trackable.isPoseInPolygon(hit.hitPose)) continue
                    val type = when (trackable.type) {
                        Plane.Type.HORIZONTAL_UPWARD_FACING -> "horizontal"
                        Plane.Type.HORIZONTAL_DOWNWARD_FACING -> "horizontal-ceiling"
                        Plane.Type.VERTICAL -> "vertical"
                        else -> "plane"
                    }
                    val conf = when (trackable.type) {
                        Plane.Type.HORIZONTAL_UPWARD_FACING -> 0.9f
                        Plane.Type.VERTICAL -> 0.85f
                        else -> 0.8f
                    }
                    val sample = HitSample(hit.hitPose, type, conf, hit)
                    if (bestPlane == null) bestPlane = sample
                } else if (bestAny == null) {
                    bestAny = HitSample(hit.hitPose, "trackable", 0.65f, hit)
                }
            }
            bestPlane ?: bestAny
        } catch (t: Throwable) {
            Log.i(TAG, "hitTestBest failed: ${t.javaClass.simpleName}: ${t.message}")
            null
        }
    }

    /** Heuristic span when AR hit-test isn't available yet (bbox coverage proxy). */
    fun suggestEntryInches(subjectCoverage: Float, checklistId: String?): Float {
        val base = when (checklistId) {
            "entry_point" -> 10f
            "overview" -> 36f
            "animal" -> 14f
            else -> 12f
        }
        val scaled = base * (0.7f + subjectCoverage.coerceIn(0f, 0.4f) * 2f)
        return scaled.coerceIn(4f, 48f)
    }
}
