package com.strobingn.wildlifefieldops.update

import kotlinx.serialization.Serializable

/** Machine-readable main-channel update info published next to `app-debug.apk`. */
@Serializable
data class AppUpdateManifest(
    val schemaVersion: Int = 1,
    val channel: String,
    val tag: String = AppUpdatePolicy.MAIN_RELEASE_TAG,
    val packageName: String = AppUpdatePolicy.EXPECTED_PACKAGE,
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkAssetName: String = "app-debug.apk",
    val commitSha: String = "",
    val buildTime: String = "",
    val runNumber: Int = 0,
    val changelog: String = ""
)

data class AppUpdateHttpResponse(
    val code: Int,
    val body: String,
    val rateLimitRemaining: String? = null
)

data class ApkIdentity(
    val packageName: String,
    val versionCode: Int,
    val signerSha256: String
)

data class ExpectedApk(
    val packageName: String,
    val versionCode: Int,
    val installedSignerSha256: String,
    val ciSignerSha256: String? = null
)

sealed class ApkVerificationResult {
    data object Ok : ApkVerificationResult()
    data class Refused(val reason: String) : ApkVerificationResult()
}

sealed class AppUpdateFetchResult {
    data class Success(val manifest: AppUpdateManifest) : AppUpdateFetchResult()
    data class Unavailable(val message: String) : AppUpdateFetchResult()
    data class Failed(val message: String) : AppUpdateFetchResult()
}

enum class AppUpdateInstallLaunch {
    SessionCommitted,
    ExternalInstallerOpened
}

sealed class GithubReleaseLookup {
    data class ManifestJsonUrl(val url: String) : GithubReleaseLookup()
    data class Manifest(val manifest: AppUpdateManifest) : GithubReleaseLookup()
    data class Unavailable(val message: String) : GithubReleaseLookup()
    data class Failed(val message: String) : GithubReleaseLookup()
}
