package com.strobingn.wildlifefieldops.data.remote

import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.JobCustomerDraft
import com.strobingn.wildlifefieldops.data.model.JobPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TextMessageImportTest {

    private val pat =
        "Hi this is Pat Lee, 12 Oak St Cornwall NY 12518, 845-555-0142, raccoons in my attic"

    @Test
    fun parsesPatLeeText() {
        val fields = TextMessageImport.parse(pat)
        assertEquals("Pat Lee", fields.name)
        assertEquals("845-555-0142", fields.phone)
        assertEquals("12 Oak St", fields.street)
        assertEquals("Cornwall", fields.city)
        assertEquals("NY", fields.state)
        assertEquals("12518", fields.zip)
        assertEquals("Raccoon", fields.animal)
        assertEquals("Raccoon Removal", fields.serviceType)
        assertEquals("Raccoon Removal — Pat Lee", fields.jobTitle)
        assertTrue(fields.problem.contains("raccoon", ignoreCase = true))
        assertTrue(fields.problem.contains("attic", ignoreCase = true))
        assertEquals("", fields.email)
    }

    @Test
    fun parsesEmailCommasAndSquirrels() {
        val text = "Hey it's Maria Gomez. Squirrels in the soffit. 45 River Rd, Newburgh, NY 12550. " +
            "Call me at (845) 555-0199 or maria.gomez@example.com"
        val fields = TextMessageImport.parse(text)
        assertEquals("Maria Gomez", fields.name)
        assertEquals("845-555-0199", fields.phone)
        assertEquals("maria.gomez@example.com", fields.email)
        assertEquals("45 River Rd", fields.street)
        assertEquals("Newburgh", fields.city)
        assertEquals("NY", fields.state)
        assertEquals("12550", fields.zip)
        assertEquals("Squirrel", fields.animal)
        assertEquals("Squirrel Removal", fields.serviceType)
        assertTrue(fields.problem.contains("Squirrel", ignoreCase = true))
    }

    @Test
    fun missingFieldsStayBlank() {
        val onlyAnimal = TextMessageImport.parse("raccoons in the attic")
        assertEquals("", onlyAnimal.name)
        assertEquals("", onlyAnimal.phone)
        assertEquals("", onlyAnimal.street)
        assertEquals("", onlyAnimal.city)
        assertEquals("", onlyAnimal.zip)
        assertEquals("Raccoon", onlyAnimal.animal)
        assertTrue(onlyAnimal.problem.contains("attic", ignoreCase = true))

        val hello = TextMessageImport.parse("hello")
        assertEquals("", hello.name)
        assertEquals("", hello.phone)
        assertEquals("", hello.street)
        assertEquals("", hello.jobTitle)
        assertEquals("", hello.problem)
    }

    @Test
    fun messySpellingStillFindsTheCustomer() {
        val text = "yo racoon in attic!! name pat lee phn 845.555.0142 12 oak st cornwall ny 12518"
        val fields = TextMessageImport.parse(text)
        assertEquals("Pat Lee", fields.name)
        assertEquals("845-555-0142", fields.phone)
        assertEquals("12 Oak St", fields.street)
        assertEquals("Cornwall", fields.city)
        assertEquals("NY", fields.state)
        assertEquals("12518", fields.zip)
        assertEquals("Raccoon", fields.animal)
        assertTrue(fields.problem.contains("attic", ignoreCase = true))
    }

    @Test
    fun apartmentAndApostropheName() {
        val text = "My name is John O'Brien. Address: 100 Main Street Apt 2, Beacon NY 12508. " +
            "Phone 8455550100. Skunk under the porch."
        val fields = TextMessageImport.parse(text)
        assertEquals("John O'Brien", fields.name)
        assertEquals("845-555-0100", fields.phone)
        assertTrue(fields.street.contains("100 Main Street"))
        assertTrue(fields.street.contains("Apt 2"))
        assertEquals("Beacon", fields.city)
        assertEquals("NY", fields.state)
        assertEquals("12508", fields.zip)
        assertEquals("Skunk", fields.animal)
    }

    @Test
    fun senderPhoneFillsOnlyWhenTheBodyHasNone() {
        val body = "Hi this is Pat Lee, 12 Oak St Cornwall NY 12518, raccoons in my attic"
        val fromSender = TextMessageImport.parse(body, senderPhone = "+1 845-555-0142")
        assertEquals("845-555-0142", fromSender.phone)
        val bodyWins = TextMessageImport.parse(pat, senderPhone = "914-555-0199")
        assertEquals("845-555-0142", bodyWins.phone)
    }

    @Test
    fun zipIsNotAPhone() {
        val fields = TextMessageImport.parse("12 Oak St Cornwall NY 12518 raccoons")
        assertEquals("", fields.phone)
        assertEquals("12518", fields.zip)
    }

    @Test
    fun joinedMessagesParseAsOneCustomer() {
        val body = TextMessageImport.joinBodies(
            listOf(
                "Hi this is Pat Lee",
                "12 Oak St Cornwall NY 12518",
                "845-555-0142 raccoons in my attic"
            )
        )
        val fields = TextMessageImport.parse(body)
        assertEquals("Pat Lee", fields.name)
        assertEquals("845-555-0142", fields.phone)
        assertEquals("12 Oak St", fields.street)
        assertEquals("Cornwall", fields.city)
        assertEquals("Raccoon", fields.animal)
    }

    @Test
    fun batsInTheChimney() {
        val fields = TextMessageImport.parse("From: Dana Cho\nBats in the chimney. 8 Elm Ave, Monroe NY 10950")
        assertEquals("Dana Cho", fields.name)
        assertEquals("8 Elm Ave", fields.street)
        assertEquals("Monroe", fields.city)
        assertEquals("Bat", fields.animal)
        assertEquals("Bat Exclusion", fields.serviceType)
    }

    @Test
    fun fillEmptyDoesNotOverwriteTypedOrClearedFields() {
        val parsed = TextMessageImport.parse(pat)
        val typed = parsed.copy(name = "Sir typed this", phone = "")
        val incoming = parsed.copy(
            name = "AI Pat",
            phone = "845-555-0142",
            email = "pat@example.com",
            street = "99 Cloud Lane",
            problem = "from cloud",
            jobTitle = "Cloud title"
        )
        val merged = TextMessageImport.fillEmpty(
            typed,
            incoming,
            manual = setOf(TextMessageImport.NAME, TextMessageImport.PHONE)
        )
        assertEquals("Sir typed this", merged.name)
        assertEquals("", merged.phone)
        assertEquals("pat@example.com", merged.email)
        assertEquals("12 Oak St", merged.street)
        assertTrue(merged.problem.contains("attic", ignoreCase = true))
        assertEquals(parsed.jobTitle, merged.jobTitle)
    }

    @Test
    fun aiSuggestionFillsOnlyEmptyFields() {
        val local = TextMessageImport.parse(pat)
        val ai = TextMessageImport.Fields(
            name = "Someone Else",
            phone = "999-999-9999",
            email = "pat@example.com",
            street = "99 Cloud Lane",
            city = "Cloudtown",
            problem = "different problem",
            jobTitle = "Cloud title",
            serviceType = "Exclusion",
            animal = "Bat",
            notes = "Bring a ladder"
        )
        val merged = TextMessageImport.fillEmpty(local, ai)
        assertEquals("Pat Lee", merged.name)
        assertEquals("845-555-0142", merged.phone)
        assertEquals("pat@example.com", merged.email)
        assertEquals("12 Oak St", merged.street)
        assertEquals("Cornwall", merged.city)
        assertEquals("Raccoon", merged.animal)
        assertEquals("Raccoon Removal", merged.serviceType)
        assertEquals("Raccoon Removal — Pat Lee", merged.jobTitle)
        assertTrue(merged.problem.contains("attic", ignoreCase = true))
        assertEquals("Bring a ladder", merged.notes)
    }

    @Test
    fun clearedJobFieldStaysBlankWhenSuggestionReturns() {
        val current = TextMessageImport.JobSnapshot(
            title = "Raccoon Removal — Pat Lee",
            description = "",
            species = "",
            serviceType = "Raccoon Removal",
            serviceTouched = true,
            customer = JobCustomerDraft(name = "Pat Lee", phone = "845-555-0142"),
            manual = setOf(TextMessageImport.PROBLEM, TextMessageImport.ANIMAL)
        )
        val incoming = TextMessageImport.parse(pat)
        val next = TextMessageImport.applyToJob(current, incoming)
        assertEquals("", next.description)
        assertEquals("", next.species)
        assertEquals("Pat Lee", next.customer.name)
        assertEquals("12 Oak St", next.customer.address)
        assertEquals("Raccoon Removal", next.serviceType)
    }

    @Test
    fun linkedCustomerIsNotOverwrittenByALaterParse() {
        val linked = JobCustomerDraft(
            customerId = "cust-1",
            name = "Pat Lee",
            phone = "845-555-0100",
            address = "9 Pine Ave",
            city = "Cornwall",
            state = "NY",
            zipCode = "12518"
        )
        val next = TextMessageImport.applyToJob(
            TextMessageImport.JobSnapshot(customer = linked, linked = true, title = ""),
            TextMessageImport.parse(pat)
        )
        assertEquals("cust-1", next.customer.customerId)
        assertEquals("845-555-0100", next.customer.phone)
        assertEquals("9 Pine Ave", next.customer.address)
        assertEquals("Raccoon Removal — Pat Lee", next.title)
        assertTrue(next.description.contains("attic", ignoreCase = true))
    }

    @Test
    fun customerFormFillLeavesTypedFirstName() {
        val current = TextMessageImport.CustomerSnapshot(
            firstName = "Dirk",
            lastName = "",
            manual = setOf(TextMessageImport.FIRST, TextMessageImport.LAST)
        )
        val next = TextMessageImport.applyToCustomer(current, TextMessageImport.parse(pat))
        assertEquals("Dirk", next.firstName)
        assertEquals("", next.lastName)
        assertEquals("845-555-0142", next.phone)
        assertEquals("12 Oak St", next.address)
        assertTrue(next.notes.contains("attic", ignoreCase = true))
    }

    @Test
    fun samePhoneOrAddressMatchesAndDoesNotMerge() {
        val existing = Customer(
            id = "cust-pat",
            firstName = "Pat",
            lastName = "Lee",
            phone = "(845) 555-0142",
            address = "12 Oak Street",
            city = "Cornwall",
            state = "NY",
            zipCode = "12518"
        )
        val otherTown = existing.copy(
            id = "other",
            phone = "914-555-0000",
            address = "12 Oak Street",
            zipCode = "10940"
        )
        val inactive = existing.copy(id = "gone", isActive = false, phone = "845-555-0142")
        val fields = TextMessageImport.parse(pat)
        val matches = TextMessageImport.matchingCustomers(listOf(existing, otherTown, inactive), fields)
        assertEquals(listOf("cust-pat"), matches.map { it.id })
        val draft = TextMessageImport.applyToJob(TextMessageImport.JobSnapshot(), fields)
        assertEquals("", draft.customer.customerId)
        assertEquals("Pat Lee", draft.customer.name)
    }

    @Test
    fun addressMatchWithoutPhone() {
        val existing = Customer(
            id = "addr",
            firstName = "Pat",
            lastName = "Lee",
            phone = "",
            address = "12 Oak St",
            city = "Cornwall",
            zipCode = "12518"
        )
        val fields = TextMessageImport.parse("12 Oak St Cornwall NY 12518, squirrels in the garage")
        val matches = TextMessageImport.matchingCustomers(listOf(existing), fields)
        assertEquals(1, matches.size)
        assertEquals("addr", matches.first().id)
    }

    @Test
    fun shareMultipleTextsAndSenderNumber() {
        val shared = SharedTextIntake.extract(
            SharePayload(
                action = SharedTextIntake.ACTION_SEND_MULTIPLE,
                mimeType = "text/plain",
                textList = listOf(
                    "Hi this is Pat Lee",
                    "raccoons in my attic"
                ),
                subject = "SMS from 845-555-0142",
                extraPhones = listOf("845-555-0142")
            )
        )
        requireNotNull(shared)
        val fields = TextMessageImport.parse(shared.body, shared.senderPhone)
        assertEquals("Pat Lee", fields.name)
        assertEquals("845-555-0142", fields.phone)
        assertEquals("Raccoon", fields.animal)
    }

    @Test
    fun shareIgnoresNonText() {
        val shared = SharedTextIntake.extract(
            SharePayload(
                action = SharedTextIntake.ACTION_SEND,
                mimeType = "image/jpeg",
                text = pat
            )
        )
        assertEquals(null, shared)
    }

    @Test
    fun manifestRegistersPlainTextShareAndDoesNotReadTheSmsInbox() {
        val xml = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first { it.isFile }.readText()
        assertTrue(xml.contains("android.intent.action.SEND"))
        assertTrue(xml.contains("android.intent.action.SEND_MULTIPLE"))
        assertTrue(xml.contains("text/plain"))
        assertFalse(xml.contains("READ_SMS"))
    }

    @Test
    fun urgentPriorityDoesNotReplaceATouchedPriority() {
        val next = TextMessageImport.applyToJob(
            TextMessageImport.JobSnapshot(priority = JobPriority.LOW, priorityTouched = true),
            TextMessageImport.parse("Urgent raccoon for Pat Lee at the house")
        )
        assertEquals(JobPriority.LOW, next.priority)
    }
}
