package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.operations.AIOperationsEngine
import com.strobingn.wildlifefieldops.data.local.JobDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AIOperationsViewModel @Inject constructor(
    jobDao: JobDao
) : ViewModel() {
    val dashboard = jobDao.getAll()
        .map(AIOperationsEngine::analyze)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AIOperationsEngine.analyze(emptyList())
        )
}

