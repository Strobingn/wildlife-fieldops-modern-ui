package com.strobingn.wildlifefieldops.util

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream


@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class EstimatePhotoPdfTest {
    @Test
    fun photoSheetsFollowTheInspectionStillsThatDecode() {
        assertEquals(0, EstimatePhotoPdf.pageCount(0))
        assertEquals(1, EstimatePhotoPdf.pageCount(4))
        assertEquals(2, EstimatePhotoPdf.pageCount(5))

        val context = RuntimeEnvironment.getApplication()
        val photo = File(context.cacheDir, "inspection-still.jpg")
        val bitmap = Bitmap.createBitmap(40, 30, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.DKGRAY)
        FileOutputStream(photo).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()

        val readable = EstimatePhotoPdf.readable(listOf(photo.absolutePath, "/missing/inspection.jpg"))
        assertEquals(1, readable)
        assertEquals(1, EstimatePhotoPdf.pageCount(readable))
        val batches = EstimatePhotoPdf.pageBatches(List(5) { photo.absolutePath })
        assertEquals(listOf(4, 1), batches.map { it.size })
        assertEquals(EstimatePhotoPdf.TITLE, "INSPECTION PHOTOS")
    }

    @Test
    fun exifRotate90TurnsLandscapePixelsUpright() {
        val context = RuntimeEnvironment.getApplication()
        val photo = File(context.cacheDir, "exif-rotate-90.jpg")
        val landscape = Bitmap.createBitmap(80, 20, Bitmap.Config.ARGB_8888)
        landscape.eraseColor(Color.DKGRAY)
        FileOutputStream(photo).use { landscape.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        landscape.recycle()
        ExifInterface(photo.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val decoded = EstimatePhotoPdf.decodeOriented(photo.absolutePath)
        assertTrue(decoded != null)
        assertTrue(decoded!!.height > decoded.width)
        decoded.recycle()
    }
}
