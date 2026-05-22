package com.example.valuefinder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import android.Manifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object DemoGallerySeeder {
    private const val TAG = "DemoGallerySeeder"
    private const val DEMO_FOLDER = "Pictures/ValuePicsDemo"
    
    // Demo image color constants
    private const val DEMO_WATCH_ACCENT = 0xFF6200EE.toInt()  // Purple
    private const val DEMO_CAMERA_ACCENT = 0xFF007B6B.toInt() // Teal
    
    private val demoImages = listOf(
        DemoImageSpec(
            fileName = "valuefinder-demo-watch.jpg",
            title = "Vintage Watch Demo",
            subtitle = "Expected labels: watch, jewelry, vintage item",
            accent = DEMO_WATCH_ACCENT,
            kind = DemoKind.WATCH,
        ),
        DemoImageSpec(
            fileName = "valuefinder-demo-camera.jpg",
            title = "Camera Demo",
            subtitle = "Expected labels: camera, electronics, device",
            accent = DEMO_CAMERA_ACCENT,
            kind = DemoKind.CAMERA,
        ),
    )

    suspend fun seedDemoImages(context: Context): DemoSeedResult = withContext(Dispatchers.IO) {
        // Check permissions before attempting to write
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: WRITE_EXTERNAL_STORAGE is not needed; we use scoped storage
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "WRITE_EXTERNAL_STORAGE permission not granted; cannot seed demo images")
            return@withContext DemoSeedResult(inserted = emptyList(), skipped = demoImages.map { it.fileName })
        }
        
        val inserted = mutableListOf<String>()
        val skipped = mutableListOf<String>()

        demoImages.forEach { spec ->
            if (demoExists(context, spec.fileName)) {
                skipped += spec.fileName
            } else {
                val success = insertDemoImage(context, spec)
                if (success) inserted += spec.fileName else skipped += spec.fileName
            }
        }

        DemoSeedResult(inserted = inserted, skipped = skipped)
    }

    private fun demoExists(context: Context, fileName: String): Boolean {
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(fileName)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            return cursor.moveToFirst()
        }
        return false
    }

    private fun insertDemoImage(context: Context, spec: DemoImageSpec): Boolean {
        val bitmap = createDemoBitmap(spec)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, spec.fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, DEMO_FOLDER)
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return false

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }

                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "ValuePicsDemo",
                )
                dir.mkdirs()
                val file = File(dir, spec.fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                true
            }
        } catch (_: Exception) {
            false
        } finally {
            bitmap.recycle()
        }
    }

    private fun createDemoBitmap(spec: DemoImageSpec): Bitmap {
        val width = 1200
        val height = 900
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(245, 247, 252))

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(210, 216, 226) }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 70, 70)
            style = Paint.Style.STROKE
            strokeWidth = 18f
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.accent
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = spec.accent
            textSize = 56f
            isFakeBoldText = true
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 70, 70)
            textSize = 32f
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 90, 90)
            textSize = 24f
        }

        when (spec.kind) {
            DemoKind.WATCH -> {
                val face = RectF(350f, 180f, 850f, 680f)
                canvas.drawOval(face, fillPaint)
                canvas.drawOval(face, strokePaint)
                canvas.drawRect(560f, 90f, 640f, 180f, accentPaint)
                canvas.drawRect(560f, 680f, 640f, 790f, accentPaint)
                canvas.drawLine(600f, 430f, 600f, 290f, strokePaint)
                canvas.drawLine(600f, 430f, 720f, 430f, strokePaint)
            }
            DemoKind.CAMERA -> {
                val body = RectF(280f, 240f, 920f, 620f)
                canvas.drawRoundRect(body, 36f, 36f, fillPaint)
                canvas.drawRoundRect(body, 36f, 36f, strokePaint)
                canvas.drawRect(380f, 170f, 540f, 250f, accentPaint)
                canvas.drawCircle(600f, 430f, 150f, accentPaint)
                canvas.drawCircle(600f, 430f, 110f, fillPaint)
                canvas.drawCircle(820f, 320f, 28f, accentPaint)
            }
        }

        canvas.drawText(spec.title, 220f, 90f, textPaint)
        canvas.drawText("ValuePics demo gallery image", 240f, 760f, bodyPaint)
        canvas.drawText(spec.subtitle, 180f, 810f, smallPaint)

        return bitmap
    }
}

data class DemoSeedResult(
    val inserted: List<String>,
    val skipped: List<String>,
)

private data class DemoImageSpec(
    val fileName: String,
    val title: String,
    val subtitle: String,
    val accent: Int,
    val kind: DemoKind,
)

private enum class DemoKind {
    WATCH,
    CAMERA,
}

