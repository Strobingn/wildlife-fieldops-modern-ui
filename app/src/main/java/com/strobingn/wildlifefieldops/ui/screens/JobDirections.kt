package com.strobingn.wildlifefieldops.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.strobingn.wildlifefieldops.data.model.Job
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Turn-by-turn to a job. Coordinates win when both are saved.
 * Otherwise the full address is the destination.
 */
object JobDirections {
    const val MAPS_PACKAGE = "com.google.android.apps.maps"
    const val MISSING_HINT = "Add an address for directions"

    data class Target(
        val latitude: Double? = null,
        val longitude: Double? = null,
        val address: String = ""
    )

    fun fromJob(job: Job, fullAddress: String = ""): Target {
        val address = fullAddress.trim().ifBlank { withState(job.address, job.state) }
        return Target(
            latitude = job.latitude,
            longitude = job.longitude,
            address = address
        )
    }

    fun hasDestination(target: Target): Boolean = query(target) != null

    /** Value placed after `google.navigation:q=`, before encoding an address. */
    fun query(target: Target): String? {
        val lat = target.latitude
        val lng = target.longitude
        if (lat != null && lng != null) return "$lat,$lng"
        val address = target.address.trim()
        return address.ifBlank { null }
    }

    fun navigationUri(target: Target): String? {
        val raw = query(target) ?: return null
        val q = if (target.latitude != null && target.longitude != null) raw else encode(raw)
        return "google.navigation:q=$q"
    }

    fun browserUri(target: Target): String? {
        val raw = query(target) ?: return null
        return "https://www.google.com/maps/dir/?api=1&destination=${encode(raw)}"
    }

    fun open(context: Context, target: Target) {
        val navigation = navigationUri(target) ?: return
        val maps = Intent(Intent.ACTION_VIEW, Uri.parse(navigation)).apply {
            setPackage(MAPS_PACKAGE)
        }
        if (start(context, maps)) return
        val browser = browserUri(target) ?: return
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse(browser)))
    }

    fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun withState(address: String, state: String?): String {
        val street = address.trim()
        val region = state?.trim().orEmpty()
        if (street.isBlank()) return region
        if (region.isBlank() || street.contains(region, ignoreCase = true)) return street
        return "$street, $region"
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
