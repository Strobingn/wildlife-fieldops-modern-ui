package com.strobingn.wildlifefieldops.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.strobingn.wildlifefieldops.data.model.Photo
import com.strobingn.wildlifefieldops.ui.theme.BackgroundCard
import com.strobingn.wildlifefieldops.ui.theme.BorderDark
import com.strobingn.wildlifefieldops.ui.theme.OnPrimary
import com.strobingn.wildlifefieldops.ui.theme.PrimaryGreen
import com.strobingn.wildlifefieldops.ui.theme.TextPrimary
import com.strobingn.wildlifefieldops.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Photos attached to this estimate. Inspection stills show up here, and Sir
 * can still take, import, or remove photos on the estimate itself.
 */
@Composable
fun EstimatePhotoStrip(
    photos: List<Photo>,
    onPhotoFiles: (List<File>) -> Unit,
    onRemove: (Photo) -> Unit,
    onCopyFailed: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }

    fun newPhotoFile(): File {
        val dir = File(context.filesDir, "photos").apply { mkdirs() }
        return File(dir, "EST_${System.currentTimeMillis()}_${(0..999).random()}.jpg")
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val path = pendingPhotoPath
        pendingPhotoPath = null
        if (path != null) {
            val file = File(path)
            if (saved && file.isFile && file.length() > 0) onPhotoFiles(listOf(file))
            else file.delete()
        }
    }
    fun startCamera() {
        val file = newPhotoFile()
        pendingPhotoPath = file.absolutePath
        takePictureLauncher.launch(
            FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val copied = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        val file = newPhotoFile()
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { input.copyTo(it) }
                        } ?: return@runCatching null
                        file.takeIf { it.length() > 0 }
                    }.getOrNull()
                }
            }
            if (copied.isNotEmpty()) onPhotoFiles(copied)
            if (copied.size < uris.size) onCopyFailed(uris.size - copied.size)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = BackgroundCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Photos (${photos.size})",
                color = TextPrimary,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Inspection photos attach here and print on the estimate PDF. Add or remove photos on this estimate without changing the inspection.",
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        ) startCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen, contentColor = OnPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Take photo")
                }
                OutlinedButton(
                    onClick = { photoPicker.launch("image/*") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryGreen),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("From gallery")
                }
            }
            if (photos.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    photos.forEach { photo ->
                        val model = photo.localPath.takeIf { it.isNotBlank() } ?: photo.filePath
                        Box(Modifier.size(96.dp)) {
                            AsyncImage(
                                model = if (File(model).isFile) File(model) else model,
                                contentDescription = "Estimate photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(BorderDark, RoundedCornerShape(10.dp))
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            IconButton(
                                onClick = { onRemove(photo) },
                                modifier = Modifier.align(Alignment.TopEnd).size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove photo from estimate",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                        .padding(3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
