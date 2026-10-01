package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.JobCustomerDraft
import com.strobingn.wildlifefieldops.data.model.JobPriority
import com.strobingn.wildlifefieldops.data.workspace.JobCustomerWorkspace
import com.strobingn.wildlifefieldops.data.workspace.JobSaveRequest
import com.strobingn.wildlifefieldops.data.workspace.JobSaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JobWorkspaceViewModel @Inject constructor(
    private val workspace: JobCustomerWorkspace
) : ViewModel() {

    private val _draft = MutableStateFlow(JobCustomerDraft())
    val draft: StateFlow<JobCustomerDraft> = _draft.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _matches = MutableStateFlow<List<Customer>>(emptyList())
    val matches: StateFlow<List<Customer>> = _matches.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun updateDraft(next: JobCustomerDraft) {
        _draft.value = next
    }

    fun updateDraft(transform: (JobCustomerDraft) -> JobCustomerDraft) {
        _draft.value = transform(_draft.value)
    }

    fun startNewCustomer() {
        _draft.value = JobCustomerDraft()
        _searchQuery.value = ""
        _matches.value = emptyList()
    }

    fun applyCustomer(customer: Customer) {
        _draft.value = JobCustomerDraft.fromCustomer(customer)
        _searchQuery.value = ""
        _matches.value = emptyList()
    }

    fun loadForJob(job: Job?) = viewModelScope.launch {
        _draft.value = workspace.loadDraft(job)
        _searchQuery.value = ""
        _matches.value = emptyList()
    }

    fun searchCustomers(query: String) {
        _searchQuery.value = query
        viewModelScope.launch {
            _matches.value = workspace.searchCustomers(query)
        }
    }

    fun saveJob(
        existingJob: Job?,
        title: String,
        description: String,
        type: String,
        priority: JobPriority,
        estimatedValue: Double,
        notes: String,
        appointmentTimes: List<Long>,
        actualCost: Double? = null,
        confirmedSpecies: String? = null,
        legalNotes: String? = null,
        nextStep: String? = null,
        nextStepDueAt: Long? = null,
        onSaved: (JobSaveResult) -> Unit
    ) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                val result = workspace.save(
                    JobSaveRequest(
                        existingJob = existingJob,
                        title = title,
                        description = description,
                        type = type,
                        priority = priority,
                        estimatedValue = estimatedValue,
                        notes = notes,
                        appointmentTimes = appointmentTimes,
                        actualCost = actualCost,
                        customer = _draft.value,
                        confirmedSpecies = confirmedSpecies,
                        legalNotes = legalNotes,
                        nextStep = nextStep,
                        nextStepDueAt = nextStepDueAt
                    )
                )
                onSaved(result)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun saveCustomerOnJob(job: Job, onSaved: (JobSaveResult) -> Unit = {}) {
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            try {
                val result = workspace.saveCustomerOnJob(job, _draft.value)
                _draft.value = result.customer?.let { JobCustomerDraft.fromCustomer(it) }
                    ?: _draft.value
                onSaved(result)
            } finally {
                _isSaving.value = false
            }
        }
    }
}
