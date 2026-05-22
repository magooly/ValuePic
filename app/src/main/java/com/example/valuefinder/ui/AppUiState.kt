package com.example.valuefinder.ui

import com.example.valuefinder.ValuedItem
import com.example.valuefinder.PdfExportResult

/**
 * Represents all possible UI errors
 */
sealed class UiError {
    data class PdfError(val message: String) : UiError()
    data class ImportError(val message: String) : UiError()
    data class DatabaseError(val message: String) : UiError()
    data class GeneralError(val message: String) : UiError()
}

/**
 * Represents the state of an async operation
 */
sealed class AsyncState<T> {
    object Idle : AsyncState<Nothing>()
    object Loading : AsyncState<Nothing>()
    data class Success<T>(val data: T) : AsyncState<T>()
    data class Error<T>(val error: UiError, val lastData: T? = null) : AsyncState<T>()
}

/**
 * Comprehensive app-level UI state
 */
data class AppUiState(
    val newPhotoPath: String = "",
    val newPhotoSource: String = "camera",
    
    // Operation states
    val isBackupRestoreBusy: Boolean = false,
    val isExportingPdf: Boolean = false,
    val isSharingDatabase: Boolean = false,
    val isCleaningOrphanedPhotos: Boolean = false,
    
    // Dialog states
    val showRestoreConfirmDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val showResumeDraftDialog: Boolean = false,
    val showBackupReminderDialog: Boolean = false,
    val showHowToDialog: Boolean = false,
    val pendingDeletedItem: ValuedItem? = null,
    
    // Export results
    val pdfExportResult: PdfExportResult? = null,
    val howToPdfUri: String? = null,  // Stored as string for testability; convert to Uri at UI layer
    
    // Error state
    val currentError: UiError? = null,
    
    // Theme
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val photoTargetSizeKb: Int = 512,
    
    // Flags for one-time operations
    val hasCheckedDraftResume: Boolean = false,
    val hasShownBackupReminder: Boolean = false,
)

/**
 * Events that modify UI state
 */
sealed class AppUiEvent {
    data class SetPhotoPath(val path: String, val source: String = "camera") : AppUiEvent()
    
    // Dialogs
    object ShowRestoreConfirmDialog : AppUiEvent()
    object HideRestoreConfirmDialog : AppUiEvent()
    object ShowDeleteConfirmDialog : AppUiEvent()
    object HideDeleteConfirmDialog : AppUiEvent()
    object ShowResumeDraftDialog : AppUiEvent()
    object HideResumeDraftDialog : AppUiEvent()
    object ShowBackupReminderDialog : AppUiEvent()
    object HideBackupReminderDialog : AppUiEvent()
    object ShowHowToDialog : AppUiEvent()
    object HideHowToDialog : AppUiEvent()
    
    // Operations
    object StartBackupRestore : AppUiEvent()
    object EndBackupRestore : AppUiEvent()
    object StartPdfExport : AppUiEvent()
    object EndPdfExport : AppUiEvent()
    object StartDatabaseShare : AppUiEvent()
    object EndDatabaseShare : AppUiEvent()
    object StartPhotoCleanup : AppUiEvent()
    object EndPhotoCleanup : AppUiEvent()
    
    // Results
    data class SetPdfExportResult(val result: PdfExportResult) : AppUiEvent()
    object ClearPdfExportResult : AppUiEvent()
     data class SetHowToPdfUri(val uri: String) : AppUiEvent()
    data class SetPendingDeletedItem(val item: ValuedItem?) : AppUiEvent()
    
    // Errors
    data class SetError(val error: UiError) : AppUiEvent()
    object ClearError : AppUiEvent()
    
    // Theme
    data class SetThemeMode(val mode: ThemeMode) : AppUiEvent()
    data class SetPhotoTargetSize(val sizeKb: Int) : AppUiEvent()
    
    // Flags
    object MarkDraftResumeChecked : AppUiEvent()
    object MarkBackupReminderShown : AppUiEvent()
}

/**
 * Reducer function for app UI state
 */
fun appUiStateReducer(state: AppUiState, event: AppUiEvent): AppUiState {
    return when (event) {
        is AppUiEvent.SetPhotoPath -> state.copy(newPhotoPath = event.path, newPhotoSource = event.source)
        
        AppUiEvent.ShowRestoreConfirmDialog -> state.copy(showRestoreConfirmDialog = true)
        AppUiEvent.HideRestoreConfirmDialog -> state.copy(showRestoreConfirmDialog = false)
        AppUiEvent.ShowDeleteConfirmDialog -> state.copy(showDeleteConfirmDialog = true)
        AppUiEvent.HideDeleteConfirmDialog -> state.copy(showDeleteConfirmDialog = false)
        AppUiEvent.ShowResumeDraftDialog -> state.copy(showResumeDraftDialog = true)
        AppUiEvent.HideResumeDraftDialog -> state.copy(showResumeDraftDialog = false)
        AppUiEvent.ShowBackupReminderDialog -> state.copy(showBackupReminderDialog = true)
        AppUiEvent.HideBackupReminderDialog -> state.copy(showBackupReminderDialog = false)
        AppUiEvent.ShowHowToDialog -> state.copy(showHowToDialog = true)
        AppUiEvent.HideHowToDialog -> state.copy(showHowToDialog = false)
        
        AppUiEvent.StartBackupRestore -> state.copy(isBackupRestoreBusy = true)
        AppUiEvent.EndBackupRestore -> state.copy(isBackupRestoreBusy = false)
        AppUiEvent.StartPdfExport -> state.copy(isExportingPdf = true)
        AppUiEvent.EndPdfExport -> state.copy(isExportingPdf = false)
        AppUiEvent.StartDatabaseShare -> state.copy(isSharingDatabase = true)
        AppUiEvent.EndDatabaseShare -> state.copy(isSharingDatabase = false)
        AppUiEvent.StartPhotoCleanup -> state.copy(isCleaningOrphanedPhotos = true)
        AppUiEvent.EndPhotoCleanup -> state.copy(isCleaningOrphanedPhotos = false)
        
        is AppUiEvent.SetPdfExportResult -> state.copy(pdfExportResult = event.result)
        AppUiEvent.ClearPdfExportResult -> state.copy(pdfExportResult = null)
        is AppUiEvent.SetHowToPdfUri -> state.copy(howToPdfUri = event.uri)
        is AppUiEvent.SetPendingDeletedItem -> state.copy(pendingDeletedItem = event.item)
        
        is AppUiEvent.SetError -> state.copy(currentError = event.error)
        AppUiEvent.ClearError -> state.copy(currentError = null)
        
        is AppUiEvent.SetThemeMode -> state.copy(themeMode = event.mode)
        is AppUiEvent.SetPhotoTargetSize -> state.copy(photoTargetSizeKb = event.sizeKb)
        
        AppUiEvent.MarkDraftResumeChecked -> state.copy(hasCheckedDraftResume = true)
        AppUiEvent.MarkBackupReminderShown -> state.copy(hasShownBackupReminder = true)
    }
}

