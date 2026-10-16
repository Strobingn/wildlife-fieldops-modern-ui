package com.strobingn.wildlifefieldops.ui.viewmodel

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.settingsDataStore by preferencesDataStore(name = "settings")

val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
val LAST_SYNC_OK = booleanPreferencesKey("last_sync_ok")
val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
/** Sync status screen only. Written next to the keys above; sync itself never reads them. */
val LAST_SYNC_SUCCESS_AT = longPreferencesKey("last_sync_success_at")
val LAST_SYNC_ERROR_AT = longPreferencesKey("last_sync_error_at")
val LAST_SYNC_ERROR = stringPreferencesKey("last_sync_error")

/** Same keys as the Settings switches; read by background work, which has no view model. */
private val OFFLINE_MODE_PREF = booleanPreferencesKey("offline_mode")
private val NOTIFICATIONS_ENABLED_PREF = booleanPreferencesKey("notifications_enabled")

/** Settings → Offline Mode. Pauses cloud sync; queued rows stay in Room. */
suspend fun Context.offlineModeOn(): Boolean =
    settingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[OFFLINE_MODE_PREF] ?: false }
        .first()

/** Settings → Enable Notifications. Master switch over trap reminders and weather alerts. */
suspend fun Context.notificationsAllowed(): Boolean =
    settingsDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[NOTIFICATIONS_ENABLED_PREF] ?: true }
        .first()

@Singleton
class ShopSettings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun address(): String =
        context.settingsDataStore.data.map { prefs ->
            prefs[stringPreferencesKey("company_address")] ?: ""
        }.first().trim()

    suspend fun taxPercent(): Double {
        val raw = context.settingsDataStore.data.map { prefs ->
            prefs[floatPreferencesKey("default_tax_rate")]
        }.first()
        return if (raw == null || raw <= 0f) 8.125 else raw.toDouble()
    }
}
