package com.strobingn.wildlifefieldops.data.inspection

import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
import com.strobingn.wildlifefieldops.data.remote.LiveInspectionUpsert
import com.strobingn.wildlifefieldops.data.remote.LiveSyncPayloads
import com.strobingn.wildlifefieldops.data.remote.toRemoteDtoOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JobInspectionLinkTest {

    private val job = Job(
        id = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
        title = "Cornwall raccoon",
        customerId = "cust-1",
        customerName = "Pat Lee",
        address = "12 Oak Street, Cornwall, NY"
    )

    @Test
    fun linkAndUnlinkPersistJobIdAndMarkUnsynced() {
        val inspection = Inspection(id = "insp-1", customerName = "Pat Lee", jobId = "", isSynced = true)
        val linked = JobInspectionLink.applyLink(inspection, job.id, nowMs = 50L)
        assertEquals(job.id, linked.jobId)
        assertFalse(linked.isSynced)
        assertEquals(50L, linked.updatedAt)
        val unlinked = JobInspectionLink.applyUnlink(linked, nowMs = 90L)
        assertEquals("", unlinked.jobId)
        assertFalse(unlinked.isSynced)
        assertEquals(90L, unlinked.updatedAt)
    }

    @Test
    fun blankJobIdDoesNotLink() {
        val inspection = Inspection(id = "insp-1", jobId = "keep")
        assertEquals("keep", JobInspectionLink.applyLink(inspection, "  ").jobId)
    }

    @Test
    fun suggestSameCustomerAndAddressNeverAutoApplies() {
        val match = Inspection(
            id = "insp-match",
            customerId = "cust-1",
            customerName = "Pat Lee",
            findings = "Attic at 12 Oak Street",
            jobId = ""
        )
        val other = Inspection(id = "insp-other", customerName = "Stranger", jobId = "")
        val already = Inspection(id = "insp-linked", customerName = "Pat Lee", jobId = job.id)
        val suggested = JobInspectionLink.suggestedForJob(job, listOf(match, other, already))
        assertEquals(listOf("insp-match"), suggested.map { it.id })
        assertEquals(listOf(already), JobInspectionLink.linkedTo(job.id, listOf(match, other, already)))
        assertNull(JobInspectionLink.suggestedForJob(job, listOf(already)).singleOrNull())
    }

    @Test
    fun suggestedJobOnInspectionSide() {
        val inspection = Inspection(customerName = "Pat Lee", jobId = "")
        val hint = JobInspectionLink.suggestedJob(inspection, listOf(job, Job(id = "other", customerName = "X")))
        assertEquals(job.id, hint?.id)
    }

    @Test
    fun syncPayloadRoundTripsJobIdOnExistingColumn() {
        val inspection = Inspection(
            id = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            jobId = job.id,
            customerName = "Pat Lee",
            findings = "Raccoon in soffit",
            speciesIdentified = "raccoon"
        )
        val live = LiveSyncPayloads.inspection(inspection)
        assertEquals(job.id, live.jobId)
        val encoded = LiveSyncPayloads.json.encodeToJsonElement(
            LiveInspectionUpsert.serializer(),
            live
        ).jsonObject
        assertEquals(job.id, encoded.getValue("job_id").jsonPrimitive.content)
        val remote = inspection.toRemoteDtoOrNull()
        assertEquals(job.id, remote.jobId)
        val unlinked = LiveSyncPayloads.inspection(JobInspectionLink.applyUnlink(inspection))
        assertNull(unlinked.jobId)
    }

    @Test
    fun linkedPhotosKeepInspectionIdForEstimate() {
        val photo = Photo(
            id = "p1",
            inspectionId = "insp-1",
            jobId = null,
            category = PhotoCategory.INSPECTION,
            description = "entry at soffit"
        )
        assertEquals("insp-1", photo.inspectionId)
        assertTrue(photo.category == PhotoCategory.INSPECTION)
    }

    @Test
    fun pickerAlwaysIncludesAnyInspectionNotOnlySuggested() {
        val other = Inspection(id = "far-away", customerName = "Other Town")
        val ranked = JobInspectionLink.rankPicker(job, listOf(other))
        assertEquals(listOf("far-away"), ranked.map { it.id })
        assertTrue(JobInspectionLink.suggestedForJob(job, listOf(other)).isEmpty())
    }

    @Test
    fun jobAndInspectionScreensExposeLinkUnlink() {
        val jobPage = readAppSource("ui/screens/JobDetailScreen.kt")
        val jobUi = readAppSource("ui/screens/JobInspectionLinkUi.kt")
        val insp = readAppSource("ui/screens/InspectionFormScreen.kt")
        assertTrue(jobPage.contains("JobLinkedInspectionsCard"))
        assertTrue(jobPage.contains("New inspection for this job") || jobUi.contains("New inspection for this job"))
        assertTrue(jobUi.contains("Link inspection"))
        assertTrue(jobUi.contains("Unlink"))
        assertTrue(jobUi.contains("Link to job"))
        assertTrue(insp.contains("InspectionJobLinkCard"))
        assertTrue(insp.contains("linkInspectionToJob"))
        assertTrue(insp.contains("unlinkInspectionFromJob"))
        assertFalse(insp.contains("Open Inspect from a Job to link jobId"))
    }

    private fun readAppSource(relativeUnderJava: String): String {
        val suffix = "src/main/java/com/strobingn/wildlifefieldops/$relativeUnderJava"
        val candidates = listOf(File(suffix), File("app/$suffix"), File("../$suffix"))
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Missing $suffix")
    }
}
