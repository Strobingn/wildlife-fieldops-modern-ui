package com.strobingn.wildlifefieldops.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Job data carries `pricing.dataVersion`. When the cloud has data written by a
 * newer FieldOps than this one, this build stops uploading (so it can't blank
 * fields it doesn't know) and asks Sir to update. Reading and offline work go
 * on; edits stay queued (unsynced) and upload after the update.
 */
object DataVersionGate {
    /** 2 = technician signatures + payments ledger in pricing (2.9.x). */
    const val CURRENT = 2
    const val MESSAGE = "Please update FieldOps. Your changes are saved on this phone and will upload after the update."

    private val _blocked = MutableStateFlow(false)
    val blocked: StateFlow<Boolean> = _blocked.asStateFlow()

    fun isNewer(remoteVersion: Int, appVersion: Int = CURRENT): Boolean = remoteVersion > appVersion

    fun observe(remoteVersions: List<Int>, appVersion: Int = CURRENT): Boolean {
        val newer = remoteVersions.any { isNewer(it, appVersion) }
        _blocked.value = newer
        return newer
    }
}
