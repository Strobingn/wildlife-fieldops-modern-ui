package com.strobingn.wildlifefieldops.sync.work

/**
 * Compile-time canary gate for WorkManager background sync.
 *
 * Release builds default **off** ([com.strobingn.wildlifefieldops.BuildConfig.WM_SYNC_CANARY_ENABLED]).
 * Debug / internal canary builds set the field to `true`. Tests inject a lambda.
 */
fun interface WorkManagerSyncCanaryFlag {
    fun isEnabled(): Boolean
}
