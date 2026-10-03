package com.strobingn.wildlifefieldops.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.ui.components.ThemePreferencePicker
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.SettingsViewModel
import com.strobingn.wildlifefieldops.util.WildlifeWhispererBrand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    focusBackup: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themePreference by viewModel.themePreference.collectAsState(initial = ThemePreference.SYSTEM)
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val autoSync by viewModel.autoSync.collectAsState(initial = true)
    val companyName by viewModel.companyName.collectAsState(initial = "Wildlife Whisperer LLC")
    val companyAddress by viewModel.companyAddress.collectAsState(initial = "")
    val technicianName by viewModel.technicianName.collectAsState(initial = "")
    val defaultTaxRate by viewModel.defaultTaxRate.collectAsState(initial = 0f)
    val offlineMode by viewModel.offlineMode.collectAsState(initial = false)
    val highAccuracyGps by viewModel.highAccuracyGps.collectAsState(initial = true)
    val nwcoName by viewModel.nwcoName.collectAsState(initial = "")
    val nwcoLicense by viewModel.nwcoLicense.collectAsState(initial = "")
    val nwcoRegion by viewModel.nwcoRegion.collectAsState(initial = "")
    val nwcoCounty by viewModel.nwcoCounty.collectAsState(initial = "")
    val nwcoPhone by viewModel.nwcoPhone.collectAsState(initial = "")
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val isBackingUp by viewModel.isBackingUp.collectAsState()
    val backlog by viewModel.backlog.collectAsState()
    val lastSyncMessage by viewModel.lastSyncMessage.collectAsState()
    val lastSyncOk by viewModel.lastSyncOk.collectAsState()
    val context = LocalContext.current
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var showAiOperations by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.restoreFromBackup(context, uri)
    }
    val fieldDataSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.writeFieldDataTo(uri)
    }
    val fieldDataOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importFieldData(uri)
    }

    if (showAiOperations) {
        AIOperationsScreen(onBack = { showAiOperations = false })
        return
    }
    if (showDiagnostics) {
        DiagnosticsScreen(onBack = { showDiagnostics = false })
        return
    }

    val scrollState = rememberScrollState()
    var backupTop by remember { mutableIntStateOf(0) }
    LaunchedEffect(focusBackup, backupTop) {
        if (focusBackup && backupTop > 0) {
            scrollState.scrollTo(backupTop.coerceAtMost(scrollState.maxValue))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SettingsSectionTitle("App updates")
            SettingsCard {
                AppUpdateSettingsSection()
            }

            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionTitle("Connections")
            SettingsCard {
                Text(connectionStatus, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Keys are baked into the APK at build time from GitHub Secrets.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingsAiDiagnosticsBlock()
                val liveMessage = syncMessage ?: lastSyncMessage
                if (!liveMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        liveMessage,
                        color = if (lastSyncOk == false && syncMessage == null) ErrorRed else
                            if (syncMessage?.contains("Failed", ignoreCase = true) == true ||
                                syncMessage?.contains("not configured", ignoreCase = true) == true
                            ) ErrorRed else PrimaryGreen,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionTitle("Mileage / shop base")
            SettingsCard {
                Text(
                    "AI uses this address to measure driving miles to each job for estimates.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(
                    storedValue = companyAddress,
                    label = "Shop address",
                    onCommit = viewModel::setCompanyAddress,
                    singleLine = false,
                    supportingText = "Home base for shop → job mileage. Example: 210 Willow Avenue, Cornwall, NY 12518"
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionTitle("AI Command Center")
            SettingsCard {
                Text(
                    "20 individually launchable AI tools plus 65 live intelligence modules for scheduling, pricing, compliance, safety, revenue, customers, inventory, field quality, and data health.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showAiOperations = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open AI Operations", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showDiagnostics = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI and App Diagnostics")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            BusinessInfoSettingsBlock(viewModel)

            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionTitle("Company Information")
            SettingsCard {
                SettingPlainField(storedValue = companyName, label = "Company Name", onCommit = viewModel::setCompanyName)
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(
                    storedValue = companyAddress,
                    label = "Shop address",
                    onCommit = viewModel::setCompanyAddress,
                    singleLine = false,
                    supportingText = "Home base for AI mileage (shop → each job). Example: 210 Willow Avenue, Cornwall, NY 12518"
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(storedValue = technicianName, label = "Default Technician Name", onCommit = viewModel::setTechnicianName)
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(
                    storedValue = if (defaultTaxRate == 0f) "8.125" else defaultTaxRate.toString(),
                    label = "Default Tax Rate (%)",
                    keyboardType = KeyboardType.Decimal,
                    onCommit = viewModel::setDefaultTaxRateText
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionTitle("NYS DEC NWCO license")
            SettingsCard {
                Text(
                    "One-time licensee fields for the official Nuisance Wildlife Control Log. Name and license number print on every PDF/CSV.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(storedValue = nwcoName, label = "Operator name (First Last)", onCommit = viewModel::setNwcoName)
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(storedValue = nwcoLicense, label = "NWCO license number", onCommit = viewModel::setNwcoLicense)
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(storedValue = nwcoRegion, label = "DEC region", onCommit = viewModel::setNwcoRegion)
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(storedValue = nwcoCounty, label = "County of residence", onCommit = viewModel::setNwcoCounty)
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(storedValue = nwcoPhone, label = "Licensee phone", keyboardType = KeyboardType.Phone, onCommit = viewModel::setNwcoPhone)
            }

            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionTitle("Service Types")
            SettingsCard {
                val serviceTypesVm: com.strobingn.wildlifefieldops.ui.viewmodel.ServiceTypesViewModel =
                    androidx.hilt.navigation.compose.hiltViewModel()
                val customTypes by serviceTypesVm.customTypes.collectAsState(initial = emptyList())
                val serviceMsg by serviceTypesVm.lastMessage.collectAsState(initial = null)
                var newService by remember { mutableStateOf("") }
                var pendingDelete by remember { mutableStateOf<String?>(null) }
                Text(
                    "Built-in wildlife services are always available on jobs. Add your own types below.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = newService,
                    onValueChange = { newService = it },
                    label = { Text("New service type") },
                    colors = settingFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (newService.isNotBlank()) {
                            serviceTypesVm.addType(newService)
                            newService = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = newService.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add service type", fontWeight = FontWeight.Bold)
                }
                if (!serviceMsg.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(serviceMsg!!, color = PrimaryGreen, style = MaterialTheme.typography.bodySmall)
                }
                customTypes.forEach { type ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(type, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { pendingDelete = type }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove $type", tint = ErrorRed)
                        }
                    }
                }
                if (pendingDelete != null) {
                    AlertDialog(
                        onDismissRequest = { pendingDelete = null },
                        title = { Text("Delete service type?", color = TextPrimary) },
                        text = { Text("Remove this custom type? Jobs using it become Inspection.", color = TextSecondary) },
                        confirmButton = {
                            TextButton(onClick = {
                                serviceTypesVm.removeCustomType(pendingDelete!!)
                                pendingDelete = null
                            }) { Text("Delete", color = ErrorRed) }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = TextSecondary) }
                        },
                        containerColor = BackgroundCard
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionTitle("Appearance")
            SettingsCard {
                ThemePreferencePicker(
                    selected = themePreference,
                    onSelect = viewModel::setThemePreference
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionTitle("Notifications")
            SettingsCard {
                SettingsSwitchItem("Enable Notifications", "Receive alerts and reminders", Icons.Default.Notifications, notificationsEnabled, viewModel::setNotificationsEnabled)
            }
            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionTitle("Sync & Data")
            SettingsCard {
                val pending = backlog
                Text(
                    pending?.summaryLine() ?: "Counting unsynced jobs and photos on this phone…",
                    color = if (pending?.hasFailures == true) ErrorRed else TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                if (pending != null && pending.recentFailures.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    pending.recentFailures.take(5).forEach { line ->
                        Text(line, color = ErrorRed, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "The in-app updater syncs this backlog before installing and never wipes local data. You can still tap Sync Now first.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
                SettingsSwitchItem("Auto Sync", "Automatically sync with cloud", Icons.Default.Sync, autoSync, viewModel::setAutoSync)
                SettingsSwitchItem("Offline Mode", "Work without internet connection", Icons.Default.CloudOff, offlineMode, viewModel::setOfflineMode)
                SettingsSwitchItem("High Accuracy GPS", "Use GPS for precise location", Icons.Default.GpsFixed, highAccuracyGps, viewModel::setHighAccuracyGps)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.triggerManualSync() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, contentColor = OnPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSyncing) CircularProgressIndicator(Modifier.size(18.dp), color = OnPrimary, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSyncing) "Syncing…" else "Sync Now", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionTitle(
                "Backup & restore",
                modifier = Modifier.onGloballyPositioned {
                    backupTop = it.positionInParent().y.toInt()
                }
            )
            SettingsCard {
                Text(
                    "${WildlifeWhispererBrand.COMPANY} one-tap backup: checkpoints the field database, then zips it with photos and settings into Downloads. Restore from a zip before installing a new APK if you still have unsynced jobs.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.exportData() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBackingUp && !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isBackingUp) CircularProgressIndicator(Modifier.size(18.dp), color = OnPrimary, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isBackingUp) "Working…" else "Export data", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showRestoreDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBackingUp && !isSyncing,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Restore from backup")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Field data JSON: jobs, customers, inspections, photo metadata, and settings in one zip. Import merges by id so a restore cannot duplicate a job. Share it or save it with the system file picker.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { viewModel.shareFieldData() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBackingUp && !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Share field data", fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { fieldDataSaveLauncher.launch("WildlifeWhisperer-field-data.zip") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBackingUp && !isSyncing,
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Save field data (Files)") }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { fieldDataOpenLauncher.launch(arrayOf("application/zip", "application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBackingUp && !isSyncing,
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Import field data") }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SettingsSectionTitle("Danger Zone")
            SettingsCard {
                Button(
                    onClick = { showClearDataDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Data", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Wildlife FieldOps v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.labelSmall, color = TextTertiary, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Restore Wildlife Whisperer backup?", color = TextPrimary) },
            text = {
                Text(
                    "This replaces jobs, photos, and settings on this phone with the zip you pick. The app restarts so Room opens on the restored database.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreDialog = false
                    restoreLauncher.launch(arrayOf("application/zip", "*/*"))
                }) { Text("Pick backup zip", color = PrimaryGreen) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = BackgroundCard
        )
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear All Data?", color = TextPrimary) },
            text = { Text("This will permanently delete all jobs, customers, inspections, and photos.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllData(); showClearDataDialog = false }) {
                    Text("Delete Everything", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = BackgroundCard
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = PrimaryGreen,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = BackgroundCard), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = PrimaryGreen, checkedTrackColor = PrimaryGreen.copy(alpha = 0.5f)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun settingFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PrimaryGreen,
    unfocusedBorderColor = BorderDark,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = BackgroundDark,
    unfocusedContainerColor = BackgroundDark
)
