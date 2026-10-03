package com.strobingn.wildlifefieldops.util

/**
 * Hard-coded Wildlife Whisperer LLC defaults. Settings → Business info overrides
 * these on every customer document. A field the owner clears is stored blank and
 * left off the header; a field never set still uses these defaults.
 */
object WildlifeWhispererIdentity {
    const val COMPANY = "Wildlife Whisperer LLC"
    const val COMPANY_UPPER = "WILDLIFE WHISPERER LLC"
    const val ADDRESS = "210 Willow Avenue, Cornwall, New York 12518"
    const val PHONE = "(845) 751-8448"
    const val EMAIL = "austin@wildlifewhispererllc.com"
    const val TAGLINE = "Nuisance Wildlife Control · Cornwall, NY"
    const val WEBSITE = ""
}
