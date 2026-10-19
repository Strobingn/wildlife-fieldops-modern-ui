package com.strobingn.wildlifefieldops.location

import com.strobingn.wildlifefieldops.data.repository.DataVersionGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class PreciseGpsMathTest {

    @Test
    fun weightedAverageUsesInverseVarianceAndDropsLooseFixes() {
        val tight = GnssSample(10.0, 20.0, accuracyMeters = 10.0, elapsedMs = 0)
        val tighter = GnssSample(16.0, 26.0, accuracyMeters = 5.0, elapsedMs = 1_000)
        val loose = GnssSample(90.0, 0.0, accuracyMeters = 15.1, elapsedMs = 2_000)
        val mean = PreciseGpsMath.weightedAverage(listOf(tight, tighter, loose))
        requireNotNull(mean)
        val w1 = 1.0 / 100.0
        val w2 = 1.0 / 25.0
        assertEquals((10.0 * w1 + 16.0 * w2) / (w1 + w2), mean.latitude, 1e-9)
        assertEquals((20.0 * w1 + 26.0 * w2) / (w1 + w2), mean.longitude, 1e-9)
        assertEquals(1.0 / sqrt(w1 + w2), mean.accuracyMeters, 1e-9)
        assertEquals(2, mean.sampleCount)
    }

    @Test
    fun fifteenMetersIsKeptAndInvalidFixesAreNot() {
        assertTrue(PreciseGpsMath.accepts(41.0, -74.0, 15.0))
        assertFalse(PreciseGpsMath.accepts(41.0, -74.0, 15.0001))
        assertFalse(PreciseGpsMath.accepts(41.0, -74.0, 0.0))
        assertFalse(PreciseGpsMath.accepts(41.0, -74.0, -2.0))
        assertFalse(PreciseGpsMath.accepts(41.0, -74.0, Double.NaN))
        assertFalse(PreciseGpsMath.accepts(91.0, -74.0, 3.0))
        assertFalse(PreciseGpsMath.accepts(41.0, 181.0, 3.0))
        assertNull(PreciseGpsMath.weightedAverage(emptyList()))
        assertNull(PreciseGpsMath.weightedAverage(listOf(GnssSample(0.0, 0.0, 40.0, 0))))
    }

    @Test
    fun singleFixAccuracyIsTheReportedRadius() {
        val mean = PreciseGpsMath.weightedAverage(listOf(GnssSample(41.4, -74.0, 4.0, 0)))
        requireNotNull(mean)
        assertEquals(4.0, mean.accuracyMeters, 1e-9)
        assertEquals(41.4, mean.latitude, 0.0)
    }

    @Test
    fun stopsWhenAverageIsUnderThreeMetersAndQuietForTenSeconds() {
        assertFalse(PreciseGpsMath.shouldAutoFinish(59_999, 2.9, 9_999))
        assertTrue(PreciseGpsMath.shouldAutoFinish(20_000, 2.999, 10_000))
        assertFalse(PreciseGpsMath.shouldAutoFinish(20_000, 3.0, 10_000))
        assertFalse(PreciseGpsMath.shouldAutoFinish(20_000, null, 10_000))
        assertTrue(PreciseGpsMath.shouldAutoFinish(60_000, 12.0, 0))
        assertTrue(PreciseGpsMath.shouldAutoFinish(60_000, null, 0))
    }

    @Test
    fun accumulatorFinishesAfterTheEstimateStopsImproving() {
        val capture = PreciseGpsAccumulator()
        val first = capture.onSample(GnssSample(41.0, -74.0, 4.0, elapsedMs = 0))
        assertEquals(1, first.acceptedCount)
        assertFalse(first.autoFinish)
        assertEquals(4.0, first.averaged!!.accuracyMeters, 1e-6)

        val second = capture.onSample(GnssSample(41.0, -74.0, 4.0, elapsedMs = 5_000))
        assertEquals(2, second.acceptedCount)
        assertTrue(second.averaged!!.accuracyMeters < 3.0)
        assertFalse(second.autoFinish)

        val quiet = capture.readout(15_000)
        assertTrue(quiet.autoFinish)
        assertTrue(quiet.progress > 0f && quiet.progress < 1f)

        val loose = capture.onSample(GnssSample(41.0, -74.0, 40.0, elapsedMs = 6_000, satellites = 9))
        assertEquals(1, loose.rejectedCount)
        assertEquals(2, loose.acceptedCount)
        assertEquals(9, loose.satellites)
        assertEquals("Latest ± 40 m", PreciseGpsMath.accuracyLabel(null, 40.0))
    }

    @Test
    fun timeoutWithNoGoodFixDoesNotInventASpot() {
        val capture = PreciseGpsAccumulator()
        capture.onSample(GnssSample(41.0, -74.0, 30.0, elapsedMs = 1_000, satellites = 4))
        val done = capture.readout(60_000)
        assertTrue(done.autoFinish)
        assertEquals(1f, done.progress, 0f)
        assertNull(capture.spot(1_700_000_000_000L))
        assertEquals("4 satellites", PreciseGpsMath.satelliteLabel(4))
        assertEquals("Satellites unavailable", PreciseGpsMath.satelliteLabel(null))
        assertEquals("0 samples", PreciseGpsMath.sampleLabel(0))
        assertEquals("1 sample", PreciseGpsMath.sampleLabel(1))
    }

    @Test
    fun useNowReturnsTheAverageEvenWhenItIsNotYetUnderThreeMeters() {
        val capture = PreciseGpsAccumulator()
        capture.onSample(GnssSample(41.25, -74.5, 10.0, elapsedMs = 2_000))
        val spot = capture.spot(1_700_000_000_000L)
        requireNotNull(spot)
        assertEquals(41.25, spot.latitude, 0.0)
        assertEquals(-74.5, spot.longitude, 0.0)
        assertEquals(10.0, spot.accuracyMeters, 1e-6)
        assertEquals(1, spot.sampleCount)
        assertEquals(1_700_000_000_000L, spot.capturedAtMillis)
        assertFalse(capture.readout(2_000).autoFinish)
    }

    @Test
    fun typedCoordinatesKeepTheSpotUntilTheOperatorEditsThem() {
        val spot = PreciseSpot(41.4401234, -74.0123456, 2.4, 6, 1_700_000_000_000L)
        val lat = PreciseGpsMath.formatCoordinate(spot.latitude)
        val lng = PreciseGpsMath.formatCoordinate(spot.longitude)
        assertEquals(spot, PreciseGpsMath.retainedSpot(lat, lng, spot))
        assertNull(PreciseGpsMath.retainedSpot("41.50", lng, spot))
        assertNull(PreciseGpsMath.retainedSpot(lat, "", spot))
        assertEquals("± 2.4 m", PreciseGpsMath.formatMeters(2.4))
        assertEquals("± 15 m", PreciseGpsMath.formatMeters(15.0))
        val day = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US).parse("10/19/2026")!!.time
        assertEquals("10/19/2026", PreciseGpsMath.formatCapturedDay(day))
    }

    @Test
    fun preciseSpotRaisesDataVersionAndLeavesOlderJobsAlone() {
        assertEquals(2, PreciseGpsMath.stampedDataVersion(2, hasPreciseGps = false))
        assertEquals(DataVersionGate.TRAP_GPS, PreciseGpsMath.stampedDataVersion(2, hasPreciseGps = true))
        assertEquals(4, PreciseGpsMath.stampedDataVersion(4, hasPreciseGps = true))
        assertFalse(DataVersionGate.isNewer(2))
        assertFalse(DataVersionGate.isNewer(DataVersionGate.CURRENT))
        assertTrue(DataVersionGate.isNewer(DataVersionGate.CURRENT + 1))
    }

    @Test
    fun jobDirectionsPublishOnlyWhenThePreciseSpotChanges() {
        val spot = PreciseSpot(41.1, -74.2, 2.0, 4, 50L)
        assertTrue(PreciseGpsMath.shouldPublishToJob(null, null, null, spot))
        assertFalse(PreciseGpsMath.shouldPublishToJob(50L, 41.1, -74.2, spot))
        assertTrue(PreciseGpsMath.shouldPublishToJob(40L, 41.1, -74.2, spot))
        assertFalse(PreciseGpsMath.shouldPublishToJob(null, null, null, null))
    }
}
