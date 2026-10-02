package com.strobingn.wildlifefieldops.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.strobingn.wildlifefieldops.ai.fieldops.ManualField
import com.strobingn.wildlifefieldops.ai.fieldops.OperatorWins
import com.strobingn.wildlifefieldops.ai.fieldops.PhotoAutoTags
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.data.model.PhotoCategory
import com.strobingn.wildlifefieldops.ui.components.*
import com.strobingn.wildlifefieldops.ui.theme.*
import com.strobingn.wildlifefieldops.ui.viewmodel.PhotosViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotoGalleryScreen(
    onBack: () -> Unit,
    viewModel: PhotosViewModel = hiltViewModel(),
    searchVm: com.strobingn.wildlifefieldops.ui.viewmodel.SearchFieldOpsViewModel = hiltViewModel()
) {
    val photos by viewModel.photos.collectAsState()
    val jobs by searchVm.jobs.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) }
    var tagFilter by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Photo?>(null) }
    val tabs = listOf("All" to null, "Inspections" to PhotoCategory.INSPECTION, "Jobs" to PhotoCategory.JOB_SITE,
        "Evidence" to PhotoCategory.EVIDENCE, "Docs" to PhotoCategory.DOCUMENT)

    // Camera launcher
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            val uri = tempPhotoUri!!
            val file = File(uri.path ?: "")
            val photo = Photo(
                filePath = uri.toString(),
                localPath = file.absolutePath,
                category = PhotoCategory.JOB_SITE,
                description = "Taken ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())}",
                takenAt = System.currentTimeMillis()
            )
            viewModel.savePhoto(photo)
        }
    }

    val filteredPhotos = photos.filter { photo ->
        val categoryOk = selectedTab == 0 || photo.category == tabs[selectedTab].second
        val jobTags = jobs.firstOrNull { it.id == photo.jobId }?.pricing?.photoAutoTags.orEmpty()
            .firstOrNull { it.photoId == photo.id }
        val tagHay = listOf(photo.description, jobTags?.asDescription().orEmpty()).joinToString(" ")
        val tagOk = tagFilter.isBlank() || tagHay.contains(tagFilter, ignoreCase = true)
        categoryOk && tagOk
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo Gallery (${photos.size})", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val uri = createImageUri(context)
                    tempPhotoUri = uri
                    cameraLauncher.launch(uri)
                },
                containerColor = PrimaryGreen,
                contentColor = OnPrimary
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Take Photo")
            }
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = BackgroundDark,
                contentColor = PrimaryGreen,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = PrimaryGreen,
                            height = 3.dp
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, (title, _) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, color = if (selectedTab == index) PrimaryGreen else TextSecondary) }
                    )
                }
            }

            OutlinedTextField(
                value = tagFilter,
                onValueChange = { tagFilter = it },
                label = { Text("Filter tags (species, damage, entry)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            if (photos.isEmpty()) {
                EmptyState(
                    icon = {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = "No photos yet",
                    subtitle = "Tap the camera button to take your first photo",
                    modifier = Modifier.fillMaxSize()
                )
            } else if (filteredPhotos.isEmpty()) {
                EmptyState(
                    icon = {
                        Icon(
                            Icons.Default.Filter,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(36.dp)
                        )
                    },
                    title = "No photos in this category",
                    subtitle = "Try a different filter or take a new photo",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPhotos, key = { it.id }) { photo ->
                        FadeSlideIn {
                            PhotoGridItem(
                                photo = photo,
                                onDelete = { viewModel.deletePhoto(photo) },
                                onClick = { editing = photo }
                            )
                        }
                    }
                }
            }
        }
    }
    editing?.let { photo ->
        val jobPricing = jobs.firstOrNull { it.id == photo.jobId }?.pricing
        PhotoTagDialog(
            photo = photo,
            jobPhotos = photos.filter { it.jobId == photo.jobId && it.id != photo.id },
            existing = jobPricing?.photoAutoTags?.firstOrNull { it.photoId == photo.id },
            pairs = jobPricing?.photoPairs?.filter { it.beforeId == photo.id || it.afterId == photo.id }.orEmpty(),
            onDismiss = { editing = null },
            onSaveTags = { tag ->
                searchVm.savePhotoTag(photo, tag)
                editing = null
            },
            onPair = { otherId, notes, tag ->
                val jobId = photo.jobId.orEmpty()
                if (jobId.isNotBlank()) {
                    searchVm.savePhotoTag(photo, tag)
                    searchVm.savePair(
                        jobId,
                        com.strobingn.wildlifefieldops.ai.fieldops.BeforeAfterPair.pair(photo.id, otherId, notes)
                    )
                }
                editing = null
            },
            onUpdatePair = { pair ->
                val jobId = photo.jobId.orEmpty()
                if (jobId.isNotBlank()) searchVm.savePair(jobId, pair)
            },
            onDeletePair = { pairId ->
                val jobId = photo.jobId.orEmpty()
                if (jobId.isNotBlank()) searchVm.deletePair(jobId, pairId)
            },
            suggest = { searchVm.suggestTags(it) }
        )
    }
}

@Composable
private fun PhotoTagDialog(
    photo: Photo,
    jobPhotos: List<Photo>,
    existing: com.strobingn.wildlifefieldops.ai.fieldops.SyncedPhotoTag?,
    pairs: List<com.strobingn.wildlifefieldops.ai.fieldops.PhotoPairRecord>,
    onDismiss: () -> Unit,
    onSaveTags: (com.strobingn.wildlifefieldops.ai.fieldops.SyncedPhotoTag) -> Unit,
    onPair: (String, String, com.strobingn.wildlifefieldops.ai.fieldops.SyncedPhotoTag) -> Unit,
    onUpdatePair: (com.strobingn.wildlifefieldops.ai.fieldops.PhotoPairRecord) -> Unit,
    onDeletePair: (String) -> Unit,
    suggest: (String) -> com.strobingn.wildlifefieldops.ai.fieldops.SyncedPhotoTag
) {
    val seed = existing ?: com.strobingn.wildlifefieldops.ai.fieldops.SyncedPhotoTag(photoId = photo.id)
    var species by remember { mutableStateOf(seed.species) }
    var damage by remember { mutableStateOf(seed.damage) }
    var entry by remember { mutableStateOf(seed.entry) }
    var extra by remember { mutableStateOf(seed.extra) }
    var speciesManual by remember {
        mutableStateOf(ManualField.PHOTO_SPECIES in seed.clearedKeys || seed.species.isNotBlank())
    }
    var damageManual by remember {
        mutableStateOf(ManualField.PHOTO_DAMAGE in seed.clearedKeys || seed.damage.isNotBlank())
    }
    var entryManual by remember {
        mutableStateOf(ManualField.PHOTO_ENTRY in seed.clearedKeys || seed.entry.isNotBlank())
    }
    var extraManual by remember {
        mutableStateOf(ManualField.PHOTO_EXTRA in seed.clearedKeys || seed.extra.isNotBlank())
    }
    var speciesPreview by remember { mutableStateOf<String?>(null) }
    var damagePreview by remember { mutableStateOf<String?>(null) }
    var entryPreview by remember { mutableStateOf<String?>(null) }
    var extraPreview by remember { mutableStateOf<String?>(null) }
    var pairNotes by remember { mutableStateOf("") }
    var pairId by remember { mutableStateOf(jobPhotos.firstOrNull()?.id.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Photo tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val ai = suggest(listOf(photo.description, species, damage, entry).joinToString(" "))
                    speciesPreview = if (species.isBlank() && !speciesManual) null else OperatorWins.preview(species, ai.species, true)
                    damagePreview = if (damage.isBlank() && !damageManual) null else OperatorWins.preview(damage, ai.damage, true)
                    entryPreview = if (entry.isBlank() && !entryManual) null else OperatorWins.preview(entry, ai.entry, true)
                    extraPreview = if (extra.isBlank() && !extraManual) null else OperatorWins.preview(extra, ai.extra, true)
                    if (species.isBlank() && !speciesManual) species = ai.species
                    if (damage.isBlank() && !damageManual) damage = ai.damage
                    if (entry.isBlank() && !entryManual) entry = ai.entry
                    if (extra.isBlank() && !extraManual) extra = ai.extra
                }) { Text("Suggest from photo notes") }
                ApplySuggestionChip(speciesPreview) { species = it; speciesManual = true; speciesPreview = null }
                ApplySuggestionChip(damagePreview) { damage = it; damageManual = true; damagePreview = null }
                ApplySuggestionChip(entryPreview) { entry = it; entryManual = true; entryPreview = null }
                ApplySuggestionChip(extraPreview) { extra = it; extraManual = true; extraPreview = null }
                OutlinedTextField(value = species, onValueChange = { species = it; speciesManual = true }, label = { Text("Species") })
                OutlinedTextField(value = damage, onValueChange = { damage = it; damageManual = true }, label = { Text("Damage") })
                OutlinedTextField(value = entry, onValueChange = { entry = it; entryManual = true }, label = { Text("Entry") })
                OutlinedTextField(value = extra, onValueChange = { extra = it; extraManual = true }, label = { Text("Extra") })
                if (jobPhotos.isNotEmpty()) {
                    Text("Pair as before → after", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(value = pairId, onValueChange = { pairId = it }, label = { Text("After photo id") })
                    jobPhotos.take(6).forEach { other ->
                        TextButton(onClick = { pairId = other.id }) {
                            Text(other.description.ifBlank { other.id.take(8) })
                        }
                    }
                    OutlinedTextField(value = pairNotes, onValueChange = { pairNotes = it }, label = { Text("Pair notes") })
                }
                pairs.forEach { pair ->
                    key(pair.id) {
                        var notes by remember(pair.notes) { mutableStateOf(pair.notes) }
                        Text("Saved pair ${pair.beforeId.take(6)} → ${pair.afterId.take(6)}", style = MaterialTheme.typography.labelSmall)
                        OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Edit pair notes") })
                        Row {
                            TextButton(onClick = { onUpdatePair(pair.copy(notes = notes.trim())) }) { Text("Save pair") }
                            TextButton(onClick = { onDeletePair(pair.id) }) { Text("Remove pair") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSaveTags(
                    PhotoAutoTags.persistTyped(
                        photoId = photo.id,
                        species = species,
                        damage = damage,
                        entry = entry,
                        extra = extra,
                        previous = existing
                    )
                )
            }) { Text("Save tags") }
        },
        dismissButton = {
            Row {
                if (pairId.isNotBlank() && photo.jobId != null) {
                    TextButton(onClick = {
                        onPair(
                            pairId,
                            pairNotes,
                            PhotoAutoTags.persistTyped(
                                photoId = photo.id,
                                species = species,
                                damage = damage,
                                entry = entry,
                                extra = extra,
                                previous = existing
                            )
                        )
                    }) { Text("Pair") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )
}

@Composable
private fun PhotoGridItem(photo: Photo, onDelete: () -> Unit, onClick: () -> Unit = {}) {
    val categoryColor = when (photo.category) {
        PhotoCategory.INSPECTION -> AccentBlue
        PhotoCategory.JOB_SITE -> PrimaryGreen
        PhotoCategory.DAMAGE -> ErrorRed
        PhotoCategory.REPAIR -> StatusPending
        PhotoCategory.WILDLIFE -> AccentPurple
        PhotoCategory.EVIDENCE -> AccentOrange
        PhotoCategory.BEFORE -> StatusPending
        PhotoCategory.AFTER -> SuccessGreen
        PhotoCategory.DOCUMENT -> TextSecondary
        PhotoCategory.SIGNATURE -> AccentCyan
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Actual photo or placeholder
            if (photo.localPath.isNotBlank() && File(photo.localPath).exists()) {
                AsyncImage(
                    model = File(photo.localPath),
                    contentDescription = photo.description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (photo.filePath.isNotBlank()) {
                AsyncImage(
                    model = photo.filePath,
                    contentDescription = photo.description,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BackgroundElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Photo,
                        contentDescription = null,
                        tint = TextTertiary.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Category badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(categoryColor.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    photo.category.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = FontWeight.Medium
                )
            }

            // Delete button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ErrorRed.copy(alpha = 0.8f))
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Info overlay
            if (photo.description.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(
                            photo.description,
                            color = androidx.compose.ui.graphics.Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                                .format(Date(photo.takenAt)),
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun createImageUri(context: Context): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "IMG_${timeStamp}.jpg"
    val storageDir = File(context.filesDir, "photos").apply { mkdirs() }
    val file = File(storageDir, fileName)
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
}
