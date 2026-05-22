package com.example.valuefinder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.valuefinder.AppTier
import com.example.valuefinder.ExportProgress
import com.example.valuefinder.ItemPhoto
import com.example.valuefinder.ReportSortOption
import com.example.valuefinder.UnlockResult
import com.example.valuefinder.ValuedItem
import com.example.valuefinder.ValuePicsViewModel
import com.example.valuefinder.ui.camera.CameraScreen

// ── Callback type aliases ─────────────────────────────────────────────────────
/** (itemId, photoId) */
internal typealias SetCoverPhotoCallback = (itemId: Int, photoId: Int) -> Unit
/** (photoId, itemId) */
internal typealias DeletePhotoCallback = (photoId: Int, itemId: Int) -> Unit

// ── Collection action bundle ──────────────────────────────────────────────────
/**
 * Groups the four collection-management callbacks into a single object so
 * route composables don't repeat the same four parameters everywhere.
 */
internal data class CollectionActions(
    val onAdd: (String, (Result<Boolean>) -> Unit) -> Unit,
    val onRename: (String, String, (Result<Int>) -> Unit) -> Unit,
    val onDelete: (String, (Result<Int>) -> Unit) -> Unit,
    val onCount: (String, (Result<Int>) -> Unit) -> Unit
)

internal data class TagActions(
    val onAdd: (String, (Result<Boolean>) -> Unit) -> Unit,
    val onRename: (String, String, (Result<Boolean>) -> Unit) -> Unit,
    val onDelete: (String, (Result<Boolean>) -> Unit) -> Unit
)

// ── Route composables ─────────────────────────────────────────────────────────

@Composable
internal fun ValuePicsListRoute(
    items: List<ValuedItem>,
    allCollections: List<String>,
    allTags: List<String>,
    navToCamera: () -> Unit,
    navToDetails: (ValuedItem) -> Unit,
    onOpenSourceLink: (String) -> Unit,
    onBackupRequested: () -> Unit,
    onQuickBackupRequested: () -> Unit,
    onRestoreRequested: () -> Unit,
    onRestoreAutoBackupRequested: () -> Unit,
    onMergeDatabaseRequested: () -> Unit,
    onImportRecordsRequested: () -> Unit,
    onDeleteSelectedRequested: (Set<Int>) -> Unit,
    isBackupRestoreBusy: Boolean,
    isMergingDatabase: Boolean,
    lastBackupText: String,
    onExportPdfRequested: (List<ValuedItem>, String, Boolean, ReportSortOption, Boolean) -> Unit,
    onPrintWillRequested: (List<ValuedItem>, String, ReportSortOption, Boolean) -> Unit,
    onBatchExportRequested: (List<ValuedItem>, String, Boolean, ReportSortOption, BatchExportOptions) -> Unit,
    onExportRecordsRequested: (List<ValuedItem>, String) -> Unit,
    isExportingPdf: Boolean,
    exportProgress: ExportProgress?,
    photoTargetSizeKb: Int,
    onPhotoTargetSizeChange: (Int) -> Unit,
    onAboutRequested: () -> Unit,
    appVersionText: String,
    appEditionLabel: String,
    backupFormatVersion: Int,
    restoreMinBackupFormatVersion: Int,
    lastBackupFormatText: String,
    databaseSizeText: String,
    photoFolderSizeText: String,
    photoFolderFileCount: Int,
    lastMergeAppliedCount: Int,
    lastMergePhotoCount: Int,
    lastMergeDedupeCount: Int,
    lastMergeAtText: String,
    pendingDraftCount: Int,
    onHowToRequested: () -> Unit,
    onShareDatabaseRequested: () -> Unit,
    isSharingDatabase: Boolean,
    collectionActions: CollectionActions,
    tagActions: TagActions,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    appTier: AppTier,
    onAppTierSelected: (AppTier) -> Unit,
    onLockPersonalNowRequested: () -> Unit,
    hasSeenExportHelper: Boolean,
    onExportHelperSeen: () -> Unit,
    hasSeenSelectionHelper: Boolean,
    duplicateGroupOrderByItemId: Map<Int, Int>,
    onSelectionHelperSeen: () -> Unit,
    willOwnerName: String,
    onWillOwnerNameChange: (String) -> Unit,
    onLoadSampleDataRequested: ((Result<Int>) -> Unit) -> Unit,
    onRemoveSampleDataRequested: ((Result<Int>) -> Unit) -> Unit,
    recordLimit: Int,
    isUnlimitedUnlocked: Boolean,
    unlockAccountId: String,
    onUnlockWithPassword: (String, String, (Result<UnlockResult>) -> Unit) -> Unit,
    onRestoreUnlimited: (String, String, (Result<Boolean>) -> Unit) -> Unit,
    showTotalValueBand: Boolean,
    onToggleTotalValueBand: () -> Unit
) {
    ItemListScreen(
        items = items,
        allCollections = allCollections,
        allTags = allTags,
        onAddItem = navToCamera,
        onItemClick = navToDetails,
        onOpenSourceLink = onOpenSourceLink,
        onBackupRequested = onBackupRequested,
        onQuickBackupRequested = onQuickBackupRequested,
        onRestoreRequested = onRestoreRequested,
        onRestoreAutoBackupRequested = onRestoreAutoBackupRequested,
        onMergeDatabaseRequested = onMergeDatabaseRequested,
        onImportRecordsRequested = onImportRecordsRequested,
        onDeleteSelectedRequested = onDeleteSelectedRequested,
        isBackupRestoreBusy = isBackupRestoreBusy,
        isMergingDatabase = isMergingDatabase,
        lastBackupText = lastBackupText,
        onExportPdfRequested = onExportPdfRequested,
        onPrintWillRequested = onPrintWillRequested,
        onBatchExportRequested = onBatchExportRequested,
        onExportRecordsRequested = onExportRecordsRequested,
        isExportingPdf = isExportingPdf,
        exportProgress = exportProgress,
        photoTargetSizeKb = photoTargetSizeKb,
        onPhotoTargetSizeChange = onPhotoTargetSizeChange,
        onAboutRequested = onAboutRequested,
        appVersionText = appVersionText,
        appEditionLabel = appEditionLabel,
        backupFormatVersion = backupFormatVersion,
        restoreMinBackupFormatVersion = restoreMinBackupFormatVersion,
        lastBackupFormatText = lastBackupFormatText,
        databaseSizeText = databaseSizeText,
        photoFolderSizeText = photoFolderSizeText,
        photoFolderFileCount = photoFolderFileCount,
        lastMergeAppliedCount = lastMergeAppliedCount,
        lastMergePhotoCount = lastMergePhotoCount,
        lastMergeDedupeCount = lastMergeDedupeCount,
        lastMergeAtText = lastMergeAtText,
        pendingDraftCount = pendingDraftCount,
        onHowToRequested = onHowToRequested,
        onShareDatabaseRequested = onShareDatabaseRequested,
        isSharingDatabase = isSharingDatabase,
        onAddCollectionRequested = collectionActions.onAdd,
        onRenameCollectionRequested = collectionActions.onRename,
        onDeleteCollectionRequested = collectionActions.onDelete,
        onCountItemsInCollectionRequested = collectionActions.onCount,
        onAddTagRequested = tagActions.onAdd,
        onRenameTagRequested = tagActions.onRename,
        onDeleteTagRequested = tagActions.onDelete,
        themeMode = themeMode,
        onThemeModeSelected = onThemeModeSelected,
        appTier = appTier,
        onAppTierSelected = onAppTierSelected,
        onLockPersonalNowRequested = onLockPersonalNowRequested,
        hasSeenExportHelper = hasSeenExportHelper,
        onExportHelperSeen = onExportHelperSeen,
        hasSeenSelectionHelper = hasSeenSelectionHelper,
        duplicateGroupOrderByItemId = duplicateGroupOrderByItemId,
        onSelectionHelperSeen = onSelectionHelperSeen,
        willOwnerName = willOwnerName,
        onWillOwnerNameChange = onWillOwnerNameChange,
        onLoadSampleDataRequested = onLoadSampleDataRequested,
        onRemoveSampleDataRequested = onRemoveSampleDataRequested,
        recordLimit = recordLimit,
        isUnlimitedUnlocked = isUnlimitedUnlocked,
        unlockAccountId = unlockAccountId,
        onUnlockWithPassword = onUnlockWithPassword,
        onRestoreUnlimited = onRestoreUnlimited,
        showTotalValueBand = showTotalValueBand,
        onToggleTotalValueBand = onToggleTotalValueBand
    )
}

@Composable
internal fun ValuePicsCameraRoute(
    photoTargetSizeKb: Int,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    appTier: AppTier,
    onAppTierSelected: (AppTier) -> Unit,
    onPhotoTargetSizeChange: (Int) -> Unit,
    onPhotoCaptured: (String, String, Boolean) -> Unit,
    supportsMultiGalleryImport: Boolean,
    onCancel: () -> Unit
) {
    CameraScreen(
        onPhotoCapture = onPhotoCaptured,
        photoTargetSizeKb = photoTargetSizeKb,
        onPhotoTargetSizeChange = onPhotoTargetSizeChange,
        themeMode = themeMode,
        onThemeModeSelected = onThemeModeSelected,
        appTier = appTier,
        onAppTierSelected = onAppTierSelected,
        supportsMultiGalleryImport = supportsMultiGalleryImport,
        onCancel = onCancel
    )
}

@Composable
internal fun ValuePicsValuationRoute(
    routePhotoPath: String,
    routePhotoSource: String,
    existingCollections: List<String>,
    existingTags: List<String>,
    isValuating: Boolean,
    viewModel: ValuePicsViewModel,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    appTier: AppTier,
    onAppTierSelected: (AppTier) -> Unit,
    onNavigateList: () -> Unit,
    onNavigateCamera: () -> Unit,
    onSave: (ValuedItem, () -> Unit) -> Unit,
    currentRecordCount: Int,
    recordLimit: Int,
    isUnlimitedUnlocked: Boolean,
    unlockAccountId: String,
    onUnlockWithPassword: (String, String, (Result<UnlockResult>) -> Unit) -> Unit,
    onRestoreUnlimited: (String, String, (Result<Boolean>) -> Unit) -> Unit
) {
    if (routePhotoPath.isBlank()) {
        LaunchedEffect(Unit) { onNavigateList() }
        return
    }

    ValuationScreen(
        photoPath = routePhotoPath,
        photoSource = routePhotoSource,
        existingCollections = existingCollections,
        existingTags = existingTags,
        isValuating = isValuating,
        viewModel = viewModel,
        onSave = { item -> onSave(item) { onNavigateList() } },
        onSaveAndAddAnother = { item -> onSave(item) { onNavigateCamera() } },
        currentRecordCount = currentRecordCount,
        recordLimit = recordLimit,
        isUnlimitedUnlocked = isUnlimitedUnlocked,
        unlockAccountId = unlockAccountId,
        onUnlockWithPassword = onUnlockWithPassword,
        onRestoreUnlimited = onRestoreUnlimited,
        themeMode = themeMode,
        onThemeModeSelected = onThemeModeSelected,
        appTier = appTier,
        onAppTierSelected = onAppTierSelected,
        onBack = onNavigateList
    )
}

@Composable
internal fun ValuePicsDetailsRoute(
    routeItemId: Int,
    items: List<ValuedItem>,
    selectedItem: ValuedItem?,
    selectedItemPhotos: List<ItemPhoto>,
    allCollections: List<String>,
    existingTags: List<String>,
    isFetchingFullDescription: Boolean,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    appTier: AppTier,
    onAppTierSelected: (AppTier) -> Unit,
    onOpenSourceLink: (String) -> Unit,
    onSelectItemById: (Int) -> Unit,
    collectionActions: CollectionActions,
    onBack: () -> Unit,
    onDelete: (ValuedItem) -> Unit,
    onExportRecordRequested: (ValuedItem) -> Unit,
    onUpdateItem: (ValuedItem) -> Unit,
    onFetchFullDescription: (ValuedItem) -> Unit,
    onSetCoverPhoto: SetCoverPhotoCallback,
    onRequestAddPhoto: (Int) -> Unit,
    onDeletePhotoFromItem: DeletePhotoCallback,
    onCopyToOtherTier: (Int, (Result<Unit>) -> Unit) -> Unit,
) {
    LaunchedEffect(routeItemId) {
        if (routeItemId > 0) onSelectItemById(routeItemId)
    }

    val item = items.firstOrNull { it.id == routeItemId }
        ?: selectedItem?.takeIf { it.id == routeItemId }

    // FIX 1: if the list has loaded but the item is genuinely gone (deleted or
    // invalid deep link), bail back rather than rendering a blank screen.
    LaunchedEffect(item, items.size) {
        if (item == null && items.isNotEmpty()) onBack()
    }

    if (item == null) {
        // Show a spinner while we wait for the list to finish loading.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    key(item.id) {
        DetailsScreen(
            item = item,
            itemPhotos = selectedItemPhotos,
            existingCollections = allCollections,
            existingTags = existingTags,
            onAddCollectionRequested = collectionActions.onAdd,
            onRenameCollectionRequested = collectionActions.onRename,
            onDeleteCollectionRequested = collectionActions.onDelete,
            onCountItemsInCollectionRequested = collectionActions.onCount,
            onBack = onBack,
            onDelete = { onDelete(item) },
            onExportRecordRequested = { onExportRecordRequested(item) },
            onUpdateItem = onUpdateItem,
            isFetchingDescription = isFetchingFullDescription,
            onFetchFullDescription = onFetchFullDescription,
            themeMode = themeMode,
            onThemeModeSelected = onThemeModeSelected,
            appTier = appTier,
            onAppTierSelected = onAppTierSelected,
            onOpenSourceLink = onOpenSourceLink,
            onSetCoverPhoto = onSetCoverPhoto,
            onRequestAddPhoto = onRequestAddPhoto,
            onDeletePhotoFromItem = onDeletePhotoFromItem,
            onCopyToOtherTier = onCopyToOtherTier,
        )
    }
}
