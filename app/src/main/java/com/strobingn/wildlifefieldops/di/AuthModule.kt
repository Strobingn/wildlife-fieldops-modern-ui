package com.strobingn.wildlifefieldops.di

import com.strobingn.wildlifefieldops.data.auth.AuthSessionPort
import com.strobingn.wildlifefieldops.data.auth.AuthSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {
    @Binds
    @Singleton
    abstract fun bindAuthSessionPort(impl: AuthSessionRepository): AuthSessionPort
}
