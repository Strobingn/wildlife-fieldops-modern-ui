package com.strobingn.wildlifefieldops.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class JobIntakeDraft(
    val title: String = "",
    val customerName: String = "",
    val address: String = "",
    val type: String = "",
    val priority: String = "MEDIUM",
    val description: String = "",
    val notes: String = ""
)

data class JobIntakeResult(
    val draft: JobIntakeDraft? = null,
    val error: String? = null,
    val sourceLabel: String = ""
)
