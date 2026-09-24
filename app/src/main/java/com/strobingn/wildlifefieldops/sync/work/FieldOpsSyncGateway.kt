package com.strobingn.wildlifefieldops.sync.work

import com.strobingn.wildlifefieldops.data.repository.SyncResult

/**
 * Narrow port so the canary worker can call existing sync without depending
 * on Room / Supabase types. [com.strobingn.wildlifefieldops.data.repository.SyncRepository]
 * is the production implementation — do not duplicate upsert / photo logic.
 */
interface FieldOpsSyncGateway {
    fun isCloudConfigured(): Boolean
    suspend fun syncAll(): SyncResult
}
