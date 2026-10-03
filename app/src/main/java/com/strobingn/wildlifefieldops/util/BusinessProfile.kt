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
    val logoPath: String = ""
) {
    fun headerLines(): List<String> {
        val lines = mutableListOf<String>()
        val company = name.trim()
        if (company.isNotBlank()) lines += company.uppercase(Locale.US)
        val street = address.trim()
        if (street.isNotBlank()) lines += street
        val contact = listOf(phone.trim(), email.trim()).filter { it.isNotBlank() }.joinToString(" · ")
        if (contact.isNotBlank()) lines += contact
        val site = website.trim()
        if (site.isNotBlank()) lines += site
        val license = licenseNumber.trim()
        if (license.isNotBlank()) lines += "NYS DEC NWCO # $license"
        return lines
    }

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
            logoPath = ""
        )
    }
}

object BusinessProfileResolve {
    const val KEY_NAME = "company_name"
    const val KEY_ADDRESS = "company_address"
    const val KEY_PHONE = "business_phone"
    const val KEY_EMAIL = "business_email"
    const val KEY_WEBSITE = "business_website"
    const val KEY_LICENSE = "nwco_license"
    const val KEY_LOGO = "business_logo_path"

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
            logoPath = value(KEY_LOGO, "")
        )
    }
}
