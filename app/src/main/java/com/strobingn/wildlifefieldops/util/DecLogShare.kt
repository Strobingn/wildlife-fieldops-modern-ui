package com.strobingn.wildlifefieldops.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object DecLogShare {
    fun shareCsv(context: Context, csv: String, fileName: String = "ny-dec-nuisance-log.csv") {
        shareFile(context, File(context.cacheDir, fileName).also { it.writeText(csv) }, "text/csv", "NY DEC nuisance wildlife log")
    }

    fun sharePdf(context: Context, file: File, subject: String) {
        shareFile(context, file, "application/pdf", subject)
    }

    fun shareFile(context: Context, file: File, mime: String, subject: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    }
}
