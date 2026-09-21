package com.strobingn.wildlifefieldops.data.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTileCachePlannerTest {

    @Test
    fun planRespectsTileBudget() {
        val plan = MapTileCachePlanner.plan(
            south = 41.30,
            west = -74.40,
            north = 41.70,
            east = -73.70,
            centerZoom = 14,
            maxTiles = 40
        )
        assertTrue(plan.tiles.size <= 40)
        assertTrue(plan.tiles.isNotEmpty())
        assertTrue(plan.truncated)
        assertTrue(plan.tiles.any { it.zoom == 14 })
    }

    @Test
    fun planPrefersCenterZoomFirst() {
        val plan = MapTileCachePlanner.plan(
            south = 41.49,
            west = -74.03,
            north = 41.51,
            east = -74.01,
            centerZoom = 13,
            maxTiles = 8
        )
        assertEquals(13, plan.tiles.first().zoom)
    }

    @Test
    fun zoomLadderRadiatesFromCenter() {
        assertEquals(listOf(12, 13, 11, 14, 10, 15, 9, 16, 8), MapTileCachePlanner.zoomLadder(12))
    }

    @Test
    fun smallViewportFitsWithoutTruncation() {
        val plan = MapTileCachePlanner.plan(
            south = 41.499,
            west = -74.021,
            north = 41.501,
            east = -74.019,
            centerZoom = 15,
            maxTiles = 200
        )
        assertFalse(plan.truncated)
        assertTrue(plan.tiles.size < 200)
        assertTrue(plan.minZoom <= 15)
        assertTrue(plan.maxZoom >= 15)
    }
}
