package com.strobingn.wildlifefieldops.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.strobingn.wildlifefieldops.data.model.*

@Database(
    entities = [
        Job::class,
        Customer::class,
        Inspection::class,
        Photo::class,
        Visit::class,
        Repair::class,
        Expense::class,
        TrapLog::class,
        InventoryItem::class,
        Reminder::class,
        Invoice::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun jobDao(): JobDao
    abstract fun customerDao(): CustomerDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun photoDao(): PhotoDao
    abstract fun visitDao(): VisitDao
    abstract fun repairDao(): RepairDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun trapLogDao(): TrapLogDao
    abstract fun inventoryItemDao(): InventoryItemDao
    abstract fun reminderDao(): ReminderDao
    abstract fun invoiceDao(): InvoiceDao
}
