package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.data.model.JobCustomerDraft
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TextImportEntryTest {

    private val sample =
        "Hi this is Maria Lopez, 22 Oak St Newburgh NY 12550, 845-555-0199, maria@example.com, raccoon in the attic"

    @Before
    @After
    fun clearInboxes() {
        TextShareInbox.peek()?.let { TextShareInbox.consume(it.id) }
        CustomerTextInbox.peek()?.let { CustomerTextInbox.consume(it.id) }
    }

    @Test
    fun boxPrefillsFromClipboardText() {
        assertEquals(sample, TextImportEntry.initialText(sample))
        assertEquals(sample, TextImportEntry.initialText("  $sample\n"))
    }

    @Test
    fun emptyClipboardLeavesBoxEmptyForTyping() {
        assertEquals("", TextImportEntry.initialText(null))
        assertEquals("", TextImportEntry.initialText("   "))
        assertFalse(TextImportEntry.canRead(""))
        assertFalse(TextImportEntry.canRead("  \n "))
    }

    @Test
    fun pasteFillsEmptyBox() {
        assertEquals(sample, TextImportEntry.paste("", sample))
        assertEquals(sample, TextImportEntry.paste("   ", " $sample "))
    }

    @Test
    fun pasteNeverReplacesTypedText() {
        val typed = "Call back after 5"
        val pasted = TextImportEntry.paste(typed, sample)
        assertTrue(pasted.startsWith(typed))
        assertTrue(pasted.endsWith(sample))
        assertEquals(pasted, TextImportEntry.paste(pasted, sample))
        assertEquals(typed, TextImportEntry.paste(typed, null))
        assertEquals(typed, TextImportEntry.paste(typed, "  "))
    }

    @Test
    fun handoffTrimsAndSkipsEmpty() {
        assertNull(TextImportEntry.handoff("  ", 1L))
        val shared = TextImportEntry.handoff("  $sample \n", 7L)!!
        assertEquals(7L, shared.id)
        assertEquals(sample, shared.body)
        assertNull(shared.senderPhone)
    }

    @Test
    fun readTextForJobUsesShareSheetInbox() {
        assertNull(TextImportEntry.send("   ", TextImportTarget.JOB, 1L))
        assertNull(TextShareInbox.peek())

        val shared = TextImportEntry.send(sample, TextImportTarget.JOB, 42L)!!
        assertEquals(shared, TextShareInbox.peek())
        assertNull(CustomerTextInbox.peek())

        // The New Job form runs the shared parser on exactly this text.
        val fields = TextMessageImport.parse(shared.body, shared.senderPhone)
        assertEquals("Maria Lopez", fields.name)
        assertEquals("22 Oak St", fields.street)
        assertEquals("Newburgh", fields.city)
        assertEquals("NY", fields.state)
        assertEquals("12550", fields.zip)
        assertEquals("maria@example.com", fields.email)
        assertEquals("8455550199", fields.phone.filter(Char::isDigit))
        assertTrue(fields.animal, fields.animal.contains("raccoon", ignoreCase = true))
        assertTrue(fields.problem, fields.problem.contains("attic", ignoreCase = true))

        TextShareInbox.consume(shared.id)
        assertNull(TextShareInbox.peek())
    }

    @Test
    fun readTextForCustomerUsesCustomerInbox() {
        val shared = TextImportEntry.send(sample, TextImportTarget.CUSTOMER, 43L)!!
        assertEquals(shared, CustomerTextInbox.peek())
        assertNull(TextShareInbox.peek())
        val next = TextMessageImport.applyToCustomer(
            TextMessageImport.CustomerSnapshot(),
            TextMessageImport.parse(shared.body)
        )
        assertEquals("Maria", next.firstName)
        assertEquals("Lopez", next.lastName)
        assertEquals("maria@example.com", next.email)
        assertEquals("22 Oak St", next.address)
        assertEquals("Newburgh", next.city)
        assertEquals("12550", next.zip)
        CustomerTextInbox.consume(shared.id)
        assertNull(CustomerTextInbox.peek())
    }

    @Test
    fun previewIsTheSharedParser() {
        assertEquals(TextMessageImport.parse(sample), TextImportEntry.preview("  $sample  "))
        assertEquals(TextMessageImport.Fields(), TextImportEntry.preview(" "))
    }

    @Test
    fun handedOffTextFillsEmptyFieldsOnly() {
        val typed = TextMessageImport.JobSnapshot(
            title = "My title",
            customer = JobCustomerDraft(name = "M. Lopez", phone = "")
        )
        val next = TextMessageImport.applyToJob(typed, TextImportEntry.preview(sample))
        assertEquals("My title", next.title)
        assertEquals("M. Lopez", next.customer.name)
        assertEquals("8455550199", next.customer.phone.filter(Char::isDigit))
        assertEquals("22 Oak St", next.customer.address)
        assertEquals("maria@example.com", next.customer.email)
    }

    @Test
    fun clearedFieldStaysClearedWhenMarkedManual() {
        val cleared = TextMessageImport.JobSnapshot(
            customer = JobCustomerDraft(),
            manual = setOf(TextMessageImport.EMAIL)
        )
        val next = TextMessageImport.applyToJob(cleared, TextImportEntry.preview(sample))
        assertEquals("", next.customer.email)
        assertEquals("Maria Lopez", next.customer.name)
    }

    @Test
    fun targetFromRoute() {
        assertEquals(TextImportTarget.JOB, TextImportTarget.from(null))
        assertEquals(TextImportTarget.JOB, TextImportTarget.from("job"))
        assertEquals(TextImportTarget.CUSTOMER, TextImportTarget.from("customer"))
        assertEquals(TextImportTarget.JOB, TextImportTarget.from("nonsense"))
    }
}
