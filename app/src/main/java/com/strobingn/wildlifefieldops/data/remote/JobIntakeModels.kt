package com.strobingn.wildlifefieldops.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class JobIntakeDraft(
    val title: String = "",
    val customerName: String = "",
    /** Street line of the service address ("22 Oak Street"). */
    val address: String = "",
    val type: String = "",
    val priority: String = "MEDIUM",
    /** Scope of work ("Raccoon in the attic"). Never the whole transcript. */
    val description: String = "",
    val notes: String = "",
    val phone: String = "",
    val email: String = "",
    val city: String = "",
    val state: String = "",
    val zip: String = "",
    val species: String = "",
    /** Scheduled, In progress or Completed. */
    val status: String = "",
    /** Spoken day: today, tomorrow or a weekday. */
    val scheduleDay: String = ""
)

data class JobIntakeResult(
    val draft: JobIntakeDraft? = null,
    val error: String? = null,
    val sourceLabel: String = "",
    /** Short message for Sir when cloud AI was not used (fallback). */
    val notice: String? = null
)
