package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.ai.camera.RegionBox
import com.strobingn.wildlifefieldops.ai.camera.WildlifeEvidenceDetector
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoEvidenceContractTest {

    @Test
    fun strongEntryWithProvenanceIsAcceptedWithoutVlm() {
        val screening = PhotoEvidence.screen(entry())
        assertEquals(EvidenceStatus.ACCEPT, screening.status)
        assertTrue(screening.settled)
        assertFalse(screening.vlmEligible)
        assertTrue(screening.reasons.isEmpty())
    }

    @Test
    fun weakEntryStaysInReview() {
        val screening = PhotoEvidence.screen(entry(score = 0.40f))
        assertEquals(EvidenceStatus.REVIEW, screening.status)
        assertTrue(screening.vlmEligible)
        assertFalse(screening.settled)
        assertTrue(AbstentionReason.WEAK_SIGNAL in screening.reasons)
    }

    @Test
    fun missingRegionDerivedFrameAndBadCropAbstain() {
        assertEquals(
            AbstentionReason.REGION_MISSING,
            PhotoEvidence.screen(entry(provenance = null)).reasons.single(),
        )
        assertEquals(
            AbstentionReason.DERIVED_FRAME_NOT_AUTHORITATIVE,
            PhotoEvidence.screen(entry(provenance = provenance(authority = FrameAuthority.DERIVED))).reasons.single(),
        )
        assertEquals(
            AbstentionReason.CROP_OUT_OF_FRAME,
            PhotoEvidence.screen(
                entry(provenance = provenance(bounds = CropBounds(600, 0, 900, 80))),
            ).reasons.single(),
        )
        assertEquals(
            AbstentionReason.CROP_TOO_SMALL,
            PhotoEvidence.screen(
                entry(provenance = provenance(bounds = CropBounds(0, 0, 20, 20))),
            ).reasons.single(),
        )
        assertEquals(
            AbstentionReason.LOW_SHARPNESS,
            PhotoEvidence.screen(entry(sharpness = 0.05f)).reasons.single(),
        )
        assertEquals(
            AbstentionReason.MISSING_ENTRY_GEOMETRY,
            PhotoEvidence.screen(entry(entries = emptyList())).reasons.single(),
        )
        assertEquals(EvidenceStatus.INSUFFICIENT, PhotoEvidence.screen(entry(provenance = null)).status)
        assertFalse(PhotoEvidence.screen(entry(provenance = null)).vlmEligible)
    }

    @Test
    fun conflictingSpeciesDowngradesAcceptToReview() {
        val screening = PhotoEvidence.screen(
            entry(
                species = listOf(
                    ScoredLabel("raccoon", 0.80f),
                    ScoredLabel("skunk", 0.70f),
                ),
            ),
        )
        assertEquals(EvidenceStatus.REVIEW, screening.status)
        assertTrue(AbstentionReason.CONFLICTING_SPECIES in screening.reasons)
        assertTrue(screening.vlmEligible)
    }

    @Test
    fun vlmIsIgnoredUnlessTheCaseIsReview() {
        val accepted = PhotoEvidence.screen(entry())
        val after = PhotoEvidence.applyVlm(
            screening = accepted,
            recovered = listOf(VlmRecovery("species", "bat", 0.99f)),
            offline = false,
        )
        assertEquals(accepted.status, after.status)
        assertTrue(after.evidence.fields.none { it.value.source == FieldSource.VLM })
    }

    @Test
    fun vlmExtentIsRevalidatedAndTagged() {
        val review = PhotoEvidence.screen(entry(coverage = 0.01f, hasExtent = false))
        assertEquals(EvidenceStatus.REVIEW, review.status)
        assertTrue(AbstentionReason.MISSING_ENTRY_GEOMETRY in review.reasons)

        val accepted = PhotoEvidence.applyVlm(
            screening = review,
            recovered = listOf(VlmRecovery("extent", "measured", 0.80f)),
            offline = false,
        )
        assertEquals(EvidenceStatus.ACCEPT, accepted.status)
        assertTrue(accepted.settled)
        assertFalse(accepted.vlmEligible)
        val extent = accepted.evidence.fields.getValue("extent")
        assertEquals("measured", extent.value)
        assertEquals(FieldSource.VLM, extent.source)
        assertEquals(0.80f, extent.confidence, 0.001f)
    }

    @Test
    fun vlmCannotOverwriteADetectorFieldOrAClearedField() {
        val review = PhotoEvidence.screen(entry(score = 0.40f))
        val ignored = PhotoEvidence.applyVlm(
            screening = review,
            recovered = listOf(VlmRecovery("entry", "chimney", 0.95f)),
            offline = false,
        )
        assertEquals(EvidenceStatus.REVIEW, ignored.status)
        assertFalse(ignored.settled)
        assertTrue(AbstentionReason.VLM_FAILED_REVALIDATION in ignored.reasons)
        assertTrue(ignored.evidence.fields.none { it.value.source == FieldSource.VLM })

        val cleared = PhotoEvidence.applyVlm(
            screening = PhotoEvidence.screen(entry(coverage = 0.01f, hasExtent = false)),
            recovered = listOf(VlmRecovery("extent", "measured", 0.9f)),
            offline = false,
            clearedKeys = setOf("extent"),
        )
        assertEquals(EvidenceStatus.REVIEW, cleared.status)
        assertFalse(cleared.evidence.hasExtent)
        assertTrue(cleared.evidence.fields.none { it.key == "extent" && it.value.value.isNotBlank() })
    }

    @Test
    fun offlineReviewDoesNotApplyVlmOrSettle() {
        val review = PhotoEvidence.screen(entry(score = 0.40f))
        val queued = PhotoEvidence.applyVlm(
            screening = review,
            recovered = listOf(VlmRecovery("extent", "measured", 0.9f)),
            offline = true,
        )
        assertEquals(EvidenceStatus.REVIEW, queued.status)
        assertTrue(queued.queuedOffline)
        assertFalse(queued.settled)
        assertTrue(AbstentionReason.OFFLINE_DEFERRED in queued.reasons)
        assertTrue(queued.evidence.fields.isEmpty())
    }

    @Test
    fun manualOverrideKeepsClearedBlanksAndBlocksLaterVlm() {
        val review = PhotoEvidence.screen(entry(score = 0.40f))
        val manual = PhotoEvidence.manualOverride(
            screening = review,
            fields = mapOf("entry" to "soffit / fascia gap", "notes" to ""),
            clearedKeys = setOf("notes"),
        )
        assertEquals(EvidenceStatus.ACCEPT, manual.status)
        assertEquals(listOf(AbstentionReason.MANUAL_OVERRIDE), manual.reasons)
        assertEquals("", manual.evidence.fields.getValue("notes").value)
        assertEquals(FieldSource.MANUAL, manual.evidence.fields.getValue("notes").source)
        assertEquals("soffit / fascia gap", manual.evidence.fields.getValue("entry").value)
        val again = PhotoEvidence.applyVlm(
            screening = manual,
            recovered = listOf(VlmRecovery("notes", "vlm filled the blank", 0.99f)),
            offline = false,
        )
        assertEquals("", again.evidence.fields.getValue("notes").value)
        assertEquals(FieldSource.MANUAL, again.evidence.fields.getValue("notes").source)
    }

    @Test
    fun offlineQueueIsIdempotentAndWillNotSilentlyComplete() {
        val queue = PhotoReviewQueue()
        val review = PhotoEvidence.screen(entry(score = 0.40f))
        val first = queue.enqueue("frame-a", review, nowMs = 1_000L)
        val second = queue.enqueue("frame-a", review.copy(settled = true), nowMs = 9_000L)
        assertEquals(1_000L, first!!.enqueuedAtMs)
        assertEquals(first, second)
        assertEquals(1, queue.size())
        assertFalse(first.screening.settled)
        assertFalse(queue.complete("frame-a", first.screening))
        assertEquals(1, queue.size())
        assertNull(queue.enqueue("frame-b", PhotoEvidence.screen(entry()), nowMs = 2L))

        val manual = queue.override("frame-a", fields = mapOf("entry" to ""), clearedKeys = setOf("entry"))
        assertEquals("", manual!!.evidence.fields.getValue("entry").value)
        assertTrue(manual.settled)
        assertEquals(0, queue.size())
        assertFalse(queue.complete("frame-a", manual))
    }

    @Test
    fun originalFrameIsAuthoritativeAndDerivedPreviewsStayOutOfExports() {
        val original = Photo(id = "1", localPath = "/sdcard/job/entry.jpg", thumbnailPath = "/sdcard/derived/entry_thumb.jpg")
        val derived = Photo(id = "2", localPath = "/sdcard/derived/enhanced_preview.jpg", filePath = "/sdcard/derived/enhanced_preview.jpg")
        val renamed = Photo(id = "3", localPath = "/sdcard/job/hole_derived.jpg")
        assertEquals("/sdcard/job/entry.jpg", PhotoEvidence.evidentiarySourcePath(original))
        assertEquals(listOf("1"), PhotoEvidence.evidentiaryPhotos(listOf(original, derived, renamed)).map { it.id })
        assertTrue(PhotoEvidence.isDerivedEvidencePath("/sdcard/derived/preview.jpg"))
        assertTrue(PhotoEvidence.isDerivedEvidencePath("/sdcard/job/hole_enhanced_preview.jpg"))
        assertFalse(PhotoEvidence.isDerivedEvidencePath("/sdcard/job/entry.jpg"))

        val frames = listOf(
            EvidenceFrameRef("orig", "/sdcard/job/entry.jpg", FrameAuthority.ORIGINAL, "hash-orig"),
            EvidenceFrameRef(
                "preview",
                "/sdcard/derived/preview.jpg",
                FrameAuthority.DERIVED,
                "hash-preview",
                derivedFromHash = "hash-orig",
            ),
        )
        assertEquals(listOf("orig"), PhotoEvidence.evidentiaryFrames(frames).map { it.id })
        assertEquals(PhotoEvidence.DERIVED_PREVIEW_LABEL, PhotoEvidence.frameLabel(FrameAuthority.DERIVED))
        assertEquals("Original capture", PhotoEvidence.frameLabel(FrameAuthority.ORIGINAL))
        var rejected = false
        try {
            EvidenceFrameRef("bad", "/tmp/x.jpg", FrameAuthority.DERIVED, "hash")
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun regionSelectionPrefersTheLargestInFrameBox() {
        val crop = WildlifeEvidenceDetector.selectRegion(
            frameWidth = 640,
            frameHeight = 480,
            boxes = listOf(
                RegionBox(0, 0, 40, 40),
                RegionBox(10, 10, 200, 180),
                RegionBox(700, 0, 740, 40),
            ),
        )
        assertEquals(10, crop!!.left)
        assertEquals(200, crop.right)
        assertFalse(crop.fullFrame)
        val full = WildlifeEvidenceDetector.selectRegion(640, 480, emptyList())
        assertTrue(full!!.fullFrame)
        assertNull(WildlifeEvidenceDetector.selectRegion(0, 480, emptyList()))
    }

    @Test
    fun countableLabelsSkipEquipmentAndBattery() {
        assertTrue(WildlifeEvidenceDetector.isCountableAnimalLabel("bat"))
        assertTrue(WildlifeEvidenceDetector.isCountableAnimalLabel("raccoon"))
        assertTrue(WildlifeEvidenceDetector.isCountableAnimalLabel("animal"))
        assertFalse(WildlifeEvidenceDetector.isCountableAnimalLabel("battery"))
        assertFalse(WildlifeEvidenceDetector.isCountableAnimalLabel("bat cone"))
        assertFalse(WildlifeEvidenceDetector.isCountableAnimalLabel("live cage trap"))
    }

    @Test
    fun hashAndSharpnessAreDeterministic() {
        val samples = intArrayOf(1, 2, 3, 4)
        assertEquals(PhotoEvidence.hashSamples(8, 8, samples), PhotoEvidence.hashSamples(8, 8, samples))
        assertFalse(PhotoEvidence.hashSamples(8, 8, samples) == PhotoEvidence.hashSamples(8, 8, intArrayOf(1, 2, 3, 5)))
        val flat = IntArray(64) { 40 }
        assertTrue(PhotoEvidence.sharpnessFromLuma(flat, 8) < 0.05f)
        val checker = IntArray(64) { index -> if ((index + index / 8) % 2 == 0) 0 else 255 }
        assertTrue(PhotoEvidence.sharpnessFromLuma(checker, 8) > 0.35f)
    }

    @Test
    fun existingTagAndNoteHelpersStayIntact() {
        val photo = Photo(category = PhotoCategory.DAMAGE, description = "soffit, guano")
        val tags = PhotoEvidence.tags(listOf(photo))
        assertTrue(tags.contains("damage"))
        assertTrue(tags.contains("soffit"))
        assertTrue(tags.contains("guano"))
        assertEquals(listOf("soffit, guano"), PhotoEvidence.notes(listOf(photo)))
    }

    private fun provenance(
        authority: FrameAuthority = FrameAuthority.ORIGINAL,
        bounds: CropBounds = CropBounds(0, 0, 640, 480),
    ) = RegionProvenance(
        cropBounds = bounds,
        sourceFrameHash = "frame-hash",
        transform = CropTransform.IDENTITY,
        timestampMs = 1_700_000_000_000L,
        authority = authority,
        frameWidth = 640,
        frameHeight = 480,
    )

    private fun entry(
        score: Float = 0.80f,
        sharpness: Float = 0.80f,
        coverage: Float = 0.20f,
        hasExtent: Boolean = true,
        provenance: RegionProvenance? = provenance(),
        species: List<ScoredLabel> = emptyList(),
        entries: List<ScoredLabel>? = null,
    ) = StructuredPhotoEvidence(
        task = PhotoAnalysisTask.ENTRY_POINT,
        provenance = provenance,
        species = species,
        entries = entries ?: listOf(ScoredLabel("soffit / fascia gap", score)),
        sharpness = sharpness,
        coverage = coverage,
        hasExtent = hasExtent,
    )
}
