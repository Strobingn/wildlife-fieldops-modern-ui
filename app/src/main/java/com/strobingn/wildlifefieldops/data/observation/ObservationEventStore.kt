package com.strobingn.wildlifefieldops.data.observation

import com.strobingn.wildlifefieldops.ai.species.SpeciesRecognition
import com.strobingn.wildlifefieldops.data.local.FieldObservationDao
import com.strobingn.wildlifefieldops.data.local.ObservationEventDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Append-only Room store for species-ID [ObservationEvent]s.
 * Derived assessments are computed, never written as source-of-truth.
 */
@Singleton
class ObservationEventStore @Inject constructor(
    private val eventDao: ObservationEventDao,
    private val fieldObservationDao: FieldObservationDao,
) {
    suspend fun append(event: ObservationEvent) {
        eventDao.insert(ObservationEventMapper.toRecord(event))
    }

    suspend fun appendAll(events: Collection<ObservationEvent>) {
        if (events.isEmpty()) return
        eventDao.insertAll(events.map(ObservationEventMapper::toRecord))
    }

    suspend fun eventsFor(entityId: String): List<ObservationEvent> =
        eventDao.getForEntity(entityId).map(ObservationEventMapper::toDomain)

    fun observeEvents(entityId: String): Flow<List<ObservationEvent>> =
        eventDao.observeForEntity(entityId).map { rows ->
            rows.map(ObservationEventMapper::toDomain)
        }

    suspend fun project(entityId: String, nowMs: Long = System.currentTimeMillis()): DerivedAssessment? {
        val events = eventsFor(entityId)
        if (events.isEmpty()) return null
        return ObservationProjector.project(entityId, events, nowMs)
    }

    /**
     * Writes a confirmed/corrected ID onto the map observation hook.
     * Unreviewed suggestions never reach [com.strobingn.wildlifefieldops.data.model.FieldObservation.speciesHint].
     */
    suspend fun applyOperationalSpeciesHint(fieldObservationId: String, nowMs: Long = System.currentTimeMillis()) {
        val events = eventsFor(fieldObservationId)
        val operational = SpeciesRecognition.operationalLabel(fieldObservationId, events, nowMs)
        val row = fieldObservationDao.getById(fieldObservationId) ?: return
        if (row.speciesHint == operational.orEmpty()) return
        fieldObservationDao.update(row.copy(speciesHint = operational.orEmpty(), isSynced = false))
    }
}
