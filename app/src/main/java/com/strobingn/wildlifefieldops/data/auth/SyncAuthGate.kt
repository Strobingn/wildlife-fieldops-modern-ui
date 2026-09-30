package com.strobingn.wildlifefieldops.data.auth

/**
 * Pure sync/session policy. No Android, Room, or network.
 *
 * Missing / unusable session → block with a clear message. Callers must not
 * mark queue items synced and must not treat the block as success.
 */
object SyncAuthGate {
    const val SIGN_IN_TO_SYNC = "Sign in to sync"
    const val SESSION_EXPIRED = "Session expired. Sign in to sync."
    const val SESSION_REFRESH_FAILED = "Can't refresh session. Sign in to sync, or retry when you have a signal."
    const val CLOUD_NOT_CONFIGURED =
        "Cloud not configured. Rebuild the APK with Supabase secrets (VITE_SUPABASE_URL / VITE_SUPABASE_ANON_KEY)."

    /** Refresh when the access token is expired or within this skew. */
    const val REFRESH_SKEW_MS = 60_000L

    fun evaluate(cloudConfigured: Boolean, ready: AuthReadyResult): SyncGateDecision {
        if (!cloudConfigured || ready is AuthReadyResult.CloudNotConfigured) {
            return SyncGateDecision.Blocked(
                reason = SyncBlockReason.NOT_CONFIGURED,
                message = CLOUD_NOT_CONFIGURED,
                requiresSignIn = false
            )
        }
        return when (ready) {
            is AuthReadyResult.SignedIn -> SyncGateDecision.Proceed(ready.snapshot, ready.refreshed)
            AuthReadyResult.SignedOut -> SyncGateDecision.Blocked(
                reason = SyncBlockReason.SIGN_IN_REQUIRED,
                message = SIGN_IN_TO_SYNC,
                requiresSignIn = true
            )
            is AuthReadyResult.RefreshFailed -> SyncGateDecision.Blocked(
                reason = SyncBlockReason.SESSION_REFRESH_FAILED,
                message = if (ready.requiresSignIn) SESSION_EXPIRED else SESSION_REFRESH_FAILED,
                requiresSignIn = ready.requiresSignIn
            )
            AuthReadyResult.CloudNotConfigured -> SyncGateDecision.Blocked(
                reason = SyncBlockReason.NOT_CONFIGURED,
                message = CLOUD_NOT_CONFIGURED,
                requiresSignIn = false
            )
        }
    }

    fun planRefresh(nowMs: Long, session: AuthUserSnapshot?): RefreshPlan {
        if (session == null) return RefreshPlan.SignInRequired
        if (session.expiresAtEpochMs > nowMs + REFRESH_SKEW_MS) {
            return RefreshPlan.UseCurrent(session)
        }
        if (!session.refreshTokenPresent) return RefreshPlan.SignInRequired
        return RefreshPlan.Refresh(session)
    }

    /**
     * Combine a refresh attempt with the previous snapshot.
     *
     * - Refresh succeeded → proceed (token refresh path).
     * - Refresh failed but access token still valid → proceed without refresh.
     * - Network failure, token expired → blocked, retry later (not a silent success).
     * - Invalid refresh token → sign-in required.
     */
    fun afterRefreshAttempt(
        refreshed: AuthUserSnapshot?,
        previous: AuthUserSnapshot?,
        nowMs: Long,
        errorMessage: String?,
        invalidGrant: Boolean
    ): AuthReadyResult {
        if (refreshed != null) {
            return AuthReadyResult.SignedIn(refreshed, refreshed = true)
        }
        if (previous != null && previous.expiresAtEpochMs > nowMs) {
            return AuthReadyResult.SignedIn(previous, refreshed = false)
        }
        if (invalidGrant) {
            return AuthReadyResult.RefreshFailed(
                message = errorMessage ?: SESSION_EXPIRED,
                requiresSignIn = true
            )
        }
        return AuthReadyResult.RefreshFailed(
            message = errorMessage ?: SESSION_REFRESH_FAILED,
            requiresSignIn = false
        )
    }
}
