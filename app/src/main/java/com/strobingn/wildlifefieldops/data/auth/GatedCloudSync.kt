package com.strobingn.wildlifefieldops.data.auth

import com.strobingn.wildlifefieldops.data.repository.SyncResult

/**
 * Wraps a cloud sync body so a missing/expired session never pushes or ACKs.
 * Unsynced queue items are the caller's Room rows — this wrapper simply does
 * not invoke [perform] when blocked, so nothing is marked synced.
 */
class GatedCloudSync(
    private val auth: AuthSessionPort,
    private val isConfigured: () -> Boolean,
    private val perform: suspend () -> SyncResult
) {
    suspend fun syncAll(): SyncResult {
        val decision = SyncAuthGate.evaluate(isConfigured(), auth.awaitReadySession())
        return when (decision) {
            is SyncGateDecision.Proceed -> perform()
            is SyncGateDecision.Blocked -> SyncResult(
                success = false,
                message = decision.message,
                requiresSignIn = decision.requiresSignIn,
                sessionBlocked = true
            )
        }
    }

    suspend fun mayTouchRemote(): Boolean {
        val decision = SyncAuthGate.evaluate(isConfigured(), auth.awaitReadySession())
        return decision is SyncGateDecision.Proceed
    }
}
