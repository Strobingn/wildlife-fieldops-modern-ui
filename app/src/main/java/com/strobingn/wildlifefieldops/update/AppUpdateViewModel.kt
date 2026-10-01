package com.strobingn.wildlifefieldops.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val coordinator: AppUpdateCoordinator
) : ViewModel() {
    val state: StateFlow<AppUpdateUiState> = coordinator.state

    fun checkOnStart() {
        viewModelScope.launch { runCatching { coordinator.check(force = false) } }
    }

    fun checkOnSettingsOpened() {
        viewModelScope.launch { runCatching { coordinator.check(force = true) } }
    }

    fun checkNow() {
        viewModelScope.launch { runCatching { coordinator.check(force = true) } }
    }

    fun startUpdate() = coordinator.startUpdate()
    fun proceedDespiteUnsynced() = coordinator.proceedDespiteUnsynced()
    fun waitAndRetrySync() = coordinator.waitAndRetrySync()
    fun dismissBanner() = coordinator.dismissBanner()
    fun dismissDialog() = coordinator.dismissDialog()
    fun showDialog() = coordinator.showDialog()
    fun onReturnedFromUnknownSources() = coordinator.onReturnedFromUnknownSources()
    fun unknownSourcesIntent(): Intent = coordinator.unknownSourcesIntent()
}
