package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualEntryTest {

    @Test
    fun badDateIsAnErrorAndBlankIsNotToday() {
        val bad = FieldDate.parseDay("tomorrow")
        assertEquals(FieldDate.ERROR, bad.error)
        assertNull(bad.millis)
        val blank = FieldDate.parseDay("  ")
        assertNull(blank.error)
        assertNull(blank.millis)
        val day = FieldDate.parseDay("2026-03-02")
        assertNull(day.error)
        assertEquals("2026-03-02", FieldDate.formatDay(day.millis!!))
    }

    @Test
    fun followUpSuggestFillsOnlyBlanks() {
        val suggestion = FollowUpDraft(
            kind = FollowUpKind.TRAP_PULL,
            title = "Pull",
            notes = "AI notes",
            dueAt = 50L
        )
        val kept = FollowUpPlanner.fillBlanks(
            kind = FollowUpKind.WARRANTY,
            notes = "Sir typed this",
            dueText = "2026-04-01",
            suggestion = suggestion
        ) { "2026-05-01" }
        assertEquals(FollowUpKind.WARRANTY, kept.first)
        assertEquals("Sir typed this", kept.second)
        assertEquals("2026-04-01", kept.third)

        val filled = FollowUpPlanner.fillBlanks(null, "", "", suggestion) { "2026-05-01" }
        assertEquals(FollowUpKind.TRAP_PULL, filled.first)
        assertEquals("AI notes", filled.second)
        assertEquals("2026-05-01", filled.third)
    }

    @Test
    fun seasonalSuggestFillsBlanksAndReusesReminder() {
        val suggestion = SeasonalReminder.suggest("bat", now = 1_700_000_000_000L)
        val kept = SeasonalReminder.fillBlankFields(
            kind = SeasonalKind.FALL_RODENTS,
            title = "Sir's title",
            notes = "Sir's notes",
            dueText = "2026-10-01",
            suggestion = suggestion,
            dueTextFor = { "ignored" }
        )
        assertEquals(SeasonalKind.FALL_RODENTS, kept.kind)
        assertEquals("Sir's title", kept.title)
        assertEquals("Sir's notes", kept.notes)
        assertEquals("2026-10-01", kept.dueText)

        val filled = SeasonalReminder.fillBlankFields(null, "", "", "", suggestion) { "2026-03-15" }
        assertEquals(suggestion.kind, filled.kind)
        assertEquals(suggestion.title, filled.title)
        assertEquals("2026-03-15", filled.dueText)

        assertEquals("existing", SeasonalReminder.reminderIdForSave("existing", 10L, "new"))
        assertEquals("new", SeasonalReminder.reminderIdForSave("", 10L, "new"))
        assertEquals("", SeasonalReminder.reminderIdForSave("existing", null, "new"))
    }

    @Test
    fun checklistLoadKeepsHandEntries() {
        val hand = ChecklistItemRecord(id = "hand", species = "custom", label = "Walk the ridge", done = true, notes = "kits")
        val merged = SpeciesChecklist.mergeMissing(listOf(hand), "raccoon")
        assertEquals(hand, merged.first())
        assertTrue(merged.size > 1)
        assertTrue(merged.drop(1).all { it.label != hand.label })
        val again = SpeciesChecklist.mergeMissing(merged, "raccoon")
        assertEquals(merged.size, again.size)
    }

    @Test
    fun photoSaveKeepsBlankTagsAndClearsTagLine() {
        val ai = PhotoAutoTags.suggest("raccoon chew at the soffit")
        val saved = PhotoAutoTags.keepTyped(SyncedPhotoTag(photoId = "p", damage = "Sir typed chew"))
        assertEquals("", saved.species)
        assertEquals("Sir typed chew", saved.damage)
        assertTrue(ai.species.isNotBlank())
        val cleared = SearchFieldOpsStore.applyTagsToDescription("Porch\nTags: raccoon · soffit", SyncedPhotoTag(photoId = "p"))
        assertEquals("Porch", cleared)
    }

    @Test
    fun editingMaterialRestoresTheDifference() {
        assertEquals(6.0, InventoryDeduct.adjust(current = 5.0, oldQty = 2.0, newQty = 1.0), 0.0)
        assertEquals(4.0, InventoryDeduct.adjust(current = 5.0, oldQty = 2.0, newQty = 3.0), 0.0)
        assertEquals(7.0, InventoryDeduct.restore(5.0, 2.0), 0.0)
    }

    @Test
    fun narrativeTypedWhileDraftLoadsIsKept() {
        val typedWhileLoading = InspectionNarrativeDraft(findings = "Typed while the draft loaded", notes = "my notes")
        val arrived = InspectionNarrativeDraft(findings = "AI paragraph that arrived late", notes = "AI notes")
        val kept = InspectionNarrativeEngine.apply(typedWhileLoading, arrived, replace = false)
        assertEquals("Typed while the draft loaded", kept.findings)
        assertEquals("my notes", kept.notes)
        val emptyAi = InspectionNarrativeEngine.apply(typedWhileLoading, InspectionNarrativeDraft(), replace = true)
        assertEquals("Typed while the draft loaded", emptyAi.findings)
    }

    @Test
    fun mileageUpsertReplacesSameIdAndAllowsNoJob() {
        val first = MileageLogEntry(id = "m1", date = 20L, miles = 4.0, jobId = "", jobTitle = "No job")
        val edited = first.copy(miles = 9.0)
        val rows = MileageTaxLog.upsert(listOf(first), edited)
        assertEquals(1, rows.size)
        assertEquals(9.0, rows[0].miles, 0.0)
        assertEquals("", rows[0].jobId)
    }

    @Test
    fun trapEditorPrefillsTodayAndPlannerNextCheck() {
        val now = 1_712_000_000_000L
        val open = TrapCheckPlanner.editorDates(TrapStatus.SET, now)
        assertEquals(FieldDate.formatDay(now), open.checkDay)
        val next = TrapCheckPlanner.nextCheckAfter(TrapStatus.SET, now)!!
        assertEquals(FieldDate.formatDay(next), open.nextCheckDay)
        assertTrue(open.nextCheckDay.isNotBlank())
        val pulled = TrapCheckPlanner.editorDates(TrapStatus.REMOVED, now)
        assertEquals(FieldDate.formatDay(now), pulled.checkDay)
        assertEquals("", pulled.nextCheckDay)
    }

    @Test
    fun mileageMoveDropsTheCopyOnTheOldJob() {
        val entry = MileageLogEntry(id = "m1", date = 20L, miles = 12.0, jobId = "job-a", jobTitle = "Oak")
        val start = mapOf(
            "job-a" to listOf(entry),
            "job-b" to emptyList(),
            OpsLedger.ID to emptyList<MileageLogEntry>()
        )
        val toOther = MileageTaxLog.relocate(start, entry.copy(jobId = "job-b", jobTitle = "Maple"), "job-b")
        assertTrue(toOther.getValue("job-a").isEmpty())
        assertEquals("job-b", toOther.getValue("job-b").single().jobId)
        assertEquals(12.0, toOther.getValue("job-b").single().miles, 0.0)
        val toNone = MileageTaxLog.relocate(toOther, entry.copy(jobId = "", jobTitle = "No job"), OpsLedger.ID)
        assertTrue(toNone.getValue("job-a").isEmpty())
        assertTrue(toNone.getValue("job-b").isEmpty())
        val loose = toNone.getValue(OpsLedger.ID).single()
        assertEquals("", loose.jobId)
        assertEquals("m1", loose.id)
    }

    @Test
    fun jobSearchBoxIsTypedTextOnly() {
        val rows = listOf(
            JobSearchRow("1", "Oak attic", "Oak attic Pat"),
            JobSearchRow("2", "Maple barn", "Maple barn Lee")
        )
        assertEquals("M", JobSearch.fieldValue("M"))
        assertEquals(listOf("Maple barn"), JobSearch.filter(rows, JobSearch.fieldValue("M")).map { it.label })
        assertTrue(JobSearch.filter(rows, "Oak atticM").isEmpty())
        assertEquals("Selected: Oak attic", JobSearch.selectionNote("Oak attic", "", 2))
    }

    @Test
    fun blankTaxLockUnlocksAndTypedZeroStays() {
        val day = 1_712_764_800_000L
        val invoice = Invoice(
            id = "inv1",
            jobId = "j1",
            issueDate = day,
            status = InvoiceStatus.PAID,
            totalAmount = 100.0,
            amountPaid = 100.0
        )
        val (start, end) = NySalesTaxPeriods.monthBounds(day)
        val base = EarningsPeriodOverride(grain = "MONTH", startMs = start)
        val locked = EarningsTaxEngine.applyLock(base, "paid", "0.00")!!
        assertEquals(0.0, locked.paid!!, 0.0)
        assertTrue(ManualField.TAX_PAID in locked.locked)
        val held = EarningsTaxEngine.snapshot(listOf(invoice), emptyList(), emptyList(), listOf(locked), start, end)
        assertEquals(0.0, held.paid, 0.0)
        val cleared = EarningsTaxEngine.applyLock(locked, "paid", " ")!!
        assertNull(cleared.paid)
        assertFalse(ManualField.TAX_PAID in cleared.locked)
        val back = EarningsTaxEngine.snapshot(listOf(invoice), emptyList(), emptyList(), listOf(cleared), start, end)
        assertEquals(100.0, back.paid, 0.0)
        assertNull(EarningsTaxEngine.applyLock(base, "paid", "nope"))
    }

    @Test
    fun pairEditAndRemove() {
        val pair = BeforeAfterPair.pair("b", "a", "first")
        val edited = BeforeAfterPair.upsert(listOf(pair), pair.copy(notes = "revised"))
        assertEquals(1, edited.size)
        assertEquals("revised", edited[0].notes)
        assertTrue(BeforeAfterPair.remove(edited, pair.id).isEmpty())
    }
}
