package com.strobingn.wildlifefieldops.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundaryCodecTest {

    @Test
    fun roundTripKeepsEveryPoint() {
        val points = listOf(41.5 to -74.25, 41.51 to -74.26, 41.52 to -74.2)
        assertEquals(points, BoundaryCodec.decode(BoundaryCodec.encode(points)))
    }

    @Test
    fun blankOrMissingTextDecodesToNothing() {
        assertTrue(BoundaryCodec.decode(null).isEmpty())
        assertTrue(BoundaryCodec.decode("").isEmpty())
        assertTrue(BoundaryCodec.decode("   ").isEmpty())
    }

    @Test
    fun badPairsAreSkippedNotFatal() {
        val decoded = BoundaryCodec.decode("41.5,-74.2;oops;91.0,10.0;40.0,181.0;41.6,-74.3,9;42.0,-75.0")
        assertEquals(listOf(41.5 to -74.2, 42.0 to -75.0), decoded)
    }

    @Test
    fun minimumPointCountIsAPolygon() {
        assertEquals(3, BoundaryCodec.MIN_POINTS)
    }
}
