package com.strobingn.wildlifefieldops.ui.screens

import com.strobingn.wildlifefieldops.data.model.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobDirectionsTest {

    @Test
    fun coordinatesWinOverTheAddress() {
        val target = JobDirections.Target(
            latitude = 41.4459,
            longitude = -74.4207,
            address = "12 Oak St, Cornwall, NY 12518"
        )
        assertTrue(JobDirections.hasDestination(target))
        assertEquals("41.4459,-74.4207", JobDirections.query(target))
        assertEquals("google.navigation:q=41.4459,-74.4207", JobDirections.navigationUri(target))
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=41.4459%2C-74.4207",
            JobDirections.browserUri(target)
        )
    }

    @Test
    fun addressOnlyIsEncodedForNavigationAndTheBrowser() {
        val target = JobDirections.Target(address = "12 Oak St, Cornwall, NY 12518")
        assertEquals(
            "google.navigation:q=12%20Oak%20St%2C%20Cornwall%2C%20NY%2012518",
            JobDirections.navigationUri(target)
        )
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=12%20Oak%20St%2C%20Cornwall%2C%20NY%2012518",
            JobDirections.browserUri(target)
        )
    }

    @Test
    fun specialCharactersAreEncoded() {
        val target = JobDirections.Target(address = "100 Main St #4, O'Brien & Sons")
        assertEquals(
            "google.navigation:q=100%20Main%20St%20%234%2C%20O%27Brien%20%26%20Sons",
            JobDirections.navigationUri(target)
        )
        assertTrue(JobDirections.browserUri(target)!!.contains("destination=100%20Main%20St%20%234%2C%20O%27Brien%20%26%20Sons"))
    }

    @Test
    fun missingAddressAndCoordinatesHaveNoUri() {
        val blank = JobDirections.Target(address = "   ")
        assertFalse(JobDirections.hasDestination(blank))
        assertNull(JobDirections.query(blank))
        assertNull(JobDirections.navigationUri(blank))
        assertNull(JobDirections.browserUri(blank))
        val job = Job(address = "", state = "  ", latitude = null, longitude = null)
        assertFalse(JobDirections.hasDestination(JobDirections.fromJob(job)))
    }

    @Test
    fun oneCoordinateFallsBackToTheAddress() {
        val target = JobDirections.Target(latitude = 41.4, address = "12 Oak St")
        assertEquals("12 Oak St", JobDirections.query(target))
        assertEquals("google.navigation:q=12%20Oak%20St", JobDirections.navigationUri(target))
    }

    @Test
    fun customerAddressIsUsedAndStateIsAppendedWhenMissing() {
        val job = Job(address = "12 Oak St", state = "NY", latitude = null, longitude = null)
        val fromCustomer = JobDirections.fromJob(job, fullAddress = "12 Oak St, Cornwall, NY 12518")
        assertEquals("12 Oak St, Cornwall, NY 12518", fromCustomer.address)
        val fromJobOnly = JobDirections.fromJob(job)
        assertEquals("12 Oak St, NY", fromJobOnly.address)
    }
}
