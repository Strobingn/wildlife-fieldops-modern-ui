package com.strobingn.wildlifefieldops.data.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Testable auth surface. Production talks to supabase-kt GoTrue; tests use fakes.
 */
interface AuthSessionPort {
    val uiState: StateFlow<AuthUiState>

    suspend fun awaitReadySession(): AuthReadyResult

    suspend fun signIn(email: String, password: String): AuthActionResult

    /**
     * Clears the GoTrue session only. Never touches Room / unsynced queues.
     */
    suspend fun signOut(): AuthActionResult
}
