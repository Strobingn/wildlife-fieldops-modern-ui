package com.strobingn.wildlifefieldops.data.map

/**
 * Chooses a bounded set of XYZ tiles for a service-area snapshot.
 * Prefers the current camera zoom, then fills neighboring zooms until
 * [maxTiles] is reached so a tech can pinch slightly without going blank.
 */
data class PlannedTileCache(
    val tiles: List<TileCoord>,
    val minZoom: Int,
    val maxZoom: Int,
    val truncated: Boolean
)

object MapTileCachePlanner {
    const val DEFAULT_MAX_TILES = 350
    const val ABSOLUTE_MIN_ZOOM = 8
    const val ABSOLUTE_MAX_ZOOM = 16

    fun plan(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        centerZoom: Int,
        maxTiles: Int = DEFAULT_MAX_TILES
    ): PlannedTileCache {
        val budget = maxTiles.coerceAtLeast(1)
        val center = centerZoom.coerceIn(ABSOLUTE_MIN_ZOOM, ABSOLUTE_MAX_ZOOM)
        val zoomOrder = zoomLadder(center)
        val selected = ArrayList<TileCoord>(budget)
        var minZ = center
        var maxZ = center
        var truncated = false

        for (z in zoomOrder) {
            val layer = WebMercatorTiles.tilesCovering(south, west, north, east, z)
            if (selected.size + layer.size > budget) {
                val remaining = budget - selected.size
                if (remaining > 0) {
                    selected += layer.take(remaining)
                    minZ = minOf(minZ, z)
                    maxZ = maxOf(maxZ, z)
                }
                truncated = true
                break
            }
            selected += layer
            minZ = minOf(minZ, z)
            maxZ = maxOf(maxZ, z)
        }

        return PlannedTileCache(
            tiles = selected,
            minZoom = if (selected.isEmpty()) center else minZ,
            maxZoom = if (selected.isEmpty()) center else maxZ,
            truncated = truncated
        )
    }

    /**
     * Center zoom first, then +1 (more detail), then -1 (context), then +2, -2…
     */
    fun zoomLadder(centerZoom: Int): List<Int> {
        val center = centerZoom.coerceIn(ABSOLUTE_MIN_ZOOM, ABSOLUTE_MAX_ZOOM)
        val out = ArrayList<Int>(ABSOLUTE_MAX_ZOOM - ABSOLUTE_MIN_ZOOM + 1)
        out += center
        var delta = 1
        while (true) {
            val up = center + delta
            val down = center - delta
            val added = (up in ABSOLUTE_MIN_ZOOM..ABSOLUTE_MAX_ZOOM) ||
                (down in ABSOLUTE_MIN_ZOOM..ABSOLUTE_MAX_ZOOM)
            if (!added) break
            if (up in ABSOLUTE_MIN_ZOOM..ABSOLUTE_MAX_ZOOM) out += up
            if (down in ABSOLUTE_MIN_ZOOM..ABSOLUTE_MAX_ZOOM) out += down
            delta++
        }
        return out
    }
}
