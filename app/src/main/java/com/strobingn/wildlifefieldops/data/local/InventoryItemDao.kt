package com.strobingn.wildlifefieldops.data.local

import androidx.room.*
import com.strobingn.wildlifefieldops.data.model.InventoryItem
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryItemDao {
    @Query("SELECT * FROM inventory_items WHERE isActive = 1 ORDER BY name")
    fun getAll(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE id = :id")
    suspend fun getById(id: String): InventoryItem?

    @Query("SELECT * FROM inventory_items WHERE isActive = 1 ORDER BY name")
    suspend fun getAllOnce(): List<InventoryItem>

    @Query("SELECT * FROM inventory_items WHERE category = :category ORDER BY name")
    fun getByCategory(category: String): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE quantityOnHand <= reorderLevel AND reorderLevel > 0")
    fun getLowStock(): Flow<List<InventoryItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: InventoryItem)

    @Update
    suspend fun update(item: InventoryItem)

    @Delete
    suspend fun delete(item: InventoryItem)
}
