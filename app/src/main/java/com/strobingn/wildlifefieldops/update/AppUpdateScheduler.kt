package com.strobingn.wildlifefieldops.update

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
class AppUpdateScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun enqueuePeriodic() {
        val request = PeriodicWorkRequestBuilder<AppUpdateWorker>(
            AppUpdatePolicy.PERIODIC_CHECK_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        )
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
        const val PERIODIC_WORK_NAME = "fieldops-app-update-periodic"
        const val PERIODIC_TAG = "fo-app-update-periodic"
    }
}
