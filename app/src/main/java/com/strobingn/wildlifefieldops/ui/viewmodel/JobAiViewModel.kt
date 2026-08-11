package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.remote.AiService
import com.strobingn.wildlifefieldops.data.remote.EstimateDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobAiViewModel @Inject constructor(
    private val aiService: AiService,
    private val jobDao: JobDao
) : ViewModel() {

    val isConfigured: Boolean get() = aiService.isConfigured
    val providerLabel: String get() = aiService.providerLabel

    private val _summary = MutableStateFlow<String?>(null)
    val summary: StateFlow<String?> = _summary.asStateFlow()

    private val _summaryLoading = MutableStateFlow(false)
    val summaryLoading: StateFlow<Boolean> = _summaryLoading.asStateFlow()

    private val _estimateDraft = MutableStateFlow<EstimateDraft?>(null)
    val estimateDraft: StateFlow<EstimateDraft?> = _estimateDraft.asStateFlow()

    private val _estimateLoading = MutableStateFlow(false)
    val estimateLoading: StateFlow<Boolean> = _estimateLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun generateSummary(job: Job) {
        if (_summaryLoading.value) return
        _summaryLoading.value = true
        _message.value = null
        viewModelScope.launch {
            _summary.value = aiService.summarizeJob(job)
            _summaryLoading.value = false
        }
    }

    fun draftEstimate(job: Job) {
        if (_estimateLoading.value) return
        _estimateLoading.value = true
        _message.value = null
        viewModelScope.launch {
            val draft = aiService.draftEstimateFromJob(job)
            _estimateDraft.value = draft
            _estimateLoading.value = false
            _message.value = if (draft.fromAi) {
                "AI estimate draft ready — review before quoting."
            } else {
                "Offline/heuristic estimate draft — review carefully."
            }
        }
    }

    fun appendSummaryToNotes(job: Job) {
        val text = _summary.value?.trim().orEmpty()
        if (text.isBlank()) return
        viewModelScope.launch {
            val existing = jobDao.getById(job.id) ?: job
            val stamp = "\n\n--- AI summary ---\n$text"
            val notes = if (existing.notes.contains("--- AI summary ---")) {
                val idx = existing.notes.indexOf("--- AI summary ---")
                if (idx >= 0) existing.notes.substring(0, idx).trimEnd() + stamp
                else existing.notes + stamp
            } else {
                existing.notes + stamp
            }
            jobDao.insert(
                existing.copy(
                    notes = notes.trim(),
                    updatedAt = System.currentTimeMillis(),
                    isSynced = false
                )
            )
            _message.value = "Summary saved into job notes."
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun consumeEstimateDraft(): EstimateDraft? {
        val d = _estimateDraft.value
        _estimateDraft.value = null
        return d
    }
}
