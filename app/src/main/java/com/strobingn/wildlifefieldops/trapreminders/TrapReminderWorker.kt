package com.strobingn.wildlifefieldops.trapreminders

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TrapReminderWorkerEntryPoint {
    fun trapReminderRunner(): TrapReminderRunner
    fun trapReminderScheduler(): TrapReminderScheduler
}

/**
 * Checks for trap reminders. Uses an EntryPoint rather than @HiltWorker so the
 * WorkManager version pin stays unchanged. Needs no network.
 */
class TrapReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val entry = EntryPointAccessors.fromApplication(
                applicationContext,
                TrapReminderWorkerEntryPoint::class.java
            )
            val next = entry.trapReminderRunner().run()
            entry.trapReminderScheduler().scheduleAt(next)
            Result.success()
        } catch (error: Throwable) {
            Log.e(TAG, "Trap reminder check failed", error)
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "TrapReminderWorker"
    }
}
