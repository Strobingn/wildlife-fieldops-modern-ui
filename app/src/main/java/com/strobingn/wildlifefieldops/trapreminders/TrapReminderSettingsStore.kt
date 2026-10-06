package com.strobingn.wildlifefieldops.trapreminders

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.strobingn.wildlifefieldops.ai.fieldops.TrapReminders
import com.strobingn.wildlifefieldops.ui.viewmodel.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class TrapReminderSettings(
    val notificationsEnabled: Boolean = true,
    val defaultIntervalHours: Int = TrapReminders.DEFAULT_INTERVAL_HOURS,
    val leadMinutes: Int = TrapReminders.DEFAULT_LEAD_MINUTES,
    val sentKeys: Set<String> = emptySet()
)

@Singleton
class TrapReminderSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val settings: Flow<TrapReminderSettings> = context.settingsDataStore.data.map { read(it) }

    suspend fun current(): TrapReminderSettings = settings.first()

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[ENABLED] = enabled }
    }

    suspend fun setDefaultIntervalHours(hours: Int) {
        if (hours <= 0) return
        context.settingsDataStore.edit {
            it[INTERVAL_HOURS] = hours.coerceAtMost(TrapReminders.MAX_INTERVAL_HOURS)
        }
    }

    suspend fun setLeadMinutes(minutes: Int) {
        if (minutes < 0) return
        context.settingsDataStore.edit {
            it[LEAD_MINUTES] = minutes.coerceAtMost(TrapReminders.MAX_LEAD_MINUTES)
        }
    }

    suspend fun addSent(keys: Collection<String>) {
        if (keys.isEmpty()) return
        context.settingsDataStore.edit { prefs ->
            // Keep the newest due times; a stored set has no reliable order.
            val merged = (prefs[SENT].orEmpty() + keys)
                .sortedByDescending { it.split('|').getOrNull(1)?.toLongOrNull() ?: 0L }
                .take(MAX_SENT)
                .toSet()
            prefs[SENT] = merged
        }
    }

    private fun read(prefs: Preferences) = TrapReminderSettings(
        notificationsEnabled = prefs[ENABLED] ?: true,
        defaultIntervalHours = prefs[INTERVAL_HOURS]?.takeIf { it > 0 } ?: TrapReminders.DEFAULT_INTERVAL_HOURS,
        leadMinutes = prefs[LEAD_MINUTES]?.takeIf { it >= 0 } ?: TrapReminders.DEFAULT_LEAD_MINUTES,
        sentKeys = prefs[SENT].orEmpty()
    )

    companion object {
        private const val MAX_SENT = 400
        private val ENABLED = booleanPreferencesKey("trap_reminder_notifications")
        private val INTERVAL_HOURS = intPreferencesKey("trap_check_default_interval_hours")
        private val LEAD_MINUTES = intPreferencesKey("trap_reminder_lead_minutes")
        private val SENT = stringSetPreferencesKey("trap_reminder_sent_keys")
    }
}
