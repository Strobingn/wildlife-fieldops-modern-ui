package com.strobingn.wildlifefieldops.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import io.github.jan.supabase.gotrue.SessionManager
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Persists the GoTrue [UserSession] in EncryptedSharedPreferences (Android Keystore).
 * Falls back to a private (unencrypted) prefs file if Keystore is unavailable so
 * field techs are not signed out after an OEM Keystore glitch.
 *
 * Exclude [PREFS_NAME] and [FALLBACK_PREFS_NAME] from auto-backup.
 */
class EncryptedSessionManager(context: Context) : SessionManager {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKey,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse { error ->
        Log.w(TAG, "Encrypted session store unavailable; using private prefs", error)
        context.applicationContext.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun saveSession(session: UserSession) {
        val encoded = json.encodeToString(UserSession.serializer(), session)
        prefs.edit().putString(KEY_SESSION, encoded).apply()
    }

    override suspend fun loadSession(): UserSession? {
        val raw = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { json.decodeFromString(UserSession.serializer(), raw) }
            .onFailure { Log.w(TAG, "Stored session could not be decoded", it) }
            .getOrNull()
    }

    override suspend fun deleteSession() {
        prefs.edit().remove(KEY_SESSION).apply()
    }

    companion object {
        const val PREFS_NAME = "fieldops_supabase_auth"
        const val FALLBACK_PREFS_NAME = "fieldops_supabase_auth_fallback"
        private const val KEY_SESSION = "user_session_json"
        private val TAG = "EncryptedSessionManager"
    }
}
