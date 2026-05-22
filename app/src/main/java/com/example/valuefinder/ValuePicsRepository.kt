package com.example.valuefinder

import android.content.Context
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Represents progress during PDF export operations
 */
data class ExportProgress(
    val current: Int,
    val total: Int,
    val phase: String,
    val percentage: Int = if (total > 0) (current * 100) / total else 0
)

data class BackupSummary(
    val itemCount: Int,
    val photoCount: Int,
    val dedupeCount: Int = 0,
    val isVerified: Boolean = true
)

data class AutoBackupSlotInfo(
    val slotId: String,
    val filePath: String,
    val exists: Boolean,
    val fileSizeBytes: Long,
    val lastModifiedMillis: Long,
    val createdAtMillis: Long?,
    val itemCount: Int?,
    val photoCount: Int?,
    val isRestorable: Boolean,
    val isLatest: Boolean,
    val warningMessage: String?,
    val corruptionMessage: String?
)

data class PhotoCleanupSummary(
    val removedCount: Int,
    val removedBytes: Long
)

data class PdfExportResult(
    val fileName: String,
    val location: String,
    val itemCount: Int,
    val scopeLabel: String,
    val isFiltered: Boolean,
    val contentUriString: String? = null,
    val absolutePath: String? = null
)

data class DatabaseShareAttachment(
    val fileName: String,
    val contentUriString: String,
    val sizeBytes: Long
)

data class UnlockResult(
    val unlocked: Boolean,
    val recoveryCode: String?
)

/** Normalises a list of collection names: trims, removes blanks, deduplicates (case-insensitive), sorts. */
private fun List<String>.normalizeCollections(): List<String> =
    map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase(Locale.getDefault()) }
        .sortedBy { it.lowercase(Locale.getDefault()) }

data class ValuationDraft(
    val schemaVersion: Int = 1,  // For future schema evolution
    val photoPath: String,
    val photoSource: String,
    val itemName: String,
    val itemDescription: String,
    val itemTags: String,
    val editableValue: String,
    val userEditedValue: Boolean,
    val selectedCollection: String,
    val doNotIncludeInTotals: Boolean = false,
    val willInstructions: String = "",
    val detailedLookupMode: Boolean,
    val savedAtMillis: Long = System.currentTimeMillis()
)


private data class BackupPayload(
    val version: Int = 1,
    val createdAtMillis: Long = 0L,
    val appVersionName: String? = "",
    val items: List<BackupItemRecord>? = emptyList(),
    val itemPhotos: List<BackupItemPhotoRecord>? = emptyList(),
    val itemAudioNotes: List<BackupItemAudioNoteRecord>? = emptyList(),
    val manualCollections: List<String>? = emptyList(),
    val manualTags: List<String>? = emptyList(),
    val unlimitedUnlocked: Boolean = false,
    val unlockAccountId: String? = ""
)

private data class BackupItemAudioNoteRecord(
    val itemId: Int = 0,
    val audioFileName: String? = null
)

private data class BackupItemPhotoRecord(
    val itemId: Int = 0,
    val photoFileName: String? = null,
    val sortOrder: Int = 0,
    val isCover: Boolean = false,
    val createdAtMillis: Long = 0L
)

private data class BackupItemRecord(
    val id: Int = 0,
    val photoFileName: String? = null,
    val photoSource: String? = null,
    val itemName: String? = null,
    val collectionName: String? = null,
    val shortAiDescription: String? = null,
    val fullWebDescription: String? = null,
    val itemDescription: String? = null,
    val detectedLabels: String? = null,
    val estimatedValue: Double? = null,
    val currency: String? = null,
    val valueSource: String? = null,
    val sourceUrl: String? = null,
    val searchResults: String? = null,
    val confidence: Float = 0f,
    val createdAtMillis: Long? = null,
    val dateTaken: String? = null,
    val dateValued: Long = 0L,
    val willInstructions: String? = null,
    val notes: String? = null,
    val tags: String? = null,
    val billsEnteredPeriod: String? = null,
    val includeInTotals: Boolean = true
) {
    fun toValuedItem(resolvedPhotoPath: String): ValuedItem {
        val safeItemName = itemName ?: ""
        val safeDateTaken = dateTaken ?: ""
        val safePhotoSource = photoSource ?: "camera"
        val safeCollectionName = collectionName ?: ""
        val safeShortAiDescription = shortAiDescription ?: ""
        val safeFullWebDescription = fullWebDescription ?: ""
        val safeItemDescription = itemDescription ?: ""
        val safeDetectedLabels = detectedLabels ?: ""
        val safeCurrency = currency ?: "AUD"
        val safeValueSource = valueSource ?: ""
        val safeSourceUrl = sourceUrl ?: ""
        val safeSearchResults = searchResults ?: ""
        val safeWillInstructions = willInstructions ?: ""
        val safeNotes = notes ?: ""
        val safeTags = tags ?: ""
        val safeBillsEnteredPeriod = billsEnteredPeriod ?: ""
        
        // Validate required fields — use safe defaults rather than throwing, so a single
        // bad record never aborts (and wipes) an entire restore.
        if (safeItemName.isBlank()) {
            android.util.Log.w("BackupItemRecord", "Item id=$id has a blank name; skipping strict validation")
        }
        
        return ValuedItem(
            id = id,
            photoPath = resolvedPhotoPath,
            photoSource = safePhotoSource.ifBlank { "camera" },
            itemName = safeItemName,
            collectionName = safeCollectionName,
            shortAiDescription = safeShortAiDescription,
            fullWebDescription = safeFullWebDescription,
            itemDescription = safeItemDescription,
            detectedLabels = safeDetectedLabels,
            estimatedValue = estimatedValue,
            currency = safeCurrency.ifBlank { "AUD" },
            valueSource = safeValueSource,
            sourceUrl = safeSourceUrl,
            searchResults = safeSearchResults,
            confidence = confidence,
            createdAtMillis = createdAtMillis ?: dateValued,
            dateTaken = safeDateTaken,
            dateValued = dateValued,
            willInstructions = safeWillInstructions,
            notes = safeNotes,
            tags = safeTags,
            billsEnteredPeriod = safeBillsEnteredPeriod,
            includeInTotals = includeInTotals
        )
    }

    companion object {
        fun fromItem(item: ValuedItem, photoFileName: String?): BackupItemRecord {
            return BackupItemRecord(
                id = item.id,
                photoFileName = photoFileName,
                photoSource = item.photoSource,
                itemName = item.itemName,
                collectionName = item.collectionName,
                shortAiDescription = item.shortAiDescription,
                fullWebDescription = item.fullWebDescription,
                itemDescription = item.itemDescription,
                detectedLabels = item.detectedLabels,
                estimatedValue = item.estimatedValue,
                currency = item.currency,
                valueSource = item.valueSource,
                sourceUrl = item.sourceUrl,
                searchResults = item.searchResults,
                confidence = item.confidence,
                createdAtMillis = item.createdAtMillis,
                dateTaken = item.dateTaken,
                dateValued = item.dateValued,
                willInstructions = item.willInstructions,
                notes = item.notes,
                tags = item.tags,
                billsEnteredPeriod = item.billsEnteredPeriod,
                includeInTotals = item.includeInTotals
            )
        }
    }
}

private data class ImportedItemPhotoRecord(
    val itemId: Int,
    val photoPath: String,
    val sortOrder: Int,
    val isCover: Boolean,
    val createdAtMillis: Long
)


class ValuePicsRepository(context: Context) {
    companion object {
        private const val BACKUP_FORMAT_VERSION = 4
        // Keep restore backward-compatible with earliest JSON payload backups.
        private const val MIN_RESTORE_BACKUP_FORMAT_VERSION = 1
        private const val TAG = "ValuePicsRepository"
        private const val RECORD_LIMIT = 50
        // Feature gate only (not cryptographic security).
        private const val UNLOCK_PASSWORD = "Harbor7!"
        private const val SAMPLE_ASSET_ZIP = "example.zip"
    }

    private data class PhotoHashCacheEntry(
        val lastModified: Long,
        val fileSizeBytes: Long,
        val sha256: String
    )

    private val appContext = context.applicationContext
    private val database = ValuePicsDatabase.getDatabase(context)
    private val dao = database.valuedItemDao()
    private val valuationDraftDao = database.valuationDraftDao()
    private val itemPhotoDao = database.itemPhotoDao()
    private val valuationService = WebValuationService(context)
    private val gson = Gson()
    private val collectionPrefs by lazy {
        appContext.getSharedPreferences("valuepics_settings", Context.MODE_PRIVATE)
    }
    private val manualCollectionsKey = "manual_collections"
    private val manualTabsKey = "manual_tabs"
    private val manualTagsKey = "manual_tags"
    private val manualTabsInitializedKey = "manual_tabs_initialized"
    private val manualTagsLegacySeededKey = "manual_tags_legacy_seeded"
    private val legacyValuationDraftKey = "valuation_draft"
    private val lastBackupMillisKey = "last_backup_millis"
    private val lastBackupFormatVersionKey = "last_backup_format_version"
    private val lastBackupAppVersionKey = "last_backup_app_version"
    private val autoBackupSlotKey = "auto_backup_slot"
    private val unlimitedUnlockedKey = "unlimited_unlocked"
    private val unlockAccountIdKey = "unlock_account_id"
    private val manualCollectionsFlow = MutableStateFlow(loadManualCollections())
    private val manualTabsFlow = MutableStateFlow(loadManualTabs())
    private val manualTagsFlow = MutableStateFlow(loadManualTags())
    private val currentTierFlow = MutableStateFlow(
        collectionPrefs.getString("app_tier", null) ?: AppTier.PERSONAL.name
    )
    private val photoHashCache = mutableMapOf<String, PhotoHashCacheEntry>()

    private fun isPersonalTierTag(tier: String): Boolean = tier.equals(AppTier.PERSONAL.name, ignoreCase = true)

    private fun mergeTierCollections(tier: String, dbCollections: List<String>, manualCollections: List<String>): List<String> {
        val merged = (dbCollections + manualCollections).normalizeCollections()
        return if (isPersonalTierTag(tier)) {
            (merged + BILLS_COLLECTION_NAME).normalizeCollections()
        } else {
            merged.filterNot { isBillsCollectionName(it) }.normalizeCollections()
        }
    }

    fun setCurrentTier(tier: AppTier) {
        currentTierFlow.value = tier.name
    }

    private fun currentTierTag(): String = currentTierFlow.value

    fun getRecordLimit(): Int = RECORD_LIMIT

    fun isUnlimitedUnlocked(): Boolean = collectionPrefs.getBoolean(unlimitedUnlockedKey, false)

    fun getUnlockAccountId(): String = collectionPrefs.getString(unlockAccountIdKey, "").orEmpty().trim()

    suspend fun unlockUnlimitedWithPassword(password: String, accountId: String): UnlockResult = withContext(Dispatchers.IO) {
        val normalizedPassword = password.trim()
        if (normalizedPassword != UNLOCK_PASSWORD) {
            return@withContext UnlockResult(unlocked = false, recoveryCode = null)
        }

        val normalizedAccountId = accountId.trim().lowercase(Locale.getDefault())
        val recoveryCode = normalizedAccountId.takeIf { it.isNotBlank() }?.let(::buildRecoveryCode)
        collectionPrefs.edit()
            .putBoolean(unlimitedUnlockedKey, true)
            .putString(unlockAccountIdKey, normalizedAccountId)
            .apply()

        UnlockResult(unlocked = true, recoveryCode = recoveryCode)
    }

    suspend fun restoreUnlimitedFromAccount(accountId: String, recoveryCode: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedAccountId = accountId.trim().lowercase(Locale.getDefault())
        val normalizedRecoveryCode = recoveryCode.trim().uppercase(Locale.getDefault())
        if (normalizedAccountId.isBlank() || normalizedRecoveryCode.isBlank()) {
            return@withContext false
        }

        val expected = buildRecoveryCode(normalizedAccountId)
        val matches = normalizedRecoveryCode == expected
        if (matches) {
            collectionPrefs.edit()
                .putBoolean(unlimitedUnlockedKey, true)
                .putString(unlockAccountIdKey, normalizedAccountId)
                .apply()
        }
        matches
    }

    private fun buildRecoveryCode(normalizedAccountId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(("$normalizedAccountId|$UNLOCK_PASSWORD|valuepics_unlock_v1").toByteArray(Charsets.UTF_8))
        return digest.take(4).joinToString("") { "%02X".format(it) }
    }

    private fun sha256ForFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    suspend fun getDuplicateGroupOrderByItemId(items: List<ValuedItem>): Map<Int, Int> = withContext(Dispatchers.Default) {
        val idsByHash = linkedMapOf<String, MutableList<Int>>()
        items.forEach { item ->
            val path = item.photoPath.trim()
            if (path.isBlank()) return@forEach
            val file = File(path)
            if (!file.exists() || !file.isFile) {
                synchronized(photoHashCache) { photoHashCache.remove(path) }
                return@forEach
            }

            val cached = synchronized(photoHashCache) { photoHashCache[path] }
            val hash = if (cached != null && cached.lastModified == file.lastModified() && cached.fileSizeBytes == file.length()) {
                cached.sha256
            } else {
                val computed = runCatching { sha256ForFile(file) }.getOrNull() ?: return@forEach
                synchronized(photoHashCache) {
                    photoHashCache[path] = PhotoHashCacheEntry(
                        lastModified = file.lastModified(),
                        fileSizeBytes = file.length(),
                        sha256 = computed
                    )
                }
                computed
            }
            val duplicateKey = if (isBillsCollectionName(item.collectionName)) {
                val normalizedName = item.itemName.trim().lowercase(Locale.getDefault())
                val normalizedPeriod = (
                    BillsPeriod.fromStorageValue(item.billsEnteredPeriod)?.storageValue
                        ?: item.billsEnteredPeriod.trim().uppercase(Locale.getDefault())
                )
                val normalizedAmount = item.estimatedValue?.let { "%.2f".format(Locale.US, it) }.orEmpty()
                val valuedDayBucket = item.dateValued / 86_400_000L
                listOf(hash, normalizedName, normalizedPeriod, normalizedAmount, valuedDayBucket.toString())
                    .joinToString("|")
            } else {
                hash
            }
            idsByHash.getOrPut(duplicateKey) { mutableListOf() }.add(item.id)
        }

        val duplicateGroups = idsByHash.values
            .filter { it.size > 1 }
            .sortedByDescending { it.size }

        buildMap {
            duplicateGroups.forEachIndexed { index, ids ->
                ids.forEach { id -> put(id, index) }
            }
        }
    }

    suspend fun insertItem(item: ValuedItem): Long {
        // Tag with current tier if not already set
        val taggedItem = if (item.tier.isBlank()) item.copy(tier = currentTierTag()) else item
        val id = dao.insertItem(taggedItem)
        syncManagedTagsFromRawTags(taggedItem.tags)
        if (taggedItem.photoPath.isNotBlank()) {
            itemPhotoDao.insertPhoto(
                ItemPhoto(
                    itemId = id.toInt(),
                    photoPath = taggedItem.photoPath,
                    sortOrder = 0,
                    isCover = true,
                    createdAtMillis = taggedItem.createdAtMillis
                )
            )
        }
        return id
    }
    suspend fun updateItem(item: ValuedItem) {
        dao.updateItem(item)
        syncManagedTagsFromRawTags(item.tags)
    }

    /** Thrown by [copyItemToOtherTier] when the item already exists in the target tier. */
    class ItemAlreadyInTierException(targetTier: String) :
        Exception("Item already exists in $targetTier tier")

    /**
     * Copies [itemId] into the opposite tier.
     * - Copies all [ItemPhoto] rows (same file paths, no disk duplication).
     * - Returns [Result.failure] with [ItemAlreadyInTierException] if an item with
     *   the same name already exists in the target tier.
     */
    suspend fun copyItemToOtherTier(itemId: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val source = dao.getItemById(itemId)
                ?: error("Item $itemId not found")

            val sourceTier = source.tier.ifBlank { AppTier.PERSONAL.name }
            val targetTier = if (sourceTier == AppTier.PERSONAL.name) AppTier.INSURANCE.name
                             else AppTier.PERSONAL.name

            val duplicateCount = dao.countByNameInTier(source.itemName, targetTier)
            if (duplicateCount > 0) throw ItemAlreadyInTierException(targetTier)

            val now = System.currentTimeMillis()
            val newId = dao.insertItem(
                source.copy(id = 0, tier = targetTier, createdAtMillis = now, dateValued = now)
            ).toInt()

            // Copy all photo rows pointing at the same files (no disk copy)
            val sourcePhotos = itemPhotoDao.getPhotosForItemSnapshot(itemId)
            if (sourcePhotos.isNotEmpty()) {
                itemPhotoDao.insertPhotos(
                    sourcePhotos.map { it.copy(id = 0, itemId = newId, createdAtMillis = now) }
                )
            }
        }
    }

    /**
     * Seeds the database with 4 example records for new users.
     * Returns true if samples were inserted, false if they already exist.
     */
    suspend fun seedSampleData(): Boolean = withContext(Dispatchers.IO) {
        // Check if sample records already exist
            val allItems = dao.getAllItemsSnapshot(currentTierTag())
        if (allItems.any { SampleDataHelper.isSampleRecord(it) }) {
            return@withContext false // Already seeded
        }

        try {
            val insertedFromZip = seedSampleDataFromBundledZip()
            if (insertedFromZip > 0) {
                return@withContext true
            }

            // Fallback to legacy synthetic examples if the bundled ZIP is missing.
            val fallbackRecords = SampleDataHelper.getSampleRecords().mapIndexed { index, sample ->
                val samplePhotoPath = createSamplePhotoFile(index)
                SampleDataHelper.ensureSampleRecord(
                    sample.copy(
                        photoPath = samplePhotoPath,
                        photoSource = if (samplePhotoPath.isNotBlank()) "sample" else sample.photoSource
                    )
                )
            }
            fallbackRecords.forEach { sample -> insertItem(sample) }
            fallbackRecords.isNotEmpty()
        } catch (e: Exception) {
            Log.e("ValuePicsRepository", "Failed to seed sample data", e)
            false
        }
    }

    private suspend fun seedSampleDataFromBundledZip(): Int {
        PhotoUtils.ensureCollectionFolders(appContext)
        val photosDir = PhotoUtils.getPhotosCollectionDir(appContext)
        val extractedPhotos = mutableMapOf<String, String>()
        var payload: BackupPayload? = null

        appContext.assets.open(SAMPLE_ASSET_ZIP).use { rawInput ->
            ZipInputStream(BufferedInputStream(rawInput)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        when {
                            entry.name == "data/items.json" -> {
                                val json = zip.readBytes().toString(Charsets.UTF_8)
                                payload = parsePayload(json)
                            }
                            entry.name.startsWith("photos/") -> {
                                val zipName = entry.name.substringAfter("photos/")
                                if (zipName.isNotBlank()) {
                                    val safeName = File(zipName).name
                                    val outFile = createUniqueImportedPhotoFile(photosDir, safeName)
                                    FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                                    extractedPhotos[safeName] = outFile.absolutePath
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val backupPayload = payload ?: return 0
        var insertedCount = 0
        backupPayload.items.orEmpty().forEach { record ->
            val mappedPhotoPath = record.photoFileName
                ?.let { extractedPhotos[File(it).name] }
                .orEmpty()
            val sampleItem = SampleDataHelper.ensureSampleRecord(
                record.toValuedItem(mappedPhotoPath).copy(
                    photoSource = if (mappedPhotoPath.isNotBlank()) "sample" else record.photoSource.orEmpty()
                )
            )
            insertItem(sampleItem)
            insertedCount += 1
        }
        return insertedCount
    }

    /**
     * Removes all sample records from the database.
     */
    suspend fun removeSampleData(): Int = withContext(Dispatchers.IO) {
        val allItems = dao.getAllItemsSnapshot(currentTierTag())
        val sampleItems = allItems.filter { SampleDataHelper.isSampleRecord(it) }
        sampleItems.forEach { item ->
            deleteItem(item)
        }
        sampleItems.size
    }

    private fun createSamplePhotoFile(index: Int): String {
        return runCatching {
            PhotoUtils.ensureCollectionFolders(appContext)
            val outputDir = PhotoUtils.getPhotosCollectionDir(appContext)
            val outFile = File(outputDir, "sample_${index + 1}_${System.currentTimeMillis()}.jpg")

            val width = 1200
            val height = 900
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawSampleBackground(canvas, width, height, index)
            drawSampleObject(canvas, width, height, index)

            FileOutputStream(outFile).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
            }
            bitmap.recycle()
            outFile.absolutePath
        }.onFailure {
            Log.w(TAG, "Failed to generate sample photo for index=$index", it)
        }.getOrDefault("")
    }

    private fun drawSampleBackground(canvas: Canvas, width: Int, height: Int, index: Int) {
        val (startColor, endColor) = when (index) {
            0 -> Color.parseColor("#4E3A24") to Color.parseColor("#7A5D3A")
            1 -> Color.parseColor("#243443") to Color.parseColor("#3F566A")
            2 -> Color.parseColor("#5A4C3F") to Color.parseColor("#7B6B58")
            else -> Color.parseColor("#2A2D35") to Color.parseColor("#3F4652")
        }
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                0f,
                height.toFloat(),
                startColor,
                endColor,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
    }

    private fun drawSampleObject(canvas: Canvas, width: Int, height: Int, index: Int) {
        when (index) {
            0 -> drawWatch(canvas, width, height)
            1 -> drawCamera(canvas, width, height)
            2 -> drawFigurine(canvas, width, height)
            else -> drawPen(canvas, width, height)
        }
    }

    private fun drawWatch(canvas: Canvas, width: Int, height: Int) {
        val cx = width / 2f
        val cy = height / 2f
        val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C9A758") }
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#F2EFD9") }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#7F6538")
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#222222")
            strokeWidth = 10f
        }
        canvas.drawCircle(cx, cy, 240f, outerPaint)
        canvas.drawCircle(cx, cy, 190f, facePaint)
        canvas.drawCircle(cx, cy, 240f, strokePaint)
        canvas.drawCircle(cx, cy, 190f, strokePaint)
        canvas.drawLine(cx, cy, cx + 90f, cy - 35f, handPaint)
        canvas.drawLine(cx, cy, cx - 40f, cy + 120f, handPaint)
        canvas.drawCircle(cx, cy, 12f, handPaint)
        canvas.drawRoundRect(RectF(cx - 38f, cy - 325f, cx + 38f, cy - 240f), 8f, 8f, outerPaint)
    }

    private fun drawCamera(canvas: Canvas, width: Int, height: Int) {
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1F2227") }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#5C626B") }
        val lensOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#10151C") }
        val lensInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#385B7A") }
        canvas.drawRoundRect(RectF(220f, 260f, 980f, 700f), 40f, 40f, bodyPaint)
        canvas.drawRoundRect(RectF(300f, 215f, 560f, 295f), 20f, 20f, detailPaint)
        canvas.drawCircle(width / 2f, height / 2f + 20f, 180f, lensOuter)
        canvas.drawCircle(width / 2f, height / 2f + 20f, 125f, lensInner)
        canvas.drawRect(820f, 330f, 940f, 380f, detailPaint)
    }

    private fun drawFigurine(canvas: Canvas, width: Int, height: Int) {
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E4D7C8") }
        val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C2B09B") }
        val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#957F6A")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }
        canvas.drawOval(RectF(350f, 190f, 850f, 770f), bodyPaint)
        canvas.drawCircle(width / 2f, 320f, 45f, bodyPaint)
        canvas.drawRoundRect(RectF(525f, 420f, 675f, 620f), 30f, 30f, bodyPaint)
        canvas.drawRoundRect(RectF(470f, 620f, 730f, 700f), 16f, 16f, shadePaint)
        canvas.drawOval(RectF(350f, 190f, 850f, 770f), outlinePaint)
    }

    private fun drawPen(canvas: Canvas, width: Int, height: Int) {
        val penBody = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0E1014") }
        val trimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#B08D4B") }
        val nibPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#C59C57") }
        canvas.drawRoundRect(RectF(180f, 360f, 980f, 520f), 30f, 30f, penBody)
        canvas.drawRect(300f, 350f, 350f, 530f, trimPaint)
        canvas.drawRect(645f, 350f, 670f, 530f, trimPaint)
        val nibPath = android.graphics.Path().apply {
            moveTo(980f, 360f)
            lineTo(1090f, 440f)
            lineTo(980f, 520f)
            close()
        }
        canvas.drawPath(nibPath, nibPaint)
    }

    private fun deleteAudioNoteFilesForItem(itemId: Int) {
        if (itemId <= 0) return
        val audioDir = audioNotesDir()
        runCatching { File(audioDir, "item_${itemId}.3gp").delete() }
        runCatching { File(audioDir, "item_${itemId}.m4a").delete() }
    }

    private fun audioNotesDir(): File = File(appContext.filesDir, "audio_notes").apply { mkdirs() }

    private fun resolveAudioNoteFileForItem(itemId: Int): File? {
        if (itemId <= 0) return null
        val candidates = listOf(
            File(audioNotesDir(), "item_${itemId}.m4a"),
            File(audioNotesDir(), "item_${itemId}.3gp")
        ).filter { it.exists() && it.isFile }
        return candidates.maxByOrNull { it.lastModified() }
    }

    private fun clearAllAudioNotes() {
        val dir = audioNotesDir()
        dir.listFiles().orEmpty().forEach { file ->
            if (file.isFile) runCatching { file.delete() }
        }
    }

    private fun restoreAudioNoteForItem(itemId: Int, sourceFileName: String, extractedAudioFiles: Map<String, String>) {
        val sourcePath = extractedAudioFiles[File(sourceFileName).name] ?: return
        val source = File(sourcePath)
        if (!source.exists() || !source.isFile) return
        val extension = source.extension.lowercase(Locale.getDefault())
        val targetName = when (extension) {
            "m4a" -> "item_${itemId}.m4a"
            else -> "item_${itemId}.3gp"
        }
        runCatching {
            val target = File(audioNotesDir(), targetName)
            source.copyTo(target, overwrite = true)
        }
    }

    suspend fun deleteItem(item: ValuedItem) {
        // Capture all referenced photo paths before deleting DB rows.
        val referencedPaths = buildSet {
            if (item.photoPath.isNotBlank()) add(item.photoPath)
            addAll(
                itemPhotoDao.getPhotosForItemSnapshot(item.id)
                    .map { it.photoPath }
                    .filter { it.isNotBlank() }
            )
        }

        dao.deleteItem(item)
        deleteAudioNoteFilesForItem(item.id)

        // Best-effort disk cleanup. Only delete files no longer referenced by any row.
        referencedPaths.forEach { path ->
            if (!isPhotoPathStillReferenced(path)) {
                PhotoUtils.deletePhotoFile(path)
            }
        }
    }
    suspend fun getItemById(id: Int): ValuedItem? = dao.getItemById(id)
    
    fun getAllItems(): Flow<List<ValuedItem>> = currentTierFlow.flatMapLatest { tier -> dao.getAllItems(tier) }
    fun getAllCollections(): Flow<List<String>> = currentTierFlow.flatMapLatest { tier ->
        combine(dao.getAllCollections(tier), manualCollectionsFlow) { dbCollections, manualCollections ->
            mergeTierCollections(tier, dbCollections, manualCollections)
        }
    }

    fun getAllTabs(): Flow<List<String>> = manualTabsFlow

    fun getAllTags(): Flow<List<String>> = currentTierFlow.flatMapLatest { tier ->
        combine(dao.getAllItems(tier), manualTagsFlow) { items, manualTags ->
            (items.flatMap { TagUtils.parseTags(it.tags) } + manualTags).normalizeCollections()
        }
    }

    suspend fun ensureTabsInitialized() {
        if (!isManualTabsInitialized()) {
            val existingManual = loadManualTabs()
            saveManualTabs(existingManual, initialized = true)
            Log.i(TAG, "TAB init loaded. tabs=${existingManual.joinToString()}")
        }

        // Keep tabs as their own identity: strip any names that live in collections.
        val tabs = loadManualTabs()
        val collections = loadManualCollections()
        val cleanedTabs = tabs
            .filterNot { tab -> collections.any { it.equals(tab, ignoreCase = true) } }
            .normalizeCollections()
        if (cleanedTabs != tabs) {
            saveManualTabs(cleanedTabs, initialized = true)
            Log.i(TAG, "TAB cleanup removed collection-overlap names. tabs=${cleanedTabs.joinToString()}")
        }
    }

    suspend fun ensureLegacyTagsSeededFromLegacyLists() = withContext(Dispatchers.IO) {
        if (collectionPrefs.getBoolean(manualTagsLegacySeededKey, false)) return@withContext

        val usedCollections = dao.getAllItemsSnapshotUnscoped()
            .map { it.collectionName.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }

        val existingManualTags = loadManualTags()
        val candidateLegacyTags = (loadManualCollections() + loadManualTabs())
            .filterNot { candidate ->
                usedCollections.any { used -> used.equals(candidate, ignoreCase = true) }
            }
            .filterNot { candidate ->
                existingManualTags.any { tag -> tag.equals(candidate, ignoreCase = true) }
            }
            .normalizeCollections()

        if (candidateLegacyTags.isNotEmpty()) {
            saveManualTags(existingManualTags + candidateLegacyTags)
            Log.i(
                TAG,
                "TAG legacy seed copied orphan labels into managed tags. tags=${candidateLegacyTags.joinToString()}"
            )
        }

        collectionPrefs.edit().putBoolean(manualTagsLegacySeededKey, true).apply()
    }
    fun searchItems(query: String): Flow<List<ValuedItem>> = currentTierFlow.flatMapLatest { tier -> dao.searchItems(query, tier) }
    val getItemsByValue: Flow<List<ValuedItem>> get() = currentTierFlow.flatMapLatest { tier -> dao.getItemsByValue(tier) }

    suspend fun deleteItemById(id: Int) {
        val existingItem = dao.getItemById(id)
        val referencedPaths = buildSet {
            existingItem?.photoPath?.takeIf { it.isNotBlank() }?.let(::add)
            addAll(
                itemPhotoDao.getPhotosForItemSnapshot(id)
                    .map { it.photoPath }
                    .filter { it.isNotBlank() }
            )
        }

        dao.deleteItemById(id)
        deleteAudioNoteFilesForItem(id)
        referencedPaths.forEach { path ->
            if (!isPhotoPathStillReferenced(path)) {
                PhotoUtils.deletePhotoFile(path)
            }
        }
    }

    suspend fun deleteItemsByIds(itemIds: Set<Int>): Int {
        var removed = 0
        itemIds.filter { it > 0 }.forEach { id ->
            val item = dao.getItemById(id) ?: return@forEach
            deleteItem(item)
            removed += 1
        }
        return removed
    }

    fun getItemPhotos(itemId: Int): Flow<List<ItemPhoto>> = itemPhotoDao.getPhotosForItem(itemId)

    suspend fun logStartupPhotoIntegrityCheck(sampleSize: Int = 5) {
        withContext(Dispatchers.IO) {
            runCatching {
                val rowCount = itemPhotoDao.countAllRows()
                val distinctCount = itemPhotoDao.countDistinctPhotoPaths()
                val nonBlankDistinctPaths = itemPhotoDao.getDistinctNonBlankPhotoPaths()
                val missingPaths = nonBlankDistinctPaths.filter { path -> !File(path).exists() }
                val sample = missingPaths.take(sampleSize).joinToString(" | ")
                Log.i(
                    TAG,
                    "Startup photo integrity: rows=$rowCount, distinctPaths=$distinctCount, nonBlankDistinct=${nonBlankDistinctPaths.size}, missing=${missingPaths.size}" +
                        if (sample.isNotBlank()) ", missingSample=$sample" else ""
                )
            }.onFailure { error ->
                Log.w(TAG, "Startup photo integrity check failed", error)
            }
        }
    }

    suspend fun getItemPhotosSnapshot(itemId: Int): List<ItemPhoto> = itemPhotoDao.getPhotosForItemSnapshot(itemId)

    suspend fun addPhotoToItem(itemId: Int, photoPath: String, makeCover: Boolean = false): Long {
        val nextSortOrder = itemPhotoDao.getMaxSortOrder(itemId) + 1
        val newId = itemPhotoDao.insertPhoto(
            ItemPhoto(
                itemId = itemId,
                photoPath = photoPath,
                sortOrder = nextSortOrder,
                isCover = false
            )
        )
        if (makeCover) {
            setCoverPhoto(itemId, newId.toInt())
        }
        return newId
    }

    suspend fun setCoverPhoto(itemId: Int, photoId: Int): Boolean {
        val photo = itemPhotoDao.getPhotoById(photoId) ?: return false
        if (photo.itemId != itemId) return false
        itemPhotoDao.setCoverPhoto(itemId, photoId)
        dao.updateCoverPhotoPath(itemId, photo.photoPath)
        return true
    }

    suspend fun deletePhotoFromItem(photoId: Int): Boolean {
        val target = itemPhotoDao.getPhotoById(photoId) ?: return false
        itemPhotoDao.deletePhotoById(photoId)

        // Delete from disk only when no rows still reference this file path.
        if (!isPhotoPathStillReferenced(target.photoPath)) {
            PhotoUtils.deletePhotoFile(target.photoPath)
        }

        if (target.isCover) {
            val remaining = itemPhotoDao.getPhotosForItemSnapshot(target.itemId)
            val nextCover = remaining.minByOrNull { it.sortOrder }
            if (nextCover != null) {
                itemPhotoDao.setCoverPhoto(target.itemId, nextCover.id)
                dao.updateCoverPhotoPath(target.itemId, nextCover.photoPath)
            } else {
                dao.updateCoverPhotoPath(target.itemId, "")
            }
        }
        return true
    }

    private suspend fun isPhotoPathStillReferenced(photoPath: String): Boolean {
        if (photoPath.isBlank()) return false
        val inItemPhotos = itemPhotoDao.countByPhotoPath(photoPath)
        if (inItemPhotos > 0) return true
        val inCoverColumn = dao.countByPhotoPath(photoPath)
        return inCoverColumn > 0
    }
    suspend fun countItemsInCollection(name: String): Int {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return 0
        return dao.countItemsInCollection(cleanName, currentTierTag())
    }

    suspend fun addCollection(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return false
        if (isBillsCollectionName(cleanName) && !isPersonalTierTag(currentTierTag())) return false
        val merged = (loadManualCollections() + cleanName).normalizeCollections()
        saveManualCollections(merged)
        return true
    }

    suspend fun renameCollection(oldName: String, newName: String): Int {
        val oldClean = oldName.trim()
        val newClean = newName.trim()
        if (oldClean.isBlank() || newClean.isBlank()) return 0
        if (isBillsCollectionName(oldClean) || isBillsCollectionName(newClean)) return 0

        Log.i(TAG, "COLLECTION rename requested: '$oldClean' -> '$newClean'")

        val affectedRows = dao.renameCollection(oldClean, newClean, currentTierTag())
        val updatedManual = loadManualCollections()
            .map { if (it.equals(oldClean, ignoreCase = true)) newClean else it }
            .normalizeCollections()
        saveManualCollections(updatedManual)
        Log.i(TAG, "COLLECTION rename applied rows=$affectedRows")
        return affectedRows
    }

    suspend fun deleteCollection(name: String): Int {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return 0
        if (isBillsCollectionName(cleanName)) return 0

        Log.i(TAG, "COLLECTION delete requested: '$cleanName'")

        val affectedRows = dao.clearCollectionFromItems(cleanName, currentTierTag())
        val updatedManual = loadManualCollections()
            .filterNot { it.equals(cleanName, ignoreCase = true) }
            .normalizeCollections()
        saveManualCollections(updatedManual)
        Log.i(TAG, "COLLECTION delete applied rows=$affectedRows")
        return affectedRows
    }

    suspend fun addTab(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return false
        Log.i(TAG, "TAB add requested: '$cleanName'")
        val tabs = resolveEditableTabs().toMutableList()
        if (tabs.any { it.equals(cleanName, ignoreCase = true) }) return false
        tabs += cleanName
        saveManualTabs(tabs, initialized = true)
        Log.i(TAG, "TAB add applied. tabs=${tabs.joinToString()}")
        return true
    }

    suspend fun renameTab(oldName: String, newName: String): Boolean {
        val oldClean = oldName.trim()
        val newClean = newName.trim()
        if (oldClean.isBlank() || newClean.isBlank()) return false
        Log.i(TAG, "TAB rename requested: '$oldClean' -> '$newClean'")
        val tabs = resolveEditableTabs().toMutableList()
        val index = tabs.indexOfFirst { it.equals(oldClean, ignoreCase = true) }
        if (index < 0) return false
        if (tabs.any { it.equals(newClean, ignoreCase = true) && !it.equals(oldClean, ignoreCase = true) }) return false
        tabs[index] = newClean
        saveManualTabs(tabs, initialized = true)
        Log.i(TAG, "TAB rename applied. tabs=${tabs.joinToString()}")
        return true
    }

    suspend fun deleteTab(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return false
        Log.i(TAG, "TAB delete requested: '$cleanName'")
        val tabs = resolveEditableTabs().toMutableList()
        val changed = tabs.removeAll { it.equals(cleanName, ignoreCase = true) }
        if (!changed) return false
        saveManualTabs(tabs, initialized = true)
        Log.i(TAG, "TAB delete applied. tabs=${tabs.joinToString()}")
        return true
    }

    suspend fun resetTabs(): Boolean {
        Log.i(TAG, "TAB reset requested")
        saveManualTabs(emptyList(), initialized = true)
        Log.i(TAG, "TAB reset applied. tabs=<empty>")
        return true
    }

    suspend fun addTag(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return false
        val existingTags = loadAllTagsSnapshot(currentTierTag())
        if (existingTags.any { it.equals(cleanName, ignoreCase = true) }) return false
        val merged = (loadManualTags() + cleanName).normalizeCollections()
        saveManualTags(merged)
        Log.i(TAG, "TAG add applied. tags=${merged.joinToString()}")
        return true
    }

    suspend fun renameTag(oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val oldClean = oldName.trim()
        val newClean = newName.trim()
        if (oldClean.isBlank() || newClean.isBlank()) return@withContext false

        val existingTags = loadAllTagsSnapshot(currentTierTag())
        if (existingTags.none { it.equals(oldClean, ignoreCase = true) }) return@withContext false
        if (existingTags.any { it.equals(newClean, ignoreCase = true) && !it.equals(oldClean, ignoreCase = true) }) {
            return@withContext false
        }

        var changed = false
        val previousManualTags = loadManualTags()
        database.withTransaction {
            val items = dao.getAllItemsSnapshot(currentTierTag())
            items.forEach { item ->
                if (!TagUtils.hasTag(item.tags, oldClean)) return@forEach
                val updatedTags = TagUtils.renameTag(item.tags, oldClean, newClean)
                if (updatedTags != TagUtils.normalizeTags(item.tags)) {
                    dao.updateItem(item.copy(tags = updatedTags))
                    changed = true
                }
            }
        }

        val updatedManual = previousManualTags
            .map { if (it.equals(oldClean, ignoreCase = true)) newClean else it }
            .let { tags -> if (tags.none { it.equals(newClean, ignoreCase = true) }) tags + newClean else tags }
            .normalizeCollections()
        if (updatedManual != previousManualTags) {
            saveManualTags(updatedManual)
            changed = true
        }

        Log.i(TAG, "TAG rename requested: '$oldClean' -> '$newClean', changed=$changed")
        changed
    }

    suspend fun deleteTag(name: String): Boolean = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return@withContext false

        var changed = false
        val previousManualTags = loadManualTags()
        database.withTransaction {
            val items = dao.getAllItemsSnapshot(currentTierTag())
            items.forEach { item ->
                if (!TagUtils.hasTag(item.tags, cleanName)) return@forEach
                val updatedTags = TagUtils.removeTag(item.tags, cleanName)
                if (updatedTags != TagUtils.normalizeTags(item.tags)) {
                    dao.updateItem(item.copy(tags = updatedTags))
                    changed = true
                }
            }
        }

        val updatedManual = previousManualTags
            .filterNot { it.equals(cleanName, ignoreCase = true) }
            .normalizeCollections()
        if (updatedManual != previousManualTags) {
            saveManualTags(updatedManual)
            changed = true
        }

        Log.i(TAG, "TAG delete requested: '$cleanName', changed=$changed")
        changed
    }

    fun getTotalItems(): Flow<Int> = currentTierFlow.flatMapLatest { tier -> dao.getTotalItems(tier) }
    fun getTotalValue(): Flow<Double?> = currentTierFlow.flatMapLatest { tier -> dao.getTotalValue(tier) }
    fun getAverageValue(): Flow<Double?> = currentTierFlow.flatMapLatest { tier -> dao.getAverageValue(tier) }

    // Valuation methods
    suspend fun getValueForItem(itemName: String, description: String, detailedMode: Boolean): ValuationResult? {
        return valuationService.searchForValue(itemName, description, detailedMode)
    }

    suspend fun getValueForItemDetailed(itemName: String, description: String, detailedMode: Boolean): WebValuationOutcome {
        return valuationService.searchForValueDetailed(itemName, description, detailedMode)
    }

    suspend fun getWebDescription(
        itemName: String,
        hint: String,
        labels: List<String>
    ): WebDescriptionResult? {
        return valuationService.fetchFullDescription(itemName, hint, labels)
    }

    suspend fun backupToUri(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val output = appContext.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Unable to open backup destination")

        output.use { rawOutput ->
            val createdAtMillis = System.currentTimeMillis()
            val (itemCount, photoCount) = writeBackupZip(rawOutput, createdAtMillis)
            val isVerified = verifyBackupZip(uri)
            collectionPrefs.edit()
                .putLong(lastBackupMillisKey, createdAtMillis)
                .putInt(lastBackupFormatVersionKey, BACKUP_FORMAT_VERSION)
                .putString(lastBackupAppVersionKey, currentAppVersionName())
                .apply()
            BackupSummary(itemCount = itemCount, photoCount = photoCount, isVerified = isVerified)
        }
    }

    suspend fun backupSelectedItemsToUri(uri: Uri, itemIds: Set<Int>): BackupSummary = withContext(Dispatchers.IO) {
        val normalizedIds = itemIds.filter { it > 0 }.toSet()
        if (normalizedIds.isEmpty()) {
            throw IllegalArgumentException("No records selected for export")
        }
        val output = appContext.contentResolver.openOutputStream(uri)
            ?: throw IllegalStateException("Unable to open backup destination")

        val selectedItems = dao.getAllItemsSnapshot(currentTierTag()).filter { it.id in normalizedIds }
        if (selectedItems.isEmpty()) {
            throw IllegalStateException("No matching records found for export")
        }

        output.use { rawOutput ->
            val createdAtMillis = System.currentTimeMillis()
            val (itemCount, photoCount) = writeBackupZip(rawOutput, createdAtMillis, selectedItems)
            BackupSummary(itemCount = itemCount, photoCount = photoCount, isVerified = true)
        }
    }

    fun getLastBackupMillis(): Long {
        return collectionPrefs.getLong(lastBackupMillisKey, 0L)
    }

    fun getCurrentBackupFormatVersion(): Int = BACKUP_FORMAT_VERSION

    fun getMinimumSupportedRestoreBackupFormatVersion(): Int = MIN_RESTORE_BACKUP_FORMAT_VERSION

    fun getLastBackupFormatDisplayText(): String {
        val formatVersion = collectionPrefs.getInt(lastBackupFormatVersionKey, -1)
        if (formatVersion <= 0) return "-"
        val appVersion = collectionPrefs.getString(lastBackupAppVersionKey, "").orEmpty().trim()
        return if (appVersion.isBlank()) {
            "v$formatVersion"
        } else {
            "v$formatVersion (app $appVersion)"
        }
    }

    suspend fun runAutoBackupOnLaunch(): BackupSummary? = withContext(Dispatchers.IO) {
        val tier = currentTierTag()
        val tierSuffix = "_${tier.lowercase(Locale.ROOT)}"
        val backupDir = File(appContext.filesDir, "auto_backups").apply { mkdirs() }
        val slotKey = "${autoBackupSlotKey}${tierSuffix}"
        val nextSlot = if (collectionPrefs.getInt(slotKey, 0) == 0) 1 else 0
        val slotName = if (nextSlot == 0) "auto_backup_A${tierSuffix}.zip" else "auto_backup_B${tierSuffix}.zip"
        val targetFile = File(backupDir, slotName)
        val tempFile = File(backupDir, "$slotName.tmp")

        runCatching {
            val createdAtMillis = System.currentTimeMillis()
            FileOutputStream(tempFile).use { out ->
                val (itemCount, photoCount) = writeBackupZip(out, createdAtMillis)
                if (targetFile.exists()) targetFile.delete()
                if (!tempFile.renameTo(targetFile)) {
                    throw IllegalStateException("Unable to finalize auto-backup file")
                }
                collectionPrefs.edit()
                    .putLong(lastBackupMillisKey, createdAtMillis)
                    .putInt(lastBackupFormatVersionKey, BACKUP_FORMAT_VERSION)
                    .putString(lastBackupAppVersionKey, currentAppVersionName())
                    .putInt(slotKey, nextSlot)
                    .apply()
                BackupSummary(itemCount = itemCount, photoCount = photoCount, isVerified = true)
            }
        }.onFailure {
            if (tempFile.exists()) tempFile.delete()
            Log.w("ValuePicsRepository", "Auto-backup failed", it)
        }.getOrNull()
    }

    suspend fun inspectAutoBackupSlots(): List<AutoBackupSlotInfo> = withContext(Dispatchers.IO) {
        val tier = currentTierTag()
        val tierSuffix = "_${tier.lowercase(Locale.ROOT)}"
        val backupDir = File(appContext.filesDir, "auto_backups").apply { mkdirs() }
        val localLastBackupMillis = getLastBackupMillis()
        val raw = listOf(
            inspectSingleAutoBackupSlot("A", File(backupDir, "auto_backup_A${tierSuffix}.zip")),
            inspectSingleAutoBackupSlot("B", File(backupDir, "auto_backup_B${tierSuffix}.zip"))
        )
        val latestCreatedAt = raw
            .mapNotNull { it.createdAtMillis }
            .maxOrNull()

        raw.map { slot ->
            val staleVsLatestSlot = latestCreatedAt != null && slot.createdAtMillis != null && slot.createdAtMillis < latestCreatedAt
            val staleVsLastKnownBackup = localLastBackupMillis > 0L && slot.createdAtMillis != null && slot.createdAtMillis < localLastBackupMillis
            val warning = when {
                !slot.isRestorable -> null
                staleVsLatestSlot && staleVsLastKnownBackup ->
                    "Older than the latest local backup and older than the other auto-backup slot. Restoring may roll back recent changes."
                staleVsLatestSlot ->
                    "Older than the other auto-backup slot. Restore only if you intentionally want an earlier snapshot."
                staleVsLastKnownBackup ->
                    "Older than your latest known local backup. Restoring may roll back recent changes."
                else -> null
            }
            slot.copy(
                isLatest = !staleVsLatestSlot,
                warningMessage = warning
            )
        }
    }

    suspend fun restoreFromAutoBackupSlot(slotId: String): BackupSummary = withContext(Dispatchers.IO) {
        val tier = currentTierTag()
        val tierSuffix = "_${tier.lowercase(Locale.ROOT)}"
        val normalized = slotId.trim().uppercase(Locale.US)
        val fileName = when (normalized) {
            "A" -> "auto_backup_A${tierSuffix}.zip"
            "B" -> "auto_backup_B${tierSuffix}.zip"
            else -> throw IllegalArgumentException("Unknown auto-backup slot: $slotId")
        }
        val backupFile = File(File(appContext.filesDir, "auto_backups"), fileName)
        val slotInfo = inspectSingleAutoBackupSlot(normalized, backupFile)
        if (!slotInfo.isRestorable) {
            throw IllegalStateException(slotInfo.corruptionMessage ?: "Auto-backup slot $normalized is not restorable")
        }
        FileInputStream(backupFile).use { input ->
            restoreFromZipInput(input, allowOlderBackup = true)
        }
    }

    suspend fun migrateLegacyValuationDraftIfNeeded() {
        if (valuationDraftDao.getLatestDraft() != null) return
        val legacy = loadLegacyValuationDraft() ?: return
        valuationDraftDao.upsertDraft(ValuationDraftEntity.fromDomain(legacy))
        clearLegacyValuationDraft()
    }

    suspend fun saveValuationDraft(draft: ValuationDraft) {
        valuationDraftDao.upsertDraft(ValuationDraftEntity.fromDomain(draft))
        clearLegacyValuationDraft()
    }

    suspend fun getValuationDraft(photoPath: String): ValuationDraft? {
        migrateLegacyValuationDraftIfNeeded()
        return valuationDraftDao.getDraftByPhotoPath(photoPath)?.toDomain()
    }

    suspend fun getLatestValuationDraft(): ValuationDraft? {
        migrateLegacyValuationDraftIfNeeded()
        return valuationDraftDao.getLatestDraft()?.toDomain()
    }

    fun observeLatestValuationDraft(): Flow<ValuationDraft?> =
        valuationDraftDao.observeLatestDraft().map { it?.toDomain() }

    fun observePendingDraftCount(): Flow<Int> = valuationDraftDao.observeDraftCount()

    suspend fun clearValuationDraft(photoPath: String) {
        valuationDraftDao.deleteDraftByPhotoPath(photoPath)
        val legacy = loadLegacyValuationDraft()
        if (legacy?.photoPath == photoPath) {
            clearLegacyValuationDraft()
        }
    }

    suspend fun clearAllValuationDrafts() {
        valuationDraftDao.clearAllDrafts()
        clearLegacyValuationDraft()
    }

    suspend fun getPhotoFolderSizeBytes(): Long = withContext(Dispatchers.IO) {
        val photosDir = PhotoUtils.getPhotosCollectionDir(appContext)
        if (!photosDir.exists()) return@withContext 0L
        photosDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    suspend fun getPhotoFolderFileCount(): Int = withContext(Dispatchers.IO) {
        val photosDir = PhotoUtils.getPhotosCollectionDir(appContext)
        if (!photosDir.exists()) return@withContext 0
        photosDir.walkTopDown()
            .count { it.isFile }
    }

    /**
     * Removes duplicate `item_photos` rows for each item.
     *
     * Dedup happens in two passes:
     * 1) Exact path duplicates: same (itemId, photoPath)
     * 2) Content duplicates: same (itemId, SHA-256(file bytes)) even when paths differ
     *
     * This addresses poisoned backups where the same image was stored multiple times under
     * different names/paths, which path-only dedupe cannot catch.
     */
    suspend fun deduplicateItemPhotos(): Int = withContext(Dispatchers.IO) {
        val allPhotos = itemPhotoDao.getAllPhotosOrdered()
        if (allPhotos.size <= 1) return@withContext 0

        var removedCount = 0
        val digestCache = mutableMapOf<String, String?>()

        // Step 1 — deduplicate by (itemId, photoPath)
        val grouped = allPhotos.groupBy { Pair(it.itemId, it.photoPath.trim()) }
        grouped.forEach { (_, photos) ->
            if (photos.size <= 1) return@forEach
            photos.drop(1).forEach { dup ->
                runCatching { itemPhotoDao.deletePhotoById(dup.id) }
                    .onSuccess { removedCount++ }
                    .onFailure { e -> Log.w(TAG, "dedup: failed to delete item_photos id=${dup.id}", e) }
            }
        }

        // Step 2 — deduplicate by content hash within each item (same photo bytes, different paths)
        val itemIdsForHashPass = itemPhotoDao.getAllPhotosOrdered().map { it.itemId }.distinct()
        itemIdsForHashPass.forEach { itemId ->
            val photosForItem = itemPhotoDao.getPhotosForItemSnapshot(itemId)
                .sortedWith(compareByDescending<ItemPhoto> { it.isCover }.thenBy { it.sortOrder }.thenBy { it.id })
            val photosWithDigest = photosForItem.mapNotNull { photo ->
                val digest = computeSha256ForPath(photo.photoPath, digestCache)
                digest?.let { photo to it }
            }
            val byDigest = photosWithDigest.groupBy({ it.second }, { it.first })
            byDigest.forEach { (_, sameContentRows) ->
                if (sameContentRows.size <= 1) return@forEach
                sameContentRows.drop(1).forEach { dup ->
                    runCatching { itemPhotoDao.deletePhotoById(dup.id) }
                        .onSuccess { removedCount++ }
                        .onFailure { e -> Log.w(TAG, "dedup(hash): failed to delete item_photos id=${dup.id}", e) }
                }
            }
        }

        // Step 3 — ensure each item has exactly one isCover=true row
        val affectedItemIds = allPhotos.map { it.itemId }.distinct()
        affectedItemIds.forEach { itemId ->
            val remaining = itemPhotoDao.getPhotosForItemSnapshot(itemId)
            val covers = remaining.filter { it.isCover }
            when {
                covers.size > 1 -> {
                    // Keep the one with the lowest id; remove extras
                    covers.drop(1).forEach { extra ->
                        runCatching { itemPhotoDao.deletePhotoById(extra.id) }
                            .onSuccess { removedCount++ }
                    }
                }
                covers.isEmpty() && remaining.isNotEmpty() -> {
                    // No cover left — promote the photo with the lowest sortOrder
                    val promote = remaining.minByOrNull { it.sortOrder } ?: remaining.first()
                    itemPhotoDao.setCoverPhoto(itemId, promote.id)
                    dao.updateCoverPhotoPath(itemId, promote.photoPath)
                }
            }
        }

        if (removedCount > 0) {
            Log.i(TAG, "deduplicateItemPhotos: removed $removedCount duplicate item_photos rows")
        }
        removedCount
    }

    suspend fun cleanupOrphanedPhotos(): PhotoCleanupSummary = withContext(Dispatchers.IO) {
        val photosDir = PhotoUtils.getPhotosCollectionDir(appContext)
        if (!photosDir.exists()) return@withContext PhotoCleanupSummary(0, 0L)

        // Collect all photo paths referenced by either table so non-cover
        // multi-photos are not incorrectly treated as orphans.
        val coverPaths = dao.getAllItemsSnapshotUnscoped()
            .map { it.photoPath }
            .filter { it.isNotBlank() }
        val multiPhotoPaths = itemPhotoDao.getAllPhotoPaths()
            .filter { it.isNotBlank() }
        val usedPhotoPaths = (coverPaths + multiPhotoPaths).toSet()

        var removedCount = 0
        var removedBytes = 0L
        photosDir.listFiles()
            .orEmpty()
            .filter { it.isFile }
            .forEach { file ->
                if (file.absolutePath !in usedPhotoPaths) {
                    val size = file.length()
                    if (file.delete()) {
                        removedCount += 1
                        removedBytes += size
                    }
                }
            }

        PhotoCleanupSummary(removedCount = removedCount, removedBytes = removedBytes)
    }

    suspend fun restoreFromUri(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open restore source")
        input.use { rawInput ->
            // Manual restore should always allow user-selected snapshots, including older ones.
            restoreFromZipInput(rawInput, allowOlderBackup = true)
        }
    }

    private suspend fun restoreFromZipInput(
        rawInput: InputStream,
        allowOlderBackup: Boolean
    ): BackupSummary {
        PhotoUtils.ensureCollectionFolders(appContext)
        val photosDir = PhotoUtils.getPhotosCollectionDir(appContext)
        val extractedPhotos = mutableMapOf<String, String>()
        val extractedAudioFiles = mutableMapOf<String, String>()
        val tempDir = File(appContext.cacheDir, "restore-${System.currentTimeMillis()}").apply { mkdirs() }
        val extractedDatabaseFiles = mutableMapOf<String, File>()
        var payload: BackupPayload? = null

        try {
            ZipInputStream(BufferedInputStream(rawInput)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        when {
                            entry.name == "data/items.json" -> {
                                val json = zip.readBytes().toString(Charsets.UTF_8)
                                payload = parsePayload(json)
                            }

                            entry.name.startsWith("photos/") -> {
                                val zipName = entry.name.substringAfter("photos/")
                                if (zipName.isNotBlank()) {
                                    val safeName = File(zipName).name
                                    val outFile = File(photosDir, safeName)
                                    FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                                    extractedPhotos[safeName] = outFile.absolutePath
                                }
                            }

                            isLikelyDatabaseZipEntry(entry.name) -> {
                                val zipName = if (entry.name.startsWith("database/")) {
                                    entry.name.substringAfter("database/")
                                } else {
                                    entry.name
                                }
                                if (zipName.isNotBlank()) {
                                    val safeName = File(zipName).name
                                    val outFile = File(tempDir, safeName)
                                    FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                                    extractedDatabaseFiles[safeName] = outFile
                                }
                            }

                            entry.name.startsWith("audio_notes/") -> {
                                val zipName = entry.name.substringAfter("audio_notes/")
                                if (zipName.isNotBlank()) {
                                    val safeName = File(zipName).name
                                    val outFile = File(tempDir, safeName)
                                    FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                                    extractedAudioFiles[safeName] = outFile.absolutePath
                                }
                            }
                        }
                    }

                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val importedDb = resolveImportedDatabaseFile(extractedDatabaseFiles)
            val payloadItemCount = payload?.items.orEmpty().size
            val shouldUsePayload = payload != null && (payloadItemCount > 0 || importedDb == null)

            if (payload != null && payloadItemCount == 0 && importedDb == null) {
                throw IllegalStateException(
                    "Backup archive contains no records to restore. Please choose a full backup ZIP with data/items.json records or an embedded database snapshot."
                )
            }

            if (shouldUsePayload) {
                val backupPayload = payload!!
                validatePayloadForRestore(backupPayload, allowOlderBackup)
                return restoreBackupPayload(backupPayload, extractedPhotos, extractedAudioFiles)
            }

            if (payload != null && importedDb != null) {
                Log.w(
                    TAG,
                    "Restore fallback: payload had 0 items, using database snapshot ${importedDb.absolutePath}"
                )
            }

            val databaseSource = importedDb
                ?: throw IllegalStateException("Selected ZIP is not a supported backup or database archive")
            return restoreDatabaseZip(databaseSource, extractedPhotos)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun mergeFromUri(uri: Uri): BackupSummary = withContext(Dispatchers.IO) {
        PhotoUtils.ensureCollectionFolders(appContext)
        val photosDir = PhotoUtils.getPhotosCollectionDir(appContext)
        val extractedPhotos = mutableMapOf<String, String>()
        val extractedAudioFiles = mutableMapOf<String, String>()
        val extractedPhotoDigestToPath = mutableMapOf<String, String>()
        val mergeDigestCache = mutableMapOf<String, String?>()
        val referencedImportedPhotoPaths = mutableSetOf<String>()
        var dedupedPhotoEntries = 0
        val tempDir = File(appContext.cacheDir, "merge-${System.currentTimeMillis()}").apply { mkdirs() }
        val extractedDatabaseFiles = mutableMapOf<String, File>()
        var payload: BackupPayload? = null

        val input = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Unable to open merge source")

        try {
            input.use { rawInput ->
                ZipInputStream(BufferedInputStream(rawInput)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            when {
                                entry.name == "data/items.json" -> {
                                    val json = zip.readBytes().toString(Charsets.UTF_8)
                                    payload = parsePayload(json)
                                }

                                entry.name.startsWith("photos/") -> {
                                    val zipName = entry.name.substringAfter("photos/")
                                    if (zipName.isNotBlank()) {
                                        val safeName = File(zipName).name
                                        val dedupeResult = importMergedPhotoWithDedupe(
                                            zip = zip,
                                            photosDir = photosDir,
                                            tempDir = tempDir,
                                            safeName = safeName,
                                            digestToPath = extractedPhotoDigestToPath
                                        )
                                        if (dedupeResult.reusedExisting) dedupedPhotoEntries += 1
                                        extractedPhotos[safeName] = dedupeResult.path
                                    }
                                }

                                isLikelyDatabaseZipEntry(entry.name) -> {
                                    val zipName = if (entry.name.startsWith("database/")) {
                                        entry.name.substringAfter("database/")
                                    } else {
                                        entry.name
                                    }
                                    if (zipName.isNotBlank()) {
                                        val safeName = File(zipName).name
                                        val outFile = File(tempDir, safeName)
                                        FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                                        extractedDatabaseFiles[safeName] = outFile
                                    }
                                }

                                entry.name.startsWith("audio_notes/") -> {
                                    val zipName = entry.name.substringAfter("audio_notes/")
                                    if (zipName.isNotBlank()) {
                                        val safeName = File(zipName).name
                                        val outFile = File(tempDir, safeName)
                                        FileOutputStream(outFile).use { out -> zip.copyTo(out) }
                                        extractedAudioFiles[safeName] = outFile.absolutePath
                                    }
                                }
                            }
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            if (dedupedPhotoEntries > 0) {
                Log.i(TAG, "Merge photo dedupe: reused $dedupedPhotoEntries duplicate ZIP entries; unique extracted files=${extractedPhotos.size}")
            }

            val importedItems: List<ValuedItem>
            val importedItemPhotos: List<ImportedItemPhotoRecord>
            val importedCollections: List<String>
            val importedDb = resolveImportedDatabaseFile(extractedDatabaseFiles)
            val payloadItemCount = payload?.items.orEmpty().size
            val shouldUsePayload = payload != null && (payloadItemCount > 0 || importedDb == null)
            if (shouldUsePayload) {
                val backupPayload = payload
                importedItems = try {
                    backupPayload.items.orEmpty().mapIndexed { index, record ->
                        try {
                            val mappedPhotoPath = record.photoFileName
                                ?.let { extractedPhotos[File(it).name] }
                                .orEmpty()
                            record.toValuedItem(mappedPhotoPath)
                        } catch (itemError: Exception) {
                            Log.e(TAG, "Failed to convert backup item at index $index: itemName='${record.itemName}', id=${record.id}", itemError)
                            throw IllegalStateException(
                                "Failed to convert backup item at index $index (itemName='${record.itemName}', id=${record.id}) to current format. This may be due to incompatible data structures.",
                                itemError
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Backup payload processing failed. Payload version: ${backupPayload.version}, App version: ${backupPayload.appVersionName.orEmpty()}, Items count: ${backupPayload.items.orEmpty().size}", e)
                    throw IllegalStateException(
                        "Failed to convert backup items to current format. Payload version: ${backupPayload.version}, App version: ${backupPayload.appVersionName.orEmpty()}, Items count: ${backupPayload.items.orEmpty().size}",
                        e
                    )
                }
                importedCollections = backupPayload.manualCollections.orEmpty()
                importedItemPhotos = try {
                    backupPayload.itemPhotos.orEmpty().mapIndexed { index, photo ->
                        try {
                            ImportedItemPhotoRecord(
                                itemId = photo.itemId,
                                photoPath = photo.photoFileName.orEmpty(),
                                sortOrder = photo.sortOrder,
                                isCover = photo.isCover,
                                createdAtMillis = photo.createdAtMillis
                            )
                        } catch (photoError: Exception) {
                            Log.e(TAG, "Failed to convert backup photo at index $index: itemId=${photo.itemId}, photoFileName='${photo.photoFileName}'", photoError)
                            throw IllegalStateException(
                                "Failed to convert backup photo at index $index (itemId=${photo.itemId}, photoFileName='${photo.photoFileName}') to current format.",
                                photoError
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Backup photo metadata processing failed. Photos count: ${backupPayload.itemPhotos.orEmpty().size}", e)
                    throw IllegalStateException(
                        "Failed to convert backup photo metadata to current format. Photos count: ${backupPayload.itemPhotos.orEmpty().size}",
                        e
                    )
                }
            } else {
                if (payload != null && importedDb != null) {
                    Log.w(
                        TAG,
                        "Merge fallback: payload had 0 items, using database snapshot ${importedDb.absolutePath}"
                    )
                }
                val databaseSource = importedDb
                    ?: throw IllegalStateException("Selected ZIP is not a supported backup or database archive")
                
                try {
                    importedItems = readItemsFromDatabaseZip(databaseSource).mapIndexed { index, item ->
                        try {
                            val mappedPhoto = item.photoPath
                                .takeIf { it.isNotBlank() }
                                ?.let { extractedPhotos[File(it).name] }
                                .orEmpty()
                            item.copy(photoPath = mappedPhoto.ifBlank { item.photoPath })
                        } catch (itemError: Exception) {
                            Log.e(TAG, "Failed to process database item at index $index: itemName='${item.itemName}', id=${item.id}", itemError)
                            throw IllegalStateException(
                                "Failed to process database item at index $index (itemName='${item.itemName}', id=${item.id}) during restore.",
                                itemError
                            )
                        }
                    }
                    importedCollections = importedItems.map { it.collectionName }
                    importedItemPhotos = try {
                        readItemPhotosFromDatabaseZip(databaseSource)
                    } catch (photoError: Exception) {
                        Log.e(TAG, "Failed to read item photos from database during restore", photoError)
                        throw IllegalStateException("Failed to read item photos from database during restore", photoError)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Database restore processing failed. Database file: ${databaseSource.absolutePath}", e)
                    throw IllegalStateException("Database restore processing failed. Database file: ${databaseSource.absolutePath}", e)
                }
            }

            if (shouldUsePayload && importedDb == null && importedItems.isEmpty() && extractedPhotos.isNotEmpty()) {
                throw IllegalStateException(
                    "Backup contains photos but no importable records. This archive format is incomplete for merge."
                )
            }

            if (importedItems.isEmpty()) {
                throw IllegalStateException(
                    "Backup contains 0 importable records. Merge was cancelled to avoid a misleading no-op."
                )
            }

            val existingItems = try {
                dao.getAllItemsSnapshot(currentTierTag())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get existing items snapshot during merge", e)
                throw IllegalStateException("Failed to get existing items snapshot during merge", e)
            }
            val existingByFingerprint = existingItems
                .associateBy { it.mergeFingerprint() }
                .toMutableMap()

            val oldToNewItemIdMapping = mutableMapOf<Int, Int>()
            var appliedCount = 0
            
            try {
                importedItems.forEach { imported ->
                val fingerprint = imported.mergeFingerprint()
                val existing = existingByFingerprint[fingerprint]
                if (existing == null) {
                    // Track the inserted row id so later duplicates in the same merge update the correct row.
                    val insertedId = dao.insertItem(imported.copy(id = 0, tier = currentTierTag())).toInt()
                    imported.photoPath
                        .takeIf { it in extractedPhotos.values }
                        ?.let(referencedImportedPhotoPaths::add)
                    existingByFingerprint[fingerprint] = imported.copy(id = insertedId)
                    oldToNewItemIdMapping[imported.id] = insertedId
                    appliedCount += 1
                } else {
                    val mergedPhotoPath = choosePreferredPhotoPath(
                        currentPath = existing.photoPath,
                        importedPath = imported.photoPath,
                        digestCache = mergeDigestCache
                    )
                    mergedPhotoPath
                        .takeIf { it in extractedPhotos.values }
                        ?.let(referencedImportedPhotoPaths::add)
                    val newer = if (imported.dateValued >= existing.dateValued) imported else existing
                    val merged = newer.copy(id = existing.id, photoPath = mergedPhotoPath)
                    if (merged != existing) {
                        dao.updateItem(merged)
                        appliedCount += 1
                    }
                    existingByFingerprint[fingerprint] = merged
                    oldToNewItemIdMapping[imported.id] = existing.id
                }
            }

            // Merge additional item photos from imported backup
            importedItemPhotos.forEach { importedPhoto ->
                val newItemId = oldToNewItemIdMapping[importedPhoto.itemId] ?: return@forEach
                val mappedPath = importedPhoto.photoPath
                    .takeIf { it.isNotBlank() }
                    ?.let { extractedPhotos[File(it).name] ?: importedPhoto.photoPath }
                    .orEmpty()
                if (mappedPath.isBlank()) return@forEach

                // Check if this exact photo already exists for this item
                val existingPhotos = itemPhotoDao.getPhotosForItemSnapshot(newItemId)
                val matchingExistingPhoto = existingPhotos.firstOrNull { existingPhoto ->
                    existingPhoto.sortOrder == importedPhoto.sortOrder &&
                        haveSamePhotoContent(existingPhoto.photoPath, mappedPath, mergeDigestCache)
                }

                if (matchingExistingPhoto != null) {
                    if (importedPhoto.isCover) {
                        if (!matchingExistingPhoto.isCover) {
                            itemPhotoDao.setCoverPhoto(newItemId, matchingExistingPhoto.id)
                        }
                        dao.updateCoverPhotoPath(newItemId, matchingExistingPhoto.photoPath)
                    }
                    return@forEach
                }

                val photoAlreadyExists = existingPhotos.any {
                    it.photoPath == mappedPath && it.sortOrder == importedPhoto.sortOrder
                }

                if (!photoAlreadyExists) {
                    // Add the photo to the item
                    runCatching {
                        val insertedPhotoId = itemPhotoDao.insertPhoto(
                            ItemPhoto(
                                itemId = newItemId,
                                photoPath = mappedPath,
                                sortOrder = importedPhoto.sortOrder,
                                isCover = importedPhoto.isCover,
                                createdAtMillis = importedPhoto.createdAtMillis
                            )
                        ).toInt()
                        mappedPath
                            .takeIf { it in extractedPhotos.values }
                            ?.let(referencedImportedPhotoPaths::add)
                        
                        // If this is marked as cover, update it
                        if (importedPhoto.isCover) {
                            itemPhotoDao.setCoverPhoto(newItemId, insertedPhotoId)
                            dao.updateCoverPhotoPath(newItemId, mappedPath)
                        }
                    }.onFailure { error ->
                        Log.w(
                            TAG,
                            "Merge skipped imported photo row for itemId=$newItemId path=$mappedPath sortOrder=${importedPhoto.sortOrder}",
                            error
                        )
                    }
                }
                }
            } catch (mergeError: Exception) {
                Log.e(TAG, "Failed during merge processing. Applied count so far: $appliedCount, Imported items: ${importedItems.size}", mergeError)
                throw IllegalStateException("Failed during merge processing. Applied count so far: $appliedCount, Imported items: ${importedItems.size}", mergeError)
            }

            if (shouldUsePayload) {
                payload.itemAudioNotes.orEmpty().forEach { audioNote ->
                    val newItemId = oldToNewItemIdMapping[audioNote.itemId] ?: return@forEach
                    val audioFileName = audioNote.audioFileName.orEmpty()
                    if (audioFileName.isBlank()) return@forEach
                    val existingAudio = resolveAudioNoteFileForItem(newItemId)
                    if (existingAudio == null) {
                        restoreAudioNoteForItem(newItemId, audioFileName, extractedAudioFiles)
                    }
                }
            }

            if (appliedCount == 0) {
                throw IllegalStateException(
                    "Merge found ${importedItems.size} records but none were new or newer than existing records."
                )
            }

            val mergedCollections = (
                loadManualCollections() +
                    importedCollections +
                    importedItems.map { it.collectionName }
                ).normalizeCollections()
            saveManualCollections(mergedCollections)

            extractedPhotos.values
                .toSet()
                .filterNot(referencedImportedPhotoPaths::contains)
                .forEach { unusedImportedPath ->
                    runCatching {
                        val importedFile = File(unusedImportedPath)
                        if (importedFile.parentFile == photosDir && importedFile.exists()) {
                            importedFile.delete()
                        }
                    }.onFailure { error ->
                        Log.w(TAG, "Failed to delete unused merged photo $unusedImportedPath", error)
                    }
                }

            BackupSummary(
                itemCount = appliedCount,
                photoCount = referencedImportedPhotoPaths.size,
                dedupeCount = dedupedPhotoEntries
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    fun getDatabaseSizeBytes(): Long {
        val dbFile = appContext.getDatabasePath(ValuePicsDatabase.DATABASE_NAME)
        val walFile = File(dbFile.path + "-wal")
        val shmFile = File(dbFile.path + "-shm")
        return listOf(dbFile, walFile, shmFile)
            .filter { it.exists() }
            .sumOf { it.length() }
    }

    suspend fun prepareDatabaseShareAttachment(): DatabaseShareAttachment = withContext(Dispatchers.IO) {
        val internalStamp = SimpleDateFormat("d-M-yy-HHmmss-SSS", Locale.getDefault()).format(Date())
        val visibleStamp = SimpleDateFormat("d-M-yy-HHmm", Locale.getDefault()).format(Date())
        val visiblePrefix = when (currentTierTag()) {
            AppTier.INSURANCE.name -> "VPM"
            else -> "VPO"
        }
        val internalFileName = "$visiblePrefix-$internalStamp.zip"
        val visibleFileName = "$visiblePrefix-$visibleStamp.zip"
        val outFile = File(appContext.cacheDir, internalFileName)

        FileOutputStream(outFile).use { out ->
            writeBackupZip(out, createdAtMillis = System.currentTimeMillis())
        }

        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            outFile
        )

        DatabaseShareAttachment(
            fileName = visibleFileName,
            contentUriString = uri.toString(),
            sizeBytes = outFile.length()
        )
    }

    suspend fun exportCollectionSummaryPdf(): PdfExportResult = exportCollectionSummaryPdf(
        items = dao.getAllItemsSnapshot(currentTierTag()),
        scopeLabel = "All records",
        isFiltered = false,
        reportSortOption = ReportSortOption.NAME_AZ,
        reportTitleOverride = null,
        useWillInstructions = false,
        includeThumbnails = false
    )

    suspend fun exportCollectionSummaryPdf(
        items: List<ValuedItem>,
        scopeLabel: String,
        isFiltered: Boolean,
        reportSortOption: ReportSortOption = ReportSortOption.NAME_AZ,
        reportTitleOverride: String? = null,
        useWillInstructions: Boolean = false,
        includeThumbnails: Boolean = false,
        onProgress: (ExportProgress) -> Unit = { }
    ): PdfExportResult = withContext(Dispatchers.IO) {
        onProgress(ExportProgress(0, 3, "Sorting items..."))
        val reportItems = items
            .filterNot { it.excludeFromPdfReport }
            .let { sortItemsForReport(it, reportSortOption) }
        PhotoUtils.ensureCollectionFolders(appContext)
        
        onProgress(ExportProgress(1, 3, "Building layout..."))
        val normalized = reportItems.map { item ->
            item to item.collectionName.trim().ifBlank { "Uncategorized" }
        }
        val byCollection = normalized.groupBy({ it.second }, { it.first })
        val collectionTotals = buildCollectionTotals(byCollection)
        val metadataLines = buildList {
            add("Scope: $scopeLabel")
            add(
                "Format: " + when {
                    useWillInstructions && includeThumbnails -> "Will record with photos"
                    useWillInstructions -> "Will record text only"
                    includeThumbnails -> "Summary with photos"
                    else -> "Summary text only"
                }
            )
            add(
                "Sort: " + when (reportSortOption) {
                    ReportSortOption.NAME_AZ -> "Name A-Z"
                    ReportSortOption.VALUE_HIGH -> "Value high to low"
                }
            )
            if (isFiltered) add("Filters: Applied")
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val fileName = "valuepics-summary-$stamp.pdf"

        // PDF canvas rendering logic extracted to PdfReportBuilder for separation of concerns
        onProgress(ExportProgress(2, 3, "Rendering PDF..."))
        val bytes = PdfReportBuilder().buildPdf(
            items = reportItems,
            byCollection = byCollection,
            collectionTotals = collectionTotals,
            metadataLines = metadataLines,
            reportTitleOverride = reportTitleOverride,
            useWillInstructions = useWillInstructions,
            includeThumbnails = includeThumbnails
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/ValuePics")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = appContext.contentResolver.insert(MediaStore.Files.getContentUri("external"), values)
                ?: throw IllegalStateException("Unable to create PDF in Documents")

            appContext.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("Unable to write PDF bytes")

            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            appContext.contentResolver.update(uri, values, null, null)
            onProgress(ExportProgress(3, 3, "Complete"))
            PdfExportResult(
                fileName = fileName,
                location = "Documents/ValuePics/$fileName",
                itemCount = reportItems.size,
                scopeLabel = scopeLabel,
                isFiltered = isFiltered,
                contentUriString = uri.toString(),
                absolutePath = null
            )
        } else {
            val docsRoot = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: appContext.filesDir
            val outDir = File(docsRoot, "ValuePics").apply { mkdirs() }
            val outFile = File(outDir, fileName)
            FileOutputStream(outFile).use { it.write(bytes) }
            onProgress(ExportProgress(3, 3, "Complete"))
            PdfExportResult(
                fileName = fileName,
                location = outFile.absolutePath,
                itemCount = reportItems.size,
                scopeLabel = scopeLabel,
                isFiltered = isFiltered,
                contentUriString = null,
                absolutePath = outFile.absolutePath
            )
        }
    }


    private fun createUniquePhotoName(itemId: Int, originalName: String, usedNames: MutableSet<String>): String {
        val safeBase = originalName.ifBlank { "item.jpg" }
        val prefixed = "${itemId}_$safeBase"
        var candidate = prefixed
        var index = 1
        while (!usedNames.add(candidate)) {
            candidate = "${itemId}_${index}_$safeBase"
            index += 1
        }
        return candidate
    }

    private fun createUniqueImportedPhotoFile(photosDir: File, originalName: String): File {
        val safeName = File(originalName).name.ifBlank { "imported_photo.jpg" }
        val dotIndex = safeName.lastIndexOf('.')
        val base = if (dotIndex > 0) safeName.substring(0, dotIndex) else safeName
        val ext = if (dotIndex > 0) safeName.substring(dotIndex) else ""

        var candidate = File(photosDir, safeName)
        var index = 1
        while (candidate.exists()) {
            candidate = File(photosDir, "${base}_merge_$index$ext")
            index += 1
        }
        return candidate
    }

    private data class MergePhotoImportResult(
        val path: String,
        val reusedExisting: Boolean
    )

    private fun importMergedPhotoWithDedupe(
        zip: ZipInputStream,
        photosDir: File,
        tempDir: File,
        safeName: String,
        digestToPath: MutableMap<String, String>
    ): MergePhotoImportResult {
        val tempFile = File.createTempFile("merge_photo_", ".tmp", tempDir)
        return try {
            FileOutputStream(tempFile).use { out -> zip.copyTo(out) }
            val digest = computeSha256(tempFile)
            val existingPath = digestToPath[digest]
            if (existingPath != null) {
                MergePhotoImportResult(path = existingPath, reusedExisting = true)
            } else {
                val outFile = createUniqueImportedPhotoFile(photosDir, safeName)
                tempFile.copyTo(outFile, overwrite = true)
                digestToPath[digest] = outFile.absolutePath
                MergePhotoImportResult(path = outFile.absolutePath, reusedExisting = false)
            }
        } finally {
            tempFile.delete()
        }
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { "%02x".format(it) }
    }

    private fun ValuedItem.mergeFingerprint(): String {
        val createdAtKey = if (createdAtMillis > 0L) createdAtMillis.toString() else ""
        val billsPeriodKey = billsEnteredPeriod.trim().uppercase(Locale.getDefault())
        return listOf(
            itemName.trim().lowercase(Locale.getDefault()),
            itemDescription.trim().lowercase(Locale.getDefault()),
            collectionName.trim().lowercase(Locale.getDefault()),
            dateTaken.trim().lowercase(Locale.getDefault()),
            sourceUrl.trim().lowercase(Locale.getDefault()),
            createdAtKey,
            billsPeriodKey
        ).joinToString("|")
    }

    private fun choosePreferredPhotoPath(currentPath: String, importedPath: String): String {
        val importedExisting = importedPath.takeIf { it.isNotBlank() && File(it).exists() }
        val currentExisting = currentPath.takeIf { it.isNotBlank() && File(it).exists() }
        return importedExisting ?: currentExisting.orEmpty()
    }

    private fun choosePreferredPhotoPath(
        currentPath: String,
        importedPath: String,
        digestCache: MutableMap<String, String?>
    ): String {
        val importedExisting = importedPath.takeIf { it.isNotBlank() && File(it).exists() }
        val currentExisting = currentPath.takeIf { it.isNotBlank() && File(it).exists() }
        return when {
            importedExisting != null && currentExisting != null && haveSamePhotoContent(currentExisting, importedExisting, digestCache) -> currentExisting
            importedExisting != null -> importedExisting
            else -> currentExisting.orEmpty()
        }
    }

    private fun haveSamePhotoContent(
        firstPath: String,
        secondPath: String,
        digestCache: MutableMap<String, String?>
    ): Boolean {
        val firstDigest = computeSha256ForPath(firstPath, digestCache)
        val secondDigest = computeSha256ForPath(secondPath, digestCache)
        return firstDigest != null && firstDigest == secondDigest
    }

    private fun computeSha256ForPath(path: String, digestCache: MutableMap<String, String?>): String? {
        val normalized = path.trim()
        if (normalized.isBlank()) return null
        return digestCache.getOrPut(normalized) {
            val file = File(normalized)
            if (file.exists() && file.isFile) computeSha256(file) else null
        }
    }

    private fun parsePayload(json: String): BackupPayload {
        val payloadType = object : TypeToken<BackupPayload>() {}.type
        val parsed = try {
            gson.fromJson<BackupPayload>(json, payloadType)
        } catch (e: JsonParseException) {
            throw IllegalStateException(
                "Backup data is invalid or corrupted (items.json parse failed).",
                e
            )
        } catch (e: RuntimeException) {
            // Defensive guard for malformed payloads that surface as Kotlin/Gson runtime errors.
            // This includes "attempted to invoke java class" errors from reflection issues.
            throw IllegalStateException(
                "Backup data format is incompatible or corrupted (items.json parse failed). This may be due to class structure changes or obfuscation issues.",
                e
            )
        } catch (e: Exception) {
            // Catch-all for any other parsing errors
            throw IllegalStateException(
                "Backup data could not be parsed (items.json parse failed).",
                e
            )
        } ?: return BackupPayload(version = 1, items = emptyList())
        
        // Allow all backup formats - attempt to restore regardless of version
        // The app should be backward compatible and handle unknown fields gracefully
        // Gson does not apply Kotlin default values — null-guard all list fields here.
        val normalized = parsed.copy(
            appVersionName = parsed.appVersionName.orEmpty(),
            items = parsed.items.orEmpty(),
            itemPhotos = parsed.itemPhotos.orEmpty(),
            itemAudioNotes = parsed.itemAudioNotes.orEmpty(),
            manualCollections = parsed.manualCollections.orEmpty(),
            manualTags = parsed.manualTags.orEmpty()
        )

        // Older obfuscated builds serialized compact field names (a,b,c,...) instead of
        // property names. In that case the typed Gson parse above yields empty lists.
        if (
            normalized.items.orEmpty().isEmpty() &&
            normalized.itemPhotos.orEmpty().isEmpty() &&
            normalized.itemAudioNotes.orEmpty().isEmpty() &&
            normalized.manualCollections.orEmpty().isEmpty() &&
            normalized.manualTags.orEmpty().isEmpty()
        ) {
            parseCompactPayload(json)?.let { return it }
        }

        return normalized
    }

    private fun parseCompactPayload(json: String): BackupPayload? {
        val root = runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull() ?: return null
        if (!root.has("d") && !root.has("e") && !root.has("g")) return null

        val items = root.getJsonArrayOrEmpty("d")
            .mapNotNull { it.asJsonObjectOrNull()?.toCompactBackupItemRecord() }
        val itemPhotos = root.getJsonArrayOrEmpty("e")
            .mapNotNull { it.asJsonObjectOrNull()?.toCompactBackupItemPhotoRecord() }
        val itemAudioNotes = root.getJsonArrayOrEmpty("f")
            .mapNotNull { it.asJsonObjectOrNull()?.toCompactBackupItemAudioNoteRecord() }
        val manualCollections = root.getJsonArrayOrEmpty("g")
            .mapNotNull { element ->
                element.takeIf { !it.isJsonNull }?.asString?.trim()?.takeIf { it.isNotBlank() }
            }

        return BackupPayload(
            version = root.getIntOrDefault("a", 1),
            createdAtMillis = root.getLongOrDefault("b", 0L),
            appVersionName = root.getStringOrDefault("c", ""),
            items = items,
            itemPhotos = itemPhotos,
            itemAudioNotes = itemAudioNotes,
            manualCollections = manualCollections,
            unlimitedUnlocked = root.getBooleanOrDefault("h", false),
            unlockAccountId = root.getStringOrDefault("i", "")
        )
    }

    private fun JsonObject.toCompactBackupItemRecord(): BackupItemRecord {
        return BackupItemRecord(
            id = getIntOrDefault("a", 0),
            photoFileName = getStringOrNull("b"),
            photoSource = getStringOrDefault("c", "camera"),
            itemName = getStringOrDefault("d", ""),
            collectionName = getStringOrDefault("e", ""),
            shortAiDescription = getStringOrDefault("f", ""),
            fullWebDescription = getStringOrDefault("g", ""),
            itemDescription = getStringOrDefault("h", ""),
            detectedLabels = getStringOrDefault("i", ""),
            estimatedValue = getDoubleOrNull("j"),
            currency = getStringOrDefault("k", "AUD"),
            valueSource = getStringOrDefault("l", ""),
            sourceUrl = getStringOrDefault("m", ""),
            searchResults = getStringOrDefault("n", ""),
            confidence = getFloatOrDefault("o", 0.95f),
            createdAtMillis = getLongOrNull("p"),
            dateTaken = getStringOrDefault("q", ""),
            dateValued = getLongOrDefault("r", System.currentTimeMillis()),
            willInstructions = getStringOrDefault("s", ""),
            notes = getStringOrDefault("t", ""),
            tags = getStringOrDefault("u", ""),
            includeInTotals = getBooleanOrDefault("v", true),
            billsEnteredPeriod = getStringOrDefault("w", "")
        )
    }

    private fun JsonObject.toCompactBackupItemPhotoRecord(): BackupItemPhotoRecord {
        return BackupItemPhotoRecord(
            itemId = getIntOrDefault("a", 0),
            photoFileName = getStringOrDefault("b", ""),
            sortOrder = getIntOrDefault("c", 0),
            isCover = getBooleanOrDefault("d", false),
            createdAtMillis = getLongOrDefault("e", System.currentTimeMillis())
        )
    }

    private fun JsonObject.toCompactBackupItemAudioNoteRecord(): BackupItemAudioNoteRecord {
        return BackupItemAudioNoteRecord(
            itemId = getIntOrDefault("a", 0),
            audioFileName = getStringOrDefault("b", "")
        )
    }

    private fun JsonObject.getJsonArrayOrEmpty(key: String): List<JsonElement> {
        val element = get(key) ?: return emptyList()
        if (!element.isJsonArray) return emptyList()
        return element.asJsonArray.toList()
    }

    private fun JsonElement?.asJsonObjectOrNull(): JsonObject? {
        if (this == null || this.isJsonNull || !this.isJsonObject) return null
        return this.asJsonObject
    }

    private fun JsonObject.getStringOrDefault(key: String, defaultValue: String): String {
        val element = get(key) ?: return defaultValue
        if (element.isJsonNull) return defaultValue
        return runCatching { element.asString }.getOrDefault(defaultValue)
    }

    private fun JsonObject.getStringOrNull(key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return runCatching { element.asString }.getOrNull()
    }

    private fun JsonObject.getIntOrDefault(key: String, defaultValue: Int): Int {
        val element = get(key) ?: return defaultValue
        if (element.isJsonNull) return defaultValue
        return runCatching { element.asInt }.getOrDefault(defaultValue)
    }

    private fun JsonObject.getLongOrDefault(key: String, defaultValue: Long): Long {
        val element = get(key) ?: return defaultValue
        if (element.isJsonNull) return defaultValue
        return runCatching { element.asLong }.getOrDefault(defaultValue)
    }

    private fun JsonObject.getLongOrNull(key: String): Long? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return runCatching { element.asLong }.getOrNull()
    }

    private fun JsonObject.getDoubleOrNull(key: String): Double? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null
        return runCatching { element.asDouble }.getOrNull()
    }

    private fun JsonObject.getFloatOrDefault(key: String, defaultValue: Float): Float {
        val element = get(key) ?: return defaultValue
        if (element.isJsonNull) return defaultValue
        return runCatching { element.asFloat }.getOrDefault(defaultValue)
    }

    private fun JsonObject.getBooleanOrDefault(key: String, defaultValue: Boolean): Boolean {
        val element = get(key) ?: return defaultValue
        if (element.isJsonNull) return defaultValue
        return runCatching { element.asBoolean }.getOrDefault(defaultValue)
    }

    private fun validatePayloadForRestore(payload: BackupPayload, allowOlderBackup: Boolean) {
        // Allow all backup versions without any validation
        // The app should be backward compatible and handle any version gracefully
        
        // Only warn about very old backups if explicitly requested, but don't block restore
        val lastBackupMillis = getLastBackupMillis()
        if (!allowOlderBackup && lastBackupMillis > 0L && payload.createdAtMillis > 0L) {
            val timeDiff = lastBackupMillis - payload.createdAtMillis
            // Only warn for extremely old backups (more than 1 year) but don't block
            if (timeDiff > 365 * 24 * 60 * 60 * 1000L) { // 1 year in milliseconds
                val payloadTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(payload.createdAtMillis))
                val latestLocalTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastBackupMillis))
                // Log warning but don't throw exception - allow restore to proceed
                android.util.Log.w(
                    "ValuePicsRepository",
                    "Restoring very old backup from $payloadTime (latest local was $latestLocalTime)"
                )
            }
        }
    }

    private fun inspectSingleAutoBackupSlot(slotId: String, file: File): AutoBackupSlotInfo {
        if (!file.exists()) {
            return AutoBackupSlotInfo(
                slotId = slotId,
                filePath = file.absolutePath,
                exists = false,
                fileSizeBytes = 0L,
                lastModifiedMillis = 0L,
                createdAtMillis = null,
                itemCount = null,
                photoCount = null,
                isRestorable = false,
                isLatest = false,
                warningMessage = null,
                corruptionMessage = "Backup slot $slotId does not exist yet."
            )
        }
        if (file.length() <= 0L) {
            return AutoBackupSlotInfo(
                slotId = slotId,
                filePath = file.absolutePath,
                exists = true,
                fileSizeBytes = file.length(),
                lastModifiedMillis = file.lastModified(),
                createdAtMillis = null,
                itemCount = null,
                photoCount = null,
                isRestorable = false,
                isLatest = false,
                warningMessage = null,
                corruptionMessage = "Backup slot $slotId is empty (0 bytes), which indicates a failed or interrupted write."
            )
        }

        return runCatching {
            var payload: BackupPayload? = null
            var photoCount = 0
            var hasDatabaseArchive = false
            FileInputStream(file).use { rawInput ->
                ZipInputStream(BufferedInputStream(rawInput)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            when {
                                entry.name == "data/items.json" -> {
                                    val json = zip.readBytes().toString(Charsets.UTF_8)
                                    payload = parsePayload(json)
                                }
                                entry.name.startsWith("photos/") -> {
                                    val zipName = entry.name.substringAfter("photos/")
                                    if (zipName.isNotBlank()) photoCount += 1
                                }
                                isLikelyDatabaseZipEntry(entry.name) -> {
                                    hasDatabaseArchive = true
                                }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            if (payload == null && !hasDatabaseArchive) {
                throw IllegalStateException(
                    "Missing backup contents. Expected data/items.json (modern backup) or an embedded SQLite database file in the ZIP."
                )
            }

            if (payload != null && payload!!.version < MIN_RESTORE_BACKUP_FORMAT_VERSION) {
                throw IllegalStateException(
                    "Backup format v${payload!!.version} is too old. This app restores v$MIN_RESTORE_BACKUP_FORMAT_VERSION and newer."
                )
            }

            AutoBackupSlotInfo(
                slotId = slotId,
                filePath = file.absolutePath,
                exists = true,
                fileSizeBytes = file.length(),
                lastModifiedMillis = file.lastModified(),
                createdAtMillis = payload?.createdAtMillis?.takeIf { it > 0L } ?: file.lastModified(),
                itemCount = payload?.items?.size,
                photoCount = payload?.items?.let { photoCount },
                isRestorable = true,
                isLatest = false,
                warningMessage = null,
                corruptionMessage = null
            )
        }.getOrElse { error ->
            val reason = error.message?.trim().takeUnless { it.isNullOrBlank() }
                ?: "ZIP could not be read. The file may be truncated or not a valid backup archive."
            AutoBackupSlotInfo(
                slotId = slotId,
                filePath = file.absolutePath,
                exists = true,
                fileSizeBytes = file.length(),
                lastModifiedMillis = file.lastModified(),
                createdAtMillis = null,
                itemCount = null,
                photoCount = null,
                isRestorable = false,
                isLatest = false,
                warningMessage = null,
                corruptionMessage = reason
            )
        }
    }

    private fun verifyBackupZip(uri: Uri): Boolean {
        return runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { rawInput ->
                ZipInputStream(BufferedInputStream(rawInput)).use { zip ->
                    var hasItemsJson = false
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory && entry.name == "data/items.json") {
                            hasItemsJson = true
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                    hasItemsJson
                }
            } ?: false
        }.getOrDefault(false)
    }

    private fun loadManualCollections(): List<String> {
        val raw = collectionPrefs.getStringSet(manualCollectionsKey, emptySet()).orEmpty()
        return raw.toList().normalizeCollections()
    }

    private fun loadManualTabs(): List<String> {
        val raw = collectionPrefs.getStringSet(manualTabsKey, emptySet()).orEmpty()
        return raw.toList().normalizeCollections()
    }

    private fun loadManualTags(): List<String> {
        val raw = collectionPrefs.getStringSet(manualTagsKey, emptySet()).orEmpty()
        return raw.toList().normalizeCollections()
    }

    private fun loadLegacyValuationDraft(): ValuationDraft? {
        val raw = collectionPrefs.getString(legacyValuationDraftKey, null) ?: return null
        return runCatching {
            gson.fromJson(raw, ValuationDraft::class.java)
        }.getOrNull()
    }

    private fun clearLegacyValuationDraft() {
        collectionPrefs.edit().remove(legacyValuationDraftKey).apply()
    }

    private fun saveManualCollections(collections: List<String>) {
        val normalized = collections.normalizeCollections().toSet()
        collectionPrefs.edit().putStringSet(manualCollectionsKey, normalized).apply()
        manualCollectionsFlow.value = normalized.toList().normalizeCollections()
    }

    private fun saveManualTabs(tabs: List<String>, initialized: Boolean = true) {
        val normalized = tabs.normalizeCollections().toSet()
        collectionPrefs.edit()
            .putStringSet(manualTabsKey, normalized)
            .putBoolean(manualTabsInitializedKey, initialized)
            .apply()
        manualTabsFlow.value = normalized.toList().normalizeCollections()
    }

    private fun saveManualTags(tags: List<String>) {
        val normalized = tags.normalizeCollections().toSet()
        collectionPrefs.edit().putStringSet(manualTagsKey, normalized).apply()
        manualTagsFlow.value = normalized.toList().normalizeCollections()
    }

    private suspend fun resolveEditableTabs(): List<String> {
        return loadManualTabs()
    }

    private suspend fun loadAllTagsSnapshot(tier: String): List<String> {
        return (dao.getAllItemsSnapshot(tier).flatMap { TagUtils.parseTags(it.tags) } + loadManualTags())
            .normalizeCollections()
    }

    private fun syncManagedTagsFromRawTags(rawTags: String) {
        val previous = loadManualTags()
        val normalized = (previous + TagUtils.parseTags(rawTags)).normalizeCollections()
        if (normalized != previous) {
            saveManualTags(normalized)
        }
    }

    private fun isManualTabsInitialized(): Boolean {
        return collectionPrefs.getBoolean(manualTabsInitializedKey, false)
    }

    private suspend fun writeBackupZip(rawOutput: OutputStream, createdAtMillis: Long): Pair<Int, Int> {
        val tier = currentTierTag()
        val items = dao.getAllItemsSnapshot(tier)
        return writeBackupZip(rawOutput, createdAtMillis, items)
    }

    private suspend fun writeBackupZip(
        rawOutput: OutputStream,
        createdAtMillis: Long,
        items: List<ValuedItem>
    ): Pair<Int, Int> {
        PhotoUtils.ensureCollectionFolders(appContext)
        val usedNames = mutableSetOf<String>()
        val usedAudioNames = mutableSetOf<String>()
        val photoFileNamesBySourcePath = mutableMapOf<String, String>()
        val audioFileNamesByItemId = mutableMapOf<Int, String>()
        val records = mutableListOf<BackupItemRecord>()
        val itemPhotoRecords = mutableListOf<BackupItemPhotoRecord>()
        val itemAudioNoteRecords = mutableListOf<BackupItemAudioNoteRecord>()
        var photoCount = 0

        ZipOutputStream(BufferedOutputStream(rawOutput)).use { zip ->
            fun addPhotoToZipOnce(sourcePath: String, itemId: Int): String? {
                val normalizedPath = sourcePath.trim()
                if (normalizedPath.isBlank()) return null
                photoFileNamesBySourcePath[normalizedPath]?.let { return it }

                val sourcePhoto = File(normalizedPath)
                if (!sourcePhoto.exists()) return null

                val uniqueName = createUniquePhotoName(itemId, sourcePhoto.name, usedNames)
                zip.putNextEntry(ZipEntry("photos/$uniqueName"))
                sourcePhoto.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                photoFileNamesBySourcePath[normalizedPath] = uniqueName
                photoCount += 1
                return uniqueName
            }

            fun addAudioToZipOnce(itemId: Int): String? {
                if (itemId <= 0) return null
                audioFileNamesByItemId[itemId]?.let { return it }
                val sourceAudio = resolveAudioNoteFileForItem(itemId) ?: return null
                val safeBase = sourceAudio.name.ifBlank { "item_${itemId}.${sourceAudio.extension.ifBlank { "3gp" }}" }
                var candidate = safeBase
                var index = 1
                while (!usedAudioNames.add(candidate)) {
                    val dot = safeBase.lastIndexOf('.')
                    candidate = if (dot > 0) {
                        "${safeBase.substring(0, dot)}_$index${safeBase.substring(dot)}"
                    } else {
                        "${safeBase}_$index"
                    }
                    index += 1
                }
                zip.putNextEntry(ZipEntry("audio_notes/$candidate"))
                sourceAudio.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                audioFileNamesByItemId[itemId] = candidate
                return candidate
            }

            // Backup cover photo from valued_items and all additional photos from item_photos
            items.forEach { item ->
                val photoFileName = addPhotoToZipOnce(item.photoPath, item.id)

                // Also backup all photo metadata from item_photos table.
                itemPhotoDao.getPhotosForItemSnapshot(item.id).forEach { itemPhoto ->
                    val itemPhotoFileName = addPhotoToZipOnce(itemPhoto.photoPath, item.id)
                    if (itemPhotoFileName != null) {
                        itemPhotoRecords += BackupItemPhotoRecord(
                            itemId = item.id,
                            photoFileName = itemPhotoFileName,
                            sortOrder = itemPhoto.sortOrder,
                            isCover = itemPhoto.isCover,
                            createdAtMillis = itemPhoto.createdAtMillis
                        )
                    }
                }

                records += BackupItemRecord.fromItem(item, photoFileName)
                addAudioToZipOnce(item.id)?.let { audioName ->
                    itemAudioNoteRecords += BackupItemAudioNoteRecord(
                        itemId = item.id,
                        audioFileName = audioName
                    )
                }
            }

            val payload = BackupPayload(
                version = BACKUP_FORMAT_VERSION,
                createdAtMillis = createdAtMillis,
                appVersionName = currentAppVersionName(),
                items = records,
                itemPhotos = itemPhotoRecords,
                itemAudioNotes = itemAudioNoteRecords,
                manualCollections = loadManualCollections(),
                manualTags = loadManualTags(),
                unlimitedUnlocked = isUnlimitedUnlocked(),
                unlockAccountId = getUnlockAccountId()
            )
            zip.putNextEntry(ZipEntry("data/items.json"))
            zip.write(gson.toJson(payload).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        return items.size to photoCount
    }

    private fun currentAppVersionName(): String {
        return runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName.orEmpty()
        }.getOrDefault("-")
    }

    private fun resolveImportedDatabaseFile(extractedDatabaseFiles: Map<String, File>): File? {
        if (extractedDatabaseFiles.isEmpty()) return null

        val lowerCaseFiles = extractedDatabaseFiles.entries.associate { entry ->
            entry.key.lowercase(Locale.getDefault()) to entry.value
        }

        val candidateNames = listOf(
            ValuePicsDatabase.DATABASE_NAME,
            "${ValuePicsDatabase.DATABASE_NAME}.db",
            "value_finder_database",
            "value_finder_database.db",
            "valuefinder_database",
            "valuefinder_database.db",
            "valued_items.db",
            "main.db",
            "database.sqlite",
            "database.sqlite3"
        )
        candidateNames.firstNotNullOfOrNull {
            lowerCaseFiles[it.lowercase(Locale.getDefault())]
        }?.let { return it }

        if (extractedDatabaseFiles.size == 1) {
            return extractedDatabaseFiles.values.first()
        }

        return extractedDatabaseFiles.values
            .filter { file ->
            val name = file.name.lowercase(Locale.getDefault())
            name.endsWith(".db") ||
                name.endsWith(".sqlite") ||
                name.endsWith(".sqlite3") ||
                name.endsWith(".db3") ||
                name.contains("database")
            }
            .maxByOrNull { it.length() }
    }

    private fun isLikelyDatabaseZipEntry(entryName: String): Boolean {
        if (entryName.isBlank()) return false
        val normalized = entryName.replace('\\', '/').lowercase(Locale.getDefault())
        if (normalized.endsWith("-wal") || normalized.endsWith("-shm")) return false
        if (normalized.startsWith("database/")) return true

        val fileName = File(entryName).name.lowercase(Locale.getDefault())
        return fileName == ValuePicsDatabase.DATABASE_NAME.lowercase(Locale.getDefault()) ||
            fileName == "${ValuePicsDatabase.DATABASE_NAME.lowercase(Locale.getDefault())}.db" ||
            fileName.endsWith(".db") ||
            fileName.endsWith(".sqlite") ||
            fileName.endsWith(".sqlite3") ||
            fileName.endsWith(".db3") ||
            fileName.contains("database")
    }

    private suspend fun restoreBackupPayload(
        payload: BackupPayload,
        extractedPhotos: Map<String, String>,
        extractedAudioFiles: Map<String, String>
    ): BackupSummary {
        val normalizedUnlockAccountId = payload.unlockAccountId
            .orEmpty()
            .trim()
            .lowercase(Locale.getDefault())
        saveManualCollections(payload.manualCollections.orEmpty())
        val backupItems = payload.items.orEmpty()
        saveManualTags(
            (payload.manualTags.orEmpty() + backupItems.flatMap { TagUtils.parseTags(it.tags.orEmpty()) })
                .normalizeCollections()
        )
        collectionPrefs.edit()
            .putBoolean(unlimitedUnlockedKey, payload.unlimitedUnlocked)
            .putString(unlockAccountIdKey, normalizedUnlockAccountId)
            .apply()
        clearAllAudioNotes()
        val tier = currentTierTag()
        val oldToNewItemId = mutableMapOf<Int, Int>()
        var restoredPhotos = 0
        // Wrap clear + insert in a single transaction so any mid-loop failure rolls back
        // instead of leaving the database empty.
        database.withTransaction {
            // Delete only current-tier items so the other tier's data is preserved.
            // Also clear item_photos rows belonging to those items via cascading delete.
            val tierItems = dao.getAllItemsSnapshot(tier)
            tierItems.forEach { item ->
                itemPhotoDao.deletePhotosByItemId(item.id)
            }
            dao.clearAllItemsByTier(tier)
            backupItems.forEach { record ->
                val photoPath = record.photoFileName
                    ?.let { extractedPhotos[File(it).name] }
                    .orEmpty()
                if (photoPath.isNotBlank()) restoredPhotos += 1
                val insertedId = dao.insertItem(record.toValuedItem(photoPath).copy(id = 0, tier = tier)).toInt()
                oldToNewItemId[record.id] = insertedId
                if (payload.itemPhotos.orEmpty().isEmpty() && photoPath.isNotBlank()) {
                    itemPhotoDao.insertPhoto(
                        ItemPhoto(
                            itemId = insertedId,
                            photoPath = photoPath,
                            sortOrder = 0,
                            isCover = true,
                            createdAtMillis = record.createdAtMillis ?: record.dateValued
                        )
                    )
                }
            }
            payload.itemPhotos.orEmpty().forEach { photo ->
                val newItemId = oldToNewItemId[photo.itemId] ?: return@forEach
                val mappedPath = photo.photoFileName
                    ?.let { extractedPhotos[File(it).name] }
                    .orEmpty()
                if (mappedPath.isBlank()) return@forEach
                restoredPhotos += 1
                val insertedPhotoId = itemPhotoDao.insertPhoto(
                    ItemPhoto(
                        itemId = newItemId,
                        photoPath = mappedPath,
                        sortOrder = photo.sortOrder,
                        isCover = photo.isCover,
                        createdAtMillis = photo.createdAtMillis
                    )
                ).toInt()
                if (photo.isCover) {
                    itemPhotoDao.setCoverPhoto(newItemId, insertedPhotoId)
                    dao.updateCoverPhotoPath(newItemId, mappedPath)
                }
            }
            // Deduplicate in case the backup itself contained duplicate item_photos rows.
            val dupesRemoved = deduplicateItemPhotos()
            if (dupesRemoved > 0) Log.i(TAG, "restoreBackupPayload: dedup removed $dupesRemoved rows")
        }
        payload.itemAudioNotes.orEmpty().forEach { audioNote ->
            val newItemId = oldToNewItemId[audioNote.itemId] ?: return@forEach
            val audioFileName = audioNote.audioFileName.orEmpty()
            if (audioFileName.isBlank()) return@forEach
            restoreAudioNoteForItem(newItemId, audioFileName, extractedAudioFiles)
        }
        return BackupSummary(itemCount = backupItems.size, photoCount = restoredPhotos)
    }

    private suspend fun restoreDatabaseZip(
        databaseFile: File,
        extractedPhotos: Map<String, String>
    ): BackupSummary {
        val importedItems = readItemsFromDatabaseZip(databaseFile)
        if (importedItems.isEmpty()) {
            throw IllegalStateException(
                "Selected database backup contains 0 records. Restore was cancelled to avoid wiping existing data."
            )
        }
        val importedItemPhotos = readItemPhotosFromDatabaseZip(databaseFile)
        val importedCollections = importedItems
            .map { it.collectionName.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .sortedBy { it.lowercase(Locale.getDefault()) }
        val importedTags = importedItems
            .flatMap { TagUtils.parseTags(it.tags) }
            .normalizeCollections()
        saveManualCollections(importedCollections)
        saveManualTags(importedTags)
        val oldToNewItemId = mutableMapOf<Int, Int>()
        var restoredPhotos = 0
        database.withTransaction {
            // Clear both tables explicitly before re-inserting, but keep the whole restore atomic.
            val tierItems = dao.getAllItemsSnapshot(currentTierTag())
            tierItems.forEach { item -> itemPhotoDao.deletePhotosByItemId(item.id) }
            dao.clearAllItemsByTier(currentTierTag())

            importedItems.forEach { item ->
                val mappedPhoto = item.photoPath
                    .takeIf { it.isNotBlank() }
                    ?.let { extractedPhotos[File(it).name] }
                    .orEmpty()
                if (mappedPhoto.isNotBlank()) restoredPhotos += 1
                val insertedId = dao.insertItem(
                    item.copy(
                        id = 0,
                        photoPath = mappedPhoto.ifBlank { item.photoPath },
                        tier = currentTierTag()
                    )
                ).toInt()
                oldToNewItemId[item.id] = insertedId
            }

            // Rebuild additional per-item photos from legacy database backups.
            importedItemPhotos.forEach { photo ->
                val newItemId = oldToNewItemId[photo.itemId] ?: return@forEach
                val mappedPath = photo.photoPath
                    .takeIf { it.isNotBlank() }
                    ?.let { extractedPhotos[File(it).name] ?: photo.photoPath }
                    .orEmpty()
                if (mappedPath.isBlank()) return@forEach
                if (extractedPhotos[File(photo.photoPath).name] != null) restoredPhotos += 1
                val insertedPhotoId = itemPhotoDao.insertPhoto(
                    ItemPhoto(
                        itemId = newItemId,
                        photoPath = mappedPath,
                        sortOrder = photo.sortOrder,
                        isCover = photo.isCover,
                        createdAtMillis = photo.createdAtMillis
                    )
                ).toInt()
                if (photo.isCover) {
                    itemPhotoDao.setCoverPhoto(newItemId, insertedPhotoId)
                    dao.updateCoverPhotoPath(newItemId, mappedPath)
                }
            }

            // Deduplicate in case the legacy database backup itself contained duplicate rows.
            val dupesRemoved = deduplicateItemPhotos()
            if (dupesRemoved > 0) Log.i(TAG, "restoreDatabaseZip: dedup removed $dupesRemoved rows")
        }
        return BackupSummary(itemCount = importedItems.size, photoCount = restoredPhotos)
    }

    private fun readItemsFromDatabaseZip(databaseFile: File): List<ValuedItem> {
        val importedDatabase = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            val cursor = importedDatabase.query(
                "valued_items",
                null,
                null,
                null,
                null,
                null,
                "dateValued DESC"
            )
            cursor.use { rows ->
                buildList {
                    while (rows.moveToNext()) {
                        add(rows.toImportedValuedItem())
                    }
                }
            }
        } finally {
            importedDatabase.close()
        }
    }

    private fun readItemPhotosFromDatabaseZip(databaseFile: File): List<ImportedItemPhotoRecord> {
        val importedDatabase = SQLiteDatabase.openDatabase(databaseFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            val hasTable = importedDatabase.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='item_photos'",
                null
            ).use { it.moveToFirst() }
            if (!hasTable) return emptyList()

            val cursor = importedDatabase.query(
                "item_photos",
                null,
                null,
                null,
                null,
                null,
                "sortOrder ASC, id ASC"
            )
            cursor.use { rows ->
                buildList {
                    while (rows.moveToNext()) {
                        fun intOrDefault(columnName: String, defaultValue: Int = 0): Int {
                            val index = rows.getColumnIndex(columnName)
                            return if (index >= 0 && !rows.isNull(index)) rows.getInt(index) else defaultValue
                        }

                        fun longOrDefault(columnName: String, defaultValue: Long = 0L): Long {
                            val index = rows.getColumnIndex(columnName)
                            return if (index >= 0 && !rows.isNull(index)) rows.getLong(index) else defaultValue
                        }

                        fun boolOrDefault(columnName: String, defaultValue: Boolean = false): Boolean {
                            val index = rows.getColumnIndex(columnName)
                            return if (index >= 0 && !rows.isNull(index)) rows.getInt(index) != 0 else defaultValue
                        }

                        fun stringOrDefault(columnName: String, defaultValue: String = ""): String {
                            val index = rows.getColumnIndex(columnName)
                            return if (index >= 0 && !rows.isNull(index)) rows.getString(index) else defaultValue
                        }

                        add(
                            ImportedItemPhotoRecord(
                                itemId = intOrDefault("itemId"),
                                photoPath = stringOrDefault("photoPath"),
                                sortOrder = intOrDefault("sortOrder"),
                                isCover = boolOrDefault("isCover", defaultValue = false),
                                createdAtMillis = longOrDefault("createdAtMillis", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }
        } finally {
            importedDatabase.close()
        }
    }

    private fun Cursor.toImportedValuedItem(): ValuedItem {
        fun stringOrDefault(columnName: String, defaultValue: String = ""): String {
            val index = getColumnIndex(columnName)
            return if (index >= 0 && !isNull(index)) getString(index) else defaultValue
        }

        fun intOrDefault(columnName: String, defaultValue: Int = 0): Int {
            val index = getColumnIndex(columnName)
            return if (index >= 0 && !isNull(index)) getInt(index) else defaultValue
        }

        fun longOrDefault(columnName: String, defaultValue: Long = 0L): Long {
            val index = getColumnIndex(columnName)
            return if (index >= 0 && !isNull(index)) getLong(index) else defaultValue
        }

        fun floatOrDefault(columnName: String, defaultValue: Float = 0f): Float {
            val index = getColumnIndex(columnName)
            return if (index >= 0 && !isNull(index)) getFloat(index) else defaultValue
        }

        fun boolOrDefault(columnName: String, defaultValue: Boolean = true): Boolean {
            val index = getColumnIndex(columnName)
            return if (index >= 0 && !isNull(index)) getInt(index) != 0 else defaultValue
        }

        fun doubleOrNull(columnName: String): Double? {
            val index = getColumnIndex(columnName)
            return if (index >= 0 && !isNull(index)) getDouble(index) else null
        }

        return ValuedItem(
            id = intOrDefault("id"),
            photoPath = stringOrDefault("photoPath"),
            photoSource = stringOrDefault("photoSource", "camera"),
            itemName = stringOrDefault("itemName"),
            collectionName = stringOrDefault("collectionName"),
            shortAiDescription = stringOrDefault("shortAiDescription"),
            fullWebDescription = stringOrDefault("fullWebDescription"),
            itemDescription = stringOrDefault("itemDescription"),
            detectedLabels = stringOrDefault("detectedLabels"),
            estimatedValue = doubleOrNull("estimatedValue"),
            currency = stringOrDefault("currency", "AUD"),
            valueSource = stringOrDefault("valueSource"),
            sourceUrl = stringOrDefault("sourceUrl"),
            searchResults = stringOrDefault("searchResults"),
            confidence = floatOrDefault("confidence"),
            createdAtMillis = longOrDefault("createdAtMillis", longOrDefault("dateValued", System.currentTimeMillis())),
            dateTaken = stringOrDefault("dateTaken"),
            dateValued = longOrDefault("dateValued", System.currentTimeMillis()),
            willInstructions = stringOrDefault("willInstructions"),
            notes = stringOrDefault("notes"),
            tags = stringOrDefault("tags"),
            billsEnteredPeriod = stringOrDefault("billsEnteredPeriod"),
            includeInTotals = boolOrDefault("includeInTotals", defaultValue = true)
        )
    }
}

