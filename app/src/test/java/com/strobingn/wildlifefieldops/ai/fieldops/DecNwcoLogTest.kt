package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.TrapLog
import com.strobingn.wildlifefieldops.pricing.JobPricing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecNwcoLogTest {

    @Test
    fun autoFillUsesJobSpeciesTrapsAndPhotoTags() {
        val job = Job(
            id = "j1",
            customerName = "Pat Lee",
            address = "10 Oak St, Cornwall, NY 12518",
            county = "Orange",
            confirmedSpecies = "Raccoon",
            type = "Exclusion",
            description = "Attic damage from raccoon",
            completedDate = 1_700_000_000_000L,
            pricing = JobPricing(photoAutoTags = listOf(SyncedPhotoTag(species = "Raccoon", damage = "chewed soffit")))
        )
        val trap = TrapLog(
            id = "t1",
            jobId = "j1",
            catchType = CatchType.RACCOON,
            catchCount = 2,
            method = "Live cage trap",
            disposition = "Released on site",
            checkDate = 1_700_000_100_000L
        )
        val rows = DecNwcoLog.autoFill(
            NwcoAutoInput(
                jobs = listOf(job),
                traps = listOf(trap),
                inspections = listOf(Inspection(jobId = "j1", speciesIdentified = "Raccoon", findings = "entry at soffit")),
                photos = listOf(Photo(jobId = "j1", description = "raccoon in attic"))
            )
        )
        assertEquals(1, rows.size)
        val row = rows.first()
        assertTrue(row.complainant.contains("Pat Lee"))
        assertTrue(row.species.contains("Raccoon", ignoreCase = true))
        assertTrue(row.abatementMethod.startsWith("A"))
        assertTrue(row.speciesAndNumberTaken.contains("2"))
        assertTrue(row.disposition.startsWith("R") || row.disposition.contains("Released"))
        assertTrue(row.complaintType.startsWith("B"))
        assertEquals("job:j1:trap:t1:Raccoon", row.sourceKey)
    }

    @Test
    fun typedCellsSurviveLaterAutoFill() {
        val job = Job(id = "j1", customerName = "Pat", address = "1 Main", confirmedSpecies = "Squirrel", completedDate = 1L)
        val auto = DecNwcoLog.autoFill(NwcoAutoInput(jobs = listOf(job), traps = emptyList()))
        val typed = DecNwcoLog.withTyped(auto.first(), "species", "Gray Squirrel")
            .let { DecNwcoLog.withTyped(it, "disposition", "E — Euthanized") }
        val laterJob = job.copy(confirmedSpecies = "Bat")
        val laterAuto = DecNwcoLog.autoFill(NwcoAutoInput(jobs = listOf(laterJob), traps = emptyList()))
        val merged = DecNwcoLog.merge(laterAuto, listOf(typed))
        assertEquals(1, merged.size)
        assertEquals("Gray Squirrel", merged.first().species)
        assertEquals("E — Euthanized", merged.first().disposition)
        assertTrue("species" in merged.first().locked)
        assertFalse(merged.first().species.contains("Bat"))
    }

    @Test
    fun deletedAutoRowDoesNotComeBack() {
        val job = Job(id = "j1", customerName = "Pat", confirmedSpecies = "Skunk", completedDate = 1L)
        val auto = DecNwcoLog.autoFill(NwcoAutoInput(jobs = listOf(job), traps = emptyList()))
        val deleted = auto.first().copy(deleted = true)
        val merged = DecNwcoLog.merge(auto, listOf(deleted))
        assertTrue(merged.isEmpty())
    }

    @Test
    fun csvUsesOfficial2024ColumnOrder() {
        val csv = DecNwcoLog.toCsv(
            NwcoOperatorProfile(lastName = "Diggler", firstName = "Dirk", licenseNumber = "NW-1", decRegion = "3", countyOfResidence = "Orange"),
            listOf(DecNwcoLog.blankManual(1_700_000_000_000L).copy(species = "Gray Squirrel", complainant = "Pat, 1 Main"))
        )
        assertTrue(csv.startsWith(DecNwcoLog.CSV_HEADER))
        assertTrue(csv.contains("NW-1"))
        assertTrue(csv.contains("5 Name and address of complainant"))
        assertTrue(csv.contains("13 Disposition of animal"))
    }
}
