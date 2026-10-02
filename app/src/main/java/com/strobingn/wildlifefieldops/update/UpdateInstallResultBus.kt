package com.strobingn.wildlifefieldops.update

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInstallStatus(
    val statusCode: Int,
    val message: String?,
    val confirmation: android.content.Intent? = null
)

@Singleton
class UpdateInstallResultBus @Inject constructor() {
    private val _events = MutableSharedFlow<UpdateInstallStatus>(extraBufferCapacity = 8)
    val events: SharedFlow<UpdateInstallStatus> = _events.asSharedFlow()

    fun post(status: UpdateInstallStatus) {
        _events.tryEmit(status)
    }
}
