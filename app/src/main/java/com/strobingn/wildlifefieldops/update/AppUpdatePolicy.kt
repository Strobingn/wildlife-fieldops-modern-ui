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
    /** Foreground / resume / worker checks. Forced Settings checks ignore this. */
    const val CHECK_INTERVAL_MS = 10L * 60L * 1000L
    /** WorkManager minimum for periodic work is 15 minutes. */
    const val PERIODIC_CHECK_INTERVAL_MS = 15L * 60L * 1000L

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

    /**
     * Dismiss hides this [remoteVersionCode] for the current process only.
     * A newer remote code shows the banner again. Next process start has
     * [dismissedVersionCode] null, so the banner returns even for the same code.
     */
    fun shouldShowBanner(
        updateAvailable: Boolean,
        remoteVersionCode: Int,
        dismissedVersionCode: Int?
    ): Boolean {
        if (!updateAvailable) return false
        val dismissed = dismissedVersionCode ?: return true
        return remoteVersionCode > dismissed
    }

    fun cacheBustUrl(url: String, nowMs: Long): String {
        val base = url.trim()
        if (base.isEmpty()) return base
        val sep = if (base.contains('?')) '&' else '?'
        return "${base}${sep}t=$nowMs"
    }

    /** After "Install unknown apps", download first unless an APK is already verified. */
    fun resumeDownloadAfterPermission(hasPendingApk: Boolean): Boolean = !hasPendingApk

    // PackageInstaller.STATUS_FAILURE_* values, kept numeric so this stays a pure JVM function.
    private const val STATUS_FAILURE_BLOCKED = 2
    private const val STATUS_FAILURE_INVALID = 4
    private const val STATUS_FAILURE_CONFLICT = 5
    private const val STATUS_FAILURE_STORAGE = 6
    private const val STATUS_FAILURE_INCOMPATIBLE = 7

    /**
     * Plain-language reason for a PackageInstaller failure, so a signature mismatch or full
     * storage is explained instead of showing Android's raw INSTALL_FAILED_* text.
     */
    fun installFailureMessage(statusCode: Int, systemMessage: String?): String {
        val detail = systemMessage?.trim().orEmpty()
        val lower = detail.lowercase()
        return when {
            lower.contains("signature") || lower.contains("update_incompatible") ->
                "Android refused the update: it is signed with a different key than the app on this phone. " +
                    "Only builds from the Wildlife FieldOps CI key can update this install in place. " +
                    "Nothing was changed and local data is intact."
            lower.contains("version_downgrade") || lower.contains("downgrade") ->
                "Android refused the update because it is not newer than the installed version."
            statusCode == STATUS_FAILURE_INCOMPATIBLE ->
                "Android says this update is not compatible with this phone or the installed app." +
                    (if (detail.isNotEmpty()) " ($detail)" else "")
            statusCode == STATUS_FAILURE_STORAGE ->
                "Not enough free storage to install the update. Free up space and tap Update again."
            statusCode == STATUS_FAILURE_BLOCKED ->
                "Android (or a security app on this phone) blocked the install. Check Play Protect or " +
                    "device policy, then tap Update again."
            statusCode == STATUS_FAILURE_INVALID ->
                "Android rejected the downloaded APK as invalid. Check for updates and try again."
            statusCode == STATUS_FAILURE_CONFLICT ->
                "Android reports a conflicting install. Try again in a moment."
            detail.isNotEmpty() -> detail
            else -> "Android could not install the update."
        }
    }

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
