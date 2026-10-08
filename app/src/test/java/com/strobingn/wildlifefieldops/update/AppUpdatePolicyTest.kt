package com.strobingn.wildlifefieldops.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {

    @Test
    fun newerVersionCodeWins() {
        assertTrue(AppUpdatePolicy.isNewerVersion(1_000_647, 1_000_646))
        assertTrue(AppUpdatePolicy.isNewerVersion(1_000_001, 50))
        assertFalse(AppUpdatePolicy.isNewerVersion(50, 50))
        assertFalse(AppUpdatePolicy.isNewerVersion(49, 50))
        assertFalse(AppUpdatePolicy.isNewerVersion(1_000_646, 1_000_646))
    }

    @Test
    fun onlyMainReleaseTagIsOffered() {
        assertTrue(AppUpdatePolicy.isMainReleaseTag("debug-latest"))
        assertTrue(AppUpdatePolicy.isMainReleaseTag("DEBUG-LATEST"))
        assertFalse(AppUpdatePolicy.isMainReleaseTag("apk-cursor-in-app-updater-a8e4-latest"))
        assertFalse(AppUpdatePolicy.isMainReleaseTag("apk-main-latest"))
        assertFalse(AppUpdatePolicy.isMainReleaseTag(""))
        assertFalse(AppUpdatePolicy.isMainReleaseTag(null))
    }

    @Test
    fun onlyMainChannelIsOffered() {
        assertTrue(AppUpdatePolicy.isMainChannel("main"))
        assertTrue(AppUpdatePolicy.isMainChannel("master"))
        assertFalse(AppUpdatePolicy.isMainChannel("cursor/in-app-updater-a8e4"))
        assertFalse(AppUpdatePolicy.isMainChannel("pr-12"))
        assertFalse(AppUpdatePolicy.isMainChannel(""))
    }

    @Test
    fun branchApkUrlsAreRejected() {
        val main = "https://github.com/Strobingn/wildlife-fieldops-modern-ui/releases/download/debug-latest/app-debug.apk"
        val branch = "https://github.com/Strobingn/wildlife-fieldops-modern-ui/releases/download/apk-cursor-in-app-updater-a8e4-latest/app-debug.apk"
        val otherTag = "https://github.com/Strobingn/wildlife-fieldops-modern-ui/releases/download/v2.4.0/app-debug.apk"
        assertTrue(AppUpdatePolicy.isMainApkUrl(main))
        assertFalse(AppUpdatePolicy.isMainApkUrl(branch))
        assertFalse(AppUpdatePolicy.isMainApkUrl(otherTag))
        assertFalse(AppUpdatePolicy.isMainApkUrl("https://example.com/app-debug.apk"))
    }

    @Test
    fun acceptRequiresMainManifest() {
        val ok = sampleManifest()
        assertNull(AppUpdatePolicy.accept(ok))
        assertTrue(
            AppUpdatePolicy.accept(ok.copy(channel = "pr"))?.contains("non-main") == true
        )
        assertTrue(
            AppUpdatePolicy.accept(ok.copy(tag = "apk-feature-latest"))?.contains("debug-latest") == true
        )
        assertTrue(
            AppUpdatePolicy.accept(
                ok.copy(
                    apkUrl = "https://github.com/Strobingn/wildlife-fieldops-modern-ui/releases/download/apk-feature-latest/app-debug.apk"
                )
            )?.contains("debug-latest") == true
        )
        assertEquals(
            "Update info is missing a valid versionCode.",
            AppUpdatePolicy.accept(ok.copy(versionCode = 0))
        )
    }

    @Test
    fun startCheckIsThrottledUnlessForced() {
        val now = 10_000_000L
        assertEquals(10L * 60L * 1000L, AppUpdatePolicy.CHECK_INTERVAL_MS)
        assertTrue(AppUpdatePolicy.shouldCheck(lastCheckAtMs = 0L, nowMs = now, force = false))
        assertFalse(
            AppUpdatePolicy.shouldCheck(
                lastCheckAtMs = now - 60_000L,
                nowMs = now,
                force = false
            )
        )
        assertFalse(
            AppUpdatePolicy.shouldCheck(
                lastCheckAtMs = now - AppUpdatePolicy.CHECK_INTERVAL_MS + 1L,
                nowMs = now,
                force = false
            )
        )
        assertTrue(
            AppUpdatePolicy.shouldCheck(
                lastCheckAtMs = now - AppUpdatePolicy.CHECK_INTERVAL_MS,
                nowMs = now,
                force = false
            )
        )
        assertTrue(
            AppUpdatePolicy.shouldCheck(
                lastCheckAtMs = now - 1L,
                nowMs = now,
                force = true
            )
        )
    }

    @Test
    fun dismissedBannerReturnsForNewerVersionAndOnFreshStart() {
        assertTrue(
            AppUpdatePolicy.shouldShowBanner(
                updateAvailable = true,
                remoteVersionCode = 1_000_702,
                dismissedVersionCode = null
            )
        )
        assertFalse(
            AppUpdatePolicy.shouldShowBanner(
                updateAvailable = true,
                remoteVersionCode = 1_000_702,
                dismissedVersionCode = 1_000_702
            )
        )
        assertTrue(
            AppUpdatePolicy.shouldShowBanner(
                updateAvailable = true,
                remoteVersionCode = 1_000_703,
                dismissedVersionCode = 1_000_702
            )
        )
        assertFalse(
            AppUpdatePolicy.shouldShowBanner(
                updateAvailable = false,
                remoteVersionCode = 1_000_703,
                dismissedVersionCode = null
            )
        )
        assertFalse(
            AppUpdatePolicy.shouldShowBanner(
                updateAvailable = true,
                remoteVersionCode = 1_000_701,
                dismissedVersionCode = 1_000_702
            )
        )
    }

    @Test
    fun cacheBustAppendsTimestampQuery() {
        assertEquals(
            "https://example.com/update.json?t=42",
            AppUpdatePolicy.cacheBustUrl("https://example.com/update.json", 42L)
        )
        assertEquals(
            "https://example.com/update.json?foo=1&t=42",
            AppUpdatePolicy.cacheBustUrl("https://example.com/update.json?foo=1", 42L)
        )
        assertEquals("", AppUpdatePolicy.cacheBustUrl("  ", 42L))
        assertEquals(
            "https://github.com/Strobingn/wildlife-fieldops-modern-ui/releases/download/debug-latest/update.json?t=1700000000000",
            AppUpdatePolicy.cacheBustUrl(
                "https://github.com/Strobingn/wildlife-fieldops-modern-ui/releases/download/debug-latest/update.json",
                1_700_000_000_000L
            )
        )
    }

    @Test
    fun permissionReturnResumesDownloadUntilAnApkIsPending() {
        assertTrue(AppUpdatePolicy.resumeDownloadAfterPermission(hasPendingApk = false))
        assertFalse(AppUpdatePolicy.resumeDownloadAfterPermission(hasPendingApk = true))
    }

    @Test
    fun ciVersionCodesOutrankLocalBuildCodes() {
        // CI builds are 1_000_000 + run number; local builds use small numbers.
        assertTrue(AppUpdatePolicy.isNewerVersion(1_000_001, 60))
        assertFalse(AppUpdatePolicy.isNewerVersion(60, 1_000_001))
    }

    @Test
    fun signatureMismatchIsExplained() {
        val raw = "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package com.strobingn.wildlifefieldops " +
            "signatures do not match newer version; ignoring!"
        val message = AppUpdatePolicy.installFailureMessage(7, raw)
        assertTrue(message.contains("different key"))
        assertTrue(message.contains("local data is intact"))
    }

    @Test
    fun installFailureMessagesCoverCommonStatuses() {
        assertTrue(AppUpdatePolicy.installFailureMessage(6, null).contains("storage"))
        assertTrue(AppUpdatePolicy.installFailureMessage(2, "").contains("blocked"))
        assertTrue(AppUpdatePolicy.installFailureMessage(4, null).contains("invalid"))
        assertTrue(
            AppUpdatePolicy.installFailureMessage(7, "INSTALL_FAILED_VERSION_DOWNGRADE")
                .contains("not newer")
        )
        assertEquals("Something odd", AppUpdatePolicy.installFailureMessage(1, " Something odd "))
        assertEquals(
            "Android could not install the update.",
            AppUpdatePolicy.installFailureMessage(1, null)
        )
    }

    @Test
    fun truncatedDownloadsAreDetected() {
        assertTrue(UpdateDownloadCheck.isComplete(read = 1_000L, total = 1_000L))
        assertTrue(UpdateDownloadCheck.isComplete(read = 1_000L, total = -1L))
        assertFalse(UpdateDownloadCheck.isComplete(read = 999L, total = 1_000L))
        assertFalse(UpdateDownloadCheck.isComplete(read = 0L, total = -1L))
    }

    private fun sampleManifest() = AppUpdateManifest(
        channel = "main",
        tag = "debug-latest",
        versionCode = 1_000_700,
        versionName = "2.5.1-in-app-update",
        apkUrl = "https://github.com/Strobingn/wildlife-fieldops-modern-ui/releases/download/debug-latest/app-debug.apk"
    )
}
