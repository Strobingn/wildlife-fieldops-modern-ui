package com.strobingn.wildlifefieldops.data.route

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class RoutePoint(
    val id: String,
    val latitude: Double,
    val longitude: Double
)

object RouteOptimizationEngine {
    fun optimize(points: List<RoutePoint>): List<RoutePoint> {
        if (points.size < 3) return points
        val remaining = points.drop(1).toMutableList()
        val route = mutableListOf(points.first())

        while (remaining.isNotEmpty()) {
            val last = route.last()
            val next = remaining.minByOrNull { distanceMiles(last, it) } ?: break
            route += next
            remaining.remove(next)
        }

        return twoOpt(route)
    }

    fun totalDistanceMiles(points: List<RoutePoint>, returnToStart: Boolean): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (index in 0 until points.lastIndex) {
            total += distanceMiles(points[index], points[index + 1])
        }
        if (returnToStart) total += distanceMiles(points.last(), points.first())
        return total
    }

    private fun twoOpt(route: List<RoutePoint>): List<RoutePoint> {
        var best = route
        var improved = true
        while (improved) {
            improved = false
            for (i in 1 until best.size - 2) {
                for (k in i + 1 until best.size - 1) {
                    val candidate = best.toMutableList()
                    candidate.subList(i, k + 1).reverse()
                    if (totalDistanceMiles(candidate, false) + 0.0001 < totalDistanceMiles(best, false)) {
                        best = candidate
                        improved = true
                    }
                }
            }
        }
        return best
    }

    private fun distanceMiles(a: RoutePoint, b: RoutePoint): Double {
        val earthRadiusMiles = 3958.7613
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val haversine = sin(dLat / 2).pow(2) +
            cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return earthRadiusMiles * 2.0 * atan2(sqrt(haversine), sqrt(1.0 - haversine))
    }
}
