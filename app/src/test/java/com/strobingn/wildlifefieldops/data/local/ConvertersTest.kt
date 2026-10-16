package com.strobingn.wildlifefieldops.data.local

import com.strobingn.wildlifefieldops.data.model.CatchType
import com.strobingn.wildlifefieldops.data.model.CustomerType
import com.strobingn.wildlifefieldops.data.model.ExpenseCategory
import com.strobingn.wildlifefieldops.data.model.FindingSeverity
import com.strobingn.wildlifefieldops.data.model.InspectionType
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
import com.strobingn.wildlifefieldops.data.model.ReminderStatus
import com.strobingn.wildlifefieldops.data.model.TrapStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test
    fun knownEnumNamesRoundTrip() {
        assertEquals(CustomerType.COMMERCIAL, c.toCustomerType(c.fromCustomerType(CustomerType.COMMERCIAL)))
        assertEquals(InspectionType.EMERGENCY, c.toInspectionType("EMERGENCY"))
        assertEquals(TrapStatus.NEEDS_BAIT, c.toTrapStatus("NEEDS_BAIT"))
        assertEquals(CatchType.RACCOON, c.toCatchType("RACCOON"))
    }

    @Test
    fun unknownEnumNamesFallBackInsteadOfCrashingTheQuery() {
        assertEquals(CustomerType.RESIDENTIAL, c.toCustomerType("MUNICIPAL"))
        assertEquals(InspectionType.ROUTINE, c.toInspectionType(""))
        assertEquals(FindingSeverity.NONE, c.toFindingSeverity("catastrophic"))
        assertEquals(PhotoCategory.JOB_SITE, c.toPhotoCategory("SELFIE"))
        assertEquals(ExpenseCategory.OTHER, c.toExpenseCategory("???"))
        assertEquals(TrapStatus.SET, c.toTrapStatus("LOST"))
        assertEquals(CatchType.OTHER, c.toCatchType("DRAGON"))
        assertEquals(ReminderStatus.PENDING, c.toReminderStatus("ARCHIVED"))
        assertEquals(InvoiceStatus.DRAFT, c.toInvoiceStatus("VOID"))
    }

    @Test
    fun malformedListJsonYieldsEmptyListInsteadOfThrowing() {
        assertEquals(listOf("a", "b"), c.toStringList(c.fromStringList(listOf("a", "b"))))
        assertTrue(c.toStringList("").isEmpty())
        assertTrue(c.toStringList("[\"a\"").isEmpty())
        assertTrue(c.toStringList("{\"not\":\"a list\"}").isEmpty())
        assertTrue(c.toInvoiceLineItemList("not json").isEmpty())
    }
}
