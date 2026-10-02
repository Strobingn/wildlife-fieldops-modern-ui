package com.strobingn.wildlifefieldops.update

/**
 * Rules for what the in-app updater may offer. Only the rolling main release
 * (`debug-latest`) is an update source — branch/PR tags such as
 * `apk-cursor-…-latest` are never offered.
 */
object AppUpdatePolicy {
    const val MAIN_RELEASE_TAG = "debug-latest"
    const val EXPECTED_PACKAGE = "com.strobingn.wildlifefieldops"
    const val CI_SIGNER_SHA256 =
        "EC:75:D0:BC:BC:62:30:6B:0C:38:91:76:9E:05:4C:EB:C7:7C:6A:84:4D:11:9B:40:18:B9:0C:7E:F7:57:0C:A6"
    const val CHECK_INTERVAL_MS = 4L * 60L * 60L * 1000L

    fun isMainReleaseTag(tag: String?): Boolean =
        !tag.isNullOrBlank() && tag.trim().equals(MAIN_RELEASE_TAG, ignoreCase = true)

    fun isMainChannel(channel: String?): Boolean {
        val value = channel?.trim().orEmpty()
        return value.equals("main", ignoreCase = true) || value.equals("master", ignoreCase = true)
    }

    fun isMainApkUrl(url: String?): Boolean {
        val path = url?.trim()?.lowercase().orEmpty()
        if (path.isEmpty()) return false
        if (!path.startsWith("https://")) return false
        val isGithub = path.contains("://github.com/") || path.contains("://objects.githubusercontent.com/")
        if (!isGithub) return false
        if (Regex("/releases/download/apk-[^/]+-latest/").containsMatchIn(path)) return false
        if (path.contains("/releases/download/") && !path.contains("/releases/download/debug-latest/")) {
            return false
        }
        return path.contains("/releases/download/debug-latest/") ||
            path.contains("objects.githubusercontent.com")
    }

    fun isNewerVersion(remoteVersionCode: Int, installedVersionCode: Int): Boolean =
        remoteVersionCode > installedVersionCode

    fun shouldCheck(lastCheckAtMs: Long, nowMs: Long, force: Boolean): Boolean {
        if (force) return true
        if (lastCheckAtMs <= 0L) return true
        return nowMs - lastCheckAtMs >= CHECK_INTERVAL_MS
    }

    /** After "Install unknown apps", download first unless an APK is already verified. */
    fun resumeDownloadAfterPermission(hasPendingApk: Boolean): Boolean = !hasPendingApk

    fun accept(manifest: AppUpdateManifest): String? {
        if (!isMainChannel(manifest.channel)) {
            return "Ignoring non-main update channel '${manifest.channel}'."
        }
        if (!isMainReleaseTag(manifest.tag)) {
            return "Ignoring release tag '${manifest.tag}' (only $MAIN_RELEASE_TAG is offered)."
        }
        if (manifest.packageName.isNotBlank() &&
            !manifest.packageName.equals(EXPECTED_PACKAGE, ignoreCase = true)
        ) {
            return "Ignoring update for package ${manifest.packageName}."
        }
        if (manifest.versionCode <= 0) {
            return "Update info is missing a valid versionCode."
        }
        if (!isMainApkUrl(manifest.apkUrl)) {
            return "APK URL is not the main-branch debug-latest release."
        }
        return null
    }
}
