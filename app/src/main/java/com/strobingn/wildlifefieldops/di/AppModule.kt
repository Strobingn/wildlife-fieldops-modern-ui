package com.strobingn.wildlifefieldops.di

import android.content.Context
import androidx.room.Room
import com.strobingn.wildlifefieldops.data.local.AppDatabase
import com.strobingn.wildlifefieldops.data.local.Migrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "wildlife_fieldops.db"
        )
            .addMigrations(Migrations.MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideJobDao(database: AppDatabase) = database.jobDao()

    @Provides
    fun provideCustomerDao(database: AppDatabase) = database.customerDao()

    @Provides
    fun provideInspectionDao(database: AppDatabase) = database.inspectionDao()

    @Provides
    fun providePhotoDao(database: AppDatabase) = database.photoDao()

    @Provides
    fun provideVisitDao(database: AppDatabase) = database.visitDao()

    @Provides
    fun provideRepairDao(database: AppDatabase) = database.repairDao()

    @Provides
    fun provideExpenseDao(database: AppDatabase) = database.expenseDao()

    @Provides
    fun provideTrapLogDao(database: AppDatabase) = database.trapLogDao()

    @Provides
    fun provideInventoryItemDao(database: AppDatabase) = database.inventoryItemDao()

    @Provides
    fun provideReminderDao(database: AppDatabase) = database.reminderDao()

    @Provides
    fun provideInvoiceDao(database: AppDatabase) = database.invoiceDao()

    @Provides
    fun provideDeletedRecordDao(database: AppDatabase) = database.deletedRecordDao()
}
