package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.fieldops.ChecklistItemRecord
import com.strobingn.wildlifefieldops.ai.fieldops.PhotoAutoTags
import com.strobingn.wildlifefieldops.ai.fieldops.PhotoPairRecord
import com.strobingn.wildlifefieldops.ai.fieldops.SearchFieldOpsStore
import com.strobingn.wildlifefieldops.ai.fieldops.SearchHit
import com.strobingn.wildlifefieldops.ai.fieldops.ShareableReport
import com.strobingn.wildlifefieldops.ai.fieldops.SpeciesChecklist
import com.strobingn.wildlifefieldops.ai.fieldops.SyncedPhotoTag
import com.strobingn.wildlifefieldops.data.local.JobDao
import com.strobingn.wildlifefieldops.data.local.PhotoDao
import com.strobingn.wildlifefieldops.data.model.Job
import com.strobingn.wildlifefieldops.data.model.Photo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SearchFieldOpsViewModel @Inject constructor(
    private val store: SearchFieldOpsStore,
    private val jobDao: JobDao,
    private val photoDao: PhotoDao
) : ViewModel() {

    val jobs: StateFlow<List<Job>> = jobDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photos: StateFlow<List<Photo>> = photoDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _hits = MutableStateFlow<List<SearchHit>>(emptyList())
    val hits: StateFlow<List<SearchHit>> = _hits

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun search(raw: String) {
        _query.value = raw
        viewModelScope.launch {
            _hits.value = store.search(raw)
        }
    }

    fun suggestTags(text: String): SyncedPhotoTag = PhotoAutoTags.suggest(text)

    fun savePhotoTag(photo: Photo, tag: SyncedPhotoTag) = viewModelScope.launch {
        store.savePhotoTag(photo, PhotoAutoTags.keepTyped(tag.copy(photoId = photo.id)))
        _message.value = "Photo tags saved."
    }

    fun savePair(jobId: String, pair: PhotoPairRecord) = viewModelScope.launch {
        store.savePair(jobId, pair)
        _message.value = "Before / after pair saved."
    }

    fun deletePair(jobId: String, pairId: String) = viewModelScope.launch {
        store.deletePair(jobId, pairId)
        _message.value = "Pair removed."
    }

    fun applyChecklist(job: Job, species: String = job.confirmedSpecies.ifBlank { job.type }) = viewModelScope.launch {
        store.applyChecklist(job.id, species)
        _message.value = "Checklist for ${SpeciesChecklist.displayName(species)} loaded."
    }

    fun saveChecklist(jobId: String, items: List<ChecklistItemRecord>) = viewModelScope.launch {
        store.saveChecklist(jobId, items)
    }

    fun saveShareReport(jobId: String, path: String) = viewModelScope.launch {
        store.saveShareReport(jobId, path, ShareableReport.payload(jobId))
        _message.value = "Report saved. Scan the QR on this phone to reopen it."
    }

    fun reportPayload(jobId: String): String = ShareableReport.payload(jobId)

    fun clearMessage() {
        _message.value = null
    }
}
