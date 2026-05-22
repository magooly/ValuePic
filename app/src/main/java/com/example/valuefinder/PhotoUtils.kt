package com.example.valuefinder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import com.example.valuefinder.util.TimeUtils

object PhotoUtils {
    enum class CropHandleInsetPreset(val prefValue: String, val insetDp: Float) {
        SMALL("small", 12f),
        MEDIUM("medium", 20f),
        LARGE("large", 28f);

        companion object {
            fun fromPrefValue(value: String?): CropHandleInsetPreset {
                return entries.firstOrNull { it.prefValue == value } ?: LARGE
            }
        }
    }

    private const val COLLECTIONS_ROOT = "ValuePicsCollections"
    private const val PHOTOS_SUBFOLDER = "photos"
    private const val DATA_SUBFOLDER = "data"
    private const val PREFS_NAME = "valuepics_settings"
    private const val PREF_KEY_PHOTO_TARGET_KB = "photo_target_kb"
    private const val PREF_KEY_CROP_HANDLE_INSET = "crop_handle_inset"
    const val PHOTO_SIZE_SMALL_KB = 200
    const val PHOTO_SIZE_BALANCED_KB = 300
    const val PHOTO_SIZE_HIGH_KB = 500
    const val MIN_TARGET_SIZE_KB = 150
    const val MAX_TARGET_SIZE_KB = 1000
    private const val DEFAULT_TARGET_SIZE_KB = PHOTO_SIZE_BALANCED_KB
    private const val DEFAULT_MAX_DIMENSION = 1600

    fun getPhotosCollectionDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
        return File(base, "$COLLECTIONS_ROOT/$PHOTOS_SUBFOLDER")
    }

    fun getDataCollectionDir(context: Context): File =
        File(context.filesDir, "$COLLECTIONS_ROOT/$DATA_SUBFOLDER")

    fun ensureCollectionFolders(context: Context): Boolean {
        val photosDir = getPhotosCollectionDir(context)
        val dataDir = getDataCollectionDir(context)
        val photosCreated = photosDir.mkdirs() || photosDir.exists()
        val dataCreated = dataDir.mkdirs() || dataDir.exists()
        if (!photosCreated || !dataCreated) {
            Log.w("PhotoUtils", "Failed to create collection folders: photos=$photosCreated, data=$dataCreated")
        }
        return photosCreated && dataCreated
    }

    fun getPhotoTargetSizeKb(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_KEY_PHOTO_TARGET_KB, DEFAULT_TARGET_SIZE_KB)
            .coerceIn(MIN_TARGET_SIZE_KB, MAX_TARGET_SIZE_KB)

    fun setPhotoTargetSizeKb(context: Context, targetKb: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_KEY_PHOTO_TARGET_KB, targetKb.coerceIn(MIN_TARGET_SIZE_KB, MAX_TARGET_SIZE_KB))
            .apply()
    }

    fun getCropHandleInsetPreset(context: Context): CropHandleInsetPreset {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_KEY_CROP_HANDLE_INSET, CropHandleInsetPreset.LARGE.prefValue)
        return CropHandleInsetPreset.fromPrefValue(raw)
    }

    fun setCropHandleInsetPreset(context: Context, preset: CropHandleInsetPreset) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY_CROP_HANDLE_INSET, preset.prefValue)
            .apply()
    }

    fun createImageFile(context: Context): File {
        val storageDir = getPhotosCollectionDir(context).also { it.mkdirs() }
        var candidate: File
        do { candidate = File(storageDir, buildUniquePhotoFileName()) } while (candidate.exists())
        return candidate
    }

    private fun buildUniquePhotoFileName(): String {
        return "IMG_${TimeUtils.getCurrentTimestampWithMs()}.jpg"
    }

    fun saveBitmap(context: Context, bitmap: Bitmap): String =
        createImageFile(context).also { writeOptimizedBitmapToFile(context, bitmap, it) }.absolutePath

    fun getPhotoUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun decodeBitmap(filePath: String): Bitmap? = runCatching { BitmapFactory.decodeFile(filePath) }.getOrNull()

    fun copyUriToImageFile(context: Context, uri: Uri): String? {
        return try {
            val outFile = createImageFile(context)
            val input: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            input.use { inp ->
                val decoded = BitmapFactory.decodeStream(inp) ?: return null
                try {
                    val oriented = applyExifOrientation(context, uri, decoded)
                    try {
                        writeOptimizedBitmapToFile(context, oriented, outFile)
                    } finally {
                        if (oriented !== decoded) oriented.recycle()
                    }
                } finally {
                    decoded.recycle()
                }
            }
            outFile.absolutePath
        } catch (e: Exception) {
            Log.w("PhotoUtils", "Failed to copy URI to image file", e)
            null
        }
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return applyExifOrientationWithMatrix(orientation, bitmap)
    }

    private fun applyExifOrientationWithMatrix(orientation: Int, bitmap: Bitmap): Bitmap {
        val matrix = matrixForExifOrientation(orientation) ?: return bitmap
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrDefault(bitmap)
    }

    private fun matrixForExifOrientation(orientation: Int): Matrix? = when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_180      -> Matrix().apply { postRotate(180f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL   -> Matrix().apply { postScale(1f, -1f) }
        ExifInterface.ORIENTATION_TRANSPOSE       -> Matrix().apply { postRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90       -> Matrix().apply { postRotate(90f) }
        ExifInterface.ORIENTATION_TRANSVERSE      -> Matrix().apply { postRotate(270f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_270      -> Matrix().apply { postRotate(270f) }
        else -> null
    }

    fun optimizeExistingImageFile(context: Context, photoPath: String): Boolean {
        return try {
            val source = BitmapFactory.decodeFile(photoPath) ?: return false
            try {
                val oriented = applyExifOrientationFromFile(photoPath, source)
                try {
                    writeOptimizedBitmapToFile(context, oriented, File(photoPath))
                } finally {
                    if (oriented !== source) oriented.recycle()
                }
            } finally {
                source.recycle()
            }
            true
        } catch (e: Exception) {
            Log.w("PhotoUtils", "Failed to optimize existing image file", e)
            false
        }
    }

    private fun applyExifOrientationFromFile(photoPath: String, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(photoPath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return applyExifOrientationWithMatrix(orientation, bitmap)
    }

    private fun writeOptimizedBitmapToFile(context: Context, bitmap: Bitmap, outFile: File) {
        val resized = resizeToMaxDimension(bitmap, DEFAULT_MAX_DIMENSION)
        val bytes = compressToTargetSize(resized, getPhotoTargetSizeKb(context) * 1024)
        FileOutputStream(outFile).use { it.write(bytes) }
        if (resized !== bitmap) resized.recycle()
    }

    private fun resizeToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun compressToTargetSize(bitmap: Bitmap, targetBytes: Int): ByteArray {
        var working = bitmap
        var best = compressJpeg(working, 90)
        var attempts = 0

        while (best.size > targetBytes && attempts < 12) {
            var quality = 85
            var candidate = best
            while (quality >= 5) {
                candidate = compressJpeg(working, quality)
                if (candidate.size <= targetBytes) break
                quality -= 5
            }
            best = candidate
            if (best.size <= targetBytes) break

            if (working.width <= 1 && working.height <= 1) {
                break
            }

            val scaled = Bitmap.createScaledBitmap(
                working,
                (working.width * 0.85f).toInt().coerceAtLeast(1),
                (working.height * 0.85f).toInt().coerceAtLeast(1),
                true
            )
            if (scaled.width == working.width && scaled.height == working.height) {
                scaled.recycle()
                break
            }
            if (working !== bitmap) working.recycle()
            working = scaled
            attempts += 1
        }

        if (working !== bitmap) working.recycle()
        return best
    }

    private fun compressJpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    fun getCurrentDateTime(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

    fun formatDateForDisplay(dateString: String): String = try {
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(dateString)
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date ?: Date())
    } catch (_: Exception) { dateString }

    fun cropPhotoFile(
        context: Context,
        photoPath: String,
        leftRatio: Float,
        topRatio: Float,
        rightRatio: Float,
        bottomRatio: Float
    ): Boolean {
        return runCatching {
            val sourceFile = File(photoPath)
            if (!sourceFile.exists()) return false
            val source = BitmapFactory.decodeFile(photoPath) ?: return false
            val oriented = applyExifOrientationFromFile(photoPath, source)
            val width = oriented.width
            val height = oriented.height

            val x = (width * leftRatio).roundToInt().coerceIn(0, width - 1)
            val y = (height * topRatio).roundToInt().coerceIn(0, height - 1)
            val cropWidth = (width * (1f - leftRatio - rightRatio)).roundToInt().coerceAtLeast(1)
            val cropHeight = (height * (1f - topRatio - bottomRatio)).roundToInt().coerceAtLeast(1)

            val boundedWidth = cropWidth.coerceAtMost(width - x)
            val boundedHeight = cropHeight.coerceAtMost(height - y)
            val cropped = Bitmap.createBitmap(oriented, x, y, boundedWidth, boundedHeight)

            // Write atomically and keep output aligned with selected target photo size.
            val tempFile = File(sourceFile.parentFile, "${sourceFile.name}.crop.tmp")
            runCatching {
                writeOptimizedBitmapToFile(context, cropped, tempFile)
            }.onFailure {
                if (tempFile.exists()) tempFile.delete()
                cropped.recycle()
                if (oriented !== source) oriented.recycle()
                source.recycle()
                return false
            }
            if (!tempFile.exists() || tempFile.length() <= 0L) {
                if (tempFile.exists()) tempFile.delete()
                cropped.recycle()
                if (oriented !== source) oriented.recycle()
                source.recycle()
                return false
            }

            if (!sourceFile.delete()) {
                tempFile.delete()
                cropped.recycle()
                if (oriented !== source) oriented.recycle()
                source.recycle()
                return false
            }
            if (!tempFile.renameTo(sourceFile)) {
                tempFile.delete()
                cropped.recycle()
                if (oriented !== source) oriented.recycle()
                source.recycle()
                return false
            }

            cropped.recycle()
            if (oriented !== source) oriented.recycle()
            source.recycle()
            true
        }.getOrDefault(false)
    }

    fun deletePhotoFile(photoPath: String): Boolean = runCatching { File(photoPath).delete() }.getOrDefault(false)

    /**
     * Saves a copy of the file at [photoPath] to the device's public Pictures gallery
     * under a "ValuePics" album, named ValuePics_YYYYMMDD_HHmmss.jpg.
     *
     * On Android 10+ (API 29) no extra permission is required.
     * On Android 9 (API 28) WRITE_EXTERNAL_STORAGE is needed (declared in manifest).
     *
     * @return true on success, false on any error.
     */
    fun saveToGallery(context: Context, photoPath: String): Boolean {
        return runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val displayName = "ValuePics_$stamp.jpg"
            val mimeType = "image/jpeg"
            val sourceFile = File(photoPath)
            if (!sourceFile.exists()) return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ — MediaStore insert, no permission needed
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/ValuePics")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val collection = MediaStore.Images.Media.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val itemUri = resolver.insert(collection, values) ?: return false
                resolver.openOutputStream(itemUri)?.use { out ->
                    sourceFile.inputStream().use { inp -> inp.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
            } else {
                // Android 9 and below — write directly to public Pictures folder
                val picturesDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES)
                val albumDir = File(picturesDir, "ValuePics").also { it.mkdirs() }
                val destFile = File(albumDir, displayName)
                sourceFile.inputStream().use { inp ->
                    FileOutputStream(destFile).use { out -> inp.copyTo(out) }
                }
                // Notify MediaStore so the image appears in Gallery immediately
                @Suppress("DEPRECATION")
                android.media.MediaScannerConnection.scanFile(
                    context, arrayOf(destFile.absolutePath), arrayOf(mimeType), null)
            }
            true
        }.getOrElse { e ->
            Log.w("PhotoUtils", "saveToGallery failed", e)
            false
        }
    }
}
