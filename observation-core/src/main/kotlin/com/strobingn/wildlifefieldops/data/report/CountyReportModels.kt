package com.strobingn.wildlifefieldops.data.report

/**
 * Immutable observation fact used for county dashboards.
 *
 * Built from the append-only [com.strobingn.wildlifefieldops.data.observation.ObservationEvent]
 * log (preferred) or from a capture-time field observation. Species and timestamps
 * are sealed at construction — callers must not pass mutable job fields that can
 * drift on reconnect (job.type, job.county, inspection.speciesIdentified).
 */
data class CountyReportObservation(
    /** Event id or field-observation id. Deduplicated by this key. */
    val id: String,
    /** Device capture clock (epoch-ms). Not upload time. */
    val observedAt: Long,
    val latitude: Double?,
    val longitude: Double?,
    /**
     * Species / label already projected from evidence (ObservationProjector
     * primaryLabel, or a capture-time species hint). Null when unknown —
     * dashboards must not invent a label.
     */
    val speciesLabel: String?,
    /**
     * Stable site key: entityId, linked jobId, or a rounded coordinate cell.
     * Repeat-site counts group on this key.
     */
    val siteKey: String,
)

/**
 * Completed-job fact for operational KPIs (volume + response time).
 *
 * County is resolved from capture-time coordinates, never from the mutable
 * [county] column cached on the job row. [completedAt] is the completion
 * clock when known; null means the job is marked complete but no completion
 * timestamp was recorded — it is counted only in the ALL window and never
 * contributes a response time.
 */
data class CountyReportCompletion(
    val id: String,
    val openedAt: Long,
    val completedAt: Long?,
    val latitude: Double?,
    val longitude: Double?,
)

enum class ReportWindow(val label: String, val durationMs: Long?) {
    LAST_7_DAYS("7 days", 7L * 24 * 60 * 60 * 1_000L),
    LAST_30_DAYS("30 days", 30L * 24 * 60 * 60 * 1_000L),
    LAST_90_DAYS("90 days", 90L * 24 * 60 * 60 * 1_000L),
    LAST_12_MONTHS("12 months", 365L * 24 * 60 * 60 * 1_000L),
    ALL("All time", null),
}

data class CountyRef(
    /** Normalized key, e.g. "orange". [UNLOCATED_KEY] when GPS is missing or outside known counties. */
    val key: String,
    val displayName: String,
    val state: String,
) {
    val isUnlocated: Boolean get() = key == UNLOCATED_KEY

    companion object {
        const val UNLOCATED_KEY = "unlocated"
        val UNLOCATED: CountyRef = CountyRef(UNLOCATED_KEY, "Unlocated", "")
    }
}

data class SpeciesCount(
    val label: String,
    val count: Int,
)

data class RepeatSite(
    val siteKey: String,
    val observationCount: Int,
    val lastObservedAt: Long,
)

data class MonthCount(
    /** Calendar year-month, e.g. "2026-03". */
    val yearMonth: String,
    val observationCount: Int,
)

data class CountyBucket(
    val county: CountyRef,
    val observationCount: Int,
    val unlabeledObservationCount: Int,
    val speciesBreakdown: List<SpeciesCount>,
    val repeatSites: List<RepeatSite>,
    val completedJobCount: Int,
    /** Median hours from openedAt → completedAt. Null when fewer than one timed completion. */
    val medianResponseHours: Double?,
    val timedCompletionCount: Int,
    val monthlyTrend: List<MonthCount>,
)

data class CountyDashboard(
    val window: ReportWindow,
    val generatedAt: Long,
    val observationCount: Int,
    val completedJobCount: Int,
    val timedCompletionCount: Int,
    val unlocatedObservationCount: Int,
    val unlabeledObservationCount: Int,
    val counties: List<CountyBucket>,
) {
    val isEmpty: Boolean get() = observationCount == 0 && completedJobCount == 0
}
