package com.strobingn.wildlifefieldops.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAuthGateTest {

    private fun snapshot(
        expiresAt: Long,
        refresh: Boolean = true,
        token: String = "access-token"
    ) = AuthUserSnapshot(
        userId = "user-1",
        email = "tech@wildlifewhispererllc.com",
        accessToken = token,
        refreshTokenPresent = refresh,
        expiresAtEpochMs = expiresAt
    )

    @Test
    fun noSessionBlocksSyncWithSignInMessage() {
        val decision = SyncAuthGate.evaluate(
            cloudConfigured = true,
            ready = AuthReadyResult.SignedOut
        )
        val blocked = decision as SyncGateDecision.Blocked
        assertEquals(SyncBlockReason.SIGN_IN_REQUIRED, blocked.reason)
        assertEquals(SyncAuthGate.SIGN_IN_TO_SYNC, blocked.message)
        assertTrue(blocked.requiresSignIn)
    }

    @Test
    fun sessionAllowsSyncToProceed() {
        val snap = snapshot(expiresAt = 9_999_999_999L)
        val decision = SyncAuthGate.evaluate(
            cloudConfigured = true,
            ready = AuthReadyResult.SignedIn(snap, refreshed = false)
        )
        val proceed = decision as SyncGateDecision.Proceed
        assertEquals("user-1", proceed.snapshot.userId)
        assertFalse(proceed.refreshed)
    }

    @Test
    fun refreshSuccessIsProceedWithRefreshedFlag() {
        val snap = snapshot(expiresAt = 9_999_999_999L, token = "new-token")
        val decision = SyncAuthGate.evaluate(
            cloudConfigured = true,
            ready = AuthReadyResult.SignedIn(snap, refreshed = true)
        )
        val proceed = decision as SyncGateDecision.Proceed
        assertTrue(proceed.refreshed)
        assertEquals("new-token", proceed.snapshot.accessToken)
    }

    @Test
    fun refreshFailureBlocksWithoutDroppingSignInRequirementWhenGrantInvalid() {
        val decision = SyncAuthGate.evaluate(
            cloudConfigured = true,
            ready = AuthReadyResult.RefreshFailed("invalid_grant", requiresSignIn = true)
        )
        val blocked = decision as SyncGateDecision.Blocked
        assertEquals(SyncBlockReason.SESSION_REFRESH_FAILED, blocked.reason)
        assertTrue(blocked.requiresSignIn)
        assertEquals(SyncAuthGate.SESSION_EXPIRED, blocked.message)
    }

    @Test
    fun networkRefreshFailureIsRetryableBlock() {
        val decision = SyncAuthGate.evaluate(
            cloudConfigured = true,
            ready = AuthReadyResult.RefreshFailed("timeout", requiresSignIn = false)
        )
        val blocked = decision as SyncGateDecision.Blocked
        assertFalse(blocked.requiresSignIn)
        assertEquals(SyncAuthGate.SESSION_REFRESH_FAILED, blocked.message)
    }

    @Test
    fun planRefreshUsesCurrentWhenNotNearExpiry() {
        val now = 1_000_000L
        val plan = SyncAuthGate.planRefresh(now, snapshot(expiresAt = now + 120_000L))
        assertTrue(plan is RefreshPlan.UseCurrent)
    }

    @Test
    fun planRefreshWhenWithinSkew() {
        val now = 1_000_000L
        val plan = SyncAuthGate.planRefresh(now, snapshot(expiresAt = now + 30_000L))
        assertTrue(plan is RefreshPlan.Refresh)
    }

    @Test
    fun afterRefreshAttemptReturnsRefreshedSnapshot() {
        val previous = snapshot(expiresAt = 1L, token = "old")
        val next = snapshot(expiresAt = 9_999L, token = "fresh")
        val result = SyncAuthGate.afterRefreshAttempt(
            refreshed = next,
            previous = previous,
            nowMs = 50L,
            errorMessage = null,
            invalidGrant = false
        )
        val signedIn = result as AuthReadyResult.SignedIn
        assertTrue(signedIn.refreshed)
        assertEquals("fresh", signedIn.snapshot.accessToken)
    }

    @Test
    fun afterRefreshNetworkFailureKeepsValidAccessToken() {
        val previous = snapshot(expiresAt = 10_000L, token = "still-good")
        val result = SyncAuthGate.afterRefreshAttempt(
            refreshed = null,
            previous = previous,
            nowMs = 1_000L,
            errorMessage = "timeout",
            invalidGrant = false
        )
        val signedIn = result as AuthReadyResult.SignedIn
        assertFalse(signedIn.refreshed)
        assertEquals("still-good", signedIn.snapshot.accessToken)
    }

    @Test
    fun afterRefreshInvalidGrantRequiresSignIn() {
        val previous = snapshot(expiresAt = 1L)
        val result = SyncAuthGate.afterRefreshAttempt(
            refreshed = null,
            previous = previous,
            nowMs = 50L,
            errorMessage = "invalid_grant",
            invalidGrant = true
        )
        val failed = result as AuthReadyResult.RefreshFailed
        assertTrue(failed.requiresSignIn)
    }

    @Test
    fun notConfiguredBlocksWithoutSignIn() {
        val decision = SyncAuthGate.evaluate(false, AuthReadyResult.SignedOut)
        val blocked = decision as SyncGateDecision.Blocked
        assertEquals(SyncBlockReason.NOT_CONFIGURED, blocked.reason)
        assertFalse(blocked.requiresSignIn)
    }
}
