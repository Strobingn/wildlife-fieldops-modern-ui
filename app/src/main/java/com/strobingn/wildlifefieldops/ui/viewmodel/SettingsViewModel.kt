package com.strobingn.wildlifefieldops.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.*
import com.strobingn.wildlifefieldops.BuildConfig
import android.content.Intent
import androidx.core.content.FileProvider
import com.strobingn.wildlifefieldops.data.backup.FieldDataCodec
import com.strobingn.wildlifefieldops.data.backup.FieldDataStore
import com.strobingn.wildlifefieldops.data.backup.FieldOpsBackupManager
import java.io.File
import com.strobingn.wildlifefieldops.data.local.AppDatabase
import com.strobingn.wildlifefieldops.data.remote.AiService
import com.strobingn.wildlifefieldops.data.remote.SupabaseService
import com.strobingn.wildlifefieldops.data.remote.WeatherService
import com.strobingn.wildlifefieldops.data.repository.SyncBacklogRepository
import com.strobingn.wildlifefieldops.data.repository.SyncBacklogSnapshot
import com.strobingn.wildlifefieldops.data.repository.SyncRepository
import com.strobingn.wildlifefieldops.ai.fieldops.DecNwcoLogStore
import com.strobingn.wildlifefieldops.ai.fieldops.NwcoOperatorProfile
import com.strobingn.wildlifefieldops.sync.work.FieldOpsSyncScheduler
import com.strobingn.wildlifefieldops.sync.work.WorkManagerSyncCanaryFlag
import com.strobingn.wildlifefieldops.ui.theme.ThemePreference
import com.strobingn.wildlifefieldops.util.BusinessProfile
import com.strobingn.wildlifefieldops.util.BusinessProfileStore
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
    private val fieldOpsSyncScheduler: FieldOpsSyncScheduler,
    private val decNwcoLogStore: DecNwcoLogStore
) : ViewModel() {

    private val dataStore = context.settingsDataStore
    private var techJob: Job? = null
    private var taxJob: Job? = null
    private val businessWrites = PendingStringWrites(viewModelScope) { batch ->
        dataStore.edit { prefs ->
            batch.forEach { (key, value) ->
                when (key) {
                    "name" -> prefs[COMPANY_NAME] = value
                    "phone" -> prefs[BUSINESS_PHONE] = value
                    "email" -> prefs[BUSINESS_EMAIL] = value
                    "address" -> prefs[COMPANY_ADDRESS] = value
                    "website" -> prefs[BUSINESS_WEBSITE] = value
                }
            }
        }
    }

    /** Writes any business-info keystroke that is still waiting on the debounce. */
    fun flushPendingBusinessEdits() = businessWrites.flushBlocking()

    override fun onCleared() {
        flushPendingBusinessEdits()
        super.onCleared()
    }

    companion object {
        const val DEFAULT_TAX_PERCENT = 8.125f
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val SYNC_INTERVAL = intPreferencesKey("sync_interval")
        val COMPANY_NAME = stringPreferencesKey("company_name")
        val TECHNICIAN_NAME = stringPreferencesKey("technician_name")
        val COMPANY_ADDRESS = stringPreferencesKey("company_address")
        val DEFAULT_TAX_RATE = floatPreferencesKey("default_tax_rate")
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val HIGH_ACCURACY_GPS = booleanPreferencesKey("high_accuracy_gps")
        val NWCO_NAME = stringPreferencesKey("nwco_operator_name")
        val NWCO_LICENSE = stringPreferencesKey("nwco_license")
        val NWCO_REGION = stringPreferencesKey("nwco_region")
        val NWCO_COUNTY = stringPreferencesKey("nwco_county")
        val NWCO_PHONE = stringPreferencesKey("nwco_phone")
        val BUSINESS_PHONE = stringPreferencesKey("business_phone")
        val BUSINESS_EMAIL = stringPreferencesKey("business_email")
        val BUSINESS_WEBSITE = stringPreferencesKey("business_website")
        val BUSINESS_LOGO = stringPreferencesKey("business_logo_path")
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
    val lastSyncAt: StateFlow<Long?> = settings.map { it[LAST_SYNC_AT] }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastSyncSuccessAt: StateFlow<Long?> = settings.map { prefs ->
        prefs[LAST_SYNC_SUCCESS_AT] ?: prefs[LAST_SYNC_AT]?.takeIf { prefs[LAST_SYNC_OK] == true }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastSyncErrorAt: StateFlow<Long?> = settings.map { prefs ->
        prefs[LAST_SYNC_ERROR_AT] ?: prefs[LAST_SYNC_AT]?.takeIf { prefs[LAST_SYNC_OK] == false }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val lastSyncError: StateFlow<String?> = settings.map { prefs ->
        prefs[LAST_SYNC_ERROR] ?: prefs[LAST_SYNC_MESSAGE]?.takeIf { prefs[LAST_SYNC_OK] == false }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themePreference = settings.map {
        ThemePreference.fromPersisted(it[THEME_MODE], it[DARK_THEME])
    }
    /** Legacy boolean for any leftover collectors; prefer [themePreference]. */
    val darkTheme = themePreference.map { it == ThemePreference.DARK }
    val notificationsEnabled = settings.map { it[NOTIFICATIONS_ENABLED] ?: true }
    val autoSync = settings.map { it[AUTO_SYNC] ?: true }
    val syncInterval = settings.map { it[SYNC_INTERVAL] ?: 15 }
    val companyName = settings.map { it[COMPANY_NAME] ?: "Wildlife Whisperer LLC" }
    val technicianName = settings.map { it[TECHNICIAN_NAME] ?: "" }
    val companyAddress = settings.map { it[COMPANY_ADDRESS] ?: "" }
    val defaultTaxRate = settings.map { storedTax(it[DEFAULT_TAX_RATE]) }
    val offlineMode = settings.map { it[OFFLINE_MODE] ?: false }
    val highAccuracyGps = settings.map { it[HIGH_ACCURACY_GPS] ?: true }
    val nwcoName = settings.map { it[NWCO_NAME] ?: (it[TECHNICIAN_NAME] ?: "") }
    val nwcoLicense = settings.map { it[NWCO_LICENSE] ?: "" }
    val nwcoRegion = settings.map { it[NWCO_REGION] ?: "" }
    val nwcoCounty = settings.map { it[NWCO_COUNTY] ?: "" }
    val nwcoPhone = settings.map { it[NWCO_PHONE] ?: "" }
    val businessProfile = settings.map { BusinessProfileStore.read(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BusinessProfile.defaults())

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

    fun setThemePreference(preference: ThemePreference) = viewModelScope.launch {
        dataStore.edit {
            it[THEME_MODE] = preference.storageValue
            it[DARK_THEME] = preference == ThemePreference.DARK
        }
    }

    fun setDarkTheme(enabled: Boolean) = setThemePreference(
        if (enabled) ThemePreference.DARK else ThemePreference.LIGHT
    )

    fun setNotificationsEnabled(enabled: Boolean) = viewModelScope.launch {
        dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    fun setAutoSync(enabled: Boolean) = viewModelScope.launch {
        dataStore.edit { it[AUTO_SYNC] = enabled }
    }

    fun setSyncInterval(minutes: Int) = viewModelScope.launch {
        dataStore.edit { it[SYNC_INTERVAL] = minutes }
    }

    fun setCompanyName(name: String) = businessWrites.schedule("name", name)

    fun setTechnicianName(name: String) {
        techJob?.cancel()
        techJob = viewModelScope.launch {
            delay(350)
            dataStore.edit { it[TECHNICIAN_NAME] = name }
        }
    }

    fun setCompanyAddress(address: String) = businessWrites.schedule("address", address)

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

    fun setNwcoName(value: String) = viewModelScope.launch {
        dataStore.edit { it[NWCO_NAME] = value }
        persistNwcoProfile()
    }

    fun setNwcoLicense(value: String) = viewModelScope.launch {
        dataStore.edit { it[NWCO_LICENSE] = value }
        persistNwcoProfile()
    }

    fun setNwcoRegion(value: String) = viewModelScope.launch {
        dataStore.edit { it[NWCO_REGION] = value }
        persistNwcoProfile()
    }

    fun setNwcoCounty(value: String) = viewModelScope.launch {
        dataStore.edit { it[NWCO_COUNTY] = value }
        persistNwcoProfile()
    }

    fun setNwcoPhone(value: String) = viewModelScope.launch {
        dataStore.edit { it[NWCO_PHONE] = value }
        persistNwcoProfile()
    }

    fun setBusinessPhone(value: String) = businessWrites.schedule("phone", value)

    fun setBusinessEmail(value: String) = businessWrites.schedule("email", value)

    fun setBusinessWebsite(value: String) = businessWrites.schedule("website", value)

    fun setBusinessLogo(uri: Uri) = viewModelScope.launch {
        val path = withContext(Dispatchers.IO) { BusinessProfileStore.importLogo(context, uri) }
        dataStore.edit { it[BUSINESS_LOGO] = path }
    }

    fun resetBusinessLogo() = viewModelScope.launch {
        withContext(Dispatchers.IO) { BusinessProfileStore.logoFile(context).delete() }
        dataStore.edit { it.remove(BUSINESS_LOGO) }
    }

    private suspend fun persistNwcoProfile() {
        val prefs = dataStore.data.first()
        val name = prefs[NWCO_NAME] ?: prefs[TECHNICIAN_NAME].orEmpty()
        val parts = name.trim().split(" ").filter { it.isNotBlank() }
        val first = parts.firstOrNull().orEmpty()
        val last = parts.drop(1).joinToString(" ").ifBlank { parts.firstOrNull().orEmpty() }
        decNwcoLogStore.saveOperator(
            NwcoOperatorProfile(
                firstName = first,
                lastName = if (parts.size > 1) last else "",
                address = prefs[COMPANY_ADDRESS].orEmpty(),
                phone = prefs[NWCO_PHONE].orEmpty(),
                licenseNumber = prefs[NWCO_LICENSE].orEmpty(),
                decRegion = prefs[NWCO_REGION].orEmpty(),
                countyOfResidence = prefs[NWCO_COUNTY].orEmpty()
            )
        )
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
        _syncMessage.value = "Use Import field data to pick a Wildlife Whisperer JSON zip."
    }

    fun shareFieldData() = viewModelScope.launch {
        if (_isBackingUp.value) return@launch
        _isBackingUp.value = true
        try {
            val file = withContext(Dispatchers.IO) { writeFieldDataFile() }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Wildlife Whisperer field data")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share field data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            _syncMessage.value = "Field data ready to share."
        } catch (t: Throwable) {
            _syncMessage.value = "Export failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            _isBackingUp.value = false
        }
    }

    fun writeFieldDataTo(uri: Uri) = viewModelScope.launch {
        if (_isBackingUp.value) return@launch
        _isBackingUp.value = true
        try {
            val bytes = withContext(Dispatchers.IO) {
                FieldDataCodec.zipBytes(FieldDataStore.exportBundle(database, settingsSnapshot()))
            }
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Could not write the selected file")
            }
            _syncMessage.value = "Field data saved."
        } catch (t: Throwable) {
            _syncMessage.value = "Export failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            _isBackingUp.value = false
        }
    }

    fun importFieldData(uri: Uri) = viewModelScope.launch {
        if (_isBackingUp.value) return@launch
        _isBackingUp.value = true
        try {
            val message = withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not read the selected file")
                val bundle = FieldDataCodec.readBytes(bytes)
                applySettings(bundle.settings)
                FieldDataStore.importBundle(database, bundle)
            }
            _syncMessage.value = message
        } catch (t: Throwable) {
            _syncMessage.value = "Import failed: ${t.message ?: t.javaClass.simpleName}"
        } finally {
            _isBackingUp.value = false
        }
    }

    private suspend fun writeFieldDataFile(): File {
        val bytes = FieldDataCodec.zipBytes(FieldDataStore.exportBundle(database, settingsSnapshot()))
        val file = File(context.cacheDir, "WildlifeWhisperer-field-data.zip")
        file.writeBytes(bytes)
        return file
    }

    private suspend fun settingsSnapshot(): Map<String, String> {
        val prefs = dataStore.data.first()
        return buildMap {
            put("theme_mode", prefs[THEME_MODE] ?: "")
            put("dark_theme", (prefs[DARK_THEME] ?: true).toString())
            put("notifications_enabled", (prefs[NOTIFICATIONS_ENABLED] ?: true).toString())
            put("auto_sync", (prefs[AUTO_SYNC] ?: true).toString())
            put("sync_interval", (prefs[SYNC_INTERVAL] ?: 15).toString())
            put("company_name", prefs[COMPANY_NAME] ?: "")
            put("technician_name", prefs[TECHNICIAN_NAME] ?: "")
            put("company_address", prefs[COMPANY_ADDRESS] ?: "")
            put("default_tax_rate", (prefs[DEFAULT_TAX_RATE] ?: DEFAULT_TAX_PERCENT).toString())
            put("offline_mode", (prefs[OFFLINE_MODE] ?: false).toString())
            put("high_accuracy_gps", (prefs[HIGH_ACCURACY_GPS] ?: true).toString())
            put("nwco_operator_name", prefs[NWCO_NAME] ?: "")
            put("nwco_license", prefs[NWCO_LICENSE] ?: "")
            put("nwco_region", prefs[NWCO_REGION] ?: "")
            put("nwco_county", prefs[NWCO_COUNTY] ?: "")
            put("nwco_phone", prefs[NWCO_PHONE] ?: "")
            if (prefs.contains(BUSINESS_PHONE)) put("business_phone", prefs[BUSINESS_PHONE].orEmpty())
            if (prefs.contains(BUSINESS_EMAIL)) put("business_email", prefs[BUSINESS_EMAIL].orEmpty())
            if (prefs.contains(BUSINESS_WEBSITE)) put("business_website", prefs[BUSINESS_WEBSITE].orEmpty())
            if (prefs.contains(BUSINESS_LOGO)) put("business_logo_path", prefs[BUSINESS_LOGO].orEmpty())
        }
    }

    private suspend fun applySettings(settings: Map<String, String>) {
        if (settings.isEmpty()) return
        dataStore.edit { prefs ->
            settings["theme_mode"]?.let { prefs[THEME_MODE] = it }
            settings["dark_theme"]?.toBooleanStrictOrNull()?.let { prefs[DARK_THEME] = it }
            settings["notifications_enabled"]?.toBooleanStrictOrNull()?.let { prefs[NOTIFICATIONS_ENABLED] = it }
            settings["auto_sync"]?.toBooleanStrictOrNull()?.let { prefs[AUTO_SYNC] = it }
            settings["sync_interval"]?.toIntOrNull()?.let { prefs[SYNC_INTERVAL] = it }
            settings["company_name"]?.let { prefs[COMPANY_NAME] = it }
            settings["technician_name"]?.let { prefs[TECHNICIAN_NAME] = it }
            settings["company_address"]?.let { prefs[COMPANY_ADDRESS] = it }
            settings["default_tax_rate"]?.toFloatOrNull()?.let { prefs[DEFAULT_TAX_RATE] = it }
            settings["offline_mode"]?.toBooleanStrictOrNull()?.let { prefs[OFFLINE_MODE] = it }
            settings["high_accuracy_gps"]?.toBooleanStrictOrNull()?.let { prefs[HIGH_ACCURACY_GPS] = it }
            settings["nwco_operator_name"]?.let { prefs[NWCO_NAME] = it }
            settings["nwco_license"]?.let { prefs[NWCO_LICENSE] = it }
            settings["nwco_region"]?.let { prefs[NWCO_REGION] = it }
            settings["nwco_county"]?.let { prefs[NWCO_COUNTY] = it }
            settings["nwco_phone"]?.let { prefs[NWCO_PHONE] = it }
            settings["business_phone"]?.let { prefs[BUSINESS_PHONE] = it }
            settings["business_email"]?.let { prefs[BUSINESS_EMAIL] = it }
            settings["business_website"]?.let { prefs[BUSINESS_WEBSITE] = it }
            settings["business_logo_path"]?.let { prefs[BUSINESS_LOGO] = it }
        }
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
