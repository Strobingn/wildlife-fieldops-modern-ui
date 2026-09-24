package com.strobingn.wildlifefieldops.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Durable domain sync-operation ledger (business states + WM correlation).
 * Separate from WorkManager's ~7-day analytics database.
 */
@Entity(
    tableName = "sync_operations",
    indices = [Index(value = ["idempotencyKey"])]
)
data class SyncOperationRecord(
    @PrimaryKey
    val operationId: String,
    val idempotencyKey: String,
    val state: String,
    val workRequestId: String? = null,
    val workGeneration: Int? = null,
    val createdAt: Long,
    val updatedAt: Long
)
