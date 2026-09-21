package com.strobingn.wildlifefieldops.data.report

import com.strobingn.wildlifefieldops.data.model.FieldObservation
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.observation.ObservationEvent

/**
 * Maps Room / event-log rows into immutable report facts.
 *
 * Species and observation times come from [ObservationEvent] (when present)
 * or capture-time [FieldObservation] fields — never from mutable [Job.type]
 * or [Job.county], which can drift on a reconnect last-write-wins sync.
 */
object CountyReportInputs {

    private val COMPLETED_STATUSES = setOf(
        JobStatus.COMPLETED,
        JobStatus.INVOICED,
        JobStatus.PAID,
    )

    fun observations(
        fieldObservations: Collection<FieldObservation>,
        events: Collection<ObservationEvent> = emptyList(),
        nowMs: Long = System.currentTimeMillis(),
    ): List<CountyReportObservation> {
        val locations = locationsByEntity(fieldObservations)
        val fromEvents = CountyReportAggregator.observationsFromEvents(events, locations, nowMs)
        val eventIds = fromEvents.map { it.id }.toSet()
        val coveredSites = events.map { it.entityId }.toSet()

        val fromField = fieldObservations
            .asSequence()
            .filter { it.id !in eventIds }
            // A site already represented by the event log should not double-count
            // the same capture via the field-observation row.
            .filter { fieldSiteKey(it) !in coveredSites }
            .map { toObservation(it) }
            .toList()

        return fromEvents + fromField
    }

    fun completions(jobs: Collection<Job>): List<CountyReportCompletion> =
        jobs
            .asSequence()
            .filter { it.status in COMPLETED_STATUSES }
            .map { job ->
                CountyReportCompletion(
                    id = job.id,
                    openedAt = job.createdAt,
                    completedAt = job.completedDate,
                    latitude = job.latitude,
                    longitude = job.longitude,
                )
            }
            .toList()

    private fun toObservation(row: FieldObservation): CountyReportObservation =
        CountyReportObservation(
            id = row.id,
            observedAt = row.observedAt,
            latitude = row.latitude,
            longitude = row.longitude,
            speciesLabel = row.speciesHint.trim().takeIf { it.isNotEmpty() },
            siteKey = fieldSiteKey(row),
        )

    private fun fieldSiteKey(row: FieldObservation): String =
        row.jobId?.takeIf { it.isNotBlank() }
            ?: CountyReportAggregator.siteKeyFromCoordinates(row.latitude, row.longitude)

    private fun locationsByEntity(
        fieldObservations: Collection<FieldObservation>,
    ): Map<String, Pair<Double, Double>> {
        val out = mutableMapOf<String, Pair<Double, Double>>()
        for (row in fieldObservations.sortedBy { it.id }) {
            if (!isMappable(row)) continue
            val loc = row.latitude to row.longitude
            row.jobId?.takeIf { it.isNotBlank() }?.let { out.putIfAbsent(it, loc) }
            out.putIfAbsent(row.id, loc)
        }
        return out
    }

    private fun isMappable(row: FieldObservation): Boolean =
        row.latitude.isFinite() &&
            row.longitude.isFinite() &&
            row.latitude in -90.0..90.0 &&
            row.longitude in -180.0..180.0
}
