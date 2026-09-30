package com.strobingn.wildlifefieldops.data.auth

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.strobingn.wildlifefieldops.data.remote.SupabaseService
import com.strobingn.wildlifefieldops.ui.viewmodel.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthSessionRepository @Inject constructor(
    private val supabaseService: SupabaseService,
    @ApplicationContext private val context: Context
) : AuthSessionPort {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _uiState = MutableStateFlow(AuthUiState())
    override val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    val continueOffline: StateFlow<Boolean?> = context.settingsDataStore.data
        .map<Preferences, Boolean?> { it[CONTINUE_OFFLINE] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { initialize() }
    }

    private suspend fun initialize() {
        val client = supabaseService.client
        if (client == null) {
            _uiState.value = AuthUiState(initialized = true, signedIn = false)
            return
        }
        runCatching { client.auth.awaitInitialization() }
            .onFailure { Log.w(TAG, "Auth initialization failed", it) }
        _uiState.value = client.auth.sessionStatus.value.toUi()
        client.auth.sessionStatus.collect { status ->
            _uiState.value = status.toUi()
        }
    }

    override suspend fun awaitReadySession(): AuthReadyResult {
        val client = supabaseService.client ?: return AuthReadyResult.CloudNotConfigured
        val auth = client.auth
        runCatching { auth.awaitInitialization() }
        val nowMs = Clock.System.now().toEpochMilliseconds()
        val current = auth.currentSessionOrNull()?.toSnapshot()
        if (current == null) {
            return when (auth.sessionStatus.value) {
                is SessionStatus.NetworkError -> AuthReadyResult.RefreshFailed(
                    message = "Network error refreshing session",
                    requiresSignIn = true
                )
                else -> AuthReadyResult.SignedOut
            }
        }
        return when (val plan = SyncAuthGate.planRefresh(nowMs, current)) {
            RefreshPlan.SignInRequired -> AuthReadyResult.SignedOut
            is RefreshPlan.UseCurrent -> AuthReadyResult.SignedIn(plan.snapshot, refreshed = false)
            is RefreshPlan.Refresh -> {
                val refresh = runCatching {
                    auth.refreshCurrentSession()
                    auth.currentSessionOrNull()?.toSnapshot()
                }
                val refreshed = refresh.getOrNull()
                val error = refresh.exceptionOrNull()
                SyncAuthGate.afterRefreshAttempt(
                    refreshed = refreshed,
                    previous = plan.snapshot,
                    nowMs = Clock.System.now().toEpochMilliseconds(),
                    errorMessage = error?.message,
                    invalidGrant = error.isInvalidGrant()
                )
            }
        }
    }

    override suspend fun signIn(email: String, password: String): AuthActionResult {
        val client = supabaseService.client
            ?: return AuthActionResult.Failure(SyncAuthGate.CLOUD_NOT_CONFIGURED)
        val trimmedEmail = email.trim()
        if (trimmedEmail.isEmpty() || password.isEmpty()) {
            return AuthActionResult.Failure("Enter email and password.")
        }
        return try {
            client.auth.signInWith(Email) {
                this.email = trimmedEmail
                this.password = password
            }
            val session = client.auth.currentSessionOrNull()
                ?: return AuthActionResult.Failure("Sign in did not create a session.")
            _uiState.value = session.toUi()
            setContinueOffline(false)
            AuthActionResult.Success
        } catch (t: Throwable) {
            Log.w(TAG, "Sign in failed", t)
            AuthActionResult.Failure(humanizeAuthError(t))
        }
    }

    override suspend fun signOut(): AuthActionResult {
        val client = supabaseService.client
        return try {
            if (client != null) {
                runCatching { client.auth.signOut() }
                    .onFailure { Log.w(TAG, "Remote sign-out failed; clearing local session", it) }
                runCatching { client.auth.clearSession() }
            }
            setContinueOffline(true)
            _uiState.value = AuthUiState(initialized = true, signedIn = false)
            AuthActionResult.Success
        } catch (t: Throwable) {
            Log.w(TAG, "Sign out failed", t)
            AuthActionResult.Failure(t.message ?: t.javaClass.simpleName)
        }
    }

    suspend fun setContinueOffline(value: Boolean) {
        context.settingsDataStore.edit { it[CONTINUE_OFFLINE] = value }
    }

    private fun SessionStatus.toUi(): AuthUiState = when (this) {
        is SessionStatus.Authenticated -> session.toUi()
        is SessionStatus.NotAuthenticated -> AuthUiState(initialized = true, signedIn = false)
        is SessionStatus.NetworkError -> {
            val snap = supabaseService.client?.auth?.currentSessionOrNull()
            if (snap != null) snap.toUi() else AuthUiState(initialized = true, signedIn = false)
        }
        SessionStatus.LoadingFromStorage -> AuthUiState(initialized = false)
    }

    private fun UserSession.toUi(): AuthUiState = AuthUiState(
        initialized = true,
        signedIn = true,
        email = user?.email?.takeIf { it.isNotBlank() },
        userId = user?.id
    )

    private fun UserSession.toSnapshot(): AuthUserSnapshot = AuthUserSnapshot(
        userId = user?.id.orEmpty(),
        email = user?.email.orEmpty(),
        accessToken = accessToken,
        refreshTokenPresent = refreshToken.isNotBlank(),
        expiresAtEpochMs = expiresAt.toEpochMilliseconds()
    )

    private fun Throwable?.isInvalidGrant(): Boolean {
        val text = buildString {
            generateSequence(this@isInvalidGrant) { it.cause }.forEach {
                append(it.message.orEmpty()).append(' ')
                append(it.javaClass.simpleName).append(' ')
            }
        }.lowercase()
        return "invalid" in text && ("grant" in text || "refresh" in text || "login" in text) ||
            "401" in text ||
            "invalid_grant" in text
    }

    private fun humanizeAuthError(error: Throwable): String {
        val text = buildString {
            generateSequence(error) { it.cause }.forEach { append(it.message.orEmpty()).append(' ') }
        }.lowercase()
        return when {
            "invalid login" in text || "invalid_grant" in text || "invalid credentials" in text ->
                "Email or password is incorrect."
            "email not confirmed" in text ->
                "This account's email is not confirmed yet."
            "user not found" in text ->
                "No account for that email. Ask the owner to create one."
            "network" in text || "unable to resolve" in text || "timeout" in text ||
                "failed to connect" in text ->
                "Network error. Try again when you have a signal."
            else -> error.message?.takeIf { it.isNotBlank() } ?: "Sign in failed."
        }
    }

    companion object {
        private val CONTINUE_OFFLINE = booleanPreferencesKey("continue_offline_without_sign_in")
        private const val TAG = "AuthSessionRepository"
    }
}
