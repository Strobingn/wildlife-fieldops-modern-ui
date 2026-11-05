package com.strobingn.wildlifefieldops.util

import java.util.Locale

/**
 * Letterhead printed on every standard document.
 * Blank strings are omitted. Missing settings keys are not blank — they resolve
 * to [WildlifeWhispererIdentity] before a [BusinessProfile] is built.
 */
data class BusinessProfile(
    val name: String,
    val phone: String,
    val email: String,
    val address: String,
    val website: String,
    val licenseNumber: String,
    /** Empty uses the built-in circular Wildlife Whisperer logo. */
    val logoPath: String = "",
    /** Owner/operator from Settings (technician name, else NWCO operator). Fills a blank Tech line. */
    val ownerName: String = "",
    /** Zelle recipient (email or phone). Blank leaves the Zelle line off every document. */
    val zelle: String = ""
) {
    /** Plain letterhead text, without the Zelle payment line. */
    fun headerLines(): List<String> = headerRows(includeZelle = false).map { it.text }

    /**
     * Letterhead rows in print order. With [includeZelle], the Zelle line sits
     * directly under the phone · email line and is marked [HeaderRowStyle.ZELLE]
     * so the layout prints it larger and bold.
     */
    fun headerRows(includeZelle: Boolean): List<HeaderRow> {
        val rows = mutableListOf<HeaderRow>()
        val company = name.trim()
        if (company.isNotBlank()) rows += HeaderRow(company.uppercase(Locale.US), HeaderRowStyle.NAME)
        val street = address.trim()
        if (street.isNotBlank()) rows += HeaderRow(street, HeaderRowStyle.DETAIL)
        val contact = listOf(phone.trim(), email.trim()).filter { it.isNotBlank() }.joinToString(" · ")
        if (contact.isNotBlank()) rows += HeaderRow(contact, HeaderRowStyle.DETAIL)
        if (includeZelle) zelleLine()?.let { rows += HeaderRow(it, HeaderRowStyle.ZELLE) }
        val site = website.trim()
        if (site.isNotBlank()) rows += HeaderRow(site, HeaderRowStyle.DETAIL)
        val license = licenseNumber.trim()
        if (license.isNotBlank()) rows += HeaderRow("NYS DEC NWCO # $license", HeaderRowStyle.DETAIL)
        return rows
    }

    /** "Zelle: <recipient>", or null when no Zelle recipient is set. */
    fun zelleLine(): String? = zelle.trim().takeIf { it.isNotBlank() }?.let { "Zelle: $it" }

    fun footerLine(): String = listOf(
        name.trim().uppercase(Locale.US),
        address.trim(),
        phone.trim()
    ).filter { it.isNotBlank() }.joinToString(" · ")

    fun usesDefaultLogo(): Boolean = logoPath.isBlank()

    companion object {
        fun defaults(): BusinessProfile = BusinessProfile(
            name = WildlifeWhispererIdentity.COMPANY,
            phone = WildlifeWhispererIdentity.PHONE,
            email = WildlifeWhispererIdentity.EMAIL,
            address = WildlifeWhispererIdentity.ADDRESS,
            website = WildlifeWhispererIdentity.WEBSITE,
            licenseNumber = "",
            logoPath = "",
            zelle = WildlifeWhispererIdentity.ZELLE
        )
    }
}

enum class HeaderRowStyle { NAME, DETAIL, ZELLE }

data class HeaderRow(val text: String, val style: HeaderRowStyle)

object BusinessProfileResolve {
    const val KEY_NAME = "company_name"
    const val KEY_ADDRESS = "company_address"
    const val KEY_PHONE = "business_phone"
    const val KEY_EMAIL = "business_email"
    const val KEY_WEBSITE = "business_website"
    const val KEY_LICENSE = "nwco_license"
    const val KEY_LOGO = "business_logo_path"
    const val KEY_TECHNICIAN = "technician_name"
    const val KEY_NWCO_NAME = "nwco_operator_name"
    const val KEY_ZELLE = "business_zelle"

    /**
     * [present] contains only keys that were actually saved.
     * A saved empty string stays empty. A missing key uses the Wildlife Whisperer default.
     */
    fun fromStored(present: Map<String, String>): BusinessProfile {
        fun value(key: String, default: String): String =
            if (present.containsKey(key)) present[key].orEmpty() else default
        return BusinessProfile(
            name = value(KEY_NAME, WildlifeWhispererIdentity.COMPANY),
            phone = value(KEY_PHONE, WildlifeWhispererIdentity.PHONE),
            email = value(KEY_EMAIL, WildlifeWhispererIdentity.EMAIL),
            address = value(KEY_ADDRESS, WildlifeWhispererIdentity.ADDRESS),
            website = value(KEY_WEBSITE, WildlifeWhispererIdentity.WEBSITE),
            licenseNumber = value(KEY_LICENSE, ""),
            logoPath = value(KEY_LOGO, ""),
            ownerName = present[KEY_TECHNICIAN].orEmpty().trim()
                .ifBlank { present[KEY_NWCO_NAME].orEmpty().trim() },
            zelle = value(KEY_ZELLE, WildlifeWhispererIdentity.ZELLE)
        )
    }
}
