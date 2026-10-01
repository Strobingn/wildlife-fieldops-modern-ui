package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object DecLogShare {
    fun shareCsv(context: Context, csv: String, fileName: String = "ny-dec-nuisance-log.csv") {
        val file = File(context.cacheDir, fileName)
        file.writeText(csv)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "NY DEC nuisance wildlife log")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share DEC log"))
    }
}
