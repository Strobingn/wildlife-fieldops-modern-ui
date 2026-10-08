package com.strobingn.wildlifefieldops.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.strobingn.wildlifefieldops.data.local.PhotoDao
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PhotosViewModel @Inject constructor(
    private val photoDao: PhotoDao
) : ViewModel() {

    val photos = photoDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun savePhoto(photo: Photo) = viewModelScope.launch {
        photoDao.insert(photo)
    }

    fun deletePhoto(photo: Photo) = viewModelScope.launch {
        photoDao.delete(photo)
        if (photo.localPath.isNotBlank()) {
            withContext(Dispatchers.IO) {
                runCatching { File(photo.localPath).takeIf { it.isFile }?.delete() }
            }
        }
    }
}

/**
 * Whether [photo] belongs on a gallery tab. [tab] null is the "All" tab. The Inspections
 * tab also takes any photo attached to an inspection, even one with no job and a
 * different category, so inspection photos are never hidden from the gallery.
 */
fun photoMatchesGalleryTab(photo: Photo, tab: PhotoCategory?): Boolean = when (tab) {
    null -> true
    PhotoCategory.INSPECTION -> photo.category == PhotoCategory.INSPECTION || !photo.inspectionId.isNullOrBlank()
    else -> photo.category == tab
}
