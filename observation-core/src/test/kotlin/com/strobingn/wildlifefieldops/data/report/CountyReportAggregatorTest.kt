package com.strobingn.wildlifefieldops.data.report

import com.strobingn.wildlifefieldops.data.observation.HumanVerificationState
import com.strobingn.wildlifefieldops.data.observation.ObservationEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountyReportAggregatorTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    // Cornwall, NY (Orange) and New City, NY (Rockland)
    private val orangeLat = 41.4376
    private val orangeLng = -74.0354
    private val rocklandLat = 41.1476
    private val rocklandLng = -73.9893

    private fun obs(
        id: String,
        observedAt: Long,
        lat: Double? = orangeLat,
        lng: Double? = orangeLng,
        species: String? = "raccoon",
        siteKey: String = "site-orange",
    ) = CountyReportObservation(
        id = id,
        observedAt = observedAt,
        latitude = lat,
        longitude = lng,
        speciesLabel = species,
        siteKey = siteKey,
    )

    private fun job(
        id: String,
        openedAt: Long,
        completedAt: Long?,
        lat: Double? = orangeLat,
        lng: Double? = orangeLng,
    ) = CountyReportCompletion(
        id = id,
        openedAt = openedAt,
        completedAt = completedAt,
        latitude = lat,
        longitude = lng,
    )

    private fun modelEvent(
        eventId: String,
        entityId: String,
        observedAt: Long,
        label: String,
        confidence: Float = 0.9f,
        quality: Float = 0.8f,
        uploadedAt: Long = observedAt + 1_000L,
    ) = ObservationEvent(
        eventId = eventId,
        entityId = entityId,
        observedAt = observedAt,
        uploadedAt = uploadedAt,
        deviceId = "dev-1",
        operatorId = "op-1",
        modelId = "wildlife_evidence_v3",
        modelHash = "abc",
        backendTag = "tflite",
        quantizerTag = "int8",
        frameHash = "fh-$eventId",
        cropHash = "ch-$eventId",
        labelDistribution = mapOf(label to confidence),
        captureQuality = quality,
        geometryTrust = 0.9f,
        humanVerificationState = HumanVerificationState.UNREVIEWED,
    )

    private fun humanCorrected(
        eventId: String,
        entityId: String,
        observedAt: Long,
        label: String,
        supersedes: String,
    ) = ObservationEvent(
        eventId = eventId,
        entityId = entityId,
        observedAt = observedAt,
        uploadedAt = observedAt + 500L,
        deviceId = "dev-1",
        operatorId = "op-1",
        modelId = "human",
        modelHash = "human",
        backendTag = "human",
        quantizerTag = "none",
        frameHash = "fh-$eventId",
        cropHash = "ch-$eventId",
        labelDistribution = mapOf(label to 1.0f),
        captureQuality = 1.0f,
        geometryTrust = 0.0f,
        humanVerificationState = HumanVerificationState.CORRECTED,
        supersedesEventId = supersedes,
    )

    @Test
    fun emptyInputsProduceEmptyDashboardWithNoFakeCounties() {
        val dash = CountyReportAggregator.aggregate(
            observations = emptyList(),
            completions = emptyList(),
            window = ReportWindow.LAST_30_DAYS,
            nowMs = now,
        )
        assertTrue(dash.isEmpty)
        assertEquals(0, dash.observationCount)
        assertEquals(0, dash.completedJobCount)
        assertTrue(dash.counties.isEmpty())
    }

    @Test
    fun countsObservationsByCountyAndSpecies() {
        val dash = CountyReportAggregator.aggregate(
            observations = listOf(
                obs("a", now - day, species = "Raccoon"),
                obs("b", now - day, species = "raccoon"),
                obs("c", now - day, rocklandLat, rocklandLng, species = "bat", siteKey = "site-rock"),
                obs("d", now - day, species = null),
            ),
            completions = emptyList(),
            window = ReportWindow.ALL,
            nowMs = now,
        )
        assertEquals(4, dash.observationCount)
        assertEquals(1, dash.unlabeledObservationCount)
        val orange = dash.counties.first { it.county.key == "orange" }
        val rockland = dash.counties.first { it.county.key == "rockland" }
        assertEquals(3, orange.observationCount)
        assertEquals(1, orange.unlabeledObservationCount)
        assertEquals(listOf(SpeciesCount("raccoon", 2)), orange.speciesBreakdown)
        assertEquals(1, rockland.observationCount)
        assertEquals(listOf(SpeciesCount("bat", 1)), rockland.speciesBreakdown)
    }

    @Test
    fun timeWindowDropsOlderObservations() {
        val dash = CountyReportAggregator.aggregate(
            observations = listOf(
                obs("old", now - 40 * day),
                obs("new", now - 2 * day),
            ),
            completions = emptyList(),
            window = ReportWindow.LAST_7_DAYS,
            nowMs = now,
        )
        assertEquals(1, dash.observationCount)
        assertEquals(1, dash.counties.single().observationCount)
    }

    @Test
    fun duplicateObservationIdsAreDeduped() {
        val dash = CountyReportAggregator.aggregate(
            observations = listOf(
                obs("same", now - day, species = "raccoon"),
                obs("same", now - day, species = "bat"),
            ),
            completions = emptyList(),
            window = ReportWindow.ALL,
            nowMs = now,
        )
        assertEquals(1, dash.observationCount)
        assertEquals(listOf(SpeciesCount("bat", 1)), dash.counties.single().speciesBreakdown)
    }

    @Test
    fun repeatSitesRequireTwoOrMoreObservations() {
        val dash = CountyReportAggregator.aggregate(
            observations = listOf(
                obs("a", now - 3 * day, siteKey = "barn"),
                obs("b", now - 2 * day, siteKey = "barn"),
                obs("c", now - day, siteKey = "shed"),
            ),
            completions = emptyList(),
            window = ReportWindow.ALL,
            nowMs = now,
        )
        val sites = dash.counties.single().repeatSites
        assertEquals(1, sites.size)
        assertEquals("barn", sites.single().siteKey)
        assertEquals(2, sites.single().observationCount)
        assertEquals(now - 2 * day, sites.single().lastObservedAt)
    }

    @Test
    fun missingCoordsBecomeUnlocatedNotAGuessedCounty() {
        val dash = CountyReportAggregator.aggregate(
            observations = listOf(obs("a", now - day, lat = null, lng = null)),
            completions = emptyList(),
            window = ReportWindow.ALL,
            nowMs = now,
        )
        assertEquals(1, dash.unlocatedObservationCount)
        assertEquals(CountyRef.UNLOCATED_KEY, dash.counties.single().county.key)
    }

    @Test
    fun completionsUseCompletedAtForWindowAndResponseTime() {
        val dash = CountyReportAggregator.aggregate(
            observations = emptyList(),
            completions = listOf(
                job("fresh", openedAt = now - 10 * day, completedAt = now - 2 * day),
                job("old", openedAt = now - 80 * day, completedAt = now - 40 * day),
                job("untimed", openedAt = now - 3 * day, completedAt = null),
            ),
            window = ReportWindow.LAST_30_DAYS,
            nowMs = now,
        )
        assertEquals(1, dash.completedJobCount)
        assertEquals(1, dash.timedCompletionCount)
        val orange = dash.counties.single()
        assertEquals(1, orange.completedJobCount)
        assertEquals(8.0 * 24, orange.medianResponseHours!!, 0.01)
        assertNull(
            CountyReportAggregator.aggregate(
                observations = emptyList(),
                completions = listOf(job("untimed", openedAt = now - 3 * day, completedAt = null)),
                window = ReportWindow.LAST_7_DAYS,
                nowMs = now,
            ).counties.firstOrNull()
        )
    }

    @Test
    fun untimedCompletionAppearsOnlyInAllWindow() {
        val all = CountyReportAggregator.aggregate(
            observations = emptyList(),
            completions = listOf(job("untimed", openedAt = now - 3 * day, completedAt = null)),
            window = ReportWindow.ALL,
            nowMs = now,
        )
        assertEquals(1, all.completedJobCount)
        assertEquals(0, all.timedCompletionCount)
        assertNull(all.counties.single().medianResponseHours)
    }

    @Test
    fun monthlyTrendUsesUtcYearMonth() {
        // 2023-12-31 23:00 UTC and 2024-01-01 01:00 UTC
        val dec = 1_704_063_600_000L
        val jan = 1_704_070_800_000L
        val dash = CountyReportAggregator.aggregate(
            observations = listOf(
                obs("dec", dec),
                obs("jan", jan),
            ),
            completions = emptyList(),
            window = ReportWindow.ALL,
            nowMs = jan + day,
        )
        assertEquals(
            listOf(MonthCount("2023-12", 1), MonthCount("2024-01", 1)),
            dash.counties.single().monthlyTrend
        )
    }

    @Test
    fun eventFactsUseProjectorLabelNotLaterModelVote() {
        val earlyRaccoon = modelEvent("e1", "site-1", now - 3 * day, "raccoon", confidence = 0.6f)
        val laterBat = modelEvent("e2", "site-1", now - day, "bat", confidence = 0.99f)
        val correction = humanCorrected("e3", "site-1", now - day + 10, "raccoon", supersedes = "e1")
        val facts = CountyReportAggregator.observationsFromEvents(
            events = listOf(laterBat, correction, earlyRaccoon),
            locationsByEntityId = mapOf("site-1" to (orangeLat to orangeLng)),
            nowMs = now,
        )
        assertEquals(3, facts.size)
        assertTrue(facts.all { it.speciesLabel == "raccoon" })
        assertTrue(facts.all { it.siteKey == "site-1" })

        val dash = CountyReportAggregator.aggregate(
            observations = facts,
            completions = emptyList(),
            window = ReportWindow.ALL,
            nowMs = now,
        )
        assertEquals(listOf(SpeciesCount("raccoon", 3)), dash.counties.single().speciesBreakdown)
    }

    @Test
    fun eventFactsWithoutLocationStayUnlocated() {
        val event = modelEvent("e1", "site-x", now - day, "opossum")
        val facts = CountyReportAggregator.observationsFromEvents(
            events = listOf(event),
            locationsByEntityId = emptyMap(),
            nowMs = now,
        )
        val dash = CountyReportAggregator.aggregate(
            observations = facts,
            completions = emptyList(),
            window = ReportWindow.ALL,
            nowMs = now,
        )
        assertEquals(1, dash.unlocatedObservationCount)
        assertEquals("opossum", dash.counties.single().speciesBreakdown.single().label)
    }

    @Test
    fun reorderDoesNotChangeDashboard() {
        val a = obs("z-last", now - day, species = "bat", siteKey = "a")
        val b = obs("a-first", now - 2 * day, rocklandLat, rocklandLng, species = "raccoon", siteKey = "b")
        val c = job("j2", now - 5 * day, now - day)
        val d = job("j1", now - 8 * day, now - 3 * day, rocklandLat, rocklandLng)
        val left = CountyReportAggregator.aggregate(listOf(a, b), listOf(c, d), ReportWindow.ALL, now)
        val right = CountyReportAggregator.aggregate(listOf(b, a), listOf(d, c), ReportWindow.ALL, now)
        assertEquals(left, right)
    }

    @Test
    fun siteKeyFromCoordinatesRoundsToCell() {
        assertEquals(
            "geo:41.437,-74.035",
            CountyReportAggregator.siteKeyFromCoordinates(41.4372, -74.0351),
        )
        assertEquals(
            CountyReportAggregator.siteKeyFromCoordinates(41.4372, -74.0351),
            CountyReportAggregator.siteKeyFromCoordinates(41.4374, -74.0354),
        )
    }
}
