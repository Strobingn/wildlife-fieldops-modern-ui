package com.strobingn.wildlifefieldops.data.map

import com.google.android.gms.maps.model.Tile
import com.google.android.gms.maps.model.TileProvider

/**
 * Google Maps [TileProvider] that serves PNGs from [MapTileCache].
 * Used as a TileOverlay on the existing GoogleMap — not a second map stack.
 * Maps SDK calls [getTile] off the main thread.
 */
class CachedMapTileProvider(
    private val cache: MapTileCache,
    private val networkAvailable: () -> Boolean
) : TileProvider {

    override fun getTile(x: Int, y: Int, zoom: Int): Tile {
        if (zoom !in 0..22 || x < 0 || y < 0) return TileProvider.NO_TILE
        val coord = TileCoord(zoom, x, y)
        val bytes = cache.getOrFetch(coord, networkAvailable()) ?: return TileProvider.NO_TILE
        return Tile(WebMercatorTiles.TILE_SIZE_PX, WebMercatorTiles.TILE_SIZE_PX, bytes)
    }
}
