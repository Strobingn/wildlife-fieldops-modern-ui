package com.strobingn.wildlifefieldops.sync.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkAnalyticsAdapterTest {

    @Test
    fun flagOffIsNoOpAndDoesNotRegisterExperimentalListeners() {
        val telemetry = RecordingWorkSchedulerTelemetry()
        val adapter = WorkAnalyticsAdapter(flag = { false }, telemetry)
        adapter.record(
            SchedulerEvent(
                type = SchedulerEventType.ENQUEUE,
                workRequestId = "wr-1",
                generation = 0,
                tags = setOf(FieldOpsSyncWorkNames.UNIQUE_WORK_NAME)
            )
        )
        assertFalse(adapter.isFlagEnabled())
        assertFalse(adapter.shouldRegisterExperimentalListeners())
        assertTrue(adapter.snapshot().isEmpty())
        assertTrue(telemetry.snapshot().isEmpty())
    }

    @Test
    fun flagOnRecordsSanitizedSchedulerEvents() {
        val telemetry = RecordingWorkSchedulerTelemetry()
        val adapter = WorkAnalyticsAdapter(flag = { true }, telemetry)
        adapter.record(
            SchedulerEvent(
                type = SchedulerEventType.START,
                workRequestId = "wr-2",
                generation = 1,
                tags = setOf(
                    FieldOpsSyncWorkNames.UNIQUE_WORK_NAME,
                    "species=raccoon",
                    "lat=41.5"
                )
            )
        )
        assertTrue(adapter.shouldRegisterExperimentalListeners())
        val events = adapter.snapshot()
        assertEquals(1, events.size)
        assertEquals(SchedulerEventType.START, events[0].type)
        assertEquals("wr-2", events[0].workRequestId)
        assertEquals(1, events[0].generation)
        assertEquals(setOf(FieldOpsSyncWorkNames.UNIQUE_WORK_NAME), events[0].tags)
    }
}
