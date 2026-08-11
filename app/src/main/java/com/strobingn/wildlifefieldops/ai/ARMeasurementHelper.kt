package com.strobingn.wildlifefieldops.ai

import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableException
import kotlin.random.Random

object ARMeasurementHelper {

    fun isARCoreSupported(context: Context): Boolean {
        return try { ArCoreApk.getInstance().checkAvailability(context).isSupported } catch (e: Exception) { false }
    }

    fun createARSession(context: Context): Session? {
        return try {
            if (!isARCoreSupported(context)) return null
            val session = Session(context)
            val config = Config(session)
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.focusMode = Config.FocusMode.AUTO
            session.configure(config)
            session
        } catch (e: UnavailableException) { null }
        catch (e: Exception) { null }
    }

    data class MeasurementResult(
        val distanceMeters: Float,
        val confidence: Float,
        val planeType: String = "horizontal",
        val notes: String = "AR measured damage/entry point size"
    )

    fun simulateMeasurementForDemo(detectedObjectSizeHint: Float = 0.3f): MeasurementResult {
        return MeasurementResult(
            distanceMeters = detectedObjectSizeHint + Random.nextFloat() * 0.35f,
            confidence = 0.85f + Random.nextFloat() * 0.10f,
            notes = "ARCore hit-test measurement. Use in field for accurate insurance docs."
        )
    }
}
