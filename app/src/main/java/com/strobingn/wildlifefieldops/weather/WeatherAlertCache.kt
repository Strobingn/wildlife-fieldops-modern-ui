package com.strobingn.wildlifefieldops.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class WeatherAlertCache(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private val state = MutableStateFlow(readQuietly())

    fun current(): WeatherCacheFile = state.value

    fun updates(): StateFlow<WeatherCacheFile> = state.asStateFlow()

    suspend fun update(block: (WeatherCacheFile) -> WeatherCacheFile) {
        mutex.withLock {
            val next = block(state.value)
            withContext(Dispatchers.IO) {
                runCatching {
                    file.parentFile?.mkdirs()
                    file.writeText(json.encodeToString(WeatherCacheFile.serializer(), next))
                }
            }
            state.value = next
        }
    }

    private fun readQuietly(): WeatherCacheFile {
        return runCatching {
            if (!file.exists()) return WeatherCacheFile()
            json.decodeFromString<WeatherCacheFile>(file.readText())
        }.getOrDefault(WeatherCacheFile())
    }
}
