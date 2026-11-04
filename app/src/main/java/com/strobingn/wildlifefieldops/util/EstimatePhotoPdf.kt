package com.strobingn.wildlifefieldops.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface

/**
 * Extra letter pages after a Whisperer document. The money / report layout is
 * unchanged; inspection stills print here so the customer copy includes them.
 * Bitmaps are decoded and recycled one page at a time.
 */
object EstimatePhotoPdf {
    const val PER_PAGE = 4
    const val PAGE_W = 612
    const val PAGE_H = 792
    private const val MARGIN = 48f
    const val TITLE = "INSPECTION PHOTOS"

    fun pageCount(photoCount: Int): Int =
        if (photoCount <= 0) 0 else (photoCount + PER_PAGE - 1) / PER_PAGE

    /** Paths grouped the way [append] decodes them: at most [PER_PAGE] at once. */
    fun pageBatches(paths: List<String>): List<List<String>> = paths.chunked(PER_PAGE)

    /** Stills that can be drawn. Missing files and non-images are skipped. */
    fun readable(paths: List<String>): Int = paths.count { path ->
        val bitmap = decodeOriented(path) ?: return@count false
        if (!bitmap.isRecycled) bitmap.recycle()
        true
    }

    fun append(pdf: PdfDocument, paths: List<String>, firstPageNumber: Int): Int {
        val readablePaths = paths.filter { hasImageBounds(it) }
        if (readablePaths.isEmpty()) return 0
        val sheets = pageCount(readablePaths.size)
        var added = 0
        pageBatches(readablePaths).forEachIndexed { index, batch ->
            val bitmaps = batch.mapNotNull { decodeOriented(it) }
            try {
                if (bitmaps.isEmpty()) return@forEachIndexed
                val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, firstPageNumber + added).create()
                val page = pdf.startPage(info)
                paint(page.canvas, bitmaps, index + 1, sheets)
                pdf.finishPage(page)
                added++
            } finally {
                bitmaps.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
            }
        }
        return added
    }

    internal fun decodeOriented(path: String): Bitmap? {
        if (path.isBlank()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 1400 || bounds.outHeight / sample > 1400) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
        return orient(path, decoded)
    }

    internal fun orient(path: String, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.preScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.preScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (turned != bitmap && !bitmap.isRecycled) bitmap.recycle()
        return turned
    }

    private fun hasImageBounds(path: String): Boolean {
        if (path.isBlank()) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    private fun paint(canvas: Canvas, photos: List<Bitmap>, sheet: Int, sheets: Int) {
        canvas.drawColor(Color.WHITE)
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 14f
            isFakeBoldText = true
        }
        canvas.drawText(TITLE, MARGIN, MARGIN + 4f, title)
        val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(58, 58, 58)
            textSize = 9f
        }
        canvas.drawText("Page $sheet of $sheets", PAGE_W - MARGIN - 70f, MARGIN + 2f, caption)

        val top = MARGIN + 28f
        val gap = 12f
        val cellW = (PAGE_W - MARGIN * 2 - gap) / 2f
        val cellH = (PAGE_H - top - MARGIN - gap) / 2f
        photos.forEachIndexed { index, bitmap ->
            val col = index % 2
            val row = index / 2
            val left = MARGIN + col * (cellW + gap)
            val cellTop = top + row * (cellH + gap)
            val frame = RectF(left, cellTop, left + cellW, cellTop + cellH - 16f)
            val dest = fit(bitmap, frame)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bitmap, null, dest, paint)
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(58, 58, 58)
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRect(dest, border)
        }
    }

    private fun fit(bitmap: Bitmap, frame: RectF): RectF {
        val scale = minOf(frame.width() / bitmap.width.coerceAtLeast(1), frame.height() / bitmap.height.coerceAtLeast(1))
        val w = bitmap.width * scale
        val h = bitmap.height * scale
        val left = frame.left + (frame.width() - w) / 2f
        val top = frame.top + (frame.height() - h) / 2f
        return RectF(left, top, left + w, top + h)
    }
}
