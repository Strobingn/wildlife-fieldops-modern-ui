package com.strobingn.wildlifefieldops.sync.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.room.InvalidationTracker
import com.strobingn.wildlifefieldops.data.local.AppDatabase
import com.strobingn.wildlifefieldops.ui.viewmodel.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Always-on auto-sync for release and debug. Local Room writes, app foreground,
 * and network restoration enqueue unique `fieldops-sync` work. Sync Now remains
 * a manual override; it is not required.
 */
@Singleton
class AutoSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scheduler: FieldOpsSyncScheduler,
    private val database: AppDatabase,
    private val gate: AutoSyncGate
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val trigger = AutoSyncTrigger(
        scope = scope,
        enqueue = {
            runCatching { scheduler.enqueueSync() }
                .onFailure { Log.w(TAG, "auto-enqueue failed", it) }
        },
        isEnabled = { gate.isEnabled() && !offlineModeCached }
    )

    @Volatile
    private var offlineModeCached: Boolean = false

    @Volatile
    private var started: Boolean = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            offlineModeCached = runCatching {
                context.settingsDataStore.data.map { it[OFFLINE_MODE] ?: false }.first()
            }.getOrDefault(false)
            context.settingsDataStore.data
                .map { it[OFFLINE_MODE] ?: false }
                .distinctUntilChanged()
                .collect { offline ->
                    val wasOffline = offlineModeCached
                    offlineModeCached = offline
                    if (wasOffline && !offline) trigger.onConnectivityRestored()
                }
        }
        database.invalidationTracker.addObserver(
            object : InvalidationTracker.Observer(AutoSyncTrigger.WATCHED_TABLES) {
                override fun onInvalidated(tables: Set<String>) {
                    Log.i(TAG, "local write in $tables; scheduling auto-sync")
                    trigger.notifyLocalChange()
                }
            }
        )
        registerNetworkCallback()
        scheduler.enqueuePeriodicSafetyNet()
        trigger.onAppForeground()
        Log.i(TAG, "Auto-sync started (Room writes, reconnect, periodic, foreground)")
    }

    fun onForeground() {
        trigger.onAppForeground()
    }

    fun notifyLocalChange() {
        trigger.notifyLocalChange()
    }

    private fun registerNetworkCallback() {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(
                request,
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.i(TAG, "connectivity restored; auto-sync")
                        trigger.onConnectivityRestored()
                    }
                }
            )
        }.onFailure { Log.w(TAG, "network callback not registered", it) }
    }

    companion object {
        private const val TAG = "FieldOpsAutoSync"
        private val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
    }
}
