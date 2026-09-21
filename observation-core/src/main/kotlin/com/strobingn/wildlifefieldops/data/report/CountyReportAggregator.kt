package com.strobingn.wildlifefieldops.data.report

import com.strobingn.wildlifefieldops.data.observation.ObservationEvent
import com.strobingn.wildlifefieldops.data.observation.ObservationProjector
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.round

/**
 * Pure county-dashboard projector.
 *
 * Inputs are unordered observation and completion facts. County is derived
 * from capture coordinates (or [CountyRef.UNLOCATED]); species comes from
 * the caller-supplied label (already projected from the immutable event log
 * when events exist). Duplicate [CountyReportObservation.id] / completion
 * ids are dropped. Output is deterministic for a given input set.
 */
object CountyReportAggregator {

    const val AGGREGATOR_VERSION = 1

    /** ~110 m cells when a site has no entity / job key. */
    const val SITE_CELL_DECIMALS = 3

    fun aggregate(
        observations: Collection<CountyReportObservation>,
        completions: Collection<CountyReportCompletion>,
        window: ReportWindow,
        nowMs: Long,
        resolveCounty: (Double?, Double?) -> CountyRef = NyCountyGeometry::resolveOrUnlocated,
    ): CountyDashboard {
        val windowStart = window.durationMs?.let { nowMs - it }

        val obs = observations
            .associateBy { it.id }
            .values
            .filter { inObservationWindow(it.observedAt, windowStart) }
            .sortedBy { it.id }

        val jobs = completions
            .associateBy { it.id }
            .values
            .filter { inCompletionWindow(it, windowStart) }
            .sortedBy { it.id }

        if (obs.isEmpty() && jobs.isEmpty()) {
            return CountyDashboard(
                window = window,
                generatedAt = nowMs,
                observationCount = 0,
                completedJobCount = 0,
                timedCompletionCount = 0,
                unlocatedObservationCount = 0,
                unlabeledObservationCount = 0,
                counties = emptyList(),
            )
        }

        data class LocatedObs(
            val fact: CountyReportObservation,
            val county: CountyRef,
            val species: String?,
        )

        val locatedObs = obs.map { fact ->
            LocatedObs(
                fact = fact,
                county = resolveCounty(fact.latitude, fact.longitude),
                species = fact.speciesLabel?.trim()?.takeIf { it.isNotEmpty() },
            )
        }

        data class LocatedJob(
            val fact: CountyReportCompletion,
            val county: CountyRef,
        )

        val locatedJobs = jobs.map { fact ->
            LocatedJob(fact = fact, county = resolveCounty(fact.latitude, fact.longitude))
        }

        val countyKeys = (locatedObs.map { it.county.key } + locatedJobs.map { it.county.key }).toSet()

        val buckets = countyKeys.map { key ->
            val countyObs = locatedObs.filter { it.county.key == key }.sortedBy { it.fact.id }
            val countyJobs = locatedJobs.filter { it.county.key == key }.sortedBy { it.fact.id }
            val ref = (countyObs.firstOrNull()?.county ?: countyJobs.first().county)

            val speciesBreakdown = countyObs
                .mapNotNull { it.species }
                .groupingBy { normalizeSpecies(it) }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { SpeciesCount(label = it.key, count = it.value) }

            val repeatSites = countyObs
                .groupBy { it.fact.siteKey }
                .map { (siteKey, rows) ->
                    RepeatSite(
                        siteKey = siteKey,
                        observationCount = rows.size,
                        lastObservedAt = rows.maxOf { it.fact.observedAt },
                    )
                }
                .filter { it.observationCount >= 2 }
                .sortedWith(compareByDescending<RepeatSite> { it.observationCount }.thenBy { it.siteKey })

            val timedHours = countyJobs.mapNotNull { responseHours(it.fact) }.sorted()

            CountyBucket(
                county = ref,
                observationCount = countyObs.size,
                unlabeledObservationCount = countyObs.count { it.species == null },
                speciesBreakdown = speciesBreakdown,
                repeatSites = repeatSites,
                completedJobCount = countyJobs.size,
                medianResponseHours = median(timedHours),
                timedCompletionCount = timedHours.size,
                monthlyTrend = monthlyTrend(countyObs.map { it.fact.observedAt }),
            )
        }.sortedWith(
            compareByDescending<CountyBucket> { it.observationCount + it.completedJobCount }
                .thenBy { it.county.displayName }
        )

        return CountyDashboard(
            window = window,
            generatedAt = nowMs,
            observationCount = obs.size,
            completedJobCount = jobs.size,
            timedCompletionCount = jobs.count { responseHours(it) != null },
            unlocatedObservationCount = locatedObs.count { it.county.isUnlocated },
            unlabeledObservationCount = locatedObs.count { it.species == null },
            counties = buckets,
        )
    }

    /**
     * Turns an unordered event set into report facts.
     *
     * Species is the [ObservationProjector] primary label for the event's
     * [ObservationEvent.entityId] — never a last-write-wins job field.
     * Location is joined from [locationsByEntityId]; missing coords become
     * Unlocated rather than guessing.
     */
    fun observationsFromEvents(
        events: Collection<ObservationEvent>,
        locationsByEntityId: Map<String, Pair<Double, Double>>,
        nowMs: Long = System.currentTimeMillis(),
    ): List<CountyReportObservation> {
        val deduped = events.associateBy { it.eventId }.values
        val byEntity = deduped.groupBy { it.entityId }
        val labels = byEntity.mapValues { (entityId, entityEvents) ->
            ObservationProjector.project(entityId, entityEvents, nowMs).primaryLabel
        }
        return deduped
            .sortedBy { it.eventId }
            .map { event ->
                val loc = locationsByEntityId[event.entityId]
                CountyReportObservation(
                    id = event.eventId,
                    observedAt = event.observedAt,
                    latitude = loc?.first,
                    longitude = loc?.second,
                    speciesLabel = labels[event.entityId],
                    siteKey = event.entityId,
                )
            }
    }

    fun siteKeyFromCoordinates(latitude: Double, longitude: Double): String {
        val lat = roundTo(latitude, SITE_CELL_DECIMALS)
        val lng = roundTo(longitude, SITE_CELL_DECIMALS)
        return "geo:$lat,$lng"
    }

    // ── window + math ────────────────────────────────────────────────────────

    private fun inObservationWindow(observedAt: Long, windowStart: Long?): Boolean =
        windowStart == null || observedAt >= windowStart

    /**
     * Completions with a real [CountyReportCompletion.completedAt] follow that
     * clock. Completions missing a completion timestamp are only included in
     * [ReportWindow.ALL] so a time-bounded view never invents when the job closed.
     */
    private fun inCompletionWindow(
        fact: CountyReportCompletion,
        windowStart: Long?,
    ): Boolean {
        val completedAt = fact.completedAt
        return when {
            windowStart == null -> true
            completedAt != null -> completedAt >= windowStart
            else -> false
        }
    }

    private fun responseHours(fact: CountyReportCompletion): Double? {
        val completedAt = fact.completedAt ?: return null
        if (completedAt < fact.openedAt) return null
        return (completedAt - fact.openedAt) / 3_600_000.0
    }

    private fun median(sorted: List<Double>): Double? {
        if (sorted.isEmpty()) return null
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }

    private fun monthlyTrend(timestamps: List<Long>): List<MonthCount> {
        if (timestamps.isEmpty()) return emptyList()
        val utc = TimeZone.getTimeZone("UTC")
        val counts = timestamps
            .map { yearMonthUtc(it, utc) }
            .groupingBy { it }
            .eachCount()
        return counts.entries
            .sortedBy { it.key }
            .map { MonthCount(yearMonth = it.key, observationCount = it.value) }
    }

    private fun yearMonthUtc(epochMs: Long, tz: TimeZone): String {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = epochMs }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        return "%04d-%02d".format(year, month)
    }

    private fun normalizeSpecies(raw: String): String = raw.trim().lowercase()

    private fun roundTo(value: Double, decimals: Int): Double {
        var factor = 1.0
        repeat(decimals) { factor *= 10.0 }
        return round(value * factor) / factor
    }
}
