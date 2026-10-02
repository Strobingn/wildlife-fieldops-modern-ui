package com.strobingn.wildlifefieldops.ai.fieldops

object ShareableReport {
    fun payload(jobId: String): String = "fieldops://report/${jobId.trim()}"

    fun jobIdFromUri(uri: String): String? {
        val trimmed = uri.trim()
        if (!trimmed.startsWith("fieldops://report/")) return null
        return trimmed.removePrefix("fieldops://report/").substringBefore('/').takeIf { it.isNotBlank() }
    }
}
