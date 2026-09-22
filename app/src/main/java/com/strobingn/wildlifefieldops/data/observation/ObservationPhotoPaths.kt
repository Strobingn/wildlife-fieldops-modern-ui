package com.strobingn.wildlifefieldops.data.observation

/**
 * Deterministic `observation-photos` object keys and local-file heuristics.
 * Retry-safe: the same observation / event always maps to the same storage path.
 */
object ObservationPhotoPaths {
    const val BUCKET = "observation-photos"
    const val MAX_BYTES = 50L * 1024 * 1024

    fun isRemoteUrl(pathOrUri: String?): Boolean {
        val value = pathOrUri?.trim().orEmpty()
        if (value.isEmpty()) return false
        val lower = value.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://")
    }

    fun isLocalCandidate(pathOrUri: String?): Boolean {
        val value = pathOrUri?.trim().orEmpty()
        return value.isNotEmpty() && !isRemoteUrl(value)
    }

    fun filesystemPath(pathOrUri: String): String? {
        val trimmed = pathOrUri.trim()
        if (trimmed.isEmpty()) return null
        val lower = trimmed.lowercase()
        if (lower.startsWith("content:") || isRemoteUrl(trimmed)) return null
        if (lower.startsWith("file:")) {
            return trimmed
                .removePrefix("file://")
                .removePrefix("file:")
                .let { if (it.startsWith("/")) it else "/$it" }
        }
        return trimmed
    }

    fun fieldObservationPath(observationId: String, localPath: String): String =
        "field/${sanitizeSegment(observationId)}/${sanitizeSegment(observationId)}.${extension(localPath)}"

    fun eventPath(eventId: String, localPath: String): String =
        "events/${sanitizeSegment(eventId)}/${sanitizeSegment(eventId)}.${extension(localPath)}"

    fun mimeType(localPath: String): String = when (extension(localPath)) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        else -> "image/jpeg"
    }

    fun isAllowedMime(mime: String): Boolean = mime in setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/heic"
    )

    fun extension(localPath: String): String {
        val name = localPath.substringAfterLast('/').substringAfterLast('\\')
        val ext = name.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")
        return when (ext) {
            "png" -> "png"
            "webp" -> "webp"
            "heic", "heif" -> "heic"
            "jpeg" -> "jpg"
            else -> "jpg"
        }
    }

    fun sanitizeSegment(raw: String): String =
        raw.trim()
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(120)
            .ifBlank { "unknown" }
}
