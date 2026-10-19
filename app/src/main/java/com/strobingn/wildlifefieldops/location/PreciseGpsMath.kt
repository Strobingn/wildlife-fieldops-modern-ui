package com.strobingn.wildlifefieldops.location

import com.strobingn.wildlifefieldops.ai.fieldops.FieldDate
import com.strobingn.wildlifefieldops.data.repository.DataVersionGate
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One GNSS fix. [elapsedMs] is milliseconds since the capture session started.
 * Accuracy is the reported 1-sigma radius in meters.
 */
data class GnssSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val elapsedMs: Long,
    val satellites: Int? = null
)

/** Accuracy-weighted mean of the accepted fixes. */
data class AveragedFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val sampleCount: Int
)

/** What gets stored on the trap: coordinates plus the quality of the average. */
data class PreciseSpot(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double,
    val sampleCount: Int,
    val capturedAtMillis: Long
)

/** Live numbers for the capture sheet. */
data class CaptureReadout(
    val elapsedMs: Long,
    val averaged: AveragedFix?,
    val latestAccuracyMeters: Double?,
    val satellites: Int?,
    val acceptedCount: Int,
    val rejectedCount: Int,
    val autoFinish: Boolean,
    val progress: Float
)

/**
 * Precise trap-spot math. Fixes worse than [MAX_ACCEPT_M] are dropped.
 * The mean uses weights 1/accuracy². Its accuracy estimate is
 * 1 / sqrt(sum of weights), which is the 1-sigma of an inverse-variance mean.
 * Capture stops at [SESSION_MS], or earlier when that estimate is below
 * [TARGET_ACCURACY_M] and has not improved for [STALE_MS].
 */
object PreciseGpsMath {
    const val MAX_ACCEPT_M = 15.0
    const val TARGET_ACCURACY_M = 3.0
    const val SESSION_MS = 60_000L
    const val STALE_MS = 10_000L

    const val GPS_OFF = "GPS is off. Turn on location, then try Precise GPS again."
    const val GPS_UNAVAILABLE = "Couldn't start GPS on this phone. You can type the coordinates."
    const val PERMISSION_DENIED =
        "Precise location was not allowed. You can type the coordinates, or enable location for FieldOps in settings."
    const val RATIONALE =
        "Precise GPS marks this trap from the phone's location. FieldOps needs precise location while it collects fixes, for up to 60 seconds."
    const val NO_FIX = "No fix within ±15 m yet."
    const val NO_FIX_TIMEOUT =
        "No fix within ±15 m after 60 seconds. Move into open sky, or type the coordinates."
    const val SAVE_FAILED = "Couldn't save the trap spot. It's still on this phone — try again."

    fun accepts(sample: GnssSample): Boolean = accepts(
        sample.latitude,
        sample.longitude,
        sample.accuracyMeters
    )

    fun accepts(latitude: Double, longitude: Double, accuracyMeters: Double): Boolean {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) return false
        if (!longitude.isFinite() || longitude !in -180.0..180.0) return false
        if (!accuracyMeters.isFinite() || accuracyMeters <= 0.0) return false
        return accuracyMeters <= MAX_ACCEPT_M
    }

    /**
     * Inverse-variance mean. Returns null when no sample is acceptable.
     * Accuracy of the mean is 1/sqrt(Σ 1/acc²).
     */
    fun weightedAverage(samples: List<GnssSample>): AveragedFix? {
        var weightSum = 0.0
        var latSum = 0.0
        var lngSum = 0.0
        var count = 0
        for (sample in samples) {
            if (!accepts(sample)) continue
            val weight = 1.0 / (sample.accuracyMeters * sample.accuracyMeters)
            if (!weight.isFinite() || weight <= 0.0) continue
            weightSum += weight
            latSum += sample.latitude * weight
            lngSum += sample.longitude * weight
            count += 1
        }
        if (count == 0 || weightSum <= 0.0 || !weightSum.isFinite()) return null
        val accuracy = 1.0 / sqrt(weightSum)
        if (!accuracy.isFinite()) return null
        return AveragedFix(
            latitude = latSum / weightSum,
            longitude = lngSum / weightSum,
            accuracyMeters = accuracy,
            sampleCount = count
        )
    }

    /**
     * True when the sheet should close on its own.
     * [averagedAccuracyM] is the current weighted estimate, null when no fix has been kept.
     * [msSinceBestImprovement] is how long that estimate has gone without getting tighter.
     */
    fun shouldAutoFinish(
        elapsedMs: Long,
        averagedAccuracyM: Double?,
        msSinceBestImprovement: Long,
        sessionMs: Long = SESSION_MS,
        staleMs: Long = STALE_MS,
        targetAccuracyM: Double = TARGET_ACCURACY_M
    ): Boolean {
        if (elapsedMs >= sessionMs) return true
        if (averagedAccuracyM == null || !averagedAccuracyM.isFinite()) return false
        return averagedAccuracyM < targetAccuracyM && msSinceBestImprovement >= staleMs
    }

    fun progress(elapsedMs: Long, sessionMs: Long = SESSION_MS): Float {
        if (sessionMs <= 0L) return 1f
        return (elapsedMs.toFloat() / sessionMs.toFloat()).coerceIn(0f, 1f)
    }

    fun formatMeters(meters: Double): String {
        if (!meters.isFinite() || meters < 0.0) return "± — m"
        val number = if (meters >= 10.0) {
            meters.toInt().let { whole ->
                if (meters - whole >= 0.5) (whole + 1).toString() else whole.toString()
            }
        } else {
            String.format(Locale.US, "%.1f", meters)
        }
        return "± $number m"
    }

    fun formatCoordinate(value: Double): String = String.format(Locale.US, "%.7f", value)

    fun formatCapturedDay(millis: Long): String = FieldDate.formatDay(millis)

    fun accuracyLabel(averaged: AveragedFix?, latestAccuracyMeters: Double?): String {
        if (averaged != null) return formatMeters(averaged.accuracyMeters)
        if (latestAccuracyMeters != null && latestAccuracyMeters.isFinite()) {
            return "Latest ${formatMeters(latestAccuracyMeters)}"
        }
        return "Waiting for a fix within ±15 m"
    }

    fun sampleLabel(count: Int): String = if (count == 1) "1 sample" else "$count samples"

    fun satelliteLabel(count: Int?): String = when (count) {
        null -> "Satellites unavailable"
        1 -> "1 satellite"
        else -> "$count satellites"
    }

    /** Hand edits drop the ± claim. The typed numbers are what gets saved. */
    fun retainedSpot(latitudeText: String, longitudeText: String, spot: PreciseSpot?): PreciseSpot? {
        if (spot == null) return null
        if (!matchesTyped(latitudeText, spot.latitude)) return null
        if (!matchesTyped(longitudeText, spot.longitude)) return null
        return spot
    }

    fun matchesTyped(text: String, captured: Double): Boolean {
        val parsed = text.trim().toDoubleOrNull() ?: return false
        return kotlin.math.abs(parsed - captured) <= 1e-6
    }

    /**
     * Older builds keep uploading jobs that stay at data version 2.
     * A precise spot raises the job to [DataVersionGate.TRAP_GPS] so those builds
     * stop uploading and cannot strip the new trap fields.
     */
    fun stampedDataVersion(current: Int, hasPreciseGps: Boolean): Int {
        if (!hasPreciseGps) return current
        return maxOf(current, DataVersionGate.TRAP_GPS)
    }

    fun shouldPublishToJob(
        previousCapturedAt: Long?,
        previousLat: Double?,
        previousLng: Double?,
        saved: PreciseSpot?
    ): Boolean {
        if (saved == null) return false
        if (!saved.latitude.isFinite() || !saved.longitude.isFinite()) return false
        return previousCapturedAt != saved.capturedAtMillis ||
            previousLat != saved.latitude ||
            previousLng != saved.longitude
    }
}

/**
 * Collects fixes for one capture. Call [onSample] as fixes arrive and [readout]
 * on a timer so the 10-second and 60-second stops are checked between fixes.
 */
class PreciseGpsAccumulator {
    private val accepted = mutableListOf<GnssSample>()
    private var bestAccuracy = Double.POSITIVE_INFINITY
    private var lastImprovementAt = 0L
    private var latestAccuracy: Double? = null
    private var satellites: Int? = null
    private var rejected = 0

    fun onSample(sample: GnssSample): CaptureReadout {
        if (sample.satellites != null) satellites = sample.satellites
        val reported = sample.accuracyMeters.takeIf { it.isFinite() && it > 0.0 }
        if (reported != null) latestAccuracy = reported
        if (PreciseGpsMath.accepts(sample)) {
            accepted += sample
            val averaged = PreciseGpsMath.weightedAverage(accepted)
            if (averaged != null && averaged.accuracyMeters < bestAccuracy) {
                bestAccuracy = averaged.accuracyMeters
                lastImprovementAt = sample.elapsedMs
            }
        } else {
            rejected += 1
        }
        return readout(sample.elapsedMs)
    }

    fun noteSatellites(count: Int, elapsedMs: Long): CaptureReadout {
        if (count >= 0) satellites = count
        return readout(elapsedMs)
    }

    fun readout(elapsedMs: Long): CaptureReadout {
        val averaged = PreciseGpsMath.weightedAverage(accepted)
        val sinceBest = if (averaged == null) 0L else (elapsedMs - lastImprovementAt).coerceAtLeast(0L)
        return CaptureReadout(
            elapsedMs = elapsedMs,
            averaged = averaged,
            latestAccuracyMeters = latestAccuracy,
            satellites = satellites,
            acceptedCount = accepted.size,
            rejectedCount = rejected,
            autoFinish = PreciseGpsMath.shouldAutoFinish(
                elapsedMs = elapsedMs,
                averagedAccuracyM = averaged?.accuracyMeters,
                msSinceBestImprovement = sinceBest
            ),
            progress = PreciseGpsMath.progress(elapsedMs)
        )
    }

    fun spot(capturedAtMillis: Long): PreciseSpot? {
        val averaged = PreciseGpsMath.weightedAverage(accepted) ?: return null
        return PreciseSpot(
            latitude = averaged.latitude,
            longitude = averaged.longitude,
            accuracyMeters = averaged.accuracyMeters,
            sampleCount = averaged.sampleCount,
            capturedAtMillis = capturedAtMillis
        )
    }
}
