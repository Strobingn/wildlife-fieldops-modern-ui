package com.strobingn.wildlifefieldops.update

object ApkInstallVerification {
    fun evaluate(
        downloaded: ApkIdentity,
        installed: ApkIdentity,
        expected: ExpectedApk
    ): ApkVerificationResult {
        if (downloaded.packageName.isBlank() || expected.packageName.isBlank()) {
            return ApkVerificationResult.Refused("Downloaded APK is missing a package name.")
        }
        if (!downloaded.packageName.equals(expected.packageName, ignoreCase = true)) {
            return ApkVerificationResult.Refused(
                "Downloaded APK package is ${downloaded.packageName}, expected ${expected.packageName}."
            )
        }
        if (!downloaded.packageName.equals(installed.packageName, ignoreCase = true)) {
            return ApkVerificationResult.Refused(
                "Downloaded APK package ${downloaded.packageName} does not match the installed app (${installed.packageName})."
            )
        }
        if (downloaded.versionCode != expected.versionCode) {
            return ApkVerificationResult.Refused(
                "Downloaded APK versionCode ${downloaded.versionCode} does not match the main release (${expected.versionCode})."
            )
        }
        if (!AppUpdatePolicy.isNewerVersion(downloaded.versionCode, installed.versionCode)) {
            return ApkVerificationResult.Refused(
                "Downloaded APK versionCode ${downloaded.versionCode} is not newer than this phone (${installed.versionCode})."
            )
        }
        if (!ApkSignerFingerprint.matches(downloaded.signerSha256, installed.signerSha256)) {
            return ApkVerificationResult.Refused(
                "Downloaded APK is signed with a different key than the app on this phone. " +
                    "Refusing install so local data is not wiped."
            )
        }
        if (!ApkSignerFingerprint.matches(downloaded.signerSha256, expected.installedSignerSha256)) {
            return ApkVerificationResult.Refused(
                "Downloaded APK signer does not match the installed app certificate. Refusing install."
            )
        }
        val ci = expected.ciSignerSha256
        if (!ci.isNullOrBlank() && !ApkSignerFingerprint.matches(downloaded.signerSha256, ci)) {
            return ApkVerificationResult.Refused(
                "Downloaded APK signer does not match the Wildlife FieldOps CI certificate. Refusing install."
            )
        }
        return ApkVerificationResult.Ok
    }

    fun installedCanReceiveMainUpdate(installedSignerSha256: String, ciSignerSha256: String): Boolean =
        ApkSignerFingerprint.matches(installedSignerSha256, ciSignerSha256)

    fun looksLikeApk(header: ByteArray): Boolean =
        header.size >= 4 &&
            header[0] == 0x50.toByte() &&
            header[1] == 0x4B.toByte()
}
