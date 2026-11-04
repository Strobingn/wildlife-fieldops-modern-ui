package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.ai.detection.DetectionCorrectionMemory
import com.strobingn.wildlifefieldops.ai.detection.FeedbackDraft
import com.strobingn.wildlifefieldops.ai.detection.FrameAverageHashEncoder
import com.strobingn.wildlifefieldops.data.detection.DetectionFeedbackRepository
import com.strobingn.wildlifefieldops.data.local.PhotoDao
import com.strobingn.wildlifefieldops.data.model.Photo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val photoDao: PhotoDao,
    private val detectionFeedback: DetectionFeedbackRepository,
) : ViewModel() {

    val photos = photoDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun savePhoto(photo: Photo) = viewModelScope.launch {
        photoDao.insert(photo)
        runCatching {
            detectionFeedback.recordUnlabeled(
                photoId = photo.id,
                jobId = photo.jobId.orEmpty(),
                photoLocalPath = photo.localPath.ifBlank { photo.filePath },
                modelLabel = "",
                modelConfidence = 0.0,
            )
        }
    }

    fun saveDetectionFeedback(
        photo: Photo,
        draft: FeedbackDraft,
        modelLabel: String,
        modelConfidence: Double,
        onSaved: (String) -> Unit,
    ) = viewModelScope.launch {
        val message = detectionFeedback.saveFeedback(
            draft = draft,
            jobId = photo.jobId.orEmpty(),
            photoId = photo.id,
            photoLocalPath = photo.localPath.ifBlank { photo.filePath },
            photoStoragePath = "",
            modelLabel = modelLabel,
            modelConfidence = modelConfidence,
        )
        onSaved(message)
    }

    fun prepareSavedPhotoFeedback(photo: Photo, onReady: (String, Double, String, Boolean) -> Unit) =
        viewModelScope.launch {
            val latest = detectionFeedback.latestForPhoto(photo.id)
            val modelLabel = latest?.modelLabel.orEmpty()
            val confidence = latest?.modelConfidence ?: 0.0
            val embedding = FrameAverageHashEncoder.fromFile(photo.localPath.ifBlank { photo.filePath })
            val preference = DetectionCorrectionMemory.prefer(
                modelLabel = modelLabel,
                embedding = embedding,
                examples = detectionFeedback.corrections(),
            )
            onReady(
                modelLabel,
                confidence,
                preference?.label.orEmpty(),
                preference?.learned == true,
            )
        }

    fun deletePhoto(photo: Photo) = viewModelScope.launch {
        photoDao.delete(photo)
    }
}
