package com.strobingn.wildlifefieldops.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/**
 * Background update.json check. Uses an EntryPoint rather than @HiltWorker so
 * the WorkManager 2.12 pin stays unchanged.
 */
class AppUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            AppUpdateWorkerEntryPoint::class.java
        )
        return runCatching {
            entry.appUpdateCoordinator().check(force = false)
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
