package com.strobingn.wildlifefieldops.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.*
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.data.backup.FieldOpsBackupManager
import com.strobingn.wildlifefieldops.data.local.AppDatabase
import com.strobingn.wildlifefieldops.data.remote.AiService
import com.strobingn.wildlifefieldops.data.remote.SupabaseService
import com.strobingn.wildlifefieldops.data.remote.WeatherService
import com.strobingn.wildlifefieldops.data.repository.SyncBacklogRepository
import com.strobingn.wildlifefieldops.data.repository.SyncBacklogSnapshot
import com.strobingn.wildlifefieldops.data.repository.SyncRepository
import com.strobingn.wildlifefieldops.sync.work.FieldOpsSyncScheduler
import com.strobingn.wildlifefieldops.sync.work.WorkManagerSyncCanaryFlag
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncRepository: SyncRepository,
    private val syncBacklogRepository: SyncBacklogRepository,
    private val supabaseService: SupabaseService,
    private val weatherService: WeatherService,
    private val database: AppDatabase,
    private val aiService: AiService,
    private val workManagerSyncCanaryFlag: WorkManagerSyncCanaryFlag,
    private val fieldOpsSyncScheduler: FieldOpsSyncScheduler
) : ViewModel() {

    private val dataStore = context.settingsDataStore
    private var companyJob: Job? = null
    private var techJob: Job? = null
    private var addressJob: Job? = null
    private var taxJob: Job? = null

    companion object {
        const val DEFAULT_TAX_PERCENT = 8.125f
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval")
        val COMPANY_NAME = stringPreferencesKey("company_name")
        val TECHNICIAN_NAME = stringPreferencesKey("technician_name")
        val COMPANY_ADDRESS = stringPreferencesKey("company_address")
        val DEFAULT_TAX_RATE = floatPreferencesKey("default_tax_rate")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val HIGH_ACCURACY_GPS = booleanPreferencesKey("high_accuracy_gps")
        val LAST_SYNC_MESSAGE = stringPreferencesKey("last_sync_message")
        val LAST_SYNC_OK = booleanPreferencesKey("last_sync_ok")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    }

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _isBackingUp = MutableStateFlow(false)
    val isBackingUp: StateFlow<Boolean> = _isBackingUp.asStateFlow()

    private val _backlog = MutableStateFlow<SyncBacklogSnapshot?>(null)
    val backlog: StateFlow<SyncBacklogSnapshot?> = _backlog.asStateFlow()

    val connectionStatus: StateFlow<String> = flow {
        emit(buildConnectionStatus())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Checking…")

    private val settings = dataStore.data
        .catch { emit(emptyPreferences()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyPreferences())

    val lastSyncMessage: StateFlow<String?> = settings.map { it[LAST_SYNC_MESSAGE] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastSyncOk: StateFlow<Boolean?> = settings.map { it[LAST_SYNC_OK] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val darkTheme = settings.map { it[DARK_THEME] ?: true }
    val notificationsEnabled = settings.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val autoSync = settings.map { it[AUTO_SYNC] ?: true }
    val syncInterval = settings.map { it[SYNC_INTERVAL] ?: 15 }
    val companyName = settings.map { it[COMPANY_NAME] ?: "Wildlife Whisperer LLC" }
    val technicianName = settings.map { it[TECHNICIAN_NAME] ?: "" }
    val companyAddress = settings.map { it[COMPANY_ADDRESS] ?: "" }
    val defaultTaxRate = settings.map { storedTax(it[DEFAULT_TAX_RATE]) }
    val offlineMode = settings.map { it[OFFLINE_MODE] ?: false }
    val highAccuracyGps = settings.map { it[HIGH_ACCURACY_GPS] ?: true }

    init {
        refreshBacklog()
    }

    fun refreshBacklog() {
        viewModelScope.launch {
            _backlog.value = withContext(Dispatchers.IO) {
                runCatching { syncBacklogRepository.snapshot() }.getOrNull()
            }
        }
    }

    fun aiDiagnostics(): String = try {
        aiService.configDiagnostics()
    } catch (t: Throwable) {
        "AI diagnostics unavailable: ${t.message ?: t.javaClass.simpleName}"
    }

    private fun storedTax(raw: Float?): Float {
        if (raw == null || raw <= 0f) return DEFAULT_TAX_PERCENT
        return raw
    }

    private fun buildConnectionStatus(): String {
        val cloud = if (supabaseService.isConfigured) "Supabase OK" else "Supabase missing"
        val maps = if (
            BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank() &&
            !BuildConfig.GOOGLE_MAPS_API_KEY.contains("YOUR_")
        ) "Maps OK" else "Maps missing"
        val weather = if (weatherService.isConfigured) "Weather OK" else "Weather optional"
        val wm = if (workManagerSyncCanaryFlag.isEnabled()) "WM canary" else "WM off"
        return "$cloud · $maps · $weather · $wm"
    }

    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch {
        dataStore.edit { it[DARK_THEME] = enabled }
    }

    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    fun setAutoSync(enabled: Boolean) = viewModelScope.launch {
        dataStore.edit { it[AUTO_SYNC] = enabled }
    }

    fun setSyncInterval(minutes: Int) = viewModelScope.launch {
        dataStore.edit { it[SYNC_INTERVAL] = minutes }
    }

    fun setCompanyName(name: String) {
        companyJob?.cancel()
        companyJob = viewModelScope.launch {
            delay(350)
            dataStore.edit { it[COMPANY_NAME] = name }
        }
    }

    fun setTechnicianName(name: String) {
        techJob?.cancel()
        techJob = viewModelScope.launch {
            delay(350)
            dataStore.edit { it[TECHNICIAN_NAME] = name }
        }
    }

    fun setCompanyAddress(address: String) {
        addressJob?.cancel()
        addressJob = viewModelScope.launch {
            delay(350)
            dataStore.edit { it[COMPANY_ADDRESS] = address }
        }
    }

    fun setDefaultTaxRateText(raw: String) {
        val parsed = raw.trim().toFloatOrNull() ?: return
        taxJob?.cancel()
        taxJob = viewModelScope.launch {
            delay(350)
            dataStore.edit { it[DEFAULT_TAX_RATE] = parsed }
        }
    }

    fun setDefaultTaxRate(rate: Float) = viewModelScope.launch {
        dataStore.edit { it[DEFAULT_TAX_RATE] = rate }
    }

    fun setOfflineMode(enabled: Boolean) = viewModelScope.launch {
        dataStore.edit { it[OFFLINE_MODE] = enabled }
    }

    fun setHighAccuracyGps(enabled: Boolean) = viewModelScope.launch {
        dataStore.edit { it[HIGH_ACCURACY_GPS] = enabled }
    }

    fun triggerManualSync() = viewModelScope.launch {
        if (_isSyncing.value) return@launch
        _isSyncing.value = true
        _syncMessage.value = "Syncing…"
        try {
            val offline = offlineMode.first()
            if (offline) {
                _syncMessage.value = "Offline mode is on. Turn it off to sync."
                return@launch
            }
            if (!syncRepository.isCloudConfigured()) {
                _syncMessage.value =
                    "Cloud not configured. Rebuild APK with Supabase secrets set (Settings shows connection status)."
                return@launch
            }
            if (workManagerSyncCanaryFlag.isEnabled()) {
                withContext(Dispatchers.IO) {
                    runCatching { fieldOpsSyncScheduler.enqueueSync() }
                }
            }
            val result = syncRepository.syncAll()
            dataStore.edit {
                it[LAST_SYNC_MESSAGE] = result.message
                it[LAST_SYNC_OK] = result.success
                it[LAST_SYNC_AT] = System.currentTimeMillis()
            }
            _syncMessage.value = result.message
            refreshBacklog()
        } catch (t: Throwable) {
            android.util.Log.e("SettingsViewModel", "Sync UI crash prevented", t)
            _syncMessage.value = "Sync error: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            _isSyncing.value = false
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun exportData() = viewModelScope.launch {
        if (_isBackingUp.value) return@launch
        _isBackingUp.value = true
        _syncMessage.value = "Creating Wildlife Whisperer backup…"
        try {
            val message = withContext(Dispatchers.IO) {
                FieldOpsBackupManager.exportToDownloads(context, database)
            }
            _syncMessage.value = message
        } catch (t: Throwable) {
            android.util.Log.e("SettingsViewModel", "Backup failed", t)
            _syncMessage.value = "Backup failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            _isBackingUp.value = false
        }
    }

    fun restoreFromBackup(hostContext: Context, uri: Uri) = viewModelScope.launch {
        if (_isBackingUp.value) return@launch
        _isBackingUp.value = true
        _syncMessage.value = "Validating Wildlife Whisperer backup…"
        try {
            withContext(Dispatchers.IO) {
                FieldOpsBackupManager.stageRestore(hostContext, uri)
            }
            _syncMessage.value = "Backup OK. Restarting to restore…"
            delay(400)
            FieldOpsBackupManager.restartApp(hostContext)
        } catch (t: Throwable) {
            android.util.Log.e("SettingsViewModel", "Restore failed", t)
            _syncMessage.value = "Restore failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            _isBackingUp.value = false
        }
    }

    fun importData() = viewModelScope.launch {
        _syncMessage.value = "Use Restore from backup to pick a Wildlife Whisperer zip from Downloads."
    }

    fun clearAllData() = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                database.clearAllTables()
            }
            _syncMessage.value = "All local data cleared."
        } catch (t: Throwable) {
            android.util.Log.e("SettingsViewModel", "Clear data failed", t)
            _syncMessage.value = "Clear failed: ${t.message ?: t.javaClass.simpleName}"
        }
    }
}
