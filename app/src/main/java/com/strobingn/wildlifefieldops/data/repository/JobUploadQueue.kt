package com.strobingn.wildlifefieldops.data.repository

import com.strobingn.wildlifefieldops.ai.fieldops.OpsLedger
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.remote.SyncIds

/**
 * Which local job rows go to Supabase `jobs`. That table's id is a uuid, so the
 * internal Ops ledger row ("fieldops-ops-ledger") and any other non-UUID id can
 * never upload; they used to fail every sync and sit in the Failed count.
 *
 * Only the upload queue and the pending/failed counts skip them. The rows stay in
 * Room unchanged (the ledger keeps its earnings, DEC, and NWCO extras on this phone).
 * Every job the app creates gets a random UUID, so real jobs are never skipped.
 */
object JobUploadQueue {

    fun isUploadable(id: String): Boolean = !OpsLedger.isLedgerId(id) && SyncIds.isUuid(id)

    fun isUploadable(job: Job): Boolean = isUploadable(job.id)

    /** Unsynced rows that should upload, in the order Room returned them. */
    fun pending(unsynced: List<Job>): List<Job> = unsynced.filter(::isUploadable)

    /** Rows that will upload but whose last attempt failed. */
    fun failed(unsynced: List<Job>): List<Job> = pending(unsynced).filter { !it.syncError.isNullOrBlank() }
}
