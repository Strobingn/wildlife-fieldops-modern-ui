package com.strobingn.wildlifefieldops.ui.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** FileProvider content Uri for a freshly captured photo under filesDir/[subdir]. */
fun createCapturePhotoUri(context: Context, subdir: String, prefix: String): Uri {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val storageDir = File(context.filesDir, subdir).apply { mkdirs() }
    val file = File(storageDir, "${prefix}_$timeStamp.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
}
