package com.strobingn.wildlifefieldops.data.auth

/**
 * Snapshot of a signed-in GoTrue session. Tokens stay in the encrypted session
 * manager; this copy is for gating and UI (email / expiry), not for logging.
 */
data class AuthUserSnapshot(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshTokenPresent: Boolean,
    val expiresAtEpochMs: Long
)

sealed class AuthReadyResult {
    data class SignedIn(
        val snapshot: AuthUserSnapshot,
        val refreshed: Boolean
    ) : AuthReadyResult()

    data object SignedOut : AuthReadyResult()

    data class RefreshFailed(
        val message: String,
        val requiresSignIn: Boolean
    ) : AuthReadyResult()

    data object CloudNotConfigured : AuthReadyResult()
}

data class AuthUiState(
    val initialized: Boolean = false,
    val signedIn: Boolean = false,
    val email: String? = null,
    val userId: String? = null
)

sealed class AuthActionResult {
    data object Success : AuthActionResult()
    data class Failure(val message: String) : AuthActionResult()
}

sealed class SyncGateDecision {
    data class Proceed(
        val snapshot: AuthUserSnapshot,
        val refreshed: Boolean
    ) : SyncGateDecision()

    data class Blocked(
        val reason: SyncBlockReason,
        val message: String,
        val requiresSignIn: Boolean
    ) : SyncGateDecision()
}

enum class SyncBlockReason {
    NOT_CONFIGURED,
    SIGN_IN_REQUIRED,
    SESSION_REFRESH_FAILED
}

sealed class RefreshPlan {
    data object SignInRequired : RefreshPlan()
    data class UseCurrent(val snapshot: AuthUserSnapshot) : RefreshPlan()
    data class Refresh(val snapshot: AuthUserSnapshot) : RefreshPlan()
}
