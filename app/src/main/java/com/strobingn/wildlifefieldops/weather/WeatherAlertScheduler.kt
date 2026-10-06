package com.strobingn.wildlifefieldops.weather

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherAlertScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notifier: WeatherAlertNotifier
) {
    fun enqueuePeriodic() {
        runCatching { notifier.ensureChannel() }
        val request = PeriodicWorkRequestBuilder<WeatherAlertWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(PERIODIC_TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val PERIODIC_WORK_NAME = "fieldops-weather-alerts-periodic"
        const val PERIODIC_TAG = "fo-weather-alerts-periodic"
    }
}
