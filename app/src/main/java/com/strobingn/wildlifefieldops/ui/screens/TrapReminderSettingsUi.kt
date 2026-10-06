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
import androidx.compose.material.icons.filled.Notifications
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.fieldops.TrapReminders
import com.strobingn.wildlifefieldops.trapreminders.TrapReminderScheduler
import com.strobingn.wildlifefieldops.trapreminders.TrapReminderSettings
import com.strobingn.wildlifefieldops.trapreminders.TrapReminderSettingsStore
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import com.strobingn.wildlifefieldops.ui.theme.TextTertiary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

@HiltViewModel
class TrapReminderSettingsViewModel @Inject constructor(
    private val store: TrapReminderSettingsStore,
    private val scheduler: TrapReminderScheduler
) : ViewModel() {
    val settings: StateFlow<TrapReminderSettings> = store.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrapReminderSettings())

    fun setNotifications(enabled: Boolean) = viewModelScope.launch {
        store.setNotificationsEnabled(enabled)
        scheduler.checkSoon()
    }

    fun setDefaultIntervalHours(hours: Int) = viewModelScope.launch {
        store.setDefaultIntervalHours(hours)
        scheduler.checkSoon()
    }

    fun setLeadMinutes(minutes: Int) = viewModelScope.launch {
        store.setLeadMinutes(minutes)
        scheduler.checkSoon()
    }
}

@Composable
fun TrapReminderSettingsSection(
    viewModel: TrapReminderSettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    TrapReminderSettingsBody(
        settings = settings,
        onNotifications = { viewModel.setNotifications(it) },
        onDefaultInterval = { viewModel.setDefaultIntervalHours(it) },
        onLeadMinutes = { viewModel.setLeadMinutes(it) }
    )
}

@Composable
fun TrapReminderSettingsBody(
    settings: TrapReminderSettings,
    onNotifications: (Boolean) -> Unit,
    onDefaultInterval: (Int) -> Unit,
    onLeadMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.testTag("trap-reminder-settings")) {
        Text(
            "Trap checks",
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
                    "Each set trap gets a check time. You get one reminder before it is due and one when it is due, unless you check it first.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Trap check reminders", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("Phone alert before and at each check time", style = MaterialTheme.typography.bodySmall, color = TextTertiary)
                        }
                    }
                    Switch(
                        checked = settings.notificationsEnabled,
                        onCheckedChange = onNotifications,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PrimaryGreen,
                            checkedTrackColor = PrimaryGreen.copy(alpha = 0.5f)
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(
                    storedValue = settings.defaultIntervalHours.toString(),
                    label = "Check every (hours)",
                    onCommit = { raw ->
                        raw.trim().toIntOrNull()
                            ?.takeIf { it in 1..TrapReminders.MAX_INTERVAL_HOURS }
                            ?.let(onDefaultInterval)
                    },
                    keyboardType = KeyboardType.Number,
                    supportingText = "Default for traps without their own interval. NY rules call for at least once every 24 hours."
                )
                Spacer(modifier = Modifier.height(8.dp))
                SettingPlainField(
                    storedValue = formatLeadHours(settings.leadMinutes),
                    label = "Remind me before (hours)",
                    onCommit = { raw ->
                        raw.trim().toDoubleOrNull()
                            ?.let { (it * 60).toInt() }
                            ?.takeIf { it in 0..TrapReminders.MAX_LEAD_MINUTES }
                            ?.let(onLeadMinutes)
                    },
                    keyboardType = KeyboardType.Decimal,
                    supportingText = "0 means only the reminder at the due time."
                )
            }
        }
    }
}

internal fun formatLeadHours(minutes: Int): String {
    val hours = minutes / 60.0
    return if (minutes % 60 == 0) String.format(Locale.US, "%.0f", hours)
    else String.format(Locale.US, "%.2f", hours).trimEnd('0').trimEnd('.')
}
