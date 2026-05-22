package com.example.valuefinder

import com.google.gson.Gson
import com.google.gson.JsonParser
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assume.assumeTrue

@RunWith(AndroidJUnit4::class)
class ValuePicsBackupRestoreSmokeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun backupThenRestore_preservesPhotoRowsAndFiles() {
        runBlocking {
            // Safety: this test resets app data and should only run on CI emulators.
            assumeTrue(isEmulator())

        val repository = ValuePicsRepository(context)
        val db = ValuePicsDatabase.getDatabase(context)

        db.valuedItemDao().clearAllItems()
        val photosDir = PhotoUtils.getPhotosCollectionDir(context)
        PhotoUtils.ensureCollectionFolders(context)
        photosDir.listFiles().orEmpty().forEach { if (it.isFile) it.delete() }

        val stamp = System.currentTimeMillis()
        val coverFile = File(photosDir, "smoke_cover_$stamp.jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        }
        val extraFile = File(photosDir, "smoke_extra_$stamp.jpg").apply {
            writeBytes(byteArrayOf(9, 10, 11, 12, 13, 14, 15, 16))
        }

        val itemId = repository.insertItem(
            ValuedItem(
                photoPath = coverFile.absolutePath,
                itemName = "Backup Smoke Item",
                itemDescription = "Smoke test",
                detectedLabels = "",
                dateTaken = "2026-04-29 00:00:00"
            )
        ).toInt()
        repository.addPhotoToItem(itemId = itemId, photoPath = extraFile.absolutePath)

        val backupFile = File(context.cacheDir, "backup_restore_smoke_$stamp.zip")
        val backupUri = Uri.fromFile(backupFile)
        repository.backupToUri(backupUri)

        db.valuedItemDao().clearAllItems()

        repository.restoreFromUri(backupUri)

        val restoredItems = db.valuedItemDao().getAllItemsSnapshot(AppTier.PERSONAL.name)
        val restoredPhotoRows = db.itemPhotoDao().countAllRows()

        assertEquals(1, restoredItems.size)
        assertEquals(2, restoredPhotoRows)

        val restoredItemId = restoredItems.first().id
        val restoredPhotos = db.itemPhotoDao().getPhotosForItemSnapshot(restoredItemId)
        assertEquals(2, restoredPhotos.size)
        assertTrue(restoredPhotos.all { it.photoPath.isNotBlank() })
        assertTrue(restoredPhotos.all { File(it.photoPath).exists() })

            backupFile.delete()
        }
    }

    @Test
    fun restoreFromUri_allowsOlderBackupTimestamp() {
        runBlocking {
            // Safety: this test resets app data and should only run on CI emulators.
            assumeTrue(isEmulator())

        val repository = ValuePicsRepository(context)
        val db = ValuePicsDatabase.getDatabase(context)

        db.valuedItemDao().clearAllItems()
        val photosDir = PhotoUtils.getPhotosCollectionDir(context)
        PhotoUtils.ensureCollectionFolders(context)
        photosDir.listFiles().orEmpty().forEach { if (it.isFile) it.delete() }

        val stamp = System.currentTimeMillis()
        val coverFile = File(photosDir, "older_restore_cover_$stamp.jpg").apply {
            writeBytes(byteArrayOf(1, 3, 5, 7, 9))
        }

        repository.insertItem(
            ValuedItem(
                photoPath = coverFile.absolutePath,
                itemName = "Older Restore Smoke Item",
                itemDescription = "Older timestamp restore",
                detectedLabels = "",
                dateTaken = "2026-04-29 00:00:00"
            )
        )

        val backupFile = File(context.cacheDir, "backup_restore_older_$stamp.zip")
        val backupUri = Uri.fromFile(backupFile)
        repository.backupToUri(backupUri)

        // Simulate an intentionally older snapshot chosen by the user.
        rewriteBackupCreatedAtMillis(backupFile, forcedCreatedAtMillis = 1L)

        db.valuedItemDao().clearAllItems()
        repository.restoreFromUri(backupUri)

        val restoredItems = db.valuedItemDao().getAllItemsSnapshot(AppTier.PERSONAL.name)
        assertEquals(1, restoredItems.size)
        assertTrue(restoredItems.first().itemName.contains("Older Restore"))

            backupFile.delete()
        }
    }

    private fun rewriteBackupCreatedAtMillis(backupFile: File, forcedCreatedAtMillis: Long) {
        val tempFile = File(backupFile.parentFile, backupFile.nameWithoutExtension + "_tmp.zip")
        ZipInputStream(BufferedInputStream(FileInputStream(backupFile))).use { input ->
            ZipOutputStream(BufferedOutputStream(FileOutputStream(tempFile))).use { output ->
                var entry = input.nextEntry
                while (entry != null) {
                    val outEntry = ZipEntry(entry.name)
                    output.putNextEntry(outEntry)
                    if (!entry.isDirectory) {
                        if (entry.name == "data/items.json") {
                            val originalJson = input.readBytes().toString(Charsets.UTF_8)
                            val root = JsonParser.parseString(originalJson).asJsonObject
                            root.addProperty("createdAtMillis", forcedCreatedAtMillis)
                            output.write(Gson().toJson(root).toByteArray(Charsets.UTF_8))
                        } else {
                            input.copyTo(output)
                        }
                    }
                    output.closeEntry()
                    input.closeEntry()
                    entry = input.nextEntry
                }
            }
        }

        if (backupFile.exists()) backupFile.delete()
        tempFile.renameTo(backupFile)
    }

    private fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val product = Build.PRODUCT.lowercase()
        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("emulator") ||
            manufacturer.contains("genymotion") ||
            product.contains("sdk")
    }
}

