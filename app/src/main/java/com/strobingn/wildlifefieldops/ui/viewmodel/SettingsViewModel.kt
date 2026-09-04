package com.strobingn.wildlifefieldops.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.data.local.AppDatabase
import com.strobingn.wildlifefieldops.data.remote.AiService
import com.strobingn.wildlifefieldops.data.remote.SupabaseService
import com.strobingn.wildlifefieldops.data.remote.WeatherService
import com.strobingn.wildlifefieldops.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private val Context.dataStore by preferencesDataStore(name = "settings")

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncRepository: SyncRepository,
    private val supabaseService: SupabaseService,
    private val weatherService: WeatherService,
    private val database: AppDatabase,
    private val aiService: AiService
) : ViewModel() {

    private val dataStore = context.dataStore
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
    }

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val connectionStatus: StateFlow<String> = flow {
        emit(buildConnectionStatus())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Checking…")

    private val settings = dataStore.data
        .catch { emit(emptyPreferences()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyPreferences())

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

    fun aiDiagnostics(): String = try {
        aiService.configDiagnostics()
    } catch (t: Throwable) {
        "AI diagnostics unavailable: ${t.message ?: t.javaClass.simpleName}"
    }

    suspend fun shopAddress(): String = companyAddress.first().trim()

    suspend fun taxPercent(): Double = defaultTaxRate.first().toDouble()

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
        return "$cloud · $maps · $weather"
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
            val result = syncRepository.syncAll()
            _syncMessage.value = result.message
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
        _syncMessage.value = "Export: use Share from job/invoice PDFs for now. Full dump coming in a later update."
    }

    fun importData() = viewModelScope.launch {
        _syncMessage.value = "Import: use Sync Now to pull jobs and customers from Supabase."
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
