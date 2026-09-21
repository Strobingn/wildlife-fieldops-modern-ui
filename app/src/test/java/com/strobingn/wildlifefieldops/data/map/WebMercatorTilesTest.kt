package com.strobingn.wildlifefieldops.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebMercatorTilesTest {

    @Test
    fun tileForKnownHudsonValleyPointIsStable() {
        // Newburgh / Hudson Valley service area, zoom 12
        val tile = WebMercatorTiles.tileFor(41.50, -74.02, 12)
        assertEquals(12, tile.zoom)
        assertEquals(1205, tile.x)
        assertEquals(1528, tile.y)
    }

    @Test
    fun tilesCoveringIncludesCornerAndInterior() {
        val tiles = WebMercatorTiles.tilesCovering(
            south = 41.40,
            west = -74.20,
            north = 41.60,
            east = -73.90,
            zoom = 12
        )
        assertTrue(tiles.isNotEmpty())
        val corners = listOf(
            WebMercatorTiles.tileFor(41.40, -74.20, 12),
            WebMercatorTiles.tileFor(41.60, -73.90, 12),
            WebMercatorTiles.tileFor(41.50, -74.05, 12)
        )
        corners.forEach { corner ->
            assertTrue("$corner missing from ${tiles.size} tiles", corner in tiles)
        }
        tiles.forEach { assertEquals(12, it.zoom) }
    }

    @Test
    fun containsRejectsPointOutsideBbox() {
        assertTrue(WebMercatorTiles.contains(41.0, -75.0, 42.0, -73.0, 41.5, -74.0))
        assertFalse(WebMercatorTiles.contains(41.0, -75.0, 42.0, -73.0, 40.0, -74.0))
        assertFalse(WebMercatorTiles.contains(41.0, -75.0, 42.0, -73.0, 41.5, -72.0))
    }

    @Test
    fun longitudeWrapStaysInRange() {
        assertEquals(0, WebMercatorTiles.longitudeToTileX(180.0, 1))
        assertTrue(WebMercatorTiles.longitudeToTileX(-180.0, 3) in 0 until 8)
    }
}
