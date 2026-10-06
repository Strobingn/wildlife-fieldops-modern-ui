package com.strobingn.wildlifefieldops.trapreminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A 15-minute safety check plus one exact one-shot wake-up for the next reminder.
 * The runner keeps a list of sent reminders, so overlapping runs never duplicate.
 */
@Singleton
class TrapReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: TrapReminderNotifier
) {
    fun enqueuePeriodic() {
        runCatching { notifier.ensureChannel() }
        val request = PeriodicWorkRequestBuilder<TrapReminderWorker>(15, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        checkSoon()
    }

    /** Run now, for example right after a trap is saved, checked, or pulled. */
    fun checkSoon() = scheduleAt(System.currentTimeMillis())

    fun scheduleAt(fireAt: Long?) {
        if (fireAt == null) return
        val delay = (fireAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<TrapReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NEXT_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "fieldops-trap-reminders-periodic"
        const val NEXT_WORK_NAME = "fieldops-trap-reminders-next"
        const val TAG = "fo-trap-reminders"
    }
}
