package com.strobingn.wildlifefieldops.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument

/**
 * Extra letter pages after the Whisperer estimate. The money layout is unchanged;
 * inspection stills print here so the customer copy includes them.
 */
object EstimatePhotoPdf {
    const val PER_PAGE = 4
    const val PAGE_W = 612
    const val PAGE_H = 792
    private const val MARGIN = 48f
    private const val TITLE = "INSPECTION PHOTOS"

    fun pageCount(photoCount: Int): Int =
        if (photoCount <= 0) 0 else (photoCount + PER_PAGE - 1) / PER_PAGE

    /** Stills that can be drawn. Missing files and non-images are skipped. */
    fun readable(paths: List<String>): Int = paths.count { path ->
        val bitmap = decode(path) ?: return@count false
        if (!bitmap.isRecycled) bitmap.recycle()
        true
    }

    fun append(pdf: PdfDocument, paths: List<String>, firstPageNumber: Int): Int {
        val bitmaps = paths.mapNotNull { decode(it) }
        if (bitmaps.isEmpty()) return 0
        var added = 0
        bitmaps.chunked(PER_PAGE).forEach { chunk ->
            val info = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, firstPageNumber + added).create()
            val page = pdf.startPage(info)
            paint(page.canvas, chunk, added + 1, pageCount(bitmaps.size))
            pdf.finishPage(page)
            chunk.forEach { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
            added++
        }
        return added
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

    private fun decode(path: String): Bitmap? {
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
        return BitmapFactory.decodeFile(path, opts)
    }
}
