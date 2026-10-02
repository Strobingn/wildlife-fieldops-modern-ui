package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.remote.LiveJobUpsert
import com.strobingn.wildlifefieldops.data.remote.LiveSyncPayloads
import com.strobingn.wildlifefieldops.data.remote.toLocal
import com.strobingn.wildlifefieldops.data.remote.toRemoteDto
import com.strobingn.wildlifefieldops.pricing.JobPricing
import com.strobingn.wildlifefieldops.pricing.PricingJson
import com.strobingn.wildlifefieldops.pricing.hasSyncPayload
import com.strobingn.wildlifefieldops.pricing.isManual
import com.strobingn.wildlifefieldops.pricing.markManual
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Manual-entry rule for the 12 features that resurrected blanks or overwrote
 * typed text: (a) clear → save → reload/recompute/sync stays blank;
 * (b) typed value + Suggest leaves the typed value unchanged.
 */
class ManualEntryRuleTest {

    @Test
    fun speciesBlankSurvivesSaveReloadAndSync() {
        val saved = JobFieldOpsCodec.mergeForSave(
            Job(
                confirmedSpecies = "",
                type = "Raccoon",
                pricing = JobPricing(confirmedSpecies = "raccoon").markManual(ManualField.SPECIES)
            )
        )
        assertEquals("", saved.confirmedSpecies)
        assertEquals("", saved.pricing.confirmedSpecies)
        val reloaded = JobFieldOpsCodec.applyFromPricing(
            Job(type = "Raccoon", pricing = PricingJson.decode(PricingJson.encode(saved.pricing)))
        )
        assertEquals("", reloaded.confirmedSpecies)
        val remote = saved.toRemoteDto().toLocal(existing = Job(confirmedSpecies = "raccoon", type = "Raccoon"))
        assertEquals("", remote.confirmedSpecies)
        assertTrue(remote.pricing.isManual(ManualField.SPECIES))
        assertBlankPricingRoundTrip(saved.pricing, "confirmedSpecies")
    }

    @Test
    fun speciesSuggestDoesNotOverwriteTyped() {
        val typed = "Sir said skunk"
        assertEquals(typed, OperatorWins.suggest(typed, "raccoon", manual = false))
        assertEquals("", OperatorWins.suggest("", "raccoon", manual = true))
        assertEquals("raccoon", OperatorWins.preview(typed, "raccoon", manual = false))
    }

    @Test
    fun legalNotesBlankSurvivesSaveReloadAndSync() {
        val catalog = SpeciesJobLegal.catalogText("raccoon")
        assertTrue(catalog.isNotBlank())
        assertEquals("", SpeciesJobLegal.card("raccoon", savedNotes = "").displayNotes)
        val saved = JobFieldOpsCodec.mergeForSave(
            Job(
                legalNotes = "",
                confirmedSpecies = "raccoon",
                pricing = JobPricing(legalNotes = catalog).markManual(ManualField.LEGAL_NOTES)
            )
        )
        assertEquals("", saved.legalNotes)
        assertEquals("", saved.pricing.legalNotes)
        val reloaded = JobFieldOpsCodec.applyFromPricing(Job(pricing = saved.pricing))
        assertEquals("", reloaded.legalNotes)
        assertBlankPricingRoundTrip(saved.pricing, "legalNotes")
    }

    @Test
    fun legalNotesSuggestDoesNotOverwriteTyped() {
        val catalog = SpeciesJobLegal.catalogText("raccoon")
        val typed = "Sir's rewrite"
        assertEquals(typed, OperatorWins.suggest(typed, catalog, manual = false))
        assertEquals("", OperatorWins.suggest("", catalog, manual = true))
    }

    @Test
    fun nextStepBlankSurvivesSaveReloadAndDoesNotRegen() {
        val ai = JobNextStepEngine.suggest(NextStepInput(status = "IN_PROGRESS", species = "raccoon", notes = "trap set"))
        val saved = JobFieldOpsCodec.mergeForSave(
            Job(
                nextStep = "",
                nextStepDueAt = null,
                pricing = JobPricing(nextStep = ai.text, nextStepDueAt = ai.dueAt)
                    .markManual(ManualField.NEXT_STEP, ManualField.NEXT_STEP_DUE)
            )
        )
        assertEquals("", saved.nextStep)
        assertEquals(null, saved.nextStepDueAt)
        val reloaded = JobFieldOpsCodec.applyFromPricing(Job(pricing = saved.pricing))
        assertEquals("", reloaded.nextStep)
        assertEquals(null, reloaded.nextStepDueAt)
        assertBlankPricingRoundTrip(saved.pricing, "nextStep")
    }

    @Test
    fun nextStepSuggestDoesNotOverwriteTyped() {
        val ai = JobNextStepEngine.suggest(NextStepInput(status = "IN_PROGRESS", species = "raccoon"))
        val typed = "Call Pat at 7"
        assertEquals(typed, OperatorWins.suggest(typed, ai.text, manual = false))
        assertEquals("", OperatorWins.suggest("", ai.text, manual = true))
    }

    @Test
    fun followUpNotesBlankSurvivesSaveReloadAndSync() {
        val saved = JobFieldOpsCodec.mergeForSave(
            Job(
                followUpKind = FollowUpKind.TRAP_PULL.name,
                followUpNotes = "",
                pricing = JobPricing(followUpNotes = "AI pull traps").markManual(ManualField.FOLLOW_NOTES)
            )
        )
        assertEquals("", saved.followUpNotes)
        assertEquals("", saved.pricing.followUpNotes)
        val reloaded = JobFieldOpsCodec.applyFromPricing(Job(pricing = saved.pricing))
        assertEquals("", reloaded.followUpNotes)
        assertBlankPricingRoundTrip(saved.pricing, "followUpNotes")
    }

    @Test
    fun followUpSuggestDoesNotOverwriteTyped() {
        val draft = FollowUpPlanner.suggest(FollowUpInput(species = "raccoon", hasActiveTraps = true))
        val typed = "Sir will pull Friday"
        assertEquals(typed, OperatorWins.suggest(typed, draft.notes, manual = false))
        assertEquals("", OperatorWins.suggest("", draft.notes, manual = true))
    }

    @Test
    fun narrativeBlankClearedStaysBlankOnApplyAndReloadPack() {
        val current = InspectionNarrativeDraft(findings = "", recommendations = "")
        val ai = InspectionNarrativeEngine.draft(
            InspectionEvidence(jobAddress = "12 Oak", photoTags = listOf("raccoon", "soffit"))
        )
        val cleared = setOf(ManualField.NARRATIVE_FINDINGS, ManualField.NARRATIVE_RECS)
        val merged = InspectionNarrativeEngine.apply(current, ai, replace = false, cleared = cleared)
        assertEquals("", merged.findings)
        assertEquals("", merged.recommendations)
        val packed = NarrativeCleared.pack("heuristic", cleared)
        assertEquals(cleared, NarrativeCleared.cleared(packed))
        assertEquals("heuristic", NarrativeCleared.source(packed))
    }

    @Test
    fun narrativeSuggestDoesNotOverwriteTyped() {
        val current = InspectionNarrativeDraft(findings = "I already typed this", recommendations = "My recs")
        val ai = InspectionNarrativeDraft(findings = "AI paragraph", recommendations = "AI recs")
        val merged = InspectionNarrativeEngine.apply(current, ai, replace = false)
        assertEquals("I already typed this", merged.findings)
        assertEquals("My recs", merged.recommendations)
    }

    @Test
    fun photoTagsBlankClearedStayBlankThroughMergeSaveAndJson() {
        val previous = PhotoAutoTags.persistTyped("p1", "", "chew", "soffit", "", previous = null)
        assertTrue(ManualField.PHOTO_SPECIES in previous.clearedKeys)
        val ai = PhotoAutoTags.suggest("raccoon chew at the soffit")
        val merged = PhotoAutoTags.mergeOperatorWins(ai, previous)
        assertEquals("", merged.species)
        val decoded = PricingJson.decode(
            PricingJson.encode(JobPricing(photoAutoTags = listOf(merged)).also { assertTrue(it.hasSyncPayload()) })
        )
        assertEquals("", decoded.photoAutoTags.first().species)
        assertTrue(ManualField.PHOTO_SPECIES in decoded.photoAutoTags.first().clearedKeys)
    }

    @Test
    fun photoTagsSuggestDoesNotOverwriteTyped() {
        val typed = SyncedPhotoTag(photoId = "p", species = "Sir said skunk", damage = "my chew")
        val ai = PhotoAutoTags.suggest("raccoon chew at the soffit one-way door")
        val merged = PhotoAutoTags.mergeOperatorWins(ai, typed)
        assertEquals("Sir said skunk", merged.species)
        assertEquals("my chew", merged.damage)
    }

    @Test
    fun weatherAdviceBlankSurvivesSaveReloadAndSync() {
        val saved = JobFieldOpsCodec.mergeForSave(
            Job(
                weatherTrapAdvice = "",
                pricing = JobPricing(weatherTrapAdvice = "Check at dawn").markManual(ManualField.WEATHER)
            )
        )
        assertEquals("", saved.weatherTrapAdvice)
        assertEquals("", saved.pricing.weatherTrapAdvice)
        val reloaded = JobFieldOpsCodec.applyFromPricing(Job(pricing = saved.pricing))
        assertEquals("", reloaded.weatherTrapAdvice)
        assertBlankPricingRoundTrip(saved.pricing, "weatherTrapAdvice")
    }

    @Test
    fun weatherAdviceSuggestDoesNotOverwriteTyped() {
        val typed = "Sir skips the rain"
        assertEquals(typed, OperatorWins.suggest(typed, "Check after the rain", manual = false))
        assertEquals("", OperatorWins.suggest("", "Check after the rain", manual = true))
    }

    @Test
    fun seasonalBlankTitleNotesSurvivePricingRoundTrip() {
        val pricing = JobPricing(
            seasonalKind = SeasonalKind.SPRING_BATS.name,
            seasonalTitle = "",
            seasonalNotes = "",
            seasonalDueAt = null
        ).markManual(ManualField.SEASONAL_TITLE, ManualField.SEASONAL_NOTES, ManualField.SEASONAL_DUE)
        val decoded = PricingJson.decode(PricingJson.encode(pricing))
        assertEquals("", decoded.seasonalTitle)
        assertEquals("", decoded.seasonalNotes)
        assertEquals(null, decoded.seasonalDueAt)
        assertTrue(decoded.isManual(ManualField.SEASONAL_TITLE))
        val suggested = SeasonalReminder.suggest("bat")
        assertEquals("", OperatorWins.suggest(decoded.seasonalTitle, suggested.title, decoded.isManual(ManualField.SEASONAL_TITLE)))
    }

    @Test
    fun seasonalSuggestDoesNotOverwriteTyped() {
        val suggested = SeasonalReminder.suggest("bat")
        val typedTitle = "Sir's spring walk"
        assertEquals(typedTitle, OperatorWins.suggest(typedTitle, suggested.title, manual = false))
        assertEquals("keep", OperatorWins.suggest("keep", suggested.notes, manual = false))
    }

    @Test
    fun warrantyBlankCoveredSurvivesPricingRoundTrip() {
        val pricing = JobPricing(
            warrantyStartAt = 1_700_000_000_000L,
            warrantyTermMonths = 12,
            warrantyCovered = ""
        ).markManual(ManualField.WARRANTY_COVERED)
        val decoded = PricingJson.decode(PricingJson.encode(pricing))
        assertEquals("", decoded.warrantyCovered)
        val plan = WarrantyTracker.fromJob(decoded.warrantyStartAt, decoded.warrantyTermMonths, decoded.warrantyCovered)
        assertEquals("", plan.covered)
        assertTrue(decoded.isManual(ManualField.WARRANTY_COVERED))
    }

    @Test
    fun warrantyRecomputeDoesNotOverwriteTypedCovered() {
        val typed = "Exclusion only — not trapping"
        val plan = WarrantyTracker.fromJob(1_700_000_000_000L, 12, typed)
        assertEquals(typed, plan.covered)
        assertEquals(typed, OperatorWins.suggest(typed, "Full wildlife work", manual = false))
    }

    @Test
    fun messageDraftClearedBlankStaysBlankOnSuggest() {
        val draft = CustomerMessageDraft.draft(CustomerMessageKind.ON_THE_WAY, "Pat", "Attic raccoon", "12 Oak")
        assertEquals("", OperatorWins.suggest("", draft.subject, manual = true))
        assertEquals("", OperatorWins.suggest("", draft.body, manual = true))
        val packed = NarrativeCleared.pack("", setOf(ManualField.MESSAGE_SUBJECT, ManualField.MESSAGE_BODY))
        assertTrue(ManualField.MESSAGE_SUBJECT in NarrativeCleared.cleared(packed))
    }

    @Test
    fun messageDraftSuggestDoesNotOverwriteTyped() {
        val draft = CustomerMessageDraft.draft(CustomerMessageKind.ESTIMATE, "Pat", "Attic raccoon", "12 Oak", amount = 850.0)
        val typedSubject = "My subject"
        val typedBody = "My body"
        assertEquals(typedSubject, OperatorWins.suggest(typedSubject, draft.subject, manual = false))
        assertEquals(typedBody, OperatorWins.suggest(typedBody, draft.body, manual = false))
        assertNotEquals(typedBody, draft.body)
    }

    @Test
    fun decLogClearedCellStaysBlankOnLaterAutoFill() {
        val job = Job(id = "j1", customerName = "Pat", address = "1 Main", confirmedSpecies = "Squirrel", completedDate = 1L)
        val auto = DecNwcoLog.autoFill(NwcoAutoInput(jobs = listOf(job), traps = emptyList()))
        val cleared = DecNwcoLog.withTyped(auto.first(), "species", "")
            .let { DecNwcoLog.withTyped(it, "town", "") }
        val later = DecNwcoLog.autoFill(NwcoAutoInput(jobs = listOf(job.copy(confirmedSpecies = "Bat")), traps = emptyList()))
        val merged = DecNwcoLog.merge(later, listOf(cleared))
        assertEquals(1, merged.size)
        assertEquals("", merged.first().species)
        assertEquals("", merged.first().town)
        assertTrue("species" in merged.first().locked)
        val decoded = PricingJson.decode(PricingJson.encode(JobPricing(decNwcoRows = merged)))
        assertEquals("", decoded.decNwcoRows.first().species)
    }

    @Test
    fun decLogSuggestAutoFillDoesNotOverwriteTyped() {
        val job = Job(id = "j1", customerName = "Pat", confirmedSpecies = "Squirrel", completedDate = 1L)
        val auto = DecNwcoLog.autoFill(NwcoAutoInput(jobs = listOf(job), traps = emptyList()))
        val typed = DecNwcoLog.withTyped(auto.first(), "species", "Gray squirrel Sir confirmed")
        val later = DecNwcoLog.autoFill(NwcoAutoInput(jobs = listOf(job.copy(confirmedSpecies = "Bat")), traps = emptyList()))
        val merged = DecNwcoLog.merge(later, listOf(typed))
        assertEquals("Gray squirrel Sir confirmed", merged.first().species)
    }

    @Test
    fun taxOverrideEmptyLockStaysZeroOnRecompute() {
        val day = 1_712_764_800_000L
        val invoice = Invoice(
            id = "inv1",
            jobId = "j1",
            issueDate = day,
            status = InvoiceStatus.PAID,
            totalAmount = 100.0,
            amountPaid = 100.0,
            subtotal = 92.5,
            taxAmount = 7.5
        )
        val (start, end) = NySalesTaxPeriods.monthBounds(day)
        val locked = EarningsPeriodOverride(
            grain = "MONTH",
            startMs = start,
            paid = EarningsTaxEngine.parseLock(""),
            locked = setOf(ManualField.TAX_PAID)
        )
        assertEquals(0.0, locked.paid!!, 0.0)
        val snap = EarningsTaxEngine.snapshot(listOf(invoice), emptyList(), emptyList(), listOf(locked), start, end)
        assertEquals(0.0, snap.paid, 0.0)
        assertTrue(snap.overrideApplied)
        val decoded = PricingJson.decode(PricingJson.encode(JobPricing(earningsPeriodOverrides = listOf(locked))))
        assertEquals(0.0, decoded.earningsPeriodOverrides.first().paid!!, 0.0)
        assertTrue(ManualField.TAX_PAID in decoded.earningsPeriodOverrides.first().locked)
    }

    @Test
    fun taxOverrideTypedValueUnchangedOnRecompute() {
        val day = 1_712_764_800_000L
        val invoice = Invoice(
            id = "inv1",
            jobId = "j1",
            issueDate = day,
            status = InvoiceStatus.PAID,
            totalAmount = 100.0,
            amountPaid = 100.0,
            subtotal = 92.5,
            taxAmount = 7.5
        )
        val (start, end) = NySalesTaxPeriods.monthBounds(day)
        val locked = EarningsPeriodOverride(
            grain = "MONTH",
            startMs = start,
            paid = 50.0,
            locked = setOf(ManualField.TAX_PAID)
        )
        val snap = EarningsTaxEngine.snapshot(listOf(invoice), emptyList(), emptyList(), listOf(locked), start, end)
        assertEquals(50.0, snap.paid, 0.0)
        assertNotEquals(100.0, snap.paid)
    }

    @Test
    fun liveUpsertEncodesBlankManualFieldsInPricingJson() {
        val job = Job(
            id = "11111111-1111-1111-1111-111111111111",
            title = "Cornwall raccoon",
            customerName = "Pat",
            confirmedSpecies = "",
            legalNotes = "",
            nextStep = "",
            weatherTrapAdvice = "",
            followUpNotes = "",
            pricing = JobPricing(
                confirmedSpecies = "",
                legalNotes = "",
                nextStep = "",
                weatherTrapAdvice = "",
                followUpNotes = "",
                warrantyCovered = "",
                seasonalTitle = "",
                seasonalNotes = ""
            ).markManual(
                ManualField.SPECIES,
                ManualField.LEGAL_NOTES,
                ManualField.NEXT_STEP,
                ManualField.WEATHER,
                ManualField.FOLLOW_NOTES,
                ManualField.WARRANTY_COVERED,
                ManualField.SEASONAL_TITLE,
                ManualField.SEASONAL_NOTES
            )
        )
        val payload = LiveSyncPayloads.job(job)
        val encoded = LiveSyncPayloads.json.encodeToJsonElement(LiveJobUpsert.serializer(), payload).jsonObject
        val pricing = encoded.getValue("pricing").jsonObject
        assertEquals("", pricing.getValue("confirmedSpecies").jsonPrimitive.content)
        assertEquals("", pricing.getValue("legalNotes").jsonPrimitive.content)
        assertEquals("", pricing.getValue("nextStep").jsonPrimitive.content)
        assertTrue(pricing.getValue("manualFields").toString().contains(ManualField.SPECIES))
        val pulled = job.toRemoteDto().copy(pricing = job.pricing).toLocal()
        assertEquals("", pulled.confirmedSpecies)
        assertEquals("", pulled.legalNotes)
        assertEquals("", pulled.nextStep)
    }

    private fun assertBlankPricingRoundTrip(pricing: JobPricing, field: String) {
        val decoded = PricingJson.decode(PricingJson.encode(pricing))
        when (field) {
            "confirmedSpecies" -> assertEquals("", decoded.confirmedSpecies)
            "legalNotes" -> assertEquals("", decoded.legalNotes)
            "nextStep" -> assertEquals("", decoded.nextStep)
            "followUpNotes" -> assertEquals("", decoded.followUpNotes)
            "weatherTrapAdvice" -> assertEquals("", decoded.weatherTrapAdvice)
            else -> error(field)
        }
        assertTrue(decoded.hasSyncPayload())
    }
}
