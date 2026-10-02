package com.strobingn.wildlifefieldops.update

import com.strobingn.wildlifefieldops.BuildConfig

enum class AppUpdatePhase {
    Idle,
    Checking,
    Ready,
    Downloading,
    Verifying,
    Flushing,
    AwaitingUnsynced,
    AwaitingPermission,
    Installing
}

data class AppUpdateUiState(
    val phase: AppUpdatePhase = AppUpdatePhase.Idle,
    val currentName: String = BuildConfig.VERSION_NAME,
    val currentCode: Int = BuildConfig.VERSION_CODE,
    val latest: AppUpdateManifest? = null,
    val lastError: String? = null,
    val statusMessage: String? = null,
    val downloadBytes: Long = 0L,
    val downloadTotal: Long = 0L,
    val showBanner: Boolean = false,
    val showDialog: Boolean = false,
    val showUnknownSourcesPrompt: Boolean = false,
    val pendingUnsynced: Int = 0,
    val updateAvailable: Boolean = false,
    val lastCheckedAtMs: Long = 0L
) {
    val latestLabel: String
        get() {
            val remote = latest ?: return "—"
            val name = remote.versionName.ifBlank { "main build" }
            return "$name (code ${remote.versionCode})"
        }

    val currentLabel: String
        get() = "$currentName (code $currentCode)"

    val downloadFraction: Float
        get() = if (downloadTotal > 0L) {
            (downloadBytes.toFloat() / downloadTotal.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}
