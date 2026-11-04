package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.review.GoogleReviewPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GoogleReviewViewModel @Inject constructor(
    private val settings: GoogleReviewSettings
) : ViewModel() {
    val prefs: StateFlow<GoogleReviewPrefs> = settings.prefs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        GoogleReviewPrefs()
    )

    fun setLink(value: String) {
        viewModelScope.launch { settings.setLink(value) }
    }

    fun setTemplate(value: String) {
        viewModelScope.launch { settings.setTemplate(value) }
    }

    fun markSent(jobId: String, at: Long) {
        viewModelScope.launch { settings.markSent(jobId, at) }
    }
}
