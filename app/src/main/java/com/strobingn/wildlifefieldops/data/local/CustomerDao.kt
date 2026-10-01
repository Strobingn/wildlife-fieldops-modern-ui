package com.strobingn.wildlifefieldops.data.local

import androidx.room.*
import com.strobingn.wildlifefieldops.data.model.Customer
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE isActive = 1 ORDER BY firstName, lastName")
    fun getAll(): Flow<List<Customer>>

    @Query("SELECT * FROM customers ORDER BY firstName, lastName")
    suspend fun getAllOnce(): List<Customer>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getById(id: String): Customer?

    @Query("SELECT * FROM customers WHERE isSynced = 0")
    suspend fun getUnsynced(): List<Customer>

    @Query("SELECT COUNT(*) FROM customers WHERE isSynced = 0")
    suspend fun countUnsynced(): Int

    @Query("SELECT * FROM customers WHERE firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%' OR companyName LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE isActive = 1 AND (firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%' OR companyName LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%') ORDER BY firstName, lastName LIMIT 12")
    suspend fun searchOnce(query: String): List<Customer>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observeById(id: String): Flow<Customer?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customer: Customer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<Customer>)

    @Update
    suspend fun update(customer: Customer)

    @Delete
    suspend fun delete(customer: Customer)

    @Query("SELECT COUNT(*) FROM customers WHERE isActive = 1")
    suspend fun count(): Int

    @Query("UPDATE customers SET isSynced = 1, syncError = NULL WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("UPDATE customers SET syncError = :error WHERE id = :id")
    suspend fun markSyncError(id: String, error: String?)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}
