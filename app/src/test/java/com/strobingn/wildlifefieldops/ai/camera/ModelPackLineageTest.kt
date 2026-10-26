package com.strobingn.wildlifefieldops.ai.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ModelPackLineageTest {

    @Test
    fun bundledSidecarCarriesLineageAndUsDateDisplay() {
        val file = listOf(
            File("src/main/assets/models/model_meta.json"),
            File("app/src/main/assets/models/model_meta.json"),
        ).first { it.isFile }
        val meta = ModelPackLineage.parse(file.readText())
        assertTrue(meta.complete)
        assertEquals("mobilenetv2-imagenet-tf2", meta.parentModelDigest)
        assertEquals("2026-09-12", meta.sourceDataCutoff)
        assertEquals("2.3.3-wildlife-retrain", meta.reviewVersion)
        assertEquals(
            "sha256:cabf6387a3f70f67d42cfadb5b3ce49e5f49fb0b6eba07da1de1eaba751bcb98",
            meta.buildDigest,
        )
        assertEquals(0L, meta.inheritedLagHours)
        assertEquals("09/12/2026", ModelPackLineage.displayCutoff(meta.sourceDataCutoff))
        assertEquals("2026-10-21", ModelPackLineage.canonicalCutoff("10/21/2026"))
    }

    @Test
    fun newerFileTimestampDoesNotPromoteAStaleCutoff() {
        val bundled = pack("sha256:bundled", cutoff = "2026-09-12", lag = 0)
        val stale = pack("sha256:stale-rebuild", cutoff = "2026-08-01", lag = 0)
        val olderFile = ModelPackLineage.plan(bundled, stale, downloadedFileTimestampMs = 10L, ledger = emptyList(), nowMs = 100L)
        val newerFile = ModelPackLineage.plan(bundled, stale, downloadedFileTimestampMs = 9_000L, ledger = emptyList(), nowMs = 100L)
        assertFalse(olderFile.useCandidate)
        assertFalse(newerFile.useCandidate)
        assertEquals(olderFile.useCandidate, newerFile.useCandidate)
        assertEquals(olderFile.reasons, newerFile.reasons)
        assertTrue(newerFile.reasons.single().contains("stale source cutoff"))
        assertEquals(bundled.buildDigest, newerFile.activeDigest)
        assertTrue(newerFile.ignoredFileTimestampMs > olderFile.ignoredFileTimestampMs)
    }

    @Test
    fun newerCutoffPromotesEvenWhenTheFileTimestampIsOlder() {
        val bundled = pack("sha256:bundled", cutoff = "2026-09-12", lag = 0)
        val fresher = pack("sha256:fresher", cutoff = "2026-10-21", lag = 4)
        val plan = ModelPackLineage.plan(
            bundled = bundled,
            downloaded = fresher,
            downloadedFileTimestampMs = 1L,
            ledger = emptyList(),
            nowMs = 500L,
        )
        assertTrue(plan.useCandidate)
        assertEquals(fresher.buildDigest, plan.activeDigest)
        assertEquals(WeightSource.CACHED_DOWNLOAD, plan.weightSource)
        assertEquals(listOf(bundled.buildDigest, fresher.buildDigest), plan.ledger.map { ModelPackLineage.effectiveDigest(it) })
        assertTrue(ModelPackLineage.verifyAppendOnly(emptyList(), plan.ledger))
    }

    @Test
    fun equalCutoffRejectsHigherInheritedLagAndOlderReview() {
        val active = pack("sha256:active", cutoff = "2026-09-12", lag = 0, review = "2.3.3-wildlife-retrain")
        val laggy = pack("sha256:laggy", cutoff = "2026-09-12", lag = 400, review = "2.3.4-wildlife-retrain")
        val olderReview = pack("sha256:older", cutoff = "2026-09-12", lag = 0, review = "2.3.2-wildlife-retrain")
        val sameLineage = pack("sha256:copy", cutoff = "2026-09-12", lag = 0, review = "2.3.3-wildlife-retrain")
        val newerReview = pack("sha256:reviewed", cutoff = "2026-09-12", lag = 0, review = "2.3.4-wildlife-retrain")

        assertTrue(reason(active, laggy).contains("inherited lag"))
        assertTrue(reason(active, olderReview).contains("older review"))
        assertTrue(reason(active, sameLineage).contains("same lineage"))
        val promoted = ModelPackLineage.plan(active, newerReview, 50L, ledger = emptyList(), nowMs = 80L)
        assertTrue(promoted.useCandidate)
        assertEquals(newerReview.buildDigest, promoted.activeDigest)
    }

    @Test
    fun sizeMismatchAndIncompleteLineageStayOnTheBundledPack() {
        val bundled = pack("sha256:bundled", cutoff = "2026-09-12", lag = 0, size = 100)
        val mismatched = pack("sha256:other", cutoff = "2026-10-21", lag = 0, size = 100)
        val mismatch = ModelPackLineage.plan(
            bundled = bundled,
            downloaded = mismatched,
            downloadedFileTimestampMs = 10_000L,
            ledger = emptyList(),
            nowMs = 11L,
            cachedWeightBytes = 50L,
        )
        assertFalse(mismatch.useCandidate)
        assertTrue(mismatch.reasons.single().contains("sizeBytes"))

        val incomplete = mismatched.copy(parentModelDigest = "")
        val rejected = ModelPackLineage.plan(bundled, incomplete, 99L, emptyList(), 100L)
        assertFalse(rejected.useCandidate)
        assertTrue(rejected.reasons.single().contains("incomplete"))
    }

    @Test
    fun rollbackAppendsAndRestoresThePriorPack() {
        val bundled = pack("sha256:bundled", cutoff = "2026-09-12", lag = 0)
        val fresher = pack("sha256:fresher", cutoff = "2026-10-21", lag = 1)
        val activated = ModelPackLineage.plan(bundled, fresher, 5L, emptyList(), nowMs = 20L)
        val rolled = ModelPackLineage.rollback(activated.ledger, listOf(bundled, fresher), nowMs = 40L)
        assertTrue(rolled.changed)
        assertEquals(bundled.buildDigest, rolled.activeDigest)
        assertEquals(LedgerAction.ROLLBACK, rolled.ledger.last().action)
        assertEquals(3, rolled.ledger.size)
        assertTrue(ModelPackLineage.verifyAppendOnly(activated.ledger, rolled.ledger))
        assertFalse(ModelPackLineage.verifyAppendOnly(rolled.ledger, activated.ledger))
        val tampered = rolled.ledger.drop(1)
        assertFalse(ModelPackLineage.verifyAppendOnly(rolled.ledger, tampered))
    }

    @Test
    fun ledgerRoundTripKeepsOrderAndStopsAtAGap() {
        val bundled = pack("sha256:bundled", cutoff = "2026-09-12", lag = 0)
        val plan = ModelPackLineage.plan(bundled, null, 0L, emptyList(), nowMs = 7L)
        val text = ModelPackLineage.writeLedger(plan.ledger)
        val read = ModelPackLineage.readLedger(text)
        assertEquals(plan.ledger, read)
        val gapped = read + read.last().copy(sequence = 4L)
        assertEquals(1, ModelPackLineage.validPrefix(gapped).size)
        var rejected = false
        try {
            ModelPackLineage.append(gapped, read.last().copy(sequence = 5L))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    private fun reason(active: ModelPackMeta, candidate: ModelPackMeta): String =
        ModelPackLineage.plan(active, candidate, downloadedFileTimestampMs = 123L, ledger = emptyList(), nowMs = 200L)
            .reasons.single()

    private fun pack(
        digest: String,
        cutoff: String,
        lag: Long,
        review: String = "2.3.3-wildlife-retrain",
        size: Long = 0L,
    ) = ModelPackMeta(
        parentModelDigest = "mobilenetv2-imagenet-tf2",
        sourceDataCutoff = cutoff,
        reviewVersion = review,
        buildDigest = digest,
        inheritedLagHours = lag,
        sizeBytes = size,
    )
}
