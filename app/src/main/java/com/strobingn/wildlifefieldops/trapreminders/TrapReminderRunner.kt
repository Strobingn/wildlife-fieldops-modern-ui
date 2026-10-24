package com.strobingn.wildlifefieldops.trapreminders

import android.content.Context
import com.strobingn.wildlifefieldops.ai.fieldops.TrapReminders
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.TrapLogDao
import com.strobingn.wildlifefieldops.ui.viewmodel.notificationsAllowed
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts trap check reminders that are due and remembers each one, so a reminder
 * is never sent twice. Then asks the scheduler to wake up for the next one.
 */
@Singleton
class TrapReminderRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trapLogDao: TrapLogDao,
    private val jobDao: JobDao,
    private val settingsStore: TrapReminderSettingsStore,
    private val notifier: TrapReminderNotifier
) {
    private val mutex = Mutex()

    /** @return the next wake-up time, or null when nothing is planned. */
    suspend fun run(now: Long = System.currentTimeMillis()): Long? = mutex.withLock {
        val settings = settingsStore.current()
        val traps = trapLogDao.getAllOnce()
        if (!settings.notificationsEnabled || !context.notificationsAllowed()) return@withLock null
        val due = TrapReminders.dueNow(traps, now, settings.leadMinutes, settings.sentKeys)
        if (due.isNotEmpty()) {
            val titles = jobDao.getAllOnce().associate { it.id to it.title.ifBlank { it.customerName } }
            val posted = due
                .groupBy { it.trap.id }
                .mapNotNull { (_, list) ->
                    // When both stages are ready (phone was off), post only the due one.
                    val pick = list.maxByOrNull { it.stage.ordinal } ?: return@mapNotNull null
                    val ok = notifier.post(
                        pick.trap.id,
                        TrapReminders.notificationTitle(pick),
                        TrapReminders.notificationBody(pick, titles[pick.trap.jobId].orEmpty(), now)
                    )
                    if (ok) list else null
                }
                .flatten()
            settingsStore.addSent(TrapReminders.keysToMark(posted))
        }
        val sent = settingsStore.current().sentKeys
        TrapReminders.nextFireAt(traps, now, settings.leadMinutes, sent)
    }
}
