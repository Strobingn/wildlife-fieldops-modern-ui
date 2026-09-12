package com.strobingn.wildlifefieldops.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local tombstone so cloud pull cannot resurrect a record the user deleted.
 * [synced] means the remote DELETE has succeeded (or there was nothing to delete).
 */
@Entity(tableName = "deleted_records")
data class DeletedRecord(
    @PrimaryKey
    val id: String,
    /** One of: "job", "customer", "inspection" */
    val entityType: String,
    val deletedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
) {
    companion object {
        const val TYPE_JOB = "job"
        const val TYPE_CUSTOMER = "customer"
        const val TYPE_INSPECTION = "inspection"
    }
}
