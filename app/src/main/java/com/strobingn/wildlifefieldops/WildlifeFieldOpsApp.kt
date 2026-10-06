package com.strobingn.wildlifefieldops

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.work.Configuration
import com.strobingn.wildlifefieldops.data.backup.FieldOpsBackupManager
import com.strobingn.wildlifefieldops.data.local.AppDatabase
import com.strobingn.wildlifefieldops.sync.work.AutoSync
import com.strobingn.wildlifefieldops.sync.work.WorkManagerConfigurationFactory
import com.strobingn.wildlifefieldops.update.AppUpdateCoordinator
import com.strobingn.wildlifefieldops.update.AppUpdateScheduler
import com.strobingn.wildlifefieldops.weather.WeatherAlertScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WildlifeFieldOpsApp : Application(), Configuration.Provider {
    @Inject lateinit var workManagerConfigurationFactory: WorkManagerConfigurationFactory
    @Inject lateinit var autoSync: AutoSync
    @Inject lateinit var appUpdateCoordinator: AppUpdateCoordinator
    @Inject lateinit var appUpdateScheduler: AppUpdateScheduler
    @Inject lateinit var weatherAlertScheduler: WeatherAlertScheduler
    @Inject lateinit var trapReminderScheduler: com.strobingn.wildlifefieldops.trapreminders.TrapReminderScheduler

    override fun onCreate() {
        // Restore a staged SAF backup before Hilt opens Room.
        runCatching { FieldOpsBackupManager.applyPendingRestore(this) }
            .onSuccess { restored ->
                if (restored) {
                    Log.i(
                        "WildlifeFieldOps",
                        "Applied Wildlife Whisperer field-data backup; Room migrates to v${AppDatabase.VERSION} on open"
                    )
                }
            }
            .onFailure { Log.e("WildlifeFieldOps", "Pending backup restore failed", it) }
        super.onCreate()
        // Catch uncaught crashes so we can identify future launch failures from logcat.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("WildlifeFieldOps", "FATAL on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
        Log.i("WildlifeFieldOps", "App starting v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        if (BuildConfig.WM_SYNC_CANARY_ENABLED) {
            Log.i("WildlifeFieldOps", "WorkManager sync canary listeners are ON")
        }
        runCatching { autoSync.start() }
            .onFailure { Log.e("WildlifeFieldOps", "Auto-sync failed to start", it) }
        runCatching { appUpdateScheduler.enqueuePeriodic() }
            .onFailure { Log.e("WildlifeFieldOps", "App-update periodic check failed to enqueue", it) }
        runCatching { weatherAlertScheduler.enqueuePeriodic() }
            .onFailure { Log.e("WildlifeFieldOps", "Weather alert check failed to enqueue", it) }
        runCatching { trapReminderScheduler.enqueuePeriodic() }
            .onFailure { Log.e("WildlifeFieldOps", "Trap check reminders failed to enqueue", it) }
        registerActivityLifecycleCallbacks(
            ForegroundAutoSyncCallbacks {
                autoSync.onForeground()
                appUpdateCoordinator.onForeground()
            }
        )
    }

    /**
     * Custom WM init — the manifest removes the default WorkManagerInitializer.
     * Experimental listeners are attached only when the canary flag is on.
     */
    override val workManagerConfiguration: Configuration
        get() = if (::workManagerConfigurationFactory.isInitialized) {
            workManagerConfigurationFactory.create()
        } else {
            Configuration.Builder().build()
        }
}

private class ForegroundAutoSyncCallbacks(
    private val onForeground: () -> Unit
) : Application.ActivityLifecycleCallbacks {
    private var started = 0
    override fun onActivityStarted(activity: Activity) {
        if (started == 0) onForeground()
        started++
    }
    override fun onActivityStopped(activity: Activity) {
        started = (started - 1).coerceAtLeast(0)
    }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
