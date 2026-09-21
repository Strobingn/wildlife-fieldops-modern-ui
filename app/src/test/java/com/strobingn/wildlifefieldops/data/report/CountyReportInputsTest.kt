package com.strobingn.wildlifefieldops.data.report

import com.strobingn.wildlifefieldops.data.model.FieldObservation
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobStatus
import com.strobingn.wildlifefieldops.data.observation.HumanVerificationState
import com.strobingn.wildlifefieldops.data.observation.ObservationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CountyReportInputsTest {

    private val now = 1_700_000_000_000L

    private fun field(
        id: String,
        lat: Double = 41.4376,
        lng: Double = -74.0354,
        species: String = "raccoon",
        jobId: String? = "job-1",
        observedAt: Long = now,
    ) = FieldObservation(
        id = id,
        notes = "note",
        latitude = lat,
        longitude = lng,
        speciesHint = species,
        jobId = jobId,
        observedAt = observedAt,
    )

    private fun job(
        id: String,
        status: JobStatus,
        createdAt: Long = now - 10_000L,
        completedDate: Long? = now,
        lat: Double? = 41.4376,
        lng: Double? = -74.0354,
        type: String = "SHOULD_NOT_APPEAR_AS_SPECIES",
        county: String? = "SHOULD_NOT_BE_USED",
    ) = Job(
        id = id,
        title = "Job $id",
        type = type,
        status = status,
        createdAt = createdAt,
        completedDate = completedDate,
        latitude = lat,
        longitude = lng,
        county = county,
        state = "NY",
    )

    private fun event(
        eventId: String,
        entityId: String,
        label: String,
    ) = ObservationEvent(
        eventId = eventId,
        entityId = entityId,
        observedAt = now,
        uploadedAt = now + 1,
        deviceId = "d",
        operatorId = "o",
        modelId = "wildlife_evidence_v3",
        modelHash = "h",
        backendTag = "tflite",
        quantizerTag = "int8",
        frameHash = "f-$eventId",
        cropHash = "c-$eventId",
        labelDistribution = mapOf(label to 0.95f),
        captureQuality = 0.9f,
        geometryTrust = 0.8f,
        humanVerificationState = HumanVerificationState.UNREVIEWED,
    )

    @Test
    fun fieldObservationMapsSpeciesHintAndJobSite() {
        val facts = CountyReportInputs.observations(listOf(field("obs-1")))
        assertEquals(1, facts.size)
        assertEquals("raccoon", facts.single().speciesLabel)
        assertEquals("job-1", facts.single().siteKey)
        assertEquals(41.4376, facts.single().latitude!!, 0.0001)
    }

    @Test
    fun blankSpeciesHintIsNullNotUnknown() {
        val facts = CountyReportInputs.observations(listOf(field("obs-1", species = "  ")))
        assertEquals(null, facts.single().speciesLabel)
    }

    @Test
    fun eventsSupplySpeciesAndSuppressDuplicateFieldRowForSameSite() {
        val facts = CountyReportInputs.observations(
            fieldObservations = listOf(field("obs-1", species = "field-hint", jobId = "site-9")),
            events = listOf(event("evt-1", entityId = "site-9", label = "bat")),
            nowMs = now,
        )
        assertEquals(1, facts.size)
        assertEquals("evt-1", facts.single().id)
        assertEquals("bat", facts.single().speciesLabel)
        assertEquals("site-9", facts.single().siteKey)
        assertEquals(41.4376, facts.single().latitude!!, 0.0001)
    }

    @Test
    fun completionsIgnoreOpenJobsAndMutableCountyType() {
        val facts = CountyReportInputs.completions(
            listOf(
                job("open", JobStatus.PENDING),
                job("done", JobStatus.COMPLETED),
                job("paid", JobStatus.PAID, completedDate = null),
                job("cancel", JobStatus.CANCELLED),
            )
        )
        assertEquals(setOf("done", "paid"), facts.map { it.id }.toSet())
        assertTrue(facts.none { it.id == "cancel" })
        facts.forEach { fact ->
            assertFalse(fact.id.contains("SHOULD"))
        }
        val dash = CountyReportAggregator.aggregate(
            observations = emptyList(),
            completions = facts,
            window = ReportWindow.ALL,
            nowMs = now,
        )
        assertEquals("orange", dash.counties.single().county.key)
        assertTrue(dash.counties.none { it.county.displayName.contains("SHOULD") })
    }

    @Test
    fun jobTypeIsNeverCopiedOntoObservationSpecies() {
        val observations = CountyReportInputs.observations(listOf(field("obs-1", species = "opossum")))
        val completions = CountyReportInputs.completions(
            listOf(job("done", JobStatus.COMPLETED, type = "Bat exclusion"))
        )
        val dash = CountyReportAggregator.aggregate(
            observations = observations,
            completions = completions,
            window = ReportWindow.ALL,
            nowMs = now,
        )
        val species = dash.counties.flatMap { it.speciesBreakdown }.map { it.label }
        assertEquals(listOf("opossum"), species)
        assertFalse(species.any { it.contains("bat", ignoreCase = true) })
        assertFalse(species.any { it.contains("exclusion", ignoreCase = true) })
    }
}
