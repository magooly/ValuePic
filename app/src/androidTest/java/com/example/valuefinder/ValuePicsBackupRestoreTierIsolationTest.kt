package com.example.valuefinder

import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValuePicsBackupRestoreTierIsolationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun backupAndRestore_areTierIsolated_forPersonalAndInsurance() {
        runBlocking {
            // Safety: this test resets app data and should only run on CI emulators.
            assumeTrue(isEmulator())

            val repository = ValuePicsRepository(context)
            val db = ValuePicsDatabase.getDatabase(context)

            db.valuedItemDao().clearAllItems()

            val personalBaseName = "Tier Personal Base ${System.currentTimeMillis()}"
            val insuranceBaseName = "Tier Insurance Base ${System.currentTimeMillis()}"
            val personalAfterBackupName = "Tier Personal AfterBackup ${System.currentTimeMillis()}"
            val insuranceAfterBackupName = "Tier Insurance AfterBackup ${System.currentTimeMillis()}"

            repository.setCurrentTier(AppTier.PERSONAL)
            repository.insertItem(
                ValuedItem(
                    photoPath = "",
                    itemName = personalBaseName,
                    itemDescription = "personal base",
                    detectedLabels = "",
                    dateTaken = "2026-05-22 00:00:00"
                )
            )

            repository.setCurrentTier(AppTier.INSURANCE)
            repository.insertItem(
                ValuedItem(
                    photoPath = "",
                    itemName = insuranceBaseName,
                    itemDescription = "insurance base",
                    detectedLabels = "",
                    dateTaken = "2026-05-22 00:00:00"
                )
            )

            val stamp = System.currentTimeMillis()
            val personalBackupFile = File(context.cacheDir, "tier_personal_$stamp.zip")
            val insuranceBackupFile = File(context.cacheDir, "tier_insurance_$stamp.zip")

            repository.setCurrentTier(AppTier.PERSONAL)
            repository.backupToUri(Uri.fromFile(personalBackupFile))
            repository.setCurrentTier(AppTier.INSURANCE)
            repository.backupToUri(Uri.fromFile(insuranceBackupFile))

            val personalBackupNames = readBackupItemNames(personalBackupFile)
            val insuranceBackupNames = readBackupItemNames(insuranceBackupFile)

            assertTrue(personalBackupNames.contains(personalBaseName))
            assertTrue(!personalBackupNames.contains(insuranceBaseName))
            assertTrue(insuranceBackupNames.contains(insuranceBaseName))
            assertTrue(!insuranceBackupNames.contains(personalBaseName))

            repository.setCurrentTier(AppTier.PERSONAL)
            repository.insertItem(
                ValuedItem(
                    photoPath = "",
                    itemName = personalAfterBackupName,
                    itemDescription = "personal after backup",
                    detectedLabels = "",
                    dateTaken = "2026-05-22 00:00:00"
                )
            )

            repository.setCurrentTier(AppTier.INSURANCE)
            repository.insertItem(
                ValuedItem(
                    photoPath = "",
                    itemName = insuranceAfterBackupName,
                    itemDescription = "insurance after backup",
                    detectedLabels = "",
                    dateTaken = "2026-05-22 00:00:00"
                )
            )

            repository.setCurrentTier(AppTier.PERSONAL)
            repository.restoreFromUri(Uri.fromFile(personalBackupFile))

            val personalAfterPersonalRestore = db.valuedItemDao()
                .getAllItemsSnapshot(AppTier.PERSONAL.name)
                .map { it.itemName }
                .toSet()
            val insuranceAfterPersonalRestore = db.valuedItemDao()
                .getAllItemsSnapshot(AppTier.INSURANCE.name)
                .map { it.itemName }
                .toSet()

            assertEquals(setOf(personalBaseName), personalAfterPersonalRestore)
            assertTrue(insuranceAfterPersonalRestore.contains(insuranceBaseName))
            assertTrue(insuranceAfterPersonalRestore.contains(insuranceAfterBackupName))

            repository.setCurrentTier(AppTier.INSURANCE)
            repository.restoreFromUri(Uri.fromFile(insuranceBackupFile))

            val personalAfterInsuranceRestore = db.valuedItemDao()
                .getAllItemsSnapshot(AppTier.PERSONAL.name)
                .map { it.itemName }
                .toSet()
            val insuranceAfterInsuranceRestore = db.valuedItemDao()
                .getAllItemsSnapshot(AppTier.INSURANCE.name)
                .map { it.itemName }
                .toSet()

            assertEquals(setOf(personalBaseName), personalAfterInsuranceRestore)
            assertEquals(setOf(insuranceBaseName), insuranceAfterInsuranceRestore)

            if (personalBackupFile.exists()) personalBackupFile.delete()
            if (insuranceBackupFile.exists()) insuranceBackupFile.delete()
        }
    }

    private fun readBackupItemNames(backupZip: File): Set<String> {
        var json: String? = null
        ZipInputStream(BufferedInputStream(FileInputStream(backupZip))).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name == "data/items.json") {
                    json = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val payload = JsonParser.parseString(json ?: "{}").asJsonObject
        val items = payload.getAsJsonArray("items") ?: return emptySet()
        return items.mapNotNull { node ->
            node.asJsonObject.get("itemName")?.asString?.trim()?.takeIf { it.isNotBlank() }
        }.toSet()
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
