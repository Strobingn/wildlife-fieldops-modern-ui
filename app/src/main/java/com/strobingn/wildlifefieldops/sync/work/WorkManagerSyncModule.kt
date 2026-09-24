package com.strobingn.wildlifefieldops.sync.work

import com.strobingn.wildlifefieldops.BuildConfig
import com.strobingn.wildlifefieldops.data.repository.SyncRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkManagerSyncBindModule {
    @Binds
    @Singleton
    abstract fun bindDomainSyncLedger(impl: RoomDomainSyncLedger): DomainSyncLedger

    @Binds
    @Singleton
    abstract fun bindSyncGateway(impl: SyncRepository): FieldOpsSyncGateway
}

@Module
@InstallIn(SingletonComponent::class)
object WorkManagerSyncProvideModule {
    @Provides
    @Singleton
    fun provideCanaryFlag(): WorkManagerSyncCanaryFlag =
        WorkManagerSyncCanaryFlag { BuildConfig.WM_SYNC_CANARY_ENABLED }

    @Provides
    @Singleton
    fun provideTelemetry(): WorkSchedulerTelemetry = RecordingWorkSchedulerTelemetry()

    @Provides
    @Singleton
    fun provideAnalyticsAdapter(
        flag: WorkManagerSyncCanaryFlag,
        telemetry: WorkSchedulerTelemetry
    ): WorkAnalyticsAdapter = WorkAnalyticsAdapter(flag, telemetry)

    @Provides
    @Singleton
    fun provideEnqueueCoordinator(
        flag: WorkManagerSyncCanaryFlag,
        ledger: DomainSyncLedger,
        adapter: WorkAnalyticsAdapter
    ): FieldOpsSyncEnqueueCoordinator = FieldOpsSyncEnqueueCoordinator(flag, ledger, adapter)

    @Provides
    @Singleton
    fun provideWorkRunner(
        gateway: FieldOpsSyncGateway,
        ledger: DomainSyncLedger,
        adapter: WorkAnalyticsAdapter
    ): FieldOpsSyncWorkRunner = FieldOpsSyncWorkRunner(gateway, ledger, adapter)
}
