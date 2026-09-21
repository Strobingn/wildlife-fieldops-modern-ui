package com.strobingn.wildlifefieldops.data.report

/**
 * Offline lat/lng → NY county resolver for Hudson Valley service areas.
 *
 * Bounding boxes are coarse gazetteer extents, not cadastral polygons. A point
 * that falls in more than one box is assigned to the nearest county centroid.
 * Points outside every box return null so callers can keep an honest
 * [CountyRef.UNLOCATED] bucket instead of inventing a county.
 */
object NyCountyGeometry {

    data class CountyBox(
        val key: String,
        val displayName: String,
        val south: Double,
        val north: Double,
        val west: Double,
        val east: Double,
        val centroidLat: Double,
        val centroidLng: Double,
    ) {
        fun contains(lat: Double, lng: Double): Boolean =
            lat in south..north && lng in west..east

        fun distanceSquared(lat: Double, lng: Double): Double {
            val dLat = lat - centroidLat
            val dLng = lng - centroidLng
            return dLat * dLat + dLng * dLng
        }

        fun toRef(): CountyRef = CountyRef(key = key, displayName = displayName, state = "NY")
    }

    /**
     * Hudson Valley / nearby counties Wildlife Whisperer actually services,
     * plus NYC and Long Island so coastal jobs are not dumped into Unlocated.
     */
    val boxes: List<CountyBox> = listOf(
        CountyBox("orange", "Orange County", 41.20, 41.63, -74.77, -73.95, 41.40, -74.32),
        CountyBox("rockland", "Rockland County", 41.03, 41.28, -74.24, -73.88, 41.15, -74.03),
        CountyBox("ulster", "Ulster County", 41.58, 42.19, -74.73, -73.90, 41.93, -74.26),
        CountyBox("dutchess", "Dutchess County", 41.45, 42.08, -73.98, -73.48, 41.76, -73.74),
        CountyBox("putnam", "Putnam County", 41.32, 41.53, -73.95, -73.54, 41.43, -73.75),
        CountyBox("westchester", "Westchester County", 40.88, 41.37, -73.98, -73.48, 41.12, -73.79),
        CountyBox("sullivan", "Sullivan County", 41.44, 41.86, -75.07, -74.37, 41.72, -74.76),
        CountyBox("columbia", "Columbia County", 42.00, 42.51, -73.93, -73.35, 42.25, -73.63),
        CountyBox("greene", "Greene County", 42.15, 42.50, -74.53, -73.85, 42.28, -74.12),
        CountyBox("warren", "Warren County", 43.25, 43.79, -74.14, -73.47, 43.56, -73.84),
        CountyBox("albany", "Albany County", 42.41, 42.79, -74.26, -73.69, 42.60, -73.97),
        CountyBox("nassau", "Nassau County", 40.58, 40.91, -73.76, -73.42, 40.75, -73.56),
        CountyBox("suffolk", "Suffolk County", 40.70, 41.29, -73.50, -71.86, 40.95, -72.70),
        CountyBox("new york city", "New York City", 40.48, 40.92, -74.26, -73.70, 40.71, -74.00),
    )

    fun resolve(latitude: Double, longitude: Double): CountyRef? {
        if (!latitude.isFinite() || !longitude.isFinite()) return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null

        val hits = boxes.filter { it.contains(latitude, longitude) }
        val chosen = when {
            hits.isEmpty() -> return null
            hits.size == 1 -> hits.first()
            else -> hits.minWith(compareBy({ it.distanceSquared(latitude, longitude) }, { it.key }))
        }
        return chosen.toRef()
    }

    fun resolveOrUnlocated(latitude: Double?, longitude: Double?): CountyRef {
        if (latitude == null || longitude == null) return CountyRef.UNLOCATED
        return resolve(latitude, longitude) ?: CountyRef.UNLOCATED
    }
}
