package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DecCatchPrefillTest {
    private val job = Job(id = "j1", title = "Attic raccoon", customerName = "Willow Properties", address = "210 Willow Ave, Cornwall, NY")
    private val trap = TrapLog(
        id = "t1", jobId = "j1", trapId = "Cage 1", status = TrapStatus.TRIGGERED,
        catchType = CatchType.RACCOON, catchCount = 1, disposition = "Released on site"
    )

    @Test
    fun prefillNeverOverwritesTypedCells() {
        val typed = NwcoLogRecord(species = "Typed species", complainant = "", disposition = "")
        val source = NwcoLogRecord(species = "Raccoon", complainant = "Willow", disposition = "Released")
        val out = DecCatchPrefill.prefillEmpty(typed, source)
        assertEquals("Typed species", out.species)
        assertEquals("Willow", out.complainant)
        assertEquals("Released", out.disposition)
    }

    @Test
    fun newEntryIsManualAndTiedToTheTrap() {
        val entry = DecCatchPrefill.newEntry(job, trap, now = 1_000L)
        assertTrue(entry.manual)
        assertEquals("t1", entry.trapId)
        assertEquals("j1", entry.jobId)
        assertTrue(entry.species.isNotBlank())
    }
}
