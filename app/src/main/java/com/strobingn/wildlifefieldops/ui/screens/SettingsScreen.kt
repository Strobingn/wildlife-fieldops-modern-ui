package com.strobingn.wildlifefieldops.ui.screens

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val darkTheme by viewModel.darkTheme.collectAsState(initial = true)
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState(initial = true)
    val autoSync by viewModel.autoSync.collectAsState(initial = true)
    val syncInterval by viewModel.syncInterval.collectAsState(initial = 15)
    val companyName by viewModel.companyName.collectAsState(initial = "Wildlife Whisperer LLC")
    val technicianName by viewModel.technicianName.collectAsState(initial = "")
    val defaultTaxRate by viewModel.defaultTaxRate.collectAsState(initial = 0f)
    val offlineMode by viewModel.offlineMode.collectAsState(initial = false)
    val highAccuracyGps by viewModel.highAccuracyGps.collectAsState(initial = true)
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showAiOperations by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    if (showAiOperations) {
        AIOperationsScreen(onBack = { showAiOperations = false })
        return
    }
    if (showDiagnostics) {
        DiagnosticsScreen(onBack = { showDiagnostics = false })
        return
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
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
                if (!syncMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(syncMessage!!, color = PrimaryGreen, style = MaterialTheme.typography.bodySmall)
                }
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.Black)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open AI Operations", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDiagnostics = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.BugReport, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AI and App Diagnostics")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSectionTitle("Company Information")
            SettingsCard {
                OutlinedTextField(
                    value = companyName,
                    onValueChange = viewModel::setCompanyName,
                    label = { Text("Company Name") },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = TextSecondary) },
                    colors = settingFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = technicianName,
                    onValueChange = viewModel::setTechnicianName,
                    label = { Text("Default Technician Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary) },
                    colors = settingFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = defaultTaxRate.toString(),
                    onValueChange = {
                        it.toFloatOrNull()?.let { rate -> viewModel.setDefaultTaxRate(rate) }
                    },
                    label = { Text("Default Tax Rate (%)") },
                    leadingIcon = { Icon(Icons.Default.Percent, contentDescription = null, tint = TextSecondary) },
                    colors = settingFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
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
                    "Built-in wildlife services are always available on jobs. Add your own types below. Deleting a custom type reassigns any jobs using it to \u201cInspection\u201d.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = newService,
                    onValueChange = { newService = it },
                    label = { Text("New service type") },
                    placeholder = { Text("e.g. Gutter guard install") },
                    leadingIcon = { Icon(Icons.Default.Build, contentDescription = null, tint = TextSecondary) },
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
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = Color.Black),
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
                if (customTypes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Your custom types (tap trash to delete)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    customTypes.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(type, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { pendingDelete = type }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove $type",
                                    tint = ErrorRed
                                )
                            }
                        }
                    }
                }

                if (pendingDelete != null) {
                    AlertDialog(
                        onDismissRequest = { pendingDelete = null },
                        title = { Text("Delete service type?", color = TextPrimary) },
                        text = {
                            Text(
                                "Remove \u201c$pendingDelete\u201d from your list? Any jobs using this service will be set to \u201cInspection\u201d. You can change them again when editing the job.",
                                color = TextSecondary
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                serviceTypesVm.removeCustomType(pendingDelete!!)
                                pendingDelete = null
                            }) {
                                Text("Delete", color = ErrorRed)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingDelete = null }) {
                                Text("Cancel", color = TextSecondary)
                            }
                        },
                        containerColor = BackgroundCard
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSectionTitle("Appearance")
            SettingsCard {
                SettingsSwitchItem(
                    title = "Dark Theme",
                    subtitle = "Use dark color scheme",
                    icon = Icons.Default.DarkMode,
                    checked = darkTheme,
                    onCheckedChange = viewModel::setDarkTheme
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSectionTitle("Notifications")
            SettingsCard {
                SettingsSwitchItem(
                    title = "Enable Notifications",
                    subtitle = "Receive alerts and reminders",
                    icon = Icons.Default.Notifications,
                    checked = notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            SettingsSectionTitle("Sync & Data")
            SettingsCard {
                SettingsSwitchItem(
                    title = "Auto Sync",
                    subtitle = "Automatically sync with cloud",
                    icon = Icons.Default.Sync,
                    checked = autoSync,
                    onCheckedChange = viewModel::setAutoSync
                )
                SettingsSwitchItem(
                    title = "Offline Mode",
                    subtitle = "Work without internet connection",
                    icon = Icons.Default.CloudOff,
                    checked = offlineMode,
                    onCheckedChange = viewModel::setOfflineMode
                )
                SettingsSwitchItem(
                    title = "High Accuracy GPS",
                    subtitle = "Use GPS for precise location",
                    icon = Icons.Default.GpsFixed,
                    checked = highAccuracyGps,
                    onCheckedChange = viewModel::setHighAccuracyGps
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { viewModel.triggerManualSync() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSyncing) "Syncing\u2026" else "Sync Now", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.exportData() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { viewModel.importData() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import")
                    }
                }
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
            Text(
                "Wildlife FieldOps v2.0.1",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear All Data?", color = TextPrimary) },
            text = { Text("This will permanently delete all jobs, customers, inspections, and photos. This action cannot be undone.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDataDialog = false
                }) {
                    Text("Delete Everything", color = ErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BackgroundCard
        )
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = PrimaryGreen,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextTertiary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = PrimaryGreen, checkedTrackColor = PrimaryGreen.copy(alpha = 0.5f))
        )
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
