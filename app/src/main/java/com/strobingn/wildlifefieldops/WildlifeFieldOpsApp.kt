package com.strobingn.wildlifefieldops

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WildlifeFieldOpsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("WildlifeFieldOps", "FATAL on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
        Log.i("WildlifeFieldOps", "App starting v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }

    companion object {
        @Volatile
        private var app: WildlifeFieldOpsApp? = null

        fun instanceOrNull(): WildlifeFieldOpsApp? = app
    }
}
