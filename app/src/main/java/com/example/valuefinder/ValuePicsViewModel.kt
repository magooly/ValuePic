/*
 * Copyright (c) 2026 Wally Horsman.
 * All rights reserved.
 */

package com.example.valuefinder

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.valuefinder.ui.UiError
import com.example.valuefinder.util.RetryUtil
import com.example.valuefinder.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ValuePicsViewModel(
    private val repository: ValuePicsRepository,
    private val recognitionService: ImageRecognitionService,
    private val appContext: Context
) : ViewModel() {
    constructor(context: Context) : this(
        ValuePicsRepository(context.applicationContext),
        ImageRecognitionService(context.applicationContext),
        context.applicationContext
    )

    companion object {
        private const val TAG = "ValuePicsViewModel"
        @Volatile
        private var startupPhotoIntegrityLogged: Boolean = false
        @Volatile
        private var startupOrphanCleanupRun: Boolean = false
        @Volatile
        private var startupDedupRun: Boolean = false

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(ValuePicsViewModel::class.java)) {
                        return ValuePicsViewModel(appContext) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
                }
            }
        }
    }

    private val _isValuating = MutableStateFlow(false)
    val isValuating: StateFlow<Boolean> = _isValuating
    
    private val _operationError = MutableStateFlow<UiError?>(null)
    val operationError: StateFlow<UiError?> = _operationError

    private val _valuationResult = MutableStateFlow<ValuationResult?>(null)
    val valuationResult: StateFlow<ValuationResult?> = _valuationResult

    private val _detectedLabels = MutableStateFlow<List<DetectionResult>>(emptyList())
    val detectedLabels: StateFlow<List<DetectionResult>> = _detectedLabels

    private val _aiOneLineDescription = MutableStateFlow("")
    val aiOneLineDescription: StateFlow<String> = _aiOneLineDescription

    private val _isFetchingFullDescription = MutableStateFlow(false)
    val isFetchingFullDescription: StateFlow<Boolean> = _isFetchingFullDescription

    private val _selectedItem = MutableStateFlow<ValuedItem?>(null)
    val selectedItem: StateFlow<ValuedItem?> = _selectedItem

    private val _selectedItemPhotos = MutableStateFlow<List<ItemPhoto>>(emptyList())
    val selectedItemPhotos: StateFlow<List<ItemPhoto>> = _selectedItemPhotos

    private val _databaseSizeBytes = MutableStateFlow(0L)
    val databaseSizeBytes: StateFlow<Long> = _databaseSizeBytes

    private val _lastBackupMillis = MutableStateFlow(0L)
    val lastBackupMillis: StateFlow<Long> = _lastBackupMillis

    private val _photoFolderSizeBytes = MutableStateFlow(0L)
    val photoFolderSizeBytes: StateFlow<Long> = _photoFolderSizeBytes

    private val _photoFolderFileCount = MutableStateFlow(0)
    val photoFolderFileCount: StateFlow<Int> = _photoFolderFileCount

    private val _pendingDraftCount = MutableStateFlow(0)
    val pendingDraftCount: StateFlow<Int> = _pendingDraftCount

    private val _pendingValuationDraft = MutableStateFlow<ValuationDraft?>(null)
    val pendingValuationDraft: StateFlow<ValuationDraft?> = _pendingValuationDraft

    private val _isUnlimitedUnlocked = MutableStateFlow(false)
    val isUnlimitedUnlocked: StateFlow<Boolean> = _isUnlimitedUnlocked

    private val _unlockAccountId = MutableStateFlow("")
    val unlockAccountId: StateFlow<String> = _unlockAccountId

    private val _draftWebDescription = MutableStateFlow<WebDescriptionResult?>(null)
    val draftWebDescription: StateFlow<WebDescriptionResult?> = _draftWebDescription

    private val _currentValuationDraft = MutableStateFlow<ValuationDraft?>(null)
    val currentValuationDraft: StateFlow<ValuationDraft?> = _currentValuationDraft

    private var selectedItemPhotosJob: Job? = null

    val allItems: Flow<List<ValuedItem>> = repository.getAllItems()
    val allCollections: Flow<List<String>> = repository.getAllCollections()
    val allTags: Flow<List<String>> = repository.getAllTags()

    init {
        _lastBackupMillis.value = repository.getLastBackupMillis()
        _isUnlimitedUnlocked.value = repository.isUnlimitedUnlocked()
        _unlockAccountId.value = repository.getUnlockAccountId()
        if (!startupPhotoIntegrityLogged) {
            startupPhotoIntegrityLogged = true
            viewModelScope.launch {
                repository.logStartupPhotoIntegrityCheck()
            }
        }
        if (!startupDedupRun) {
            startupDedupRun = true
            viewModelScope.launch {
                runCatching { repository.deduplicateItemPhotos() }
                    .onSuccess { removed ->
                        if (removed > 0) Log.i(TAG, "Startup dedup: removed $removed duplicate photo rows")
                    }
                    .onFailure { e -> Log.w(TAG, "Startup photo dedup failed", e) }
            }
        }
        if (!startupOrphanCleanupRun) {
            startupOrphanCleanupRun = true
            viewModelScope.launch {
                runCatching { repository.cleanupOrphanedPhotos() }
                    .onSuccess { summary ->
                        if (summary.removedCount > 0) {
                            Log.i(TAG, "Startup orphan cleanup removed ${summary.removedCount} files (${summary.removedBytes} bytes)")
                        }
                    }
                    .onFailure { error ->
                        Log.w(TAG, "Startup orphan cleanup failed", error)
                    }
                refreshPhotoFolderSize()
                refreshPhotoFolderFileCount()
            }
        }
        viewModelScope.launch {
            repository.migrateLegacyValuationDraftIfNeeded()
        }
        viewModelScope.launch {
            repository.ensureTabsInitialized()
        }
        viewModelScope.launch {
            repository.ensureLegacyTagsSeededFromLegacyLists()
        }
        viewModelScope.launch {
            repository.observePendingDraftCount().collect { count ->
                _pendingDraftCount.value = count
            }
        }
        viewModelScope.launch {
            repository.observeLatestValuationDraft().collect { draft ->
                _pendingValuationDraft.value = draft
            }
        }
        viewModelScope.launch {
            refreshPhotoFolderSize()
            refreshPhotoFolderFileCount()
        }
        runAutoBackupOnLaunch()
    }

    fun setCurrentTier(tier: AppTier) {
        repository.setCurrentTier(tier)
    }

    fun analyzePhoto(photoPath: String) {
        viewModelScope.launch {
            performImageAnalysis { recognitionService.analyzeImage(photoPath) }
        }
    }

    fun analyzePhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            performImageAnalysis { recognitionService.analyzeImage(bitmap) }
        }
    }

    private suspend fun performImageAnalysis(block: suspend () -> List<DetectionResult>) {
        try {
            _isValuating.value = true
            _operationError.value = null
            val labels = RetryUtil.withRetry(
                maxAttempts = 2,
                initialDelayMillis = 100,
                shouldRetry = RetryUtil::isRetryable
            ) {
                block()
            }
            _detectedLabels.value = labels
            _aiOneLineDescription.value = buildOneLineDescription("", labels)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing photo", e)
            _operationError.value = UiError.GeneralError("Failed to analyze image: ${e.message}")
        } finally {
            _isValuating.value = false
        }
    }

    fun valuateItem(itemName: String, description: String, detailedMode: Boolean) {
        viewModelScope.launch {
            try {
                _isValuating.value = true
                _operationError.value = null
                when (val outcome = repository.getValueForItemDetailed(itemName, description, detailedMode)) {
                    is WebValuationOutcome.Success -> {
                        _valuationResult.value = outcome.result
                    }
                    is WebValuationOutcome.Failure -> {
                        _valuationResult.value = null
                        val msg = when (outcome.reason) {
                            WebLookupFailureReason.EMPTY_QUERY ->
                                appContext.getString(R.string.valuation_error_empty_query)
                            WebLookupFailureReason.TIMEOUT ->
                                appContext.getString(R.string.valuation_error_timeout)
                            WebLookupFailureReason.NETWORK ->
                                appContext.getString(R.string.valuation_error_network)
                            WebLookupFailureReason.PARSE_CHANGED ->
                                appContext.getString(R.string.valuation_error_source_changed)
                            WebLookupFailureReason.NO_RESULTS ->
                                appContext.getString(R.string.valuation_error_no_results)
                        }
                        _operationError.value = UiError.GeneralError(msg)
                    }
                }
                _aiOneLineDescription.value = buildOneLineDescription(itemName, _detectedLabels.value)
            } catch (e: Exception) {
                Log.e(TAG, "Error valuating item", e)
                _operationError.value = UiError.GeneralError("Failed to valuate item: ${e.message}")
            } finally {
                _isValuating.value = false
            }
        }
    }

    fun lookupDraftFullDescription(itemName: String, hint: String, labels: List<String>) {
        viewModelScope.launch {
            try {
                _isFetchingFullDescription.value = true
                _operationError.value = null
                val result = repository.getWebDescription(itemName, hint, labels)
                if (result != null) {
                    _draftWebDescription.value = result
                    _aiOneLineDescription.value = result.oneLine
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching full description", e)
                _operationError.value = UiError.ImportError("Failed to fetch description: ${e.message}")
            } finally {
                _isFetchingFullDescription.value = false
            }
        }
    }

    fun fetchAndApplyFullDescription(item: ValuedItem) {
        viewModelScope.launch {
            try {
                _isFetchingFullDescription.value = true
                _operationError.value = null
                val labels = item.detectedLabels
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                val web = repository.getWebDescription(
                    itemName = item.itemName,
                    hint = item.itemDescription,
                    labels = labels
                ) ?: return@launch

                val updated = item.copy(
                    shortAiDescription = web.oneLine,
                    fullWebDescription = web.fullDescription,
                    sourceUrl = web.sourceUrl
                )
                repository.updateItem(updated)
                _selectedItem.value = updated
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching and applying full description", e)
                _operationError.value = UiError.ImportError("Failed to fetch description: ${e.message}")
            } finally {
                _isFetchingFullDescription.value = false
            }
        }
    }

    private fun buildOneLineDescription(itemName: String, labels: List<DetectionResult>): String {
        val topLabels = labels.take(3).joinToString(", ") { it.label }
        return when {
            itemName.isNotBlank() && topLabels.isNotBlank() ->
                appContext.getString(R.string.ai_desc_name_and_labels, itemName, topLabels.lowercase())
            itemName.isNotBlank() ->
                appContext.getString(R.string.ai_desc_name_only, itemName)
            topLabels.isNotBlank() ->
                appContext.getString(R.string.ai_desc_labels_only, topLabels)
            else ->
                appContext.getString(R.string.ai_desc_none)
        }
    }

    fun saveItem(
        item: ValuedItem,
        additionalPhotoPaths: List<String> = emptyList(),
        onComplete: (Int) -> Unit = {}
    ) {
        viewModelScope.launch {
            val insertedId = repository.insertItem(item).toInt()
            val normalizedExtras = additionalPhotoPaths
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filterNot { it == item.photoPath }
                .distinct()
            normalizedExtras.forEach { extraPath ->
                repository.addPhotoToItem(insertedId, extraPath, makeCover = false)
            }
            repository.clearValuationDraft(item.photoPath)
            if (_pendingValuationDraft.value?.photoPath == item.photoPath) {
                _pendingValuationDraft.value = null
            }
            clearTransientState()
            _selectedItem.value = null
            _currentValuationDraft.value = null
            onComplete(insertedId)
        }
    }

    fun restoreDeletedItem(item: ValuedItem) {
        viewModelScope.launch {
            repository.insertItem(item.copy(id = 0))
        }
    }

    fun deleteItem(item: ValuedItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
            clearTransientState()
            _selectedItem.value = null
            refreshPhotoFolderSize()
        }
    }

    fun deleteItemsByIds(itemIds: Set<Int>, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repository.deleteItemsByIds(itemIds) }
            if (result.isSuccess) {
                clearTransientState()
                _selectedItem.value = null
                refreshPhotoFolderSize()
                refreshPhotoFolderFileCount()
            }
            onResult(result)
        }
    }

    fun updateItem(item: ValuedItem) {
        viewModelScope.launch {
            repository.updateItem(item)
            _selectedItem.value = item
            loadPhotosForItem(item.id)
        }
    }

    fun selectItem(item: ValuedItem) {
        _selectedItem.value = item
        loadPhotosForItem(item.id)
    }

    fun selectItemById(itemId: Int) {
        viewModelScope.launch {
            _selectedItem.value = repository.getItemById(itemId)
            loadPhotosForItem(itemId)
        }
    }

    fun clearSelection() {
        _selectedItem.value = null
        _selectedItemPhotos.value = emptyList()
    }

    fun loadPhotosForItem(itemId: Int) {
        selectedItemPhotosJob?.cancel()
        selectedItemPhotosJob = viewModelScope.launch {
            repository.getItemPhotos(itemId).collect { photos ->
                _selectedItemPhotos.value = photos
            }
        }
    }

    fun copyItemToOtherTier(itemId: Int, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = repository.copyItemToOtherTier(itemId)
            onResult(result)
        }
    }

    fun setCoverPhoto(itemId: Int, photoId: Int, onResult: (Result<Boolean>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { repository.setCoverPhoto(itemId, photoId) }
            result.onSuccess {
                _selectedItem.value = repository.getItemById(itemId)
            }
            onResult(result)
        }
    }

    fun addPhotoToItem(itemId: Int, photoPath: String, makeCover: Boolean = false, onResult: (Result<Long>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { repository.addPhotoToItem(itemId, photoPath, makeCover) }
            result.onSuccess {
                if (makeCover) {
                    _selectedItem.value = repository.getItemById(itemId)
                }
            }
            onResult(result)
        }
    }

    fun deletePhotoFromItem(photoId: Int, itemId: Int, onResult: (Result<Boolean>) -> Unit = {}) {
        viewModelScope.launch {
            val result = runCatching { repository.deletePhotoFromItem(photoId) }
            result.onSuccess {
                _selectedItem.value = repository.getItemById(itemId)
            }
            onResult(result)
        }
    }

    fun clearTransientState() {
        _valuationResult.value = null
        _detectedLabels.value = emptyList()
        _aiOneLineDescription.value = ""
        _draftWebDescription.value = null
        _isValuating.value = false
    }

    fun clearAiOneLineDescription() {
        _aiOneLineDescription.value = ""
    }

    fun backupToUri(uri: Uri, onResult: (Result<BackupSummary>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                repository.backupToUri(uri)
            }.onSuccess {
                _lastBackupMillis.value = repository.getLastBackupMillis()
            }
            onResult(result)
        }
    }

    fun backupSelectedItemsToUri(uri: Uri, itemIds: Set<Int>, onResult: (Result<BackupSummary>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.backupSelectedItemsToUri(uri, itemIds) })
        }
    }

    fun runAutoBackupOnLaunch() {
        viewModelScope.launch {
            runCatching { repository.runAutoBackupOnLaunch() }
                .onSuccess { summary ->
                    if (summary != null) {
                        _lastBackupMillis.value = repository.getLastBackupMillis()
                        runCatching { repository.inspectAutoBackupSlots() }
                            .onFailure { Log.w(TAG, "Auto-backup slot inspection failed", it) }
                    }
                }
                .onFailure { Log.w(TAG, "Auto-backup on launch failed", it) }
        }
    }

    fun restoreFromUri(uri: Uri, onResult: (Result<BackupSummary>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                repository.restoreFromUri(uri)
            }
            result.exceptionOrNull()?.let { Log.e(TAG, "Restore from URI failed: $uri", it) }
            if (result.isSuccess) refreshUnlockState()
            onResult(result)
        }
    }

    fun inspectAutoBackupSlots(onResult: (Result<List<AutoBackupSlotInfo>>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.inspectAutoBackupSlots() })
        }
    }

    fun restoreFromAutoBackupSlot(slotId: String, onResult: (Result<BackupSummary>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repository.restoreFromAutoBackupSlot(slotId) }
            result.exceptionOrNull()?.let { Log.e(TAG, "Restore from auto-backup slot failed: $slotId", it) }
            if (result.isSuccess) refreshUnlockState()
            onResult(result)
        }
    }

    fun mergeFromUri(uri: Uri, onResult: (Result<BackupSummary>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                repository.mergeFromUri(uri)
            }
            onResult(result)
        }
    }

    fun addCollection(name: String, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.addCollection(name) })
        }
    }

    fun renameCollection(oldName: String, newName: String, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.renameCollection(oldName, newName) })
        }
    }

    fun deleteCollection(name: String, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.deleteCollection(name) })
        }
    }

    fun countItemsInCollection(name: String, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.countItemsInCollection(name) })
        }
    }

    fun addTab(name: String, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.addTab(name) })
        }
    }

    fun renameTab(oldName: String, newName: String, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.renameTab(oldName, newName) })
        }
    }

    fun deleteTab(name: String, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.deleteTab(name) })
        }
    }

    fun resetTabs(onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.resetTabs() })
        }
    }

    fun addTag(name: String, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.addTag(name) })
        }
    }

    fun renameTag(oldName: String, newName: String, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.renameTag(oldName, newName) })
        }
    }

    fun deleteTag(name: String, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.deleteTag(name) })
        }
    }

    fun refreshDatabaseSize() {
        viewModelScope.launch {
            _databaseSizeBytes.value = repository.getDatabaseSizeBytes()
        }
    }

    fun refreshPhotoFolderSize() {
        viewModelScope.launch {
            _photoFolderSizeBytes.value = repository.getPhotoFolderSizeBytes()
        }
    }

    fun refreshPhotoFolderFileCount() {
        viewModelScope.launch {
            _photoFolderFileCount.value = repository.getPhotoFolderFileCount()
        }
    }

    fun getCurrentBackupFormatVersion(): Int = repository.getCurrentBackupFormatVersion()

    fun getMinimumSupportedRestoreBackupFormatVersion(): Int =
        repository.getMinimumSupportedRestoreBackupFormatVersion()

    fun getLastBackupFormatDisplayText(): String = repository.getLastBackupFormatDisplayText()

    fun exportCollectionSummaryPdf(onResult: (Result<PdfExportResult>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.exportCollectionSummaryPdf() })
        }
    }

    fun exportCollectionSummaryPdf(
        items: List<ValuedItem>,
        scopeLabel: String,
        isFiltered: Boolean,
        reportSortOption: ReportSortOption,
        reportTitleOverride: String? = null,
        useWillInstructions: Boolean = false,
        includeThumbnails: Boolean = false,
        onProgress: (ExportProgress) -> Unit = { },
        onResult: (Result<PdfExportResult>) -> Unit
    ) {
        viewModelScope.launch {
            onResult(
                runCatching {
                    repository.exportCollectionSummaryPdf(
                        items = items,
                        scopeLabel = scopeLabel,
                        isFiltered = isFiltered,
                        reportSortOption = reportSortOption,
                        reportTitleOverride = reportTitleOverride,
                        useWillInstructions = useWillInstructions,
                        includeThumbnails = includeThumbnails,
                        onProgress = onProgress
                    )
                }
            )
        }
    }


    fun prepareDatabaseShareAttachment(onResult: (Result<DatabaseShareAttachment>) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repository.prepareDatabaseShareAttachment() })
        }
    }

    fun saveValuationDraft(draft: ValuationDraft) {
        viewModelScope.launch {
            repository.saveValuationDraft(draft)
            if (_currentValuationDraft.value?.photoPath == draft.photoPath) {
                _currentValuationDraft.value = draft
            }
        }
    }

    fun loadValuationDraftForPhoto(photoPath: String) {
        viewModelScope.launch {
            _currentValuationDraft.value = repository.getValuationDraft(photoPath)
        }
    }

    fun clearValuationDraft(photoPath: String) {
        viewModelScope.launch {
            repository.clearValuationDraft(photoPath)
            if (_currentValuationDraft.value?.photoPath == photoPath) {
                _currentValuationDraft.value = null
            }
        }
    }


    fun clearOperationError() {
        _operationError.value = null
    }

    suspend fun getDuplicateGroupOrderByItemId(items: List<ValuedItem>): Map<Int, Int> {
        return repository.getDuplicateGroupOrderByItemId(items)
    }

    fun cleanupOrphanedPhotos(onResult: (Result<PhotoCleanupSummary>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repository.cleanupOrphanedPhotos() }
            if (result.isSuccess) {
                _photoFolderSizeBytes.value = repository.getPhotoFolderSizeBytes()
                _photoFolderFileCount.value = repository.getPhotoFolderFileCount()
            }
            onResult(result)
        }
    }

    fun seedSampleData(onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val wasSeeded = repository.seedSampleData()
                if (wasSeeded) 4 else 0 // Return count of inserted samples or 0 if already existed
            }
            onResult(result)
        }
    }

    fun removeSampleData(onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                repository.removeSampleData()
            }
            onResult(result)
        }
    }

    fun getRecordLimit(): Int = repository.getRecordLimit()

    fun refreshUnlockState() {
        _isUnlimitedUnlocked.value = repository.isUnlimitedUnlocked()
        _unlockAccountId.value = repository.getUnlockAccountId()
    }

    fun unlockUnlimitedWithPassword(password: String, accountId: String, onResult: (Result<UnlockResult>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                repository.unlockUnlimitedWithPassword(password, accountId)
            }
            result.onSuccess {
                refreshUnlockState()
            }
            onResult(result)
        }
    }

    fun restoreUnlimitedFromAccount(accountId: String, recoveryCode: String, onResult: (Result<Boolean>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                repository.restoreUnlimitedFromAccount(accountId, recoveryCode)
            }
            result.onSuccess {
                if (it) refreshUnlockState()
            }
            onResult(result)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
        clearTransientState()
    }

}
