package com.strobingn.wildlifefieldops.data.local

import androidx.room.*
import com.strobingn.wildlifefieldops.data.model.Invoice
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    fun getAll(): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun getById(id: String): Invoice?

    @Query("SELECT * FROM invoices WHERE jobId = :jobId")
    fun getByJob(jobId: String): Flow<List<Invoice>>

    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    suspend fun getAllOnce(): List<Invoice>

    @Query("SELECT * FROM invoices WHERE jobId = :jobId")
    suspend fun getByJobOnce(jobId: String): List<Invoice>

    @Query("SELECT * FROM invoices WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: com.strobingn.wildlifefieldops.data.model.InvoiceStatus): Flow<List<Invoice>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invoice: Invoice)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(invoices: List<Invoice>)

    @Update
    suspend fun update(invoice: Invoice)

    @Delete
    suspend fun delete(invoice: Invoice)

    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun count(): Int
}
