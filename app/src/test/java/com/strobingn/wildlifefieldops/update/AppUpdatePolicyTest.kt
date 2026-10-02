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
        assertTrue(AppUpdatePolicy.shouldCheck(lastCheckAtMs = 0L, nowMs = now, force = false))
        assertFalse(
            AppUpdatePolicy.shouldCheck(
                lastCheckAtMs = now - 60_000L,
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
    fun permissionReturnResumesDownloadUntilAnApkIsPending() {
        assertTrue(AppUpdatePolicy.resumeDownloadAfterPermission(hasPendingApk = false))
        assertFalse(AppUpdatePolicy.resumeDownloadAfterPermission(hasPendingApk = true))
    }

    private fun sampleManifest() = AppUpdateManifest(
        channel = "main",
        tag = "debug-latest",
        versionCode = 1_000_700,
        versionName = "2.5.1-in-app-update",
        apkUrl = "https://github.com/Strobingn/wildlife-fieldops-modern-ui/releases/download/debug-latest/app-debug.apk"
    )
}
