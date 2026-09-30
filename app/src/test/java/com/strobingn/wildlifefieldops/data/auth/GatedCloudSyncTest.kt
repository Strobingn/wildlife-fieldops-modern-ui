package com.strobingn.wildlifefieldops.data.auth

import com.strobingn.wildlifefieldops.data.repository.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatedCloudSyncTest {

    @Test
    fun noSessionBlocksAndRetainsQueue() = runBlocking {
        val queue = InMemoryUnsyncedQueue(listOf("job-1", "job-2"))
        val auth = FakeAuthSessionPort(ready = AuthReadyResult.SignedOut)
        val gated = GatedCloudSync(auth, isConfigured = { true }) {
            queue.markAllSynced()
            SyncResult(success = true, message = "should not run", pushedJobs = queue.pushed)
        }

        val result = gated.syncAll()

        assertFalse(result.success)
        assertTrue(result.sessionBlocked)
        assertTrue(result.requiresSignIn)
        assertEquals(SyncAuthGate.SIGN_IN_TO_SYNC, result.message)
        assertEquals(listOf("job-1", "job-2"), queue.remaining())
        assertEquals(0, auth.signInCalls)
    }

    @Test
    fun sessionLetsSyncProceedAndDrainQueue() = runBlocking {
        val queue = InMemoryUnsyncedQueue(listOf("job-1"))
        val snap = AuthUserSnapshot("u1", "a@b.c", "tok", true, 9_999_999_999L)
        val auth = FakeAuthSessionPort(ready = AuthReadyResult.SignedIn(snap, refreshed = false))
        val gated = GatedCloudSync(auth, isConfigured = { true }) {
            queue.markAllSynced()
            SyncResult(success = true, message = "Synced", pushedJobs = 1)
        }

        val result = gated.syncAll()

        assertTrue(result.success)
        assertEquals(0, queue.remaining().size)
        assertEquals(1, result.pushedJobs)
        assertFalse(result.sessionBlocked)
    }

    @Test
    fun tokenRefreshPathProceedsWithNewToken() = runBlocking {
        val queue = InMemoryUnsyncedQueue(listOf("obs-1"))
        val auth = FakeAuthSessionPort(ready = AuthReadyResult.SignedOut).also { it.expireThenRefresh() }
        var usedToken: String? = null
        val gated = GatedCloudSync(auth, isConfigured = { true }) {
            usedToken = (auth.awaitReadySession() as AuthReadyResult.SignedIn).snapshot.accessToken
            queue.markAllSynced()
            SyncResult(success = true, message = "Synced", pushedObservations = 1)
        }

        val firstReady = auth.awaitReadySession() as AuthReadyResult.SignedIn
        assertTrue(firstReady.refreshed)
        assertEquals("refreshed-token", firstReady.snapshot.accessToken)

        val result = gated.syncAll()
        assertTrue(result.success)
        assertEquals("refreshed-token", usedToken)
        assertTrue(queue.remaining().isEmpty())
    }

    @Test
    fun signOutDoesNotWipeQueue() = runBlocking {
        val queue = InMemoryUnsyncedQueue(listOf("cust-1"))
        val snap = AuthUserSnapshot("u1", "a@b.c", "tok", true, 9_999_999_999L)
        val auth = FakeAuthSessionPort(ready = AuthReadyResult.SignedIn(snap, false))
        auth.signOut()
        val gated = GatedCloudSync(auth, isConfigured = { true }) {
            queue.markAllSynced()
            SyncResult(success = true, message = "should not run")
        }
        val result = gated.syncAll()
        assertFalse(result.success)
        assertEquals(listOf("cust-1"), queue.remaining())
    }
}

private class InMemoryUnsyncedQueue(initial: List<String>) {
    private val ids = initial.toMutableList()
    var pushed: Int = 0
        private set

    fun remaining(): List<String> = ids.toList()

    fun markAllSynced() {
        pushed = ids.size
        ids.clear()
    }
}

private class FakeAuthSessionPort(
    private var ready: AuthReadyResult
) : AuthSessionPort {
    var signInCalls = 0
    override val uiState: StateFlow<AuthUiState> = MutableStateFlow(AuthUiState(initialized = true))

    override suspend fun awaitReadySession(): AuthReadyResult = ready

    override suspend fun signIn(email: String, password: String): AuthActionResult {
        signInCalls += 1
        ready = AuthReadyResult.SignedIn(
            AuthUserSnapshot("u1", email, "tok", true, 9_999_999_999L),
            refreshed = false
        )
        return AuthActionResult.Success
    }

    override suspend fun signOut(): AuthActionResult {
        ready = AuthReadyResult.SignedOut
        return AuthActionResult.Success
    }

    fun expireThenRefresh() {
        val refreshed = AuthUserSnapshot(
            userId = "u1",
            email = "a@b.c",
            accessToken = "refreshed-token",
            refreshTokenPresent = true,
            expiresAtEpochMs = 9_999_999_999L
        )
        ready = AuthReadyResult.SignedIn(refreshed, refreshed = true)
    }
}
