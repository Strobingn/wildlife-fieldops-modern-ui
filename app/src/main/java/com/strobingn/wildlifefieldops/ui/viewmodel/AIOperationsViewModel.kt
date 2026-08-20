package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.operations.AIOperationsEngine
import com.strobingn.wildlifefieldops.ai.operations.RealAIFeatureCatalog
import com.strobingn.wildlifefieldops.ai.operations.RealAIFeatureResult
import com.strobingn.wildlifefieldops.ai.operations.RealAIFeatureService
import com.strobingn.wildlifefieldops.data.local.JobDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RealAIFeatureRunState(
    val running: Boolean = false,
    val result: RealAIFeatureResult? = null
)

@HiltViewModel
class AIOperationsViewModel @Inject constructor(
    jobDao: JobDao,
    private val realAI: RealAIFeatureService
) : ViewModel() {

    val jobs = jobDao.getAll()
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            emptyList()
        )

    val dashboard = jobs
        .map(AIOperationsEngine::analyze)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AIOperationsEngine.analyze(emptyList())
        )

    val realAIFeatures = RealAIFeatureCatalog.features
    val aiConfigured: Boolean get() = realAI.isConfigured
    val aiProviderLabel: String get() = realAI.providerLabel

    private val _selectedJobId = MutableStateFlow<String?>(null)
    val selectedJobId: StateFlow<String?> = _selectedJobId

    private val _featureStates = MutableStateFlow<Map<String, RealAIFeatureRunState>>(emptyMap())
    val featureStates: StateFlow<Map<String, RealAIFeatureRunState>> = _featureStates

    init {
        viewModelScope.launch {
            jobs.collect { currentJobs ->
                val selectedStillExists = currentJobs.any { it.id == _selectedJobId.value }
                if (!selectedStillExists) {
                    _selectedJobId.value = currentJobs.maxByOrNull { it.updatedAt }?.id
                }
            }
        }
    }

    fun selectJob(jobId: String) {
        if (jobs.value.any { it.id == jobId }) {
            _selectedJobId.value = jobId
        }
    }

    fun runRealAIFeature(featureId: String) {
        if (_featureStates.value[featureId]?.running == true) return
        _featureStates.value = _featureStates.value + (
            featureId to RealAIFeatureRunState(running = true, result = _featureStates.value[featureId]?.result)
        )

        viewModelScope.launch {
            val result = realAI.run(
                featureId = featureId,
                jobs = jobs.value,
                focusJobId = _selectedJobId.value
            )
            _featureStates.value = _featureStates.value + (
                featureId to RealAIFeatureRunState(running = false, result = result)
            )
        }
    }

    fun clearRealAIResult(featureId: String) {
        _featureStates.value = _featureStates.value - featureId
    }
}
