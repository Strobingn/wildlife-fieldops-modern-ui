package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.local.LocalLlmCatalog
import com.strobingn.wildlifefieldops.ai.local.LocalLlmEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LocalLlmViewModel @Inject constructor(
    val engine: LocalLlmEngine
) : ViewModel() {

    val status = engine.status
    val downloadProgress = engine.downloadProgress
    val models = LocalLlmCatalog.ALL

    fun select(id: String) {
        engine.selectModel(id)
    }

    fun downloadAndLoad() {
        viewModelScope.launch {
            engine.downloadSelected()
        }
    }

    fun load() {
        viewModelScope.launch {
            engine.load()
        }
    }

    fun unload() {
        engine.unload()
    }

    fun delete() {
        engine.deleteSelected()
    }
}
