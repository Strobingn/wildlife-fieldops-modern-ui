package com.strobingn.wildlifefieldops.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.strobingn.wildlifefieldops.ui.viewmodel.LAST_SYNC_AT
import com.strobingn.wildlifefieldops.ui.viewmodel.LAST_SYNC_MESSAGE
import com.strobingn.wildlifefieldops.ui.viewmodel.LAST_SYNC_OK
import com.strobingn.wildlifefieldops.ui.viewmodel.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the last sync outcome so the status bar updates after auto-sync, not only Sync Now. */
@Singleton
class SyncStatusStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun record(result: SyncResult) {
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_SYNC_MESSAGE] = result.message
            prefs[LAST_SYNC_OK] = result.success
            prefs[LAST_SYNC_AT] = System.currentTimeMillis()
        }
    }
}
