package com.strobingn.wildlifefieldops.update

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {
    @Binds
    @Singleton
    abstract fun bindTransport(impl: UrlConnectionTransport): AppUpdateTransport
}
