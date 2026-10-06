package com.strobingn.wildlifefieldops.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.strobingn.wildlifefieldops.ui.components.FieldCard
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.ErrorRed
import com.strobingn.wildlifefieldops.ui.theme.FieldMetrics
import com.strobingn.wildlifefieldops.ui.theme.FieldShapes
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.StatusUrgent
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import com.strobingn.wildlifefieldops.ui.viewmodel.WeatherAlertsViewModel
import com.strobingn.wildlifefieldops.weather.WeatherAlert
import com.strobingn.wildlifefieldops.weather.WeatherAlertEngine
import com.strobingn.wildlifefieldops.weather.WeatherAlertKind
import com.strobingn.wildlifefieldops.weather.WeatherAlertSettings
import java.util.Locale

@Composable
fun WeatherAlertsCard(
    alerts: List<WeatherAlert>,
    fetchedAtMillis: Long?,
    modifier: Modifier = Modifier,
    startExpanded: Boolean = false
) {
    if (alerts.isEmpty()) return
    var expanded by remember { mutableStateOf(startExpanded) }
    val ordered = WeatherAlertEngine.ordered(alerts)
    val lead = ordered.first()
    FieldCard(
        onClick = { expanded = !expanded },
        modifier = modifier.testTag("weather-alerts-card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Weather alerts",
                tint = StatusUrgent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Weather alerts",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                if (!expanded) {
                    Text(
                        lead.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusUrgent,
                        fontWeight = FontWeight.Bold
                    )
                    if (ordered.size > 1) {
                        Text(
                            "+${ordered.size - 1} more",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse weather alerts" else "Expand weather alerts",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(8.dp))
            ordered.forEach { alert ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        alertIcon(alert.kind),
                        contentDescription = null,
                        tint = StatusUrgent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        alert.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusUrgent,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        fetchedAtMillis?.let { fetched ->
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                WeatherAlertEngine.formatLastChecked(fetched),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun JobWeatherWarningChip(
    text: String,
    modifier: Modifier = Modifier
) {
    if (text.isBlank()) return
    androidx.compose.material3.Surface(
        modifier = modifier.testTag("job-weather-chip"),
        shape = FieldShapes.chip,
        color = StatusUrgent.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Weather warning",
                tint = StatusUrgent,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = StatusUrgent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun WeatherAlertsSettingsSection(
    viewModel: WeatherAlertsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val home by viewModel.home.collectAsState()
    WeatherAlertsSettingsBody(
        settings = settings,
        statusLine = home.statusLine,
        checking = home.checking,
        onRain = viewModel::setRainEnabled,
        onHeavyRain = viewModel::setHeavyRainEnabled,
        onHighWind = viewModel::setHighWindEnabled,
        onSnow = viewModel::setSnowEnabled,
        onNotifications = viewModel::setNotificationsEnabled,
        onWind = viewModel::setWindThreshold,
        onHeavyHour = viewModel::setHeavyHourInches,
        onHeavyDay = viewModel::setHeavyDayInches,
        onCheckNow = viewModel::checkNow
    )
}

@Composable
fun WeatherAlertsSettingsBody(
    settings: WeatherAlertSettings,
    statusLine: String,
    checking: Boolean,
    onRain: (Boolean) -> Unit,
    onHeavyRain: (Boolean) -> Unit,
    onHighWind: (Boolean) -> Unit,
    onSnow: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onWind: (Double) -> Unit,
    onHeavyHour: (Double) -> Unit,
    onHeavyDay: (Double) -> Unit,
    onCheckNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.testTag("weather-alerts-settings")) {
        Text(
            "Weather alerts",
            style = MaterialTheme.typography.titleSmall,
            color = PrimaryGreen,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = BackgroundCard),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Rain, heavy rain, wind, and snow for the next 48 hours, plus official NWS warnings, watches, and advisories. The forecast is free and needs no API key.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                WeatherSwitch("Rain", "Chance of rain at 50% or more", Icons.Default.WaterDrop, settings.rainEnabled, onRain)
                WeatherSwitch("Heavy rain", "A heavy hour or a full day of rain", Icons.Default.Warning, settings.heavyRainEnabled, onHeavyRain)
                WeatherSwitch("High wind", "Gusts over the limit. Steady speed is shown next to the gust.", Icons.Default.Air, settings.highWindEnabled, onHighWind)
                WeatherSwitch("Snow", "Any forecast snowfall", Icons.Default.AcUnit, settings.snowEnabled, onSnow)
                WeatherSwitch("Notifications", "Phone alert when a new warning shows up", Icons.Default.Notifications, settings.notificationsEnabled, onNotifications)
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(
                    storedValue = formatWindSetting(settings.windMphThreshold),
                    label = "Wind limit (mph)",
                    onCommit = { raw -> raw.toDoubleOrNull()?.takeIf { it > 0.0 }?.let(onWind) },
                    keyboardType = KeyboardType.Decimal,
                    supportingText = "Alert when gusts are over this. 30 means a 31 mph gust alerts. Steady wind is shown beside it."
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(
                    storedValue = formatInchSetting(settings.heavyRainHourInches),
                    label = "Heavy rain in one hour (in)",
                    onCommit = { raw -> raw.toDoubleOrNull()?.takeIf { it > 0.0 }?.let(onHeavyHour) },
                    keyboardType = KeyboardType.Decimal
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(
                    storedValue = formatInchSetting(settings.heavyRainDayInches),
                    label = "Heavy rain over 24 hours (in)",
                    onCommit = { raw -> raw.toDoubleOrNull()?.takeIf { it > 0.0 }?.let(onHeavyDay) },
                    keyboardType = KeyboardType.Decimal
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCheckNow,
                    enabled = !checking,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FieldMetrics.primaryTouch),
                    shape = FieldShapes.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (checking) "Checking…" else "Check now", fontWeight = FontWeight.SemiBold)
                }
                if (statusLine.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        statusLine,
                        color = if (statusLine.startsWith("Couldn't") || statusLine.startsWith("No location")) ErrorRed else TextTertiary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherSwitch(
    title: String,
    subtitle: String,
    icon: ImageVector,
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
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryGreen,
                checkedTrackColor = PrimaryGreen.copy(alpha = 0.5f)
            )
        )
    }
}

private fun alertIcon(kind: WeatherAlertKind): ImageVector = when (kind) {
    WeatherAlertKind.NWS -> Icons.Default.Info
    WeatherAlertKind.RAIN -> Icons.Default.WaterDrop
    WeatherAlertKind.HEAVY_RAIN -> Icons.Default.Warning
    WeatherAlertKind.HIGH_WIND -> Icons.Default.Air
    WeatherAlertKind.SNOW -> Icons.Default.AcUnit
}

private fun formatWindSetting(value: Double): String =
    if (value % 1.0 == 0.0) String.format(Locale.US, "%.0f", value)
    else String.format(Locale.US, "%.1f", value)

private fun formatInchSetting(value: Double): String =
    String.format(Locale.US, "%.2f", value)
