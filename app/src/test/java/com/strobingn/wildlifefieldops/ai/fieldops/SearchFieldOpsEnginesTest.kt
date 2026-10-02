package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.Inspection
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PricingJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFieldOpsEnginesTest {

    @Test
    fun smartSearchHitsJobsCustomersInspectionsPhotos() {
        val job = Job(
            id = "j1",
            title = "Oak attic",
            customerName = "Pat Lee",
            notes = "scratching in soffit",
            confirmedSpecies = "raccoon",
            pricing = JobPricing(photoAutoTags = listOf(SyncedPhotoTag(photoId = "p1", species = "raccoon", damage = "chew")))
        )
        val hits = SmartSearch.search(
            query = "soffit",
            jobs = listOf(job),
            customers = listOf(Customer(id = "c1", firstName = "Pat", lastName = "Lee", address = "12 Oak")),
            inspections = listOf(Inspection(id = "i1", jobId = "j1", findings = "Soffit gap at the ridge")),
            photos = listOf(Photo(id = "p1", jobId = "j1", description = "Tags: raccoon · soffit", category = PhotoCategory.EVIDENCE))
        )
        assertTrue(hits.any { it.kind == SearchKind.JOB })
        assertTrue(hits.any { it.kind == SearchKind.INSPECTION })
        assertTrue(hits.any { it.kind == SearchKind.PHOTO })
    }

    @Test
    fun photoAutoTagsSuggestAndOperatorWins() {
        val ai = PhotoAutoTags.suggest("raccoon chew at the soffit one-way door")
        assertEquals("raccoon", ai.species)
        assertTrue(ai.entry.isNotBlank() || ai.damage.isNotBlank() || ai.extra.isNotBlank())
        val typed = PhotoAutoTags.keepTyped(SyncedPhotoTag(photoId = "p", species = "Sir said skunk"))
        assertEquals("Sir said skunk", typed.species)
        assertEquals("", typed.damage)
        assertEquals("", PhotoAutoTags.mergeOperatorWins(ai, typed).damage)
        assertTrue(PhotoAutoTags.matchesFilter(typed, "skunk"))
        assertFalse(PhotoAutoTags.matchesFilter(typed, "bat"))
    }

    @Test
    fun beforeAfterPairAndChecklist() {
        val pair = BeforeAfterPair.pair("b1", "a1", "Exclusion done")
        assertEquals("b1", pair.beforeId)
        assertEquals(1, BeforeAfterPair.forPhoto(listOf(pair), "a1").size)
        val bat = SpeciesChecklist.templateFor("little brown bat")
        assertEquals("bat", bat.first().species)
        assertTrue(bat.any { it.label.contains("one-way") })
        val (done, total) = SpeciesChecklist.completion(bat.mapIndexed { i, item -> item.copy(done = i == 0) })
        assertEquals(1, done)
        assertEquals(bat.size, total)
        val hand = ChecklistItemRecord(species = "custom", label = "Walk the ridge by hand")
        assertEquals("Walk the ridge by hand", hand.label)
        assertFalse(hand.done)
    }

    @Test
    fun shareableReportUriRoundTrip() {
        val payload = ShareableReport.payload("job-9")
        assertEquals("fieldops://report/job-9", payload)
        assertEquals("job-9", ShareableReport.jobIdFromUri(payload))
        assertEquals(null, ShareableReport.jobIdFromUri("https://example.com"))
    }

    @Test
    fun batch5ExtrasStayInsidePricing() {
        val pricing = JobPricing(
            photoAutoTags = listOf(SyncedPhotoTag(photoId = "p1", species = "bat")),
            photoPairs = listOf(PhotoPairRecord(beforeId = "b", afterId = "a")),
            speciesChecklist = SpeciesChecklist.templateFor("squirrel"),
            shareReportToken = "fieldops://report/x"
        )
        val decoded = PricingJson.decode(PricingJson.encode(pricing))
        assertEquals("bat", decoded.photoAutoTags.first().species)
        assertEquals("b", decoded.photoPairs.first().beforeId)
        assertTrue(decoded.speciesChecklist.isNotEmpty())
        assertTrue(decoded.isEmptyWorksheet())
    }

    @Test
    fun tagLineDoesNotClobberTypedNotes() {
        val next = SearchFieldOpsStore.applyTagsToDescription(
            "Hole at the ridge",
            SyncedPhotoTag(species = "squirrel", entry = "soffit")
        )
        assertTrue(next.contains("Hole at the ridge"))
        assertTrue(next.contains("Tags: squirrel · soffit"))
        val again = SearchFieldOpsStore.applyTagsToDescription(next, SyncedPhotoTag(species = "bat"))
        assertEquals(1, again.lines().count { it.startsWith("Tags:") })
        assertTrue(again.contains("bat"))
    }
}
