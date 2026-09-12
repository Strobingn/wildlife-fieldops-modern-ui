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

    @Query("SELECT * FROM customers WHERE firstName LIKE '%' || :query || '%' OR lastName LIKE '%' || :query || '%' OR companyName LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<Customer>>

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

    @Query("UPDATE customers SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM customers")
    suspend fun deleteAll()
}
