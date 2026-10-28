package com.strobingn.wildlifefieldops.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.strobingn.wildlifefieldops.data.remote.PhotoSyncQueue
import com.strobingn.wildlifefieldops.ui.viewmodel.LAST_SYNC_AT
import com.strobingn.wildlifefieldops.ui.viewmodel.LAST_SYNC_ERROR
import com.strobingn.wildlifefieldops.ui.viewmodel.LAST_SYNC_ERROR_AT
import com.strobingn.wildlifefieldops.ui.viewmodel.LAST_SYNC_SUCCESS_AT
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
    /**
     * The previous pass stored a coroutine cancellation as a hard failure.
     * Clear that banner so the photo backlog can upload. Real errors stay.
     */
    suspend fun forgetInterruptedCancellation() {
        context.settingsDataStore.edit { prefs ->
            if (PhotoSyncQueue.isFalseCancellationMessage(prefs[LAST_SYNC_ERROR])) {
                prefs.remove(LAST_SYNC_ERROR)
                prefs.remove(LAST_SYNC_ERROR_AT)
            }
            val message = prefs[LAST_SYNC_MESSAGE]
            if (prefs[LAST_SYNC_OK] == false && PhotoSyncQueue.isFalseCancellationMessage(message)) {
                prefs[LAST_SYNC_OK] = true
                prefs[LAST_SYNC_MESSAGE] =
                    "Photo upload was interrupted and will retry. The job was not cancelled."
            }
        }
    }

    suspend fun record(result: SyncResult) {
        val now = System.currentTimeMillis()
        context.settingsDataStore.edit { prefs ->
            prefs[LAST_SYNC_MESSAGE] = result.message
            prefs[LAST_SYNC_OK] = result.success
            prefs[LAST_SYNC_AT] = now
            // History for the Sync status screen. Does not change what sync does.
            if (result.success) prefs[LAST_SYNC_SUCCESS_AT] = now
            if (!result.success || result.failedItems.isNotEmpty()) {
                prefs[LAST_SYNC_ERROR_AT] = now
                prefs[LAST_SYNC_ERROR] = result.message
            }
        }
    }
}
