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
    /**
     * 2 = technician signatures + payments ledger in pricing (2.9.x).
     * 3 = precise trap GPS (accuracy, sample count, captured-at) on trapRecords.
     * 4 = custom estimate items and price-only invoice lines inside pricing jsonb.
     * Jobs without those fields stay at 2 or 3 so older builds keep uploading them.
     * A saved precise spot is stamped [TRAP_GPS]. A custom charge is stamped
     * [CUSTOM_ITEMS]. Older builds then pause uploads so they cannot drop fields
     * they do not know.
     */
    const val TRAP_GPS = 3
    const val CUSTOM_ITEMS = 4
    const val CURRENT = CUSTOM_ITEMS
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
