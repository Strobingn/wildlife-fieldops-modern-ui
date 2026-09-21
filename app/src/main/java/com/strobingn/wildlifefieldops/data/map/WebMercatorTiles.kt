package com.strobingn.wildlifefieldops.data.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web-Mercator XYZ tile math (EPSG:3857 / OSM slippy-map).
 * Pure Kotlin — no Android, Room, or Maps SDK types — so planners and tests
 * can reason about offline tile coverage without a device.
 */
data class TileCoord(
    val zoom: Int,
    val x: Int,
    val y: Int
) {
    init {
        require(zoom in 0..22) { "zoom $zoom out of range" }
    }
}

object WebMercatorTiles {
    const val TILE_SIZE_PX = 256
    const val MIN_LAT = -85.05112878
    const val MAX_LAT = 85.05112878

    fun clampLatitude(lat: Double): Double = lat.coerceIn(MIN_LAT, MAX_LAT)

    fun clampLongitude(lng: Double): Double {
        if (!lng.isFinite()) return 0.0
        var x = lng
        while (x < -180.0) x += 360.0
        while (x > 180.0) x -= 360.0
        return x
    }

    fun n(zoom: Int): Int = 1 shl zoom.coerceIn(0, 22)

    fun longitudeToTileX(lng: Double, zoom: Int): Int {
        val tiles = n(zoom)
        val x = floor((clampLongitude(lng) + 180.0) / 360.0 * tiles).toInt()
        return x.coerceIn(0, tiles - 1)
    }

    fun latitudeToTileY(lat: Double, zoom: Int): Int {
        val tiles = n(zoom)
        val phi = clampLatitude(lat) * PI / 180.0
        val y = floor((1.0 - ln(tan(phi) + 1.0 / cos(phi)) / PI) / 2.0 * tiles).toInt()
        return y.coerceIn(0, tiles - 1)
    }

    fun tileXToLongitude(x: Int, zoom: Int): Double {
        val tiles = n(zoom).toDouble()
        return x / tiles * 360.0 - 180.0
    }

    fun tileYToLatitude(y: Int, zoom: Int): Double {
        val tiles = n(zoom).toDouble()
        val n = PI - 2.0 * PI * y / tiles
        return atan(sinh(n)) * 180.0 / PI
    }

    fun tileFor(latitude: Double, longitude: Double, zoom: Int): TileCoord =
        TileCoord(zoom, longitudeToTileX(longitude, zoom), latitudeToTileY(latitude, zoom))

    /**
     * Inclusive tile span covering [south, north] × [west, east] at [zoom].
     * Assumes a US-style bbox (west < east); antimeridian-crossing boxes are
     * not expanded across the date line.
     */
    fun tilesCovering(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        zoom: Int
    ): List<TileCoord> {
        val s = clampLatitude(minOf(south, north))
        val nLat = clampLatitude(maxOf(south, north))
        val w = clampLongitude(minOf(west, east))
        val e = clampLongitude(maxOf(west, east))
        val xMin = longitudeToTileX(w, zoom)
        val xMax = longitudeToTileX(e, zoom)
        val yMin = latitudeToTileY(nLat, zoom)
        val yMax = latitudeToTileY(s, zoom)
        val out = ArrayList<TileCoord>((xMax - xMin + 1) * (yMax - yMin + 1))
        for (x in xMin..xMax) {
            for (y in yMin..yMax) {
                out += TileCoord(zoom, x, y)
            }
        }
        return out
    }

    fun contains(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        latitude: Double,
        longitude: Double
    ): Boolean {
        val s = minOf(south, north)
        val n = maxOf(south, north)
        val w = minOf(west, east)
        val e = maxOf(west, east)
        return latitude in s..n && longitude in w..e
    }
}
