package com.example.valuefinder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class PhotoUtilsExifInstrumentationTest {

    private data class Marker(
        val name: String,
        val x: Int,
        val y: Int,
        val color: Int
    )

    private data class OrientationCase(
        val name: String,
        val orientation: Int,
        val swapsDimensions: Boolean
    )

    private val allOrientationCases = listOf(
        OrientationCase("normal", ExifInterface.ORIENTATION_NORMAL, false),
        OrientationCase("rotate_90", ExifInterface.ORIENTATION_ROTATE_90, true),
        OrientationCase("rotate_180", ExifInterface.ORIENTATION_ROTATE_180, false),
        OrientationCase("rotate_270", ExifInterface.ORIENTATION_ROTATE_270, true),
        OrientationCase("flip_horizontal", ExifInterface.ORIENTATION_FLIP_HORIZONTAL, false),
        OrientationCase("flip_vertical", ExifInterface.ORIENTATION_FLIP_VERTICAL, false),
        OrientationCase("transpose", ExifInterface.ORIENTATION_TRANSPOSE, true),
        OrientationCase("transverse", ExifInterface.ORIENTATION_TRANSVERSE, true)
    )

    private val createdFiles = mutableListOf<File>()

    @After
    fun cleanupTempFiles() {
        createdFiles.forEach { file ->
            runCatching { file.delete() }
        }
        createdFiles.clear()
    }

    private val markerMargin = 10

    private fun buildSourceMarkers(width: Int, height: Int): List<Marker> {
        return listOf(
            Marker("top_left", markerMargin, markerMargin, Color.RED),
            Marker("top_right", width - 1 - markerMargin, markerMargin, Color.GREEN),
            Marker("bottom_left", markerMargin, height - 1 - markerMargin, Color.BLUE),
            Marker("bottom_right", width - 1 - markerMargin, height - 1 - markerMargin, Color.YELLOW)
        )
    }

    @Test fun gallery_normal() = runGalleryCase("normal")
    @Test fun gallery_rotate_90() = runGalleryCase("rotate_90")
    @Test fun gallery_rotate_180() = runGalleryCase("rotate_180")
    @Test fun gallery_rotate_270() = runGalleryCase("rotate_270")
    @Test fun gallery_flip_horizontal() = runGalleryCase("flip_horizontal")
    @Test fun gallery_flip_vertical() = runGalleryCase("flip_vertical")
    @Test fun gallery_transpose() = runGalleryCase("transpose")
    @Test fun gallery_transverse() = runGalleryCase("transverse")

    @Test fun camera_normal() = runCameraCase("normal")
    @Test fun camera_rotate_90() = runCameraCase("rotate_90")
    @Test fun camera_rotate_180() = runCameraCase("rotate_180")
    @Test fun camera_rotate_270() = runCameraCase("rotate_270")
    @Test fun camera_flip_horizontal() = runCameraCase("flip_horizontal")
    @Test fun camera_flip_vertical() = runCameraCase("flip_vertical")
    @Test fun camera_transpose() = runCameraCase("transpose")
    @Test fun camera_transverse() = runCameraCase("transverse")

    private fun runGalleryCase(caseName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val case = orientationCase(caseName)
        val sourceWidth = 120
        val sourceHeight = 80
        val source = createExifTaggedJpeg(context, sourceWidth, sourceHeight, case.orientation)
        var importedBitmap: Bitmap? = null
        try {
            val importedPath = PhotoUtils.copyUriToImageFile(context, Uri.fromFile(source))
            assertNotNull("Imported path is null for ${case.name}", importedPath)
            val importedFile = File(importedPath!!)
            createdFiles += importedFile

            importedBitmap = BitmapFactory.decodeFile(importedPath)
            assertNotNull("Imported bitmap is null for ${case.name}", importedBitmap)

            assertBitmapMatchesCase(
                bitmap = importedBitmap,
                case = case,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                labelPrefix = "gallery"
            )
        } finally {
            importedBitmap?.recycle()
        }
    }

    private fun runCameraCase(caseName: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val case = orientationCase(caseName)
        val sourceWidth = 150
        val sourceHeight = 90
        val source = createExifTaggedJpeg(context, sourceWidth, sourceHeight, case.orientation)
        var optimizedBitmap: Bitmap? = null
        try {
            val optimized = PhotoUtils.optimizeExistingImageFile(context, source.absolutePath)
            assertTrue("Optimization failed for ${case.name}", optimized)

            optimizedBitmap = BitmapFactory.decodeFile(source.absolutePath)
            assertNotNull("Optimized bitmap is null for ${case.name}", optimizedBitmap)

            assertBitmapMatchesCase(
                bitmap = optimizedBitmap,
                case = case,
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                labelPrefix = "camera"
            )
        } finally {
            optimizedBitmap?.recycle()
        }
    }

    private fun assertBitmapMatchesCase(
        bitmap: Bitmap?,
        case: OrientationCase,
        sourceWidth: Int,
        sourceHeight: Int,
        labelPrefix: String
    ) {
        val checkedBitmap = bitmap!!
        val expectedWidth = if (case.swapsDimensions) sourceHeight else sourceWidth
        val expectedHeight = if (case.swapsDimensions) sourceWidth else sourceHeight
        assertEquals("Width mismatch for ${case.name}", expectedWidth, checkedBitmap.width)
        assertEquals("Height mismatch for ${case.name}", expectedHeight, checkedBitmap.height)

        val sourceMarkers = buildSourceMarkers(sourceWidth, sourceHeight)
        sourceMarkers.forEach { marker ->
            val expected = mapPointForOrientation(
                x = marker.x,
                y = marker.y,
                width = sourceWidth,
                height = sourceHeight,
                orientation = case.orientation
            )
            assertColorNear(
                bitmap = checkedBitmap,
                x = expected.first,
                y = expected.second,
                expectedColor = marker.color,
                label = "$labelPrefix ${case.name}/${marker.name}"
            )
        }
    }

    private fun orientationCase(name: String): OrientationCase {
        return allOrientationCases.first { it.name == name }
    }

    private fun createExifTaggedJpeg(
        context: android.content.Context,
        width: Int,
        height: Int,
        orientation: Int
    ): File {
        val file = File.createTempFile("exif_test_", ".jpg", context.cacheDir)
        createdFiles += file

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLACK)
        buildSourceMarkers(width, height).forEach { marker ->
            drawMarker(bitmap, marker)
        }
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
        }
        bitmap.recycle()

        val exif = ExifInterface(file.absolutePath)
        exif.setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        exif.saveAttributes()

        return file
    }

    private fun mapPointForOrientation(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        orientation: Int
    ): Pair<Int, Int> {
        return when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> Pair(x, y)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Pair(width - 1 - x, y)
            ExifInterface.ORIENTATION_ROTATE_180 -> Pair(width - 1 - x, height - 1 - y)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> Pair(x, height - 1 - y)
            ExifInterface.ORIENTATION_TRANSPOSE -> Pair(y, x)
            ExifInterface.ORIENTATION_ROTATE_90 -> Pair(height - 1 - y, x)
            ExifInterface.ORIENTATION_TRANSVERSE -> Pair(height - 1 - y, width - 1 - x)
            ExifInterface.ORIENTATION_ROTATE_270 -> Pair(y, width - 1 - x)
            else -> Pair(x, y)
        }
    }

    private fun drawMarker(bitmap: Bitmap, marker: Marker) {
        val half = 3
        val startX = (marker.x - half).coerceAtLeast(0)
        val endX = (marker.x + half).coerceAtMost(bitmap.width - 1)
        val startY = (marker.y - half).coerceAtLeast(0)
        val endY = (marker.y + half).coerceAtMost(bitmap.height - 1)
        for (px in startX..endX) {
            for (py in startY..endY) {
                bitmap.setPixel(px, py, marker.color)
            }
        }
    }

    private fun assertColorNear(bitmap: Bitmap, x: Int, y: Int, expectedColor: Int, label: String) {
        val radius = 4
        var bestDistance = Int.MAX_VALUE
        val minX = (x - radius).coerceAtLeast(0)
        val maxX = (x + radius).coerceAtMost(bitmap.width - 1)
        val minY = (y - radius).coerceAtLeast(0)
        val maxY = (y + radius).coerceAtMost(bitmap.height - 1)

        for (px in minX..maxX) {
            for (py in minY..maxY) {
                val actual = bitmap.getPixel(px, py)
                val distance = colorDistance(actual, expectedColor)
                if (distance < bestDistance) {
                    bestDistance = distance
                }
            }
        }

        assertTrue("$label color mismatch (distance=$bestDistance)", bestDistance <= 120)
    }

    private fun colorDistance(a: Int, b: Int): Int {
        val dr = abs(Color.red(a) - Color.red(b))
        val dg = abs(Color.green(a) - Color.green(b))
        val db = abs(Color.blue(a) - Color.blue(b))
        return dr + dg + db
    }
}

