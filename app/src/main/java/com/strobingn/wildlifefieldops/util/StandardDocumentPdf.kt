package com.strobingn.wildlifefieldops.util

import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * The only PdfDocument writer for customer documents. Share, view, print,
 * and email/text all send the file this object just wrote.
 */
object StandardDocumentPdf {
    fun write(
        context: Context,
        document: StandardDocument,
        fileName: String,
        cache: Boolean = false,
        qr: Bitmap? = null,
        customerSignature: Bitmap? = null,
        companySignature: Bitmap? = null
    ): String {
        val layout = document.layout()
        val logo = BusinessProfileStore.loadDocumentLogo(context, document.profile)
        val images = mapOf(
            "customer" to (customerSignature ?: SignatureInk.decodePng(document.customerSignatureBase64)),
            "company" to (companySignature ?: SignatureInk.decodePng(document.companySignatureBase64)),
            "qr" to qr
        )
        val pdf = PdfDocument()
        layout.pages.forEachIndexed { index, page ->
            val pdfPage = pdf.startPage(PdfDocument.PageInfo.Builder(page.width, page.height, index + 1).create())
            paint(pdfPage.canvas, page, logo)
            images.forEach { (tag, bitmap) ->
                if (bitmap != null) drawTagged(pdfPage.canvas, page, tag, bitmap)
            }
            pdf.finishPage(pdfPage)
        }
        val dir = if (cache) {
            context.cacheDir
        } else {
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        }
        val file = File(dir, fileName)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file.absolutePath
    }

    fun share(context: Context, path: String, chooserTitle: String = "Share PDF") {
        val uri = uriFor(context, path)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(chooserTitle, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun view(context: Context, path: String) {
        val uri = uriFor(context, path)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            clipData = ClipData.newRawUri("document", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun print(context: Context, path: String, jobName: String) {
        val activity = context.findActivity() ?: context
        val manager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        manager.print(jobName, PdfFilePrintAdapter(File(path)), null)
    }

    fun emailWithPdf(
        context: Context,
        path: String,
        email: String,
        subject: String,
        body: String,
        chooserTitle: String
    ) {
        val uri = uriFor(context, path)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(chooserTitle, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun textWithPdf(context: Context, path: String, body: String, chooserTitle: String) {
        val uri = uriFor(context, path)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_TEXT, body)
            putExtra("sms_body", body)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(chooserTitle, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun uriFor(context: Context, path: String): Uri {
        val file = File(path)
        return FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    }

    private fun paint(canvas: Canvas, page: PageLayout, logo: Bitmap?) {
        canvas.drawColor(Color.WHITE)
        val anti = Paint(Paint.ANTI_ALIAS_FLAG)
        page.ops.forEach { op ->
            when (op.kind) {
                "text" -> {
                    val paint = Paint(anti).apply {
                        textSize = op.size
                        color = argb(op.color)
                        typeface = when {
                            op.italic -> Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                            op.bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                            else -> Typeface.DEFAULT
                        }
                        textAlign = when (op.align) {
                            "center" -> Paint.Align.CENTER
                            "right" -> Paint.Align.RIGHT
                            else -> Paint.Align.LEFT
                        }
                    }
                    canvas.drawText(op.text, op.x, op.y, paint)
                }
                "line" -> {
                    val paint = Paint(anti).apply {
                        color = argb(op.color)
                        strokeWidth = op.size.coerceAtLeast(0.6f)
                        style = Paint.Style.STROKE
                    }
                    canvas.drawLine(op.x, op.y, op.x2, op.y2, paint)
                }
                "rect" -> {
                    val paint = Paint(anti).apply {
                        color = argb(op.color)
                        style = Paint.Style.STROKE
                        strokeWidth = 1.2f
                    }
                    canvas.drawRect(op.x, op.y, op.x2, op.y2, paint)
                }
                "fillrect" -> {
                    val paint = Paint(anti).apply {
                        color = argb(op.color)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(op.x, op.y, op.x2, op.y2, paint)
                }
                "logo" -> {
                    val bitmap = logo ?: return@forEach
                    val size = op.x2.toInt().coerceAtLeast(1)
                    val scaled = if (bitmap.width == size && bitmap.height == size) bitmap
                    else Bitmap.createScaledBitmap(bitmap, size, size, true)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(scaled, op.x, op.y, paint)
                    if (scaled !== bitmap) scaled.recycle()
                }
            }
        }
    }

    private fun drawTagged(canvas: Canvas, page: PageLayout, tag: String, bitmap: Bitmap) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        page.ops.filter { it.kind == "image" && it.tag == tag }.forEach { op ->
            val w = op.x2.toInt().coerceAtLeast(1)
            val h = op.y2.toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
            canvas.drawBitmap(scaled, op.x, op.y, paint)
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    private fun argb(token: String): Int {
        val rgb = DocumentPalette.rgb(DocumentPalette.tokenHex(token))
        return Color.rgb(rgb[0], rgb[1], rgb[2])
    }

    private fun Context.findActivity(): android.app.Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is android.app.Activity) return current
            current = current.baseContext
        }
        return null
    }
}

private class PdfFilePrintAdapter(private val file: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback
    ) {
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: Exception) {
            callback.onWriteFailed(error.message)
        }
    }
}
