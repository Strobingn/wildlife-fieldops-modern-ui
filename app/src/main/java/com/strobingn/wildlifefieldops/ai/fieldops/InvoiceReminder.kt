package com.strobingn.wildlifefieldops.ai.fieldops

import com.strobingn.wildlifefieldops.data.model.Invoice
import com.strobingn.wildlifefieldops.data.model.InvoiceStatus
import com.strobingn.wildlifefieldops.pricing.Money
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class InvoiceReminderDraft(
    val subject: String,
    val body: String
)

object InvoiceReminder {

    fun isOverdue(invoice: Invoice, now: Long = System.currentTimeMillis()): Boolean {
        if (invoice.status == InvoiceStatus.PAID || invoice.status == InvoiceStatus.CANCELLED) return false
        if (invoice.status == InvoiceStatus.OVERDUE) return true
        return invoice.dueDate > 0L && invoice.dueDate < now && invoice.balanceDue > 0.01
    }

    fun overdueList(invoices: List<Invoice>, now: Long = System.currentTimeMillis()): List<Invoice> =
        invoices.filter { isOverdue(it, now) }.sortedBy { it.dueDate }

    fun draft(invoice: Invoice, shopName: String = "Wildlife Whisperer"): InvoiceReminderDraft {
        val money = NumberFormat.getCurrencyInstance(Locale.US).format(invoice.balanceDue.takeIf { it > 0 } ?: invoice.totalAmount)
        val due = if (invoice.dueDate > 0L) {
            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(invoice.dueDate))
        } else "the due date"
        val number = invoice.invoiceNumber.ifBlank { invoice.id.take(8) }
        return InvoiceReminderDraft(
            subject = "Invoice $number reminder — $shopName",
            body = "Hi ${invoice.customerName.ifBlank { "there" }},\n\n" +
                "Friendly reminder that invoice $number for $money was due $due. " +
                "Please reply or call if you have a question — happy to help.\n\n" +
                "Thank you,\n$shopName"
        )
    }

    fun withStatus(invoice: Invoice, status: InvoiceStatus, now: Long = System.currentTimeMillis()): Invoice {
        val paid = status == InvoiceStatus.PAID
        val amountPaid = if (paid && invoice.amountPaid <= 0.0) invoice.totalAmount else invoice.amountPaid
        val balance = if (paid) 0.0 else Money.minus(invoice.totalAmount, amountPaid).coerceAtLeast(0.0)
        return invoice.copy(
            status = status,
            amountPaid = amountPaid,
            balanceDue = balance,
            updatedAt = now,
            isSynced = false
        )
    }
}
