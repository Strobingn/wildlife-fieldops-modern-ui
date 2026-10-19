package com.strobingn.wildlifefieldops.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Collects high-accuracy fused fixes and raw GPS_PROVIDER fixes for up to 60 seconds.
 * Failures become a message. They are not thrown.
 */
class PreciseGpsSession(context: Context) {
    private val appContext = context.applicationContext
    private val accumulator = PreciseGpsAccumulator()
    private var startedAtElapsed = 0L
    private var started = false
    private var onReadout: ((CaptureReadout) -> Unit)? = null
    private var locationManager: LocationManager? = null
    private var gpsListener: LocationListener? = null
    private var gnssCallback: GnssStatus.Callback? = null
    private var fusedCallback: LocationCallback? = null
    private var failure: String? = null

    fun failureMessage(): String? = failure

    /** Null when capture is running. A string is a message to show instead of crashing. */
    fun start(onReadout: (CaptureReadout) -> Unit): String? {
        this.onReadout = onReadout
        failure = null
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            failure = PreciseGpsMath.PERMISSION_DENIED
            return failure
        }
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null) {
            failure = PreciseGpsMath.GPS_UNAVAILABLE
            return failure
        }
        locationManager = manager
        val locationOn = runCatching { manager.isLocationEnabled }.getOrDefault(false)
        val gpsOn = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        if (!locationOn || !gpsOn) {
            failure = PreciseGpsMath.GPS_OFF
            return failure
        }
        startedAtElapsed = SystemClock.elapsedRealtime()
        started = true
        val gpsOk = requestGps(manager)
        val fusedOk = requestFused()
        requestSatellites(manager)
        if (!gpsOk && !fusedOk) {
            failure = PreciseGpsMath.GPS_UNAVAILABLE
            stop()
            return failure
        }
        emit(accumulator.readout(0L))
        return null
    }

    fun tick(nowElapsedRealtime: Long = SystemClock.elapsedRealtime()): CaptureReadout {
        val elapsed = if (started) (nowElapsedRealtime - startedAtElapsed).coerceAtLeast(0L) else 0L
        val read = accumulator.readout(elapsed)
        emit(read)
        return read
    }

    fun spot(capturedAtMillis: Long): PreciseSpot? = accumulator.spot(capturedAtMillis)

    fun stop() {
        val manager = locationManager
        gpsListener?.let { listener -> runCatching { manager?.removeUpdates(listener) } }
        gnssCallback?.let { callback -> runCatching { manager?.unregisterGnssStatusCallback(callback) } }
        fusedCallback?.let { callback ->
            runCatching {
                LocationServices.getFusedLocationProviderClient(appContext).removeLocationUpdates(callback)
            }
        }
        gpsListener = null
        gnssCallback = null
        fusedCallback = null
        started = false
    }

    @SuppressLint("MissingPermission")
    private fun requestGps(manager: LocationManager): Boolean = try {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                ingest(location)
            }

            override fun onProviderDisabled(provider: String) {
                if (provider == LocationManager.GPS_PROVIDER) {
                    failure = PreciseGpsMath.GPS_OFF
                }
            }
        }
        gpsListener = listener
        manager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            500L,
            0f,
            listener,
            Looper.getMainLooper()
        )
        true
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    @SuppressLint("MissingPermission")
    private fun requestFused(): Boolean = try {
        val client = LocationServices.getFusedLocationProviderClient(appContext)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .setWaitForAccurateLocation(true)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { ingest(it) }
            }
        }
        fusedCallback = callback
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        true
    } catch (_: SecurityException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    @SuppressLint("MissingPermission")
    private fun requestSatellites(manager: LocationManager) {
        try {
            val callback = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    val used = (0 until status.satelliteCount).count { index -> status.usedInFix(index) }
                    val count = if (used > 0) used else status.satelliteCount
                    val elapsed = if (started) {
                        (SystemClock.elapsedRealtime() - startedAtElapsed).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                    emit(accumulator.noteSatellites(count, elapsed))
                }
            }
            gnssCallback = callback
            manager.registerGnssStatusCallback(callback, Handler(Looper.getMainLooper()))
        } catch (_: SecurityException) {
        } catch (_: RuntimeException) {
        }
    }

    private fun ingest(location: Location) {
        try {
            val elapsed = (SystemClock.elapsedRealtime() - startedAtElapsed).coerceAtLeast(0L)
            val extras = location.extras
            val satellites = if (extras != null && extras.containsKey("satellites")) {
                extras.getInt("satellites").takeIf { it >= 0 }
            } else {
                null
            }
            val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else Double.NaN
            emit(
                accumulator.onSample(
                    GnssSample(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = accuracy,
                        elapsedMs = elapsed,
                        satellites = satellites
                    )
                )
            )
        } catch (_: RuntimeException) {
            failure = PreciseGpsMath.GPS_UNAVAILABLE
        }
    }

    private fun emit(readout: CaptureReadout) {
        try {
            onReadout?.invoke(readout)
        } catch (_: RuntimeException) {
        }
    }
}
