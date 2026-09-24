package com.strobingn.wildlifefieldops.sync.work

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FieldOpsSyncWorkerEntryPoint {
    fun fieldOpsSyncWorkRunner(): FieldOpsSyncWorkRunner
}
