package com.strobingn.wildlifefieldops.di

import android.content.Context
import androidx.room.Room
import com.strobingn.wildlifefieldops.data.local.AppDatabase
import com.strobingn.wildlifefieldops.data.local.Migrations
import com.strobingn.wildlifefieldops.data.remote.GeocodingService
import com.strobingn.wildlifefieldops.data.workspace.AddressGeocoder
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
            AppDatabase.NAME
        )
            .addMigrations(*Migrations.ALL)
            // Never fallbackToDestructiveMigration: a restored backup may be
            // Room user_version 10 (pre-pricing) and must migrate in place to current.
            .build()
    }

    @Provides
    fun provideAddressGeocoder(geocodingService: GeocodingService): AddressGeocoder =
        AddressGeocoder { address -> geocodingService.geocode(address) }

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

    @Provides
    fun provideFieldObservationDao(database: AppDatabase) = database.fieldObservationDao()

    @Provides
    fun provideVoiceObservationDao(database: AppDatabase) = database.voiceObservationDao()

    @Provides
    fun provideObservationEventDao(database: AppDatabase) = database.observationEventDao()

    @Provides
    fun provideSyncOperationDao(database: AppDatabase) = database.syncOperationDao()
}
