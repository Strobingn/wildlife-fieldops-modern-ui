package com.strobingn.wildlifefieldops.update

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.strobingn.wildlifefieldops.ui.viewmodel.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.settingsDataStore

    suspend fun lastCheckAt(): Long =
        dataStore.data.first()[LAST_CHECK_AT] ?: 0L

    suspend fun markChecked(nowMs: Long) {
        dataStore.edit { it[LAST_CHECK_AT] = nowMs }
    }

    suspend fun unknownSourcesPromptShown(): Boolean =
        dataStore.data.first()[UNKNOWN_SOURCES_PROMPT] ?: false

    suspend fun markUnknownSourcesPromptShown() {
        dataStore.edit { it[UNKNOWN_SOURCES_PROMPT] = true }
    }

    suspend fun pendingApkPath(): String? =
        dataStore.data.first()[PENDING_APK]?.takeIf { it.isNotBlank() }

    suspend fun setPendingApkPath(path: String?) {
        dataStore.edit {
            if (path.isNullOrBlank()) it.remove(PENDING_APK) else it[PENDING_APK] = path
        }
    }

    suspend fun lastManifest(): AppUpdateManifest? {
        val raw = dataStore.data.first()[LAST_MANIFEST]?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { json.decodeFromString(AppUpdateManifest.serializer(), raw) }.getOrNull()
    }

    suspend fun saveManifest(manifest: AppUpdateManifest) {
        dataStore.edit { it[LAST_MANIFEST] = json.encodeToString(AppUpdateManifest.serializer(), manifest) }
    }

    companion object {
        val LAST_CHECK_AT = longPreferencesKey("app_update_last_check_at")
        val UNKNOWN_SOURCES_PROMPT = booleanPreferencesKey("app_update_unknown_sources_prompted")
        val PENDING_APK = stringPreferencesKey("app_update_pending_apk")
        val LAST_MANIFEST = stringPreferencesKey("app_update_last_manifest")
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
