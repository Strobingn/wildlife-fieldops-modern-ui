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

    /**
     * FileProvider URIs from `res/xml/file_paths.xml` are not filesystem paths.
     * `content://…/internal_files/photos/IMG.jpg` and the bogus absolute path
     * `/internal_files/photos/IMG.jpg` both map back to [filesDir].
     */
    fun appFileCandidate(pathOrUri: String, filesDir: String, cacheDir: String): String? {
        val relative = providerRelative(pathOrUri) ?: return null
        if (relative.contains("..")) return null
        val child = when {
            relative.startsWith(FILES_PREFIX) -> relative.removePrefix(FILES_PREFIX)
            relative.startsWith(CACHE_PREFIX) -> relative.removePrefix(CACHE_PREFIX)
            else -> return null
        }
        if (child.isBlank()) return null
        val root = if (relative.startsWith(FILES_PREFIX)) filesDir else cacheDir
        return java.io.File(root, child).path
    }

    private fun providerRelative(pathOrUri: String): String? {
        val raw = pathOrUri.trim()
        if (raw.isEmpty() || isRemoteUrl(raw)) return null
        val path = if (raw.startsWith("content:", ignoreCase = true)) {
            val afterScheme = raw.substringAfter("://", missingDelimiterValue = "")
            val slash = afterScheme.indexOf('/')
            if (slash < 0) return null
            afterScheme.substring(slash + 1)
        } else {
            raw.removePrefix("/")
        }
        return path.replace("%2F", "/", ignoreCase = true).replace("%20", " ")
    }

    private const val FILES_PREFIX = "internal_files/"
    private const val CACHE_PREFIX = "cache_files/"

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
