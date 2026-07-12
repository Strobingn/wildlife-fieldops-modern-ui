package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.background)
            )
        },
        containerColor = colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection status
            SettingsSection("Connections") {
                SettingsInfoCard(
                    icon = Icons.Default.Cloud,
                    title = connectionStatus,
                    subtitle = "Keys are baked into the APK at build time from GitHub Secrets.",
                    accent = colorScheme.primary
                )
                if (!syncMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        syncMessage!!,
                        color = colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Company Info
            SettingsSection("Company Information") {
                ModernTextField(
                    value = companyName,
                    onValueChange = viewModel::setCompanyName,
                    label = "Company Name",
                    icon = Icons.Default.Business
                )
                Spacer(modifier = Modifier.height(12.dp))
                ModernTextField(
                    value = technicianName,
                    onValueChange = viewModel::setTechnicianName,
                    label = "Default Technician Name",
                    icon = Icons.Default.Person
                )
                Spacer(modifier = Modifier.height(12.dp))
                ModernTextField(
                    value = defaultTaxRate.toString(),
                    onValueChange = {
                        it.toFloatOrNull()?.let { rate -> viewModel.setDefaultTaxRate(rate) }
                    },
                    label = "Default Tax Rate (%)",
                    icon = Icons.Default.Percent
                )
            }

            // Appearance
            SettingsSection("Appearance") {
                SettingsSwitchItem(
                    title = "Dark Theme",
                    subtitle = "Use dark color scheme",
                    icon = Icons.Default.DarkMode,
                    checked = darkTheme,
                    onCheckedChange = viewModel::setDarkTheme
                )
            }

            // Notifications
            SettingsSection("Notifications") {
                SettingsSwitchItem(
                    title = "Enable Notifications",
                    subtitle = "Receive alerts and reminders",
                    icon = Icons.Default.Notifications,
                    checked = notificationsEnabled,
                    onCheckedChange = viewModel::setNotificationsEnabled
                )
            }

            // Sync & Data
            SettingsSection("Sync & Data") {
                SettingsSwitchItem(
                    title = "Auto Sync",
                    subtitle = "Automatically sync with cloud",
                    icon = Icons.Default.Sync,
                    checked = autoSync,
                    onCheckedChange = viewModel::setAutoSync
                )
                HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                SettingsSwitchItem(
                    title = "Offline Mode",
                    subtitle = "Work without internet connection",
                    icon = Icons.Default.CloudOff,
                    checked = offlineMode,
                    onCheckedChange = viewModel::setOfflineMode
                )
                HorizontalDivider(color = colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
                SettingsSwitchItem(
                    title = "High Accuracy GPS",
                    subtitle = "Use GPS for precise location",
                    icon = Icons.Default.GpsFixed,
                    checked = highAccuracyGps,
                    onCheckedChange = viewModel::setHighAccuracyGps
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.triggerManualSync() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                    shape = ShapeButton
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSyncing) "Syncing…" else "Sync Now", fontWeight = FontWeight.SemiBold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { viewModel.exportData() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurfaceVariant),
                        shape = ShapeButton,
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(colorScheme.outlineVariant))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = { viewModel.importData() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurfaceVariant),
                        shape = ShapeButton,
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(colorScheme.outlineVariant))
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import")
                    }
                }
            }

            // Danger Zone
            SettingsSection("Danger Zone") {
                Button(
                    onClick = { showClearDataDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                    shape = ShapeButton
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Data", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Wildlife FieldOps v1.3",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Clear All Data?", color = colorScheme.onSurface, fontWeight = FontWeight.SemiBold) },
            text = { Text("This will permanently delete all jobs, customers, inspections, and photos. This action cannot be undone.", color = colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllData()
                    showClearDataDialog = false
                }) {
                    Text("Delete Everything", color = colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) {
                    Text("Cancel", color = colorScheme.onSurfaceVariant)
                }
            },
            containerColor = colorScheme.surface,
            shape = ShapeCard
        )
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
            shape = ShapeCard,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsInfoCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(ShapeCardSmall)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector
) {
    val colorScheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = colorScheme.primary) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorScheme.primary,
            unfocusedBorderColor = colorScheme.outlineVariant,
            focusedTextColor = colorScheme.onSurface,
            unfocusedTextColor = colorScheme.onSurface,
            focusedContainerColor = colorScheme.background,
            unfocusedContainerColor = colorScheme.background,
            focusedLabelColor = colorScheme.primary,
            unfocusedLabelColor = colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeInput,
        singleLine = true
    )
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(ShapeCardSmall)
                    .background(colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colorScheme.primary,
                checkedTrackColor = colorScheme.primary.copy(alpha = 0.5f),
                uncheckedThumbColor = colorScheme.outline,
                uncheckedTrackColor = colorScheme.surfaceVariant
            )
        )
    }
}
