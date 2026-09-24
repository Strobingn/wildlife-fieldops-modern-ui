package com.strobingn.wildlifefieldops

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import com.strobingn.wildlifefieldops.sync.work.WorkManagerConfigurationFactory
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WildlifeFieldOpsApp : Application(), Configuration.Provider {
    @Inject lateinit var workManagerConfigurationFactory: WorkManagerConfigurationFactory

    override fun onCreate() {
        super.onCreate()
        // Catch uncaught crashes so we can identify future launch failures from logcat.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("WildlifeFieldOps", "FATAL on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
        Log.i("WildlifeFieldOps", "App starting v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        if (BuildConfig.WM_SYNC_CANARY_ENABLED) {
            Log.i("WildlifeFieldOps", "WorkManager sync canary is ON (unique work fieldops-sync)")
        }
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
