package com.strobingn.wildlifefieldops.data.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NyCountyGeometryTest {

    @Test
    fun cornwallNyResolvesToOrange() {
        val ref = NyCountyGeometry.resolve(41.4376, -74.0354)
        assertEquals("orange", ref?.key)
        assertEquals("Orange County", ref?.displayName)
        assertEquals("NY", ref?.state)
    }

    @Test
    fun newCityResolvesToRockland() {
        assertEquals("rockland", NyCountyGeometry.resolve(41.1476, -73.9893)?.key)
    }

    @Test
    fun kingstonResolvesToUlster() {
        assertEquals("ulster", NyCountyGeometry.resolve(41.9270, -73.9974)?.key)
    }

    @Test
    fun poughkeepsieResolvesToDutchess() {
        assertEquals("dutchess", NyCountyGeometry.resolve(41.7004, -73.9210)?.key)
    }

    @Test
    fun carmelResolvesToPutnam() {
        assertEquals("putnam", NyCountyGeometry.resolve(41.4301, -73.6801)?.key)
    }

    @Test
    fun whitePlainsResolvesToWestchester() {
        assertEquals("westchester", NyCountyGeometry.resolve(41.0340, -73.7629)?.key)
    }

    @Test
    fun monticelloResolvesToSullivan() {
        assertEquals("sullivan", NyCountyGeometry.resolve(41.6556, -74.6893)?.key)
    }

    @Test
    fun manhattanResolvesToNyc() {
        assertEquals("new york city", NyCountyGeometry.resolve(40.7580, -73.9855)?.key)
    }

    @Test
    fun oceanPointIsUnlocated() {
        assertNull(NyCountyGeometry.resolve(40.0, -70.0))
        assertEquals(CountyRef.UNLOCATED, NyCountyGeometry.resolveOrUnlocated(40.0, -70.0))
    }

    @Test
    fun missingOrInvalidCoordsAreUnlocated() {
        assertEquals(CountyRef.UNLOCATED, NyCountyGeometry.resolveOrUnlocated(null, -74.0))
        assertEquals(CountyRef.UNLOCATED, NyCountyGeometry.resolveOrUnlocated(41.4, null))
        assertEquals(CountyRef.UNLOCATED, NyCountyGeometry.resolveOrUnlocated(Double.NaN, -74.0))
        assertEquals(CountyRef.UNLOCATED, NyCountyGeometry.resolveOrUnlocated(200.0, -74.0))
    }
}
