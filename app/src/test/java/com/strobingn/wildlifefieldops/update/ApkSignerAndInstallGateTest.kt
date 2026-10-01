package com.strobingn.wildlifefieldops.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkSignerAndInstallGateTest {

    private val ci =
        "EC:75:D0:BC:BC:62:30:6B:0C:38:91:76:9E:05:4C:EB:C7:7C:6A:84:4D:11:9B:40:18:B9:0C:7E:F7:57:0C:A6"

    @Test
    fun signerMatchIgnoresColonsAndCase() {
        val compact = ci.replace(":", "").lowercase()
        assertTrue(ApkSignerFingerprint.matches(ci, compact))
        assertTrue(ApkSignerFingerprint.matches(ci.lowercase(), ci.uppercase()))
        assertFalse(ApkSignerFingerprint.matches(ci, "AA:BB:CC"))
        assertFalse(ApkSignerFingerprint.matches("", ci))
        assertEquals(
            ci.replace(":", ""),
            ApkSignerFingerprint.normalize("  $ci  ")
        )
    }

    @Test
    fun installGateAcceptsMatchingMainApk() {
        val installed = ApkIdentity(
            packageName = AppUpdatePolicy.EXPECTED_PACKAGE,
            versionCode = 1_000_646,
            signerSha256 = ci
        )
        val downloaded = installed.copy(versionCode = 1_000_700)
        val expected = ExpectedApk(
            packageName = AppUpdatePolicy.EXPECTED_PACKAGE,
            versionCode = 1_000_700,
            installedSignerSha256 = ci,
            ciSignerSha256 = ci
        )
        assertEquals(ApkVerificationResult.Ok, ApkInstallVerification.evaluate(downloaded, installed, expected))
    }

    @Test
    fun installGateRefusesPackageMismatch() {
        val installed = identity(1_000_646)
        val downloaded = identity(1_000_700).copy(packageName = "com.example.other")
        val result = ApkInstallVerification.evaluate(
            downloaded,
            installed,
            ExpectedApk(AppUpdatePolicy.EXPECTED_PACKAGE, 1_000_700, ci, ci)
        )
        assertTrue(result is ApkVerificationResult.Refused)
        assertTrue((result as ApkVerificationResult.Refused).reason.contains("package"))
    }

    @Test
    fun installGateRefusesUnexpectedVersionCode() {
        val installed = identity(1_000_646)
        val downloaded = identity(1_000_699)
        val result = ApkInstallVerification.evaluate(
            downloaded,
            installed,
            ExpectedApk(AppUpdatePolicy.EXPECTED_PACKAGE, 1_000_700, ci, ci)
        )
        assertTrue(result is ApkVerificationResult.Refused)
        assertTrue((result as ApkVerificationResult.Refused).reason.contains("versionCode"))
    }

    @Test
    fun installGateRefusesSignerMismatchSoDataIsNotWiped() {
        val installed = identity(1_000_646)
        val downloaded = identity(1_000_700).copy(signerSha256 = "00:11:22:33")
        val result = ApkInstallVerification.evaluate(
            downloaded,
            installed,
            ExpectedApk(AppUpdatePolicy.EXPECTED_PACKAGE, 1_000_700, ci, ci)
        )
        assertTrue(result is ApkVerificationResult.Refused)
        assertTrue((result as ApkVerificationResult.Refused).reason.contains("local data"))
    }

    @Test
    fun installGateRefusesNonCiSignerEvenIfItMatchesInstalled() {
        val localDebug = "11:22:33:44"
        val installed = identity(50).copy(signerSha256 = localDebug)
        val downloaded = identity(1_000_700).copy(signerSha256 = localDebug)
        val result = ApkInstallVerification.evaluate(
            downloaded,
            installed,
            ExpectedApk(AppUpdatePolicy.EXPECTED_PACKAGE, 1_000_700, localDebug, ci)
        )
        assertTrue(result is ApkVerificationResult.Refused)
        assertTrue((result as ApkVerificationResult.Refused).reason.contains("CI certificate"))
        assertFalse(ApkInstallVerification.installedCanReceiveMainUpdate(localDebug, ci))
        assertTrue(ApkInstallVerification.installedCanReceiveMainUpdate(ci.lowercase(), ci))
    }

    @Test
    fun apkMagicIsPkZip() {
        assertTrue(ApkInstallVerification.looksLikeApk(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertFalse(ApkInstallVerification.looksLikeApk(byteArrayOf(0x3C, 0x68, 0x74, 0x6D)))
        assertFalse(ApkInstallVerification.looksLikeApk(byteArrayOf(0x50)))
    }

    private fun identity(code: Int) = ApkIdentity(
        packageName = AppUpdatePolicy.EXPECTED_PACKAGE,
        versionCode = code,
        signerSha256 = ci
    )
}
