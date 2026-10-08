package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.fieldops.CustomerFieldOpsStore
import com.strobingn.wildlifefieldops.ai.fieldops.CustomerMergeUndo
import com.strobingn.wildlifefieldops.ai.fieldops.CustomerMessage
import com.strobingn.wildlifefieldops.ai.fieldops.CustomerMessageDraft
import com.strobingn.wildlifefieldops.ai.fieldops.CustomerMessageKind
import com.strobingn.wildlifefieldops.ai.fieldops.DuplicateCustomer
import com.strobingn.wildlifefieldops.ai.fieldops.DuplicateMatch
import com.strobingn.wildlifefieldops.ai.fieldops.SeasonalDraft
import com.strobingn.wildlifefieldops.ai.fieldops.SeasonalReminder
import com.strobingn.wildlifefieldops.ai.fieldops.SeasonalSave
import com.strobingn.wildlifefieldops.ai.fieldops.WarrantyPlan
import com.strobingn.wildlifefieldops.data.local.CustomerDao
import com.strobingn.wildlifefieldops.data.local.InventoryItemDao
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.model.Customer
import com.strobingn.wildlifefieldops.data.model.InventoryItem
import com.strobingn.wildlifefieldops.data.model.Job
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CustomerFieldOpsViewModel @Inject constructor(
    private val store: CustomerFieldOpsStore,
    private val inventoryItemDao: InventoryItemDao,
    private val customerDao: CustomerDao,
    private val jobDao: JobDao
) : ViewModel() {

    val inventory: StateFlow<List<InventoryItem>> = inventoryItemDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val customers: StateFlow<List<Customer>> = customerDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val jobs: StateFlow<List<Job>> = jobDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _duplicates = MutableStateFlow<List<DuplicateMatch>>(emptyList())
    val duplicates: StateFlow<List<DuplicateMatch>> = _duplicates

    private val _warranties = MutableStateFlow<List<Pair<Job, WarrantyPlan>>>(emptyList())
    val warranties: StateFlow<List<Pair<Job, WarrantyPlan>>> = _warranties

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _mergeUndo = MutableStateFlow<CustomerMergeUndo?>(null)
    val mergeUndo: StateFlow<CustomerMergeUndo?> = _mergeUndo.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        _duplicates.value = DuplicateCustomer.findPairs(customerDao.getAllOnce())
        _warranties.value = store.expiringWarranties()
    }

    fun deductMaterial(jobId: String, itemId: String, qty: Double) = viewModelScope.launch {
        _message.value = store.deductMaterial(jobId, itemId, qty)
        refresh()
    }

    fun updateMaterial(jobId: String, usageId: String, qty: Double) = viewModelScope.launch {
        _message.value = store.updateMaterial(jobId, usageId, qty)
        refresh()
    }

    fun removeMaterial(jobId: String, usageId: String) = viewModelScope.launch {
        _message.value = store.removeMaterial(jobId, usageId)
        refresh()
    }

    fun saveWarranty(jobId: String, startAt: Long, termMonths: Int, covered: String) = viewModelScope.launch {
        store.saveWarranty(jobId, startAt, termMonths, covered)
        _message.value = "Warranty saved and reminder set."
        refresh()
    }

    fun suggestSeasonal(job: Job): SeasonalDraft =
        SeasonalReminder.suggest(job.confirmedSpecies.ifBlank { job.type })

    fun saveSeasonal(job: Job, save: SeasonalSave) = viewModelScope.launch {
        store.saveSeasonal(job, save)
        _message.value = "Seasonal reminder saved."
        refresh()
    }

    fun draftMessage(job: Job, kind: CustomerMessageKind): CustomerMessage =
        CustomerMessageDraft.draft(
            kind = kind,
            customerName = job.customerName,
            jobTitle = job.title,
            address = job.address,
            amount = job.estimatedValue.takeIf { it > 0 },
            appointment = com.strobingn.wildlifefieldops.ai.fieldops.ScheduledInspections.appointmentText(job.scheduledDate)
        )

    fun mergeCustomers(keepId: String, dropId: String) = viewModelScope.launch {
        val undo = store.mergeCustomers(keepId, dropId)
        if (undo == null) {
            _message.value = "Pick two different customers."
            return@launch
        }
        _mergeUndo.value = undo
        _message.value = "Customers merged. Undo is available until you leave this screen."
        refresh()
    }

    fun undoMerge() = viewModelScope.launch {
        val snapshot = _mergeUndo.value ?: return@launch
        store.undoMerge(snapshot)
        _mergeUndo.value = null
        _message.value = "Merge undone. Both customers are active again."
        refresh()
    }

    fun clearMessage() {
        _message.value = null
    }
}
