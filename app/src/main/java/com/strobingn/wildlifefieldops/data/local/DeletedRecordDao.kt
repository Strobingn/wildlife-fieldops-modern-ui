package com.strobingn.wildlifefieldops.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.strobingn.wildlifefieldops.data.model.DeletedRecord

@Dao
interface DeletedRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DeletedRecord)

    @Query("SELECT * FROM deleted_records WHERE entityType = :entityType AND synced = 0")
    suspend fun getUnsyncedByType(entityType: String): List<DeletedRecord>

    @Query("UPDATE deleted_records SET synced = 1 WHERE id = :id AND entityType = :entityType")
    suspend fun markSynced(id: String, entityType: String)

    @Query("SELECT EXISTS(SELECT 1 FROM deleted_records WHERE id = :id AND entityType = :entityType)")
    suspend fun exists(id: String, entityType: String): Boolean

    @Query("SELECT id FROM deleted_records WHERE entityType = :entityType")
    suspend fun getIdsByType(entityType: String): List<String>
}
