package com.strobingn.wildlifefieldops.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.strobingn.wildlifefieldops.data.model.JobStatus

internal fun createStatusMarkerIcon(status: JobStatus): BitmapDescriptor {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = markerArgb(status) }
    canvas.drawCircle(cx, cy, size * 0.40f, ring)
    canvas.drawCircle(cx, cy, size * 0.30f, fill)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

internal fun createMonochromeMarkerIcon(status: JobStatus): BitmapDescriptor =
    createStatusMarkerIcon(status)

internal fun createObservationMarkerIcon(synced: Boolean): BitmapDescriptor {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (synced) {
            android.graphics.Color.rgb(55, 71, 79)
        } else {
            android.graphics.Color.rgb(255, 160, 0)
        }
    }
    canvas.drawCircle(cx, cy, size * 0.40f, ring)
    canvas.drawCircle(cx, cy, size * 0.28f, fill)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

internal fun createTrapMarkerIcon(overdue: Boolean): BitmapDescriptor {
    val size = 56
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cx = size / 2f
    val cy = size / 2f
    val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (overdue) {
            android.graphics.Color.rgb(120, 120, 120)
        } else {
            android.graphics.Color.rgb(84, 84, 84)
        }
    }
    canvas.drawCircle(cx, cy, size * 0.40f, ring)
    canvas.drawRect(cx - size * 0.18f, cy - size * 0.18f, cx + size * 0.18f, cy + size * 0.18f, fill)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}
