package com.strobingn.wildlifefieldops.util

import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.InvoiceLineItem
import com.strobingn.wildlifefieldops.data.model.Job

/**
 * Sir's City Island yellow jacket job, shaped the way the app builds the
 * packet: no phone/email typed on the packet, the contact lives on the linked
 * customer record. Phone/email values are made up.
 */
object CityIslandSample {
    const val NOW = 1_791_311_188_225L
    const val PHONE = "(718) 555-0142"
    const val EMAIL = "pam.johnston@example.com"

    val workNote = "Labor 4.5 hrs @ \$185 for 2 large YJ nests with 3-story roof access; dust heavy front+rear. " +
        "Materials: professional dust/powder. Equipment: elevated access/PPE/application. Disposal: nest/PPE waste. " +
        "Mileage 59.2 one-way @ \$0.67. Tax 8.125%. No discount. Priority HIGH / IN_PROGRESS."
    val scope = "High-priority yellow jacket removal of two large nests (front soffit corner + larger rear nest above H3 window). " +
        "Primary access constraint is roof-only reach three stories up, requiring elevated work, dust application front " +
        "and rear entry points, PPE, and careful placement of residual powder. Labor covers setup, treatment, and nest removal."

    val customer = Customer(
        id = "cust-pam",
        firstName = "Pam",
        lastName = "Johnston",
        companyName = "TSO",
        phone = PHONE,
        email = EMAIL,
        address = "33 Tier Street",
        city = "Bronx",
        state = "ny",
        zipCode = "10464"
    )

    val job = Job(
        id = "job-city-island",
        customerId = "cust-pam",
        customerName = "Pam Johnston (TSO) (TSO)",
        address = "33 Tier Street, Bronx, ny 10464",
        title = "City Island",
        // The same scope, re-saved with different spacing, is what printed twice.
        description = scope.replace(").", ").  "),
        notes = "Gate is on the left side of the house; call before arrival."
    )

    val lineItems = listOf(
        InvoiceLineItem(id = "l1", description = "Yellow jacket nest removal", quantity = 2.0, unit = "", unitPrice = 400.0, total = 800.0),
        InvoiceLineItem(id = "l2", description = "Labor / Trap Service", quantity = 2.0, unit = "hr", unitPrice = 185.0, total = 370.0),
        InvoiceLineItem(id = "l3", description = "Materials / Exclusion & Repairs", quantity = 1.0, unit = "ea", unitPrice = 95.0, total = 95.0),
        InvoiceLineItem(id = "l4", description = "Mileage", quantity = 59.2, unit = "mi", unitPrice = 0.67, total = 39.66)
    )

    /** Packet as WildlifeWhispererContractPdf.generate passes it in, before the customer lookup. */
    fun rawPacket(profile: BusinessProfile = BusinessProfile.defaults().copy(ownerName = "Dirk Diggler")) = StandardJobPacket(
        job = job,
        profile = profile,
        lineItems = lineItems,
        subtotal = 1304.66,
        taxRatePercent = 8.875,
        taxAmount = 115.79,
        total = 1420.45,
        amountPaid = 0.0,
        balanceDue = 1420.45,
        notes = workNote + "\n" + scope,
        documentNumber = "INV-1791311188225",
        invoiceDateMillis = NOW,
        dueDateMillis = NOW + 30L * 86_400_000L,
        nowMillis = NOW
    )

    /** What DocumentContacts.fill produces in the app for this job. */
    fun packet(): StandardJobPacket = JobContactFallback.apply(rawPacket(), customer)
}
