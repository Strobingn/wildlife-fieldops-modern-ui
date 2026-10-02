package com.strobingn.wildlifefieldops.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object AppUpdateManifestParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val bodyComment = Regex(
        """<!--\s*fieldops-update\s*(.*?)-->""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val legacyRunLine = Regex(
        """from\s+`([0-9a-fA-F]+)`\s+\(run\s+(\d+),\s+branch\s+`([^`]+)`\)""",
        RegexOption.IGNORE_CASE
    )

    fun parseUpdateJson(raw: String): AppUpdateManifest? {
        val text = raw.trim()
        if (text.isEmpty() || text.startsWith("<")) return null
        return runCatching { json.decodeFromString(AppUpdateManifest.serializer(), text) }.getOrNull()
            ?.takeIf { it.versionCode > 0 && it.apkUrl.isNotBlank() && it.channel.isNotBlank() }
    }

    fun parseReleaseBody(
        body: String,
        fallbackApkUrl: String = defaultApkUrl(),
        fallbackTag: String = AppUpdatePolicy.MAIN_RELEASE_TAG
    ): AppUpdateManifest? {
        parseEmbeddedBlock(body, fallbackApkUrl, fallbackTag)?.let { return it }
        return parseLegacyRunLine(body, fallbackApkUrl, fallbackTag)
    }

    fun parseGithubReleaseJson(raw: String): GithubReleaseLookup {
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?: return GithubReleaseLookup.Failed("GitHub release info was not valid JSON.")
        val tag = root["tag_name"]?.jsonPrimitive?.content.orEmpty()
        if (!AppUpdatePolicy.isMainReleaseTag(tag)) {
            return GithubReleaseLookup.Unavailable(
                "Latest GitHub release tag is '$tag', not ${AppUpdatePolicy.MAIN_RELEASE_TAG}."
            )
        }
        val assets = root["assets"]?.jsonArray.orEmpty()
        val assetObjects = assets.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        val updateJsonUrl = assetObjects
            .firstOrNull { it["name"]?.jsonPrimitive?.content.equals("update.json", ignoreCase = true) }
            ?.get("browser_download_url")?.jsonPrimitive?.content
        if (!updateJsonUrl.isNullOrBlank()) {
            return GithubReleaseLookup.ManifestJsonUrl(updateJsonUrl)
        }
        val apkUrl = assetObjects
            .firstOrNull { it["name"]?.jsonPrimitive?.content.equals("app-debug.apk", ignoreCase = true) }
            ?.get("browser_download_url")?.jsonPrimitive?.content
            ?: defaultApkUrl()
        val body = root["body"]?.jsonPrimitive?.content.orEmpty()
        val fromBody = parseReleaseBody(body, apkUrl, tag)
        return if (fromBody != null) GithubReleaseLookup.Manifest(fromBody)
        else GithubReleaseLookup.Unavailable(
            "The main release does not include update.json or versionCode yet."
        )
    }

    fun httpErrorMessage(code: Int, body: String, rateLimitRemaining: String?): String {
        val remaining = rateLimitRemaining?.toIntOrNull()
        val looksLimited = code == 429 ||
            code == 403 ||
            remaining == 0 ||
            body.contains("rate limit", ignoreCase = true) ||
            body.contains("API rate limit exceeded", ignoreCase = true)
        if (looksLimited) {
            return "GitHub is rate-limiting update checks. Try again in a little while."
        }
        return when (code) {
            in 500..599 -> "GitHub is unreachable right now (HTTP $code). Try again later."
            404 -> "No main-branch update info is published yet."
            else -> "Could not check for updates (HTTP $code)."
        }
    }

    fun networkErrorMessage(throwable: Throwable): String {
        val text = (throwable.message ?: throwable.javaClass.simpleName).lowercase()
        return when {
            "unable to resolve host" in text ||
                "unknownhost" in text ||
                "network is unreachable" in text ||
                "failed to connect" in text ||
                "timed out" in text ||
                "timeout" in text ->
                "Can't reach GitHub to check for updates. Connect to the internet and try again."
            else -> "Couldn't check for updates: ${throwable.message ?: throwable.javaClass.simpleName}"
        }
    }

    private fun parseEmbeddedBlock(
        body: String,
        fallbackApkUrl: String,
        fallbackTag: String
    ): AppUpdateManifest? {
        val block = bodyComment.find(body)?.groupValues?.getOrNull(1) ?: return null
        val values = linkedMapOf<String, String>()
        block.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@forEach
            val eq = trimmed.indexOf('=')
            if (eq <= 0) return@forEach
            values[trimmed.substring(0, eq).trim()] = trimmed.substring(eq + 1).trim()
        }
        val versionCode = values["versionCode"]?.toIntOrNull() ?: return null
        return AppUpdateManifest(
            channel = values["channel"] ?: "main",
            tag = values["tag"] ?: fallbackTag,
            packageName = values["packageName"] ?: AppUpdatePolicy.EXPECTED_PACKAGE,
            versionCode = versionCode,
            versionName = values["versionName"].orEmpty(),
            apkUrl = values["apkUrl"] ?: fallbackApkUrl,
            commitSha = values["commitSha"].orEmpty(),
            buildTime = values["buildTime"].orEmpty(),
            runNumber = values["runNumber"]?.toIntOrNull() ?: 0,
            changelog = changelogAfterComment(body)
        )
    }

    private fun parseLegacyRunLine(
        body: String,
        fallbackApkUrl: String,
        fallbackTag: String
    ): AppUpdateManifest? {
        val match = legacyRunLine.find(body) ?: return null
        val sha = match.groupValues[1]
        val run = match.groupValues[2].toIntOrNull() ?: return null
        val branch = match.groupValues[3]
        if (!AppUpdatePolicy.isMainChannel(branch)) return null
        if (!AppUpdatePolicy.isMainReleaseTag(fallbackTag)) return null
        return AppUpdateManifest(
            channel = "main",
            tag = fallbackTag,
            versionCode = 1_000_000 + run,
            versionName = "",
            apkUrl = fallbackApkUrl,
            commitSha = sha,
            runNumber = run,
            changelog = body.trim()
        )
    }

    private fun changelogAfterComment(body: String): String {
        val stripped = bodyComment.replace(body, "").trim()
        val marker = Regex("""###\s+Changes since last main build\s*""", RegexOption.IGNORE_CASE)
        val match = marker.find(stripped)
        return if (match != null) stripped.substring(match.range.last + 1).trim() else stripped
    }

    fun defaultApkUrl(
        ownerRepo: String = "Strobingn/wildlife-fieldops-modern-ui",
        tag: String = AppUpdatePolicy.MAIN_RELEASE_TAG
    ): String = "https://github.com/$ownerRepo/releases/download/$tag/app-debug.apk"

    fun defaultManifestUrl(
        ownerRepo: String = "Strobingn/wildlife-fieldops-modern-ui",
        tag: String = AppUpdatePolicy.MAIN_RELEASE_TAG
    ): String = "https://github.com/$ownerRepo/releases/download/$tag/update.json"

    fun defaultReleaseApiUrl(
        ownerRepo: String = "Strobingn/wildlife-fieldops-modern-ui",
        tag: String = AppUpdatePolicy.MAIN_RELEASE_TAG
    ): String = "https://api.github.com/repos/$ownerRepo/releases/tags/$tag"
}
