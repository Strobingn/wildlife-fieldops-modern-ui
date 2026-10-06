package com.strobingn.wildlifefieldops.weather

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WeatherAlertWorkerEntryPoint {
    fun weatherAlertRepository(): WeatherAlertRepository
}
