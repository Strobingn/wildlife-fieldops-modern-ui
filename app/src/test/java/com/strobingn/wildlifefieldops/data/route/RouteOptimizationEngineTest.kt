package com.strobingn.wildlifefieldops.data.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteOptimizationEngineTest {
    @Test
    fun optimizerKeepsFirstStopAndDoesNotIncreaseOpenRouteDistance() {
        val points = listOf(
            RoutePoint("start", 41.50, -74.20),
            RoutePoint("far", 41.90, -73.20),
            RoutePoint("near", 41.51, -74.21),
            RoutePoint("mid", 41.65, -73.90)
        )
        val optimized = RouteOptimizationEngine.optimize(points)
        assertEquals("start", optimized.first().id)
        assertEquals(points.map { it.id }.toSet(), optimized.map { it.id }.toSet())
        assertTrue(
            RouteOptimizationEngine.totalDistanceMiles(optimized, false) <=
                RouteOptimizationEngine.totalDistanceMiles(points, false)
        )
    }
}
