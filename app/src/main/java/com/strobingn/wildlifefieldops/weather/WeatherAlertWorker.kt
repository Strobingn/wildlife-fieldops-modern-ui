package com.strobingn.wildlifefieldops.weather

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.android.EntryPointAccessors

/**
 * Background forecast check. Uses an EntryPoint rather than @HiltWorker so the
 * WorkManager version pin stays unchanged.
 */
class WeatherAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val entry = EntryPointAccessors.fromApplication(
                applicationContext,
                WeatherAlertWorkerEntryPoint::class.java
            )
            entry.weatherAlertRepository().checkNow(notify = true)
            Result.success()
        } catch (error: Throwable) {
            Log.e(TAG, "Weather alert check failed", error)
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "WeatherAlertWorker"
    }
}
