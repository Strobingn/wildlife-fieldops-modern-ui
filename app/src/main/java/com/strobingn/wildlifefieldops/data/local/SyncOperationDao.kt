package com.strobingn.wildlifefieldops.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncOperationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(record: SyncOperationRecord): Long

    @Query("SELECT * FROM sync_operations WHERE operationId = :operationId")
    suspend fun get(operationId: String): SyncOperationRecord?

    @Query(
        """
        SELECT * FROM sync_operations
        WHERE idempotencyKey = :idempotencyKey
          AND state IN ('PENDING','READY','UPLOADING','RETRYABLE_FAILURE')
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    suspend fun findActive(idempotencyKey: String): SyncOperationRecord?

    @Query("SELECT * FROM sync_operations ORDER BY createdAt ASC")
    suspend fun all(): List<SyncOperationRecord>

    @Query(
        """
        UPDATE sync_operations
        SET workRequestId = :workRequestId, workGeneration = :generation, updatedAt = :updatedAt
        WHERE operationId = :operationId
        """
    )
    suspend fun bindWorkRequest(
        operationId: String,
        workRequestId: String,
        generation: Int,
        updatedAt: Long
    )

    @Query(
        "UPDATE sync_operations SET state = :state, updatedAt = :updatedAt WHERE operationId = :operationId"
    )
    suspend fun updateState(operationId: String, state: String, updatedAt: Long)

    @Query(
        """
        UPDATE sync_operations
        SET state = 'READY', updatedAt = :updatedAt
        WHERE state IN ('UPLOADING','PENDING')
        """
    )
    suspend fun revertInFlightToReady(updatedAt: Long)
}
