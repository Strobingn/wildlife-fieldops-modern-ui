package com.strobingn.wildlifefieldops.data.backup

import com.strobingn.wildlifefieldops.data.model.Inspection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Test

class FieldDataImportTest {

    @Test
    fun dropDeletedRemovesTombstonedRowsAndTheirPhotos() {
        val bundle = FieldDataBundle(
            jobs = listOf(JobSnapshot(id = "j1"), JobSnapshot(id = "j2")),
            customers = listOf(CustomerSnapshot(id = "c1"), CustomerSnapshot(id = "c2")),
            inspections = listOf(InspectionSnapshot(id = "i1"), InspectionSnapshot(id = "i2")),
            photos = listOf(
                PhotoSnapshot(id = "p-job-deleted", jobId = "j1"),
                PhotoSnapshot(id = "p-insp-deleted", inspectionId = "i1"),
                PhotoSnapshot(id = "p-keep", jobId = "j2", inspectionId = "i2"),
                PhotoSnapshot(id = "p-unlinked")
            )
        )
        val out = FieldDataCodec.dropDeleted(bundle, setOf("j1"), setOf("c1"), setOf("i1"))
        assertEquals(listOf("j2"), out.jobs.map { it.id })
        assertEquals(listOf("c2"), out.customers.map { it.id })
        assertEquals(listOf("i2"), out.inspections.map { it.id })
        assertEquals(listOf("p-keep", "p-unlinked"), out.photos.map { it.id })
    }

    @Test
    fun incomingWinnersSkipsUntouchedLocalRows() {
        val localOnly = JobSnapshot(id = "local-only", updatedAt = 5L)
        val localWins = JobSnapshot(id = "both", notes = "local", updatedAt = 50L)
        val incomingLoses = JobSnapshot(id = "both", notes = "old", updatedAt = 10L)
        val incomingNew = JobSnapshot(id = "new", updatedAt = 1L)
        val local = listOf(localOnly, localWins)
        val merged = FieldDataCodec.mergeJobs(local, listOf(incomingLoses, incomingNew))
        val toWrite = FieldDataCodec.incomingWinners(local, merged)
        assertEquals(listOf("new"), toWrite.map { it.id })
        assertSame(incomingNew, toWrite.single())
    }

    @Test
    fun incomingWinnersKeepsNewerIncomingOverLocal() {
        val local = listOf(JobSnapshot(id = "j", notes = "local", updatedAt = 10L))
        val newer = JobSnapshot(id = "j", notes = "phone2", updatedAt = 99L)
        val toWrite = FieldDataCodec.incomingWinners(local, FieldDataCodec.mergeJobs(local, listOf(newer)))
        assertEquals("phone2", toWrite.single().notes)
    }

    @Test
    fun duplicateIdsInFileKeepTheNewestRegardlessOfOrder() {
        val newer = JobSnapshot(id = "j", title = "new", updatedAt = 80L)
        val older = JobSnapshot(id = "j", title = "old", updatedAt = 10L)
        val merged = FieldDataCodec.mergeJobs(emptyList(), listOf(newer, older))
        assertEquals(1, merged.size)
        assertEquals("new", merged.single().title)
    }

    @Test
    fun inspectionWeatherFieldsSurviveSnapshotRoundTrip() {
        val original = Inspection(
            id = "i",
            weatherConditions = "Light rain",
            temperature = 41.5f,
            humidity = 80,
            windSpeed = 12.25f
        )
        val restored = FieldDataCodec.decode(
            FieldDataCodec.encode(FieldDataBundle(inspections = listOf(original.toSnapshot())))
        ).inspections.single().toInspection()
        assertEquals("Light rain", restored.weatherConditions)
        assertEquals(41.5f, restored.temperature)
        assertEquals(80, restored.humidity)
        assertEquals(12.25f, restored.windSpeed)
        assertFalse(restored.isSynced)
    }
}
