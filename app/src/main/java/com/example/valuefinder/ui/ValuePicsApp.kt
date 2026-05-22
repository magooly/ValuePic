/*
 * Copyright (c) 2026 Wally Horsman.
 * All rights reserved.
 */

package com.example.valuefinder.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.print.PrintManager
import android.text.format.Formatter
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.valuefinder.DatabaseShareAttachment
import com.example.valuefinder.AutoBackupSlotInfo
import com.example.valuefinder.BuildConfig
import com.example.valuefinder.ExportProgress
import com.example.valuefinder.PdfExportResult
import com.example.valuefinder.PhotoUtils
import com.example.valuefinder.R
import com.example.valuefinder.ReportSortOption
import com.example.valuefinder.TagUtils
import com.example.valuefinder.ValuePicsViewModel
import com.example.valuefinder.ValuedItem
import com.example.valuefinder.ui.dialogs.DeleteConfirmDialog
import com.example.valuefinder.ui.dialogs.ErrorDialog
import com.example.valuefinder.ui.dialogs.HowToDialog
import com.example.valuefinder.ui.dialogs.PdfExportResultDialog
import com.example.valuefinder.ui.dialogs.PreBackupCautionDialog
import com.example.valuefinder.ui.dialogs.ResumeDraftDialog
import com.example.valuefinder.ui.dialogs.AutoBackupRestoreDialog
import com.example.valuefinder.ui.dialogs.showDeleteUndoSnackbar
import com.example.valuefinder.AppTier
import com.example.valuefinder.ui.theme.ValuePicsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.navigation.navDeepLink
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.ContextWrapper
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh

private const val SETTINGS_PREFS = "valuepics_settings"
/** Legacy — read-only migration from old boolean dark mode pref. Do not write. */
private const val PREF_DARK_MODE = "dark_mode_enabled"
private const val PREF_THEME_MODE = "theme_mode"
private const val PREF_SELECTION_HELPER_SEEN = "selection_helper_seen"
private const val PREF_EXPORT_HELPER_SEEN = "export_helper_seen"
private const val PREF_SHOW_TOTAL_VALUE_BAND = "show_total_value_band"
private const val PREF_PROFILE_DISPLAY_NAME = "profile_display_name"
private const val PREF_APP_TIER = "app_tier"
private const val PREF_PERSONAL_TIER_PASSWORD = "personal_tier_password"
private const val PREF_PERSONAL_TIER_LOCKED = "personal_tier_locked"
private const val PREF_PERSONAL_TIER_LAST_UNLOCK_MILLIS = "personal_tier_last_unlock_millis"
private const val PERSONAL_TIER_TIMEOUT_MILLIS = 15L * 60L * 1000L
private const val DEFAULT_WILL_OWNER_NAME = "Wally Horsman"
private const val EXPORT_LOG_TAG = "ValuePicsExport"
// Internal Gradle flavor IDs used by BuildConfig.FLAVOR.
private const val FLAVOR_ID_MAIN = "abc"
private const val FLAVOR_ID_OTHER = "xyz"

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}


private enum class HapticType {
    DEFAULT,
    SAVE,
    DELETE
}

private fun parseThemeMode(raw: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: ThemeMode.SYSTEM

private fun logExportEvent(event: String, details: String) {
    Log.i(EXPORT_LOG_TAG, "$event | $details")
}

/**
 * Provides haptic feedback for important user actions.
 * Uses predefined effects on Android Q+ and falls back on older versions.
 */
private fun triggerHapticFeedback(
    context: Context,
    type: HapticType = HapticType.DEFAULT
) {
    try {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when (type) {
                HapticType.SAVE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticType.DELETE -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                HapticType.DEFAULT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                when (type) {
                    HapticType.DELETE -> 80L
                    HapticType.SAVE -> 40L
                    HapticType.DEFAULT -> 50L
                }
            )
        }
    } catch (e: Exception) {
        Log.d("HapticFeedback", "Haptic feedback unavailable: ${e.message}")
    }
}

private fun findViewModelStoreOwner(context: Context): ViewModelStoreOwner? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is ViewModelStoreOwner) return current
        current = current.baseContext
    }
    return if (current is ViewModelStoreOwner) current else null
}

@Composable
fun ValuePicsApp() {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val settingsPrefs = appContext.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
    val viewModelStoreOwner = remember(context) {
        findViewModelStoreOwner(context)
            ?: throw IllegalStateException("No ViewModelStoreOwner available in context chain")
    }
    val viewModel: ValuePicsViewModel = remember(viewModelStoreOwner, appContext) {
        ViewModelProvider(viewModelStoreOwner, ValuePicsViewModel.factory(appContext))[ValuePicsViewModel::class.java]
    }
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppDestination.List.route
    var isBackupRestoreBusy by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf<ExportProgress?>(null) }
    var isSharingDatabase by remember { mutableStateOf(false) }
    var isMergingDatabase by remember { mutableStateOf(false) }
    var pdfExportResult by remember { mutableStateOf<PdfExportResult?>(null) }
    var uiErrorMessage by remember { mutableStateOf<String?>(null) }
    var showHowToDialog by remember { mutableStateOf(false) }
    var howToPdfUri by remember { mutableStateOf<Uri?>(null) }
    var inAppBrowserUrl by remember { mutableStateOf<String?>(null) }
    var showAutoBackupRestoreDialog by remember { mutableStateOf(false) }
    var isRefreshingAutoBackupSlots by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showResumeDraftDialog by remember { mutableStateOf(false) }
    var showPreBackupCautionDialog by remember { mutableStateOf(false) }
    var pendingDestructiveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingActionAfterBackup by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingDeletedItem by remember { mutableStateOf<ValuedItem?>(null) }
    var autoBackupSlots by remember { mutableStateOf<List<AutoBackupSlotInfo>>(emptyList()) }
    var lastMergeAppliedCount by rememberSaveable { mutableIntStateOf(0) }
    var lastMergePhotoCount by rememberSaveable { mutableIntStateOf(0) }
    var lastMergeDedupeCount by rememberSaveable { mutableIntStateOf(0) }
    var lastMergeAtText by rememberSaveable { mutableStateOf("") }
    var pendingScopedExportItemIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    // New-item camera batch state: valuation uses the first photo, remaining photos are attached after save.
    var pendingNewRecordCapturedPhotos by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var pendingNewRecordExtraPhotoPaths by remember { mutableStateOf<List<String>>(emptyList()) }

    val refreshAutoBackupSlots: (onSuccess: (() -> Unit)?, onFinished: (() -> Unit)?) -> Unit = { onSuccess, onFinished ->
        isRefreshingAutoBackupSlots = true
        viewModel.inspectAutoBackupSlots { result ->
            isRefreshingAutoBackupSlots = false
            result.fold(
                onSuccess = { slots ->
                    autoBackupSlots = slots
                    onSuccess?.invoke()
                },
                onFailure = { error ->
                    uiErrorMessage = error.message?.takeIf { it.isNotBlank() }
                        ?: context.getString(R.string.toast_restore_failed)
                }
            )
            onFinished?.invoke()
        }
    }
    var photoTargetSizeKb by remember { mutableStateOf(PhotoUtils.getPhotoTargetSizeKb(context)) }
    val snackbarHostState = remember { SnackbarHostState() }
    var themeMode by remember {
        mutableStateOf(
            if (settingsPrefs.contains(PREF_THEME_MODE)) {
                parseThemeMode(settingsPrefs.getString(PREF_THEME_MODE, ThemeMode.SYSTEM.name))
            } else {
                if (settingsPrefs.getBoolean(PREF_DARK_MODE, false)) ThemeMode.DARK else ThemeMode.SYSTEM
            }
        )
    }
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    var willOwnerName by remember {
        mutableStateOf(
            settingsPrefs.getString(PREF_PROFILE_DISPLAY_NAME, null)
                ?.trim()
                .takeUnless { it.isNullOrBlank() }
                ?: DEFAULT_WILL_OWNER_NAME
        )
    }
    val onWillOwnerNameChanged: (String) -> Unit = { raw ->
        val resolved = raw.trim().ifBlank { DEFAULT_WILL_OWNER_NAME }
        willOwnerName = resolved
        settingsPrefs.edit().putString(PREF_PROFILE_DISPLAY_NAME, resolved).apply()
    }
    val updateThemeMode: (ThemeMode) -> Unit = { selected ->
        themeMode = selected
        settingsPrefs.edit()
            .putString(PREF_THEME_MODE, selected.name)
            .remove(PREF_DARK_MODE)
            .apply()
    }
    var appTier by remember {
        mutableStateOf(
            AppTier.entries.firstOrNull {
                it.name.equals(settingsPrefs.getString(PREF_APP_TIER, null), ignoreCase = true)
            } ?: AppTier.PERSONAL
        )
    }
    var personalTierLocked by remember {
        mutableStateOf(settingsPrefs.getBoolean(PREF_PERSONAL_TIER_LOCKED, true))
    }
    var personalTierLastUnlockMillis by remember {
        mutableLongStateOf(settingsPrefs.getLong(PREF_PERSONAL_TIER_LAST_UNLOCK_MILLIS, 0L))
    }
    var showPersonalUnlockDialog by remember { mutableStateOf(false) }
    var showPersonalPasswordDialog by remember { mutableStateOf(false) }
    var personalPasswordInput by rememberSaveable { mutableStateOf("") }
    var personalPasswordDraft by rememberSaveable { mutableStateOf("") }
    var personalPasswordConfirm by rememberSaveable { mutableStateOf("") }
    var personalPasswordError by remember { mutableStateOf<String?>(null) }

    fun applyTierImmediately(selected: AppTier) {
        appTier = selected
        settingsPrefs.edit().putString(PREF_APP_TIER, selected.name).apply()
        viewModel.setCurrentTier(selected)
    }

    fun persistPersonalLockState() {
        settingsPrefs.edit()
            .putBoolean(PREF_PERSONAL_TIER_LOCKED, personalTierLocked)
            .putLong(PREF_PERSONAL_TIER_LAST_UNLOCK_MILLIS, personalTierLastUnlockMillis)
            .apply()
    }

    fun isPersonalSessionExpired(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (personalTierLocked) return true
        if (personalTierLastUnlockMillis <= 0L) return true
        return nowMillis - personalTierLastUnlockMillis >= PERSONAL_TIER_TIMEOUT_MILLIS
    }

    fun lockPersonalTier() {
        personalTierLocked = true
        persistPersonalLockState()
    }

    fun unlockPersonalTierAndSwitch() {
        personalTierLocked = false
        personalTierLastUnlockMillis = System.currentTimeMillis()
        persistPersonalLockState()
        applyTierImmediately(AppTier.PERSONAL)
    }

    fun requestTierSwitch(selected: AppTier) {
        if (selected == appTier) return
        if (selected == AppTier.INSURANCE) {
            applyTierImmediately(AppTier.INSURANCE)
            return
        }

        if (!isPersonalSessionExpired()) {
            applyTierImmediately(AppTier.PERSONAL)
            return
        }

        personalPasswordInput = ""
        personalPasswordError = null
        showPersonalUnlockDialog = true
    }

    val updateAppTier: (AppTier) -> Unit = { selected -> requestTierSwitch(selected) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appTier, personalTierLocked, personalTierLastUnlockMillis) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                if (appTier == AppTier.PERSONAL && isPersonalSessionExpired()) {
                    lockPersonalTier()
                    applyTierImmediately(AppTier.INSURANCE)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(appTier, personalTierLocked, personalTierLastUnlockMillis) {
        if (appTier != AppTier.PERSONAL || personalTierLocked) return@LaunchedEffect
        while (true) {
            val elapsed = System.currentTimeMillis() - personalTierLastUnlockMillis
            val remaining = PERSONAL_TIER_TIMEOUT_MILLIS - elapsed
            if (remaining <= 0L) {
                lockPersonalTier()
                applyTierImmediately(AppTier.INSURANCE)
                Toast.makeText(context, context.getString(R.string.personal_lock_timeout_message), Toast.LENGTH_SHORT).show()
                break
            }
            delay(minOf(remaining, 1_000L))
        }
    }

    // Sync tier to repository on first composition and whenever appTier changes.
    LaunchedEffect(appTier) {
        viewModel.setCurrentTier(appTier)
    }
    var hasSeenSelectionHelper by remember {
        mutableStateOf(settingsPrefs.getBoolean(PREF_SELECTION_HELPER_SEEN, false))
    }
    var hasSeenExportHelper by remember {
        mutableStateOf(settingsPrefs.getBoolean(PREF_EXPORT_HELPER_SEEN, false))
    }
    var showTotalValueBand by remember {
        mutableStateOf(settingsPrefs.getBoolean(PREF_SHOW_TOTAL_VALUE_BAND, true))
    }
    val markSelectionHelperSeen: () -> Unit = {
        if (!hasSeenSelectionHelper) {
            hasSeenSelectionHelper = true
            settingsPrefs.edit().putBoolean(PREF_SELECTION_HELPER_SEEN, true).apply()
        }
    }
    val markExportHelperSeen: () -> Unit = {
        if (!hasSeenExportHelper) {
            hasSeenExportHelper = true
            settingsPrefs.edit().putBoolean(PREF_EXPORT_HELPER_SEEN, true).apply()
        }
    }

    data class BatchExportJob(
        val label: String,
        val items: List<ValuedItem>,
        val scopeLabel: String,
        val isFiltered: Boolean,
        val reportSortOption: ReportSortOption,
        val reportTitleOverride: String? = null,
        val useWillInstructions: Boolean = false,
        val includeThumbnails: Boolean = false
    )

    fun runBatchExportJobs(
        jobs: List<BatchExportJob>,
        index: Int = 0,
        completedCount: Int = 0
    ) {
        if (index >= jobs.size) {
            logExportEvent("batch_complete", "jobs=${jobs.size}, completed=$completedCount")
            isExportingPdf = false
            exportProgress = null
            Toast.makeText(
                context,
                context.getString(R.string.toast_batch_export_complete, completedCount),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val job = jobs[index]
        logExportEvent(
            "batch_job_start",
            "index=$index, label=${job.label}, items=${job.items.size}, thumbs=${job.includeThumbnails}, will=${job.useWillInstructions}"
        )
        exportProgress = ExportProgress(0, 3, "${job.label}: Starting...")
        viewModel.exportCollectionSummaryPdf(
            items = job.items,
            scopeLabel = job.scopeLabel,
            isFiltered = job.isFiltered,
            reportSortOption = job.reportSortOption,
            reportTitleOverride = job.reportTitleOverride,
            useWillInstructions = job.useWillInstructions,
            includeThumbnails = job.includeThumbnails,
            onProgress = { progress ->
                exportProgress = progress.copy(phase = "${job.label}: ${progress.phase}")
            }
        ) { result ->
            result.fold(
                onSuccess = {
                    logExportEvent("batch_job_success", "index=$index, label=${job.label}")
                    runBatchExportJobs(jobs, index + 1, completedCount + 1)
                },
                onFailure = { error ->
                    logExportEvent(
                        "batch_job_failure",
                        "index=$index, label=${job.label}, reason=${error.message ?: "unknown"}"
                    )
                    isExportingPdf = false
                    exportProgress = null
                    uiErrorMessage = error.message
                        ?.trim()
                        .takeUnless { it.isNullOrBlank() }
                        ?: context.getString(R.string.pdf_error_export_failed)
                }
            )
        }
    }

    val items by viewModel.allItems.collectAsStateWithLifecycle(initialValue = emptyList())
    val allCollections by viewModel.allCollections.collectAsStateWithLifecycle(initialValue = emptyList())
    val allTags by viewModel.allTags.collectAsStateWithLifecycle(initialValue = emptyList())
    val isValuating by viewModel.isValuating.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val selectedItemPhotos by viewModel.selectedItemPhotos.collectAsStateWithLifecycle()
    val isFetchingFullDescription by viewModel.isFetchingFullDescription.collectAsStateWithLifecycle()
    val databaseSizeBytes by viewModel.databaseSizeBytes.collectAsStateWithLifecycle()
    val photoFolderSizeBytes by viewModel.photoFolderSizeBytes.collectAsStateWithLifecycle()
    val photoFolderFileCount by viewModel.photoFolderFileCount.collectAsStateWithLifecycle()
    val lastBackupMillis by viewModel.lastBackupMillis.collectAsStateWithLifecycle()
    val pendingDraftCount by viewModel.pendingDraftCount.collectAsStateWithLifecycle()
    val pendingValuationDraft by viewModel.pendingValuationDraft.collectAsStateWithLifecycle()
    val isUnlimitedUnlocked by viewModel.isUnlimitedUnlocked.collectAsStateWithLifecycle()
    val unlockAccountId by viewModel.unlockAccountId.collectAsStateWithLifecycle()
    val duplicateGroupOrderByItemId by produceState(initialValue = emptyMap<Int, Int>(), items) {
        value = viewModel.getDuplicateGroupOrderByItemId(items)
    }
    val existingTags = allTags
    val appVersionText = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        }.getOrDefault("-")
    }
    val appEditionLabel = remember(context, appTier) {
        when (appTier) {
            AppTier.INSURANCE -> context.getString(R.string.list_app_edition_insurance)
            AppTier.PERSONAL -> context.getString(R.string.list_app_edition_personal)
        }
    }
    val backupFormatVersion = remember { viewModel.getCurrentBackupFormatVersion() }
    val restoreMinBackupFormatVersion = remember { viewModel.getMinimumSupportedRestoreBackupFormatVersion() }
    val lastBackupFormatText = remember(lastBackupMillis) { viewModel.getLastBackupFormatDisplayText() }
    val lastBackupText = remember(lastBackupMillis) {
        if (lastBackupMillis <= 0L) {
            context.getString(R.string.list_last_backup_never)
        } else {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(lastBackupMillis))
        }
    }

    fun createReadableBackupStamp(): String =
        SimpleDateFormat("d-M-yy-HHmm", Locale.getDefault()).format(Date())

    fun createBackupStamp(): String =
        SimpleDateFormat("d-M-yy-HHmmss-SSS", Locale.getDefault()).format(Date())

    fun createBackupPrefix(): String =
        when (appTier) {
            AppTier.INSURANCE -> "VPM"
            AppTier.PERSONAL -> "VPO"
        }

    fun createBackupFileName(): String {
        return "${createBackupPrefix()}-${createReadableBackupStamp()}.zip"
    }

    fun createScopedExportFileName(): String {
        return "${createBackupPrefix()}-${createReadableBackupStamp()}.zip"
    }

    val openSourceLink: (String) -> Unit = { url ->
        val trimmedUrl = url.trim()
        if (trimmedUrl.isNotBlank()) {
            val parsed = Uri.parse(trimmedUrl)
            when (parsed.scheme?.lowercase(Locale.ROOT)) {
                "http", "https" -> inAppBrowserUrl = trimmedUrl
                null -> inAppBrowserUrl = "https://$trimmedUrl"
                else -> {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, parsed).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    }.onFailure {
                        uiErrorMessage = context.getString(R.string.link_error_open_failed)
                    }
                }
            }
        }
    }

    fun resolvePdfUri(result: PdfExportResult): Uri? {
        result.contentUriString?.let { return Uri.parse(it) }
        val path = result.absolutePath ?: return null
        return runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(path)
            )
        }.getOrNull()
    }

    fun openPdf(result: PdfExportResult) {
        val uri = resolvePdfUri(result) ?: run {
            uiErrorMessage = context.getString(R.string.pdf_error_open_unable)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { uiErrorMessage = context.getString(R.string.pdf_error_open_no_app) }
    }

    fun printPdf(result: PdfExportResult) {
        val uri = resolvePdfUri(result) ?: run {
            uiErrorMessage = context.getString(R.string.pdf_error_print_unable)
            return
        }
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            uiErrorMessage = context.getString(R.string.pdf_error_print_service_unavailable)
            return
        }
        runCatching {
            val printJobName = if (result.isFiltered) {
                "ValuePics_Filtered_${result.itemCount}_records"
            } else {
                "ValuePics_All_${result.itemCount}_records"
            }
            printManager.print(
                printJobName,
                PdfUriPrintAdapter(context, uri, result.fileName),
                null
            )
        }.onFailure {
            uiErrorMessage = context.getString(R.string.pdf_error_print_start_failed)
        }
    }

    fun sharePdf(result: PdfExportResult) {
        val uri = resolvePdfUri(result) ?: run {
            uiErrorMessage = context.getString(R.string.pdf_error_share_unable)
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.pdf_share_chooser_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
            .onFailure { uiErrorMessage = context.getString(R.string.pdf_error_share_no_app) }
    }

    fun openHowToPdf(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { uiErrorMessage = context.getString(R.string.pdf_error_open_no_app) }
    }

    fun shareDatabaseAttachment(attachment: DatabaseShareAttachment) {
        val uri = runCatching { Uri.parse(attachment.contentUriString) }
            .getOrNull()
            ?: run {
                uiErrorMessage = context.getString(R.string.database_error_share_unable)
                return
            }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.database_share_subject))
            putExtra(Intent.EXTRA_TITLE, attachment.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.database_share_chooser_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
            .onFailure { uiErrorMessage = context.getString(R.string.database_error_share_no_app) }
    }

    fun printHowToPdf(uri: Uri) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            uiErrorMessage = context.getString(R.string.pdf_error_print_service_unavailable)
            return
        }
        runCatching {
            printManager.print(
                "ValuePics_HowTo",
                PdfUriPrintAdapter(context, uri, "ValuePics_HowTo"),
                null
            )
        }.onFailure { uiErrorMessage = context.getString(R.string.pdf_error_print_start_failed) }
    }

    LaunchedEffect(Unit) {
        PhotoUtils.ensureCollectionFolders(context)
        viewModel.cleanupOrphanedPhotos { /* Startup cleanup is silent by design. */ }
        viewModel.refreshDatabaseSize()
        viewModel.refreshPhotoFolderSize()
    }

    var lastPromptedDraftPath by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(currentRoute, pendingDraftCount, pendingValuationDraft?.photoPath, pendingValuationDraft?.savedAtMillis) {
        if (currentRoute != AppDestination.List.route) return@LaunchedEffect
        if (pendingDraftCount <= 0) {
            lastPromptedDraftPath = ""
            return@LaunchedEffect
        }

        val draft = pendingValuationDraft
        if (draft != null && draft.photoPath.isNotBlank() && File(draft.photoPath).exists()) {
            if (draft.photoPath != lastPromptedDraftPath) {
                showResumeDraftDialog = true
                lastPromptedDraftPath = draft.photoPath
            }
        }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isBackupRestoreBusy = true
        viewModel.backupToUri(uri) { result ->
            isBackupRestoreBusy = false
            result.fold(
                onSuccess = { summary ->
                    Toast.makeText(
                        context,
                        context.getString(
                            if (summary.isVerified) R.string.toast_backup_complete_verified else R.string.toast_backup_complete_unverified,
                            summary.itemCount,
                            summary.photoCount
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    // If we backed up as a safety step before a destructive action, proceed now.
                    pendingActionAfterBackup?.invoke()
                    pendingActionAfterBackup = null
                },
                onFailure = {
                    Toast.makeText(context, context.getString(R.string.toast_backup_failed), Toast.LENGTH_LONG).show()
                    // Backup failed — do NOT proceed with the destructive action.
                    pendingActionAfterBackup = null
                }
            )
        }
    }
    val launchBackupDocument = { backupLauncher.launch(createBackupFileName()) }
    val launchBackupWithName = { name: String ->
        backupLauncher.launch(name.ifBlank { createBackupFileName() })
    }

    val scopedExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val exportIds = pendingScopedExportItemIds
        if (exportIds.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_export_records_failed), Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        viewModel.backupSelectedItemsToUri(uri, exportIds) { result ->
            result.fold(
                onSuccess = { summary ->
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.toast_export_records_complete,
                            summary.itemCount,
                            summary.photoCount
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                },
                onFailure = {
                    Toast.makeText(context, context.getString(R.string.toast_export_records_failed), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val collectionActions = CollectionActions(
        onAdd    = { name, onResult -> viewModel.addCollection(name, onResult) },
        onRename = { oldName, newName, onResult -> viewModel.renameCollection(oldName, newName, onResult) },
        onDelete = { name, onResult -> viewModel.deleteCollection(name, onResult) },
        onCount  = { name, onResult -> viewModel.countItemsInCollection(name, onResult) }
    )

    val tagActions = TagActions(
        onAdd = { name, onResult -> viewModel.addTag(name, onResult) },
        onRename = { oldName, newName, onResult -> viewModel.renameTag(oldName, newName, onResult) },
        onDelete = { name, onResult -> viewModel.deleteTag(name, onResult) }
    )

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isBackupRestoreBusy = true
        viewModel.restoreFromUri(uri) { result ->
            isBackupRestoreBusy = false
            result.fold(
                onSuccess = { summary ->
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.toast_restore_complete,
                            summary.itemCount,
                            summary.photoCount
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                },
                onFailure = {
                    val message = it.message?.takeIf { msg -> msg.isNotBlank() }
                        ?: context.getString(R.string.toast_restore_failed)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            )
            viewModel.refreshDatabaseSize()
            viewModel.refreshPhotoFolderSize()
        }
    }

    val mergeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isMergingDatabase = true
        viewModel.mergeFromUri(uri) { result ->
            isMergingDatabase = false
            result.fold(
                onSuccess = { summary ->
                    lastMergeAppliedCount = summary.itemCount
                    lastMergePhotoCount = summary.photoCount
                    lastMergeDedupeCount = summary.dedupeCount
                    lastMergeAtText = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                     val mergeMessage = if (summary.dedupeCount > 0) {
                         context.getString(
                             R.string.toast_merge_complete_with_dedupe,
                             summary.itemCount,
                             summary.photoCount,
                             summary.dedupeCount
                         )
                     } else {
                         context.getString(
                             R.string.toast_merge_complete,
                             summary.itemCount,
                             summary.photoCount
                         )
                     }
                     Toast.makeText(
                         context,
                         mergeMessage,
                         Toast.LENGTH_LONG
                     ).show()
                },
                onFailure = {
                    val message = it.message?.takeIf { msg -> msg.isNotBlank() }
                        ?: context.getString(R.string.toast_merge_failed)
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            )
            viewModel.refreshDatabaseSize()
            viewModel.refreshPhotoFolderSize()
        }
    }

    ValuePicsTheme(darkTheme = useDarkTheme, appTier = appTier) {
        Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { innerPadding ->
            androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
                NavHost(
                    navController = navController,
                    startDestination = AppDestination.List.route
                ) {
                composable(AppDestination.List.route) {
                    ValuePicsListRoute(
                        items = items,
                        allCollections = allCollections,
                        allTags = allTags,
                        navToCamera = {
                            navController.navigate(AppDestination.Camera.route) {
                                launchSingleTop = true
                            }
                        },
                        navToDetails = { item ->
                            viewModel.selectItem(item)
                            navController.navigate(AppDestination.Details.createRoute(item.id)) {
                                launchSingleTop = true
                            }
                        },
                        onOpenSourceLink = openSourceLink,
                        onBackupRequested = launchBackupDocument,
                        onQuickBackupRequested = launchBackupDocument,
                        onRestoreRequested = {
                            pendingDestructiveAction = {
                                restoreLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/octet-stream",
                                        "application/x-sqlite3",
                                        "application/vnd.sqlite3",
                                        "*/*"
                                    )
                                )
                            }
                            showPreBackupCautionDialog = true
                        },
                        onRestoreAutoBackupRequested = {
                            pendingDestructiveAction = {
                                isBackupRestoreBusy = true
                                refreshAutoBackupSlots(
                                    { showAutoBackupRestoreDialog = true },
                                    { isBackupRestoreBusy = false }
                                )
                            }
                            showPreBackupCautionDialog = true
                        },
                        onMergeDatabaseRequested = {
                            pendingDestructiveAction = {
                                mergeLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/octet-stream",
                                        "application/x-sqlite3",
                                        "application/vnd.sqlite3",
                                        "*/*"
                                    )
                                )
                            }
                            showPreBackupCautionDialog = true
                        },
                        onImportRecordsRequested = {
                            pendingDestructiveAction = {
                                mergeLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/octet-stream",
                                        "application/x-sqlite3",
                                        "application/vnd.sqlite3",
                                        "*/*"
                                    )
                                )
                            }
                            showPreBackupCautionDialog = true
                        },
                        onDeleteSelectedRequested = { selectedIds ->
                            if (selectedIds.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.toast_delete_selected_none), Toast.LENGTH_LONG).show()
                            } else {
                                viewModel.deleteItemsByIds(selectedIds) { result ->
                                    result.fold(
                                        onSuccess = { removed ->
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_delete_selected_complete, removed),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        },
                                        onFailure = {
                                            Toast.makeText(context, context.getString(R.string.toast_delete_selected_failed), Toast.LENGTH_LONG).show()
                                        }
                                    )
                                }
                            }
                        },
                        isBackupRestoreBusy = isBackupRestoreBusy,
                        isMergingDatabase = isMergingDatabase,
                        lastBackupText = lastBackupText,
                        onExportPdfRequested = { filteredItems, scopeLabel, isFiltered, reportSortOption, includeThumbnails ->
                            logExportEvent(
                                "single_export_start",
                                "items=${filteredItems.size}, scope=$scopeLabel, filtered=$isFiltered, sort=$reportSortOption, thumbs=$includeThumbnails"
                            )
                            isExportingPdf = true
                            exportProgress = ExportProgress(0, 3, "Starting...")
                            viewModel.exportCollectionSummaryPdf(
                                items = filteredItems,
                                scopeLabel = scopeLabel,
                                isFiltered = isFiltered,
                                reportSortOption = reportSortOption,
                                includeThumbnails = includeThumbnails,
                                onProgress = { progress -> exportProgress = progress }
                            ) { result ->
                                isExportingPdf = false
                                exportProgress = null
                                result.fold(
                                    onSuccess = { export ->
                                        logExportEvent(
                                            "single_export_success",
                                            "file=${export.fileName}, items=${export.itemCount}, location=${export.location}"
                                        )
                                        pdfExportResult = export
                                    },
                                    onFailure = { error ->
                                        logExportEvent("single_export_failure", "reason=${error.message ?: "unknown"}")
                                        uiErrorMessage = error.message
                                            ?.trim()
                                            .takeUnless { it.isNullOrBlank() }
                                            ?: context.getString(R.string.pdf_error_export_failed)
                                    }
                                )
                            }
                        },
                        onPrintWillRequested = { scopedItems, scopeLabel, reportSortOption, includeThumbnails ->
                            val willItems = scopedItems.filter { it.willInstructions.trim().isNotBlank() }
                            logExportEvent(
                                "will_export_start",
                                "scope=$scopeLabel, totalScopeItems=${scopedItems.size}, willItems=${willItems.size}, sort=$reportSortOption, thumbs=$includeThumbnails"
                            )
                            if (willItems.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.toast_print_will_no_records), Toast.LENGTH_LONG).show()
                            } else {
                                isExportingPdf = true
                                exportProgress = ExportProgress(0, 3, "Starting...")
                                viewModel.exportCollectionSummaryPdf(
                                    items = willItems,
                                    scopeLabel = "Will records | $scopeLabel",
                                    isFiltered = true,
                                    reportSortOption = reportSortOption,
                                    reportTitleOverride = context.getString(R.string.will_report_title, willOwnerName),
                                    useWillInstructions = true,
                                    includeThumbnails = includeThumbnails,
                                    onProgress = { progress -> exportProgress = progress }
                                ) { result ->
                                    isExportingPdf = false
                                    exportProgress = null
                                    result.fold(
                                        onSuccess = { export ->
                                            logExportEvent(
                                                "will_export_success",
                                                "file=${export.fileName}, items=${export.itemCount}, location=${export.location}"
                                            )
                                            printPdf(export)
                                        },
                                        onFailure = { error ->
                                            logExportEvent("will_export_failure", "reason=${error.message ?: "unknown"}")
                                            uiErrorMessage = error.message
                                                ?.trim()
                                                .takeUnless { it.isNullOrBlank() }
                                                ?: context.getString(R.string.pdf_error_export_failed)
                                        }
                                    )
                                }
                            }
                        },
                        onBatchExportRequested = { scopedItems, scopeLabel, isFiltered, reportSortOption, options ->
                            val jobs = buildList {
                                if (options.summaryTextOnly) {
                                    add(
                                        BatchExportJob(
                                            label = context.getString(R.string.pdf_batch_export_summary_text),
                                            items = scopedItems,
                                            scopeLabel = scopeLabel,
                                            isFiltered = isFiltered,
                                            reportSortOption = reportSortOption,
                                            includeThumbnails = false
                                        )
                                    )
                                }
                                if (options.summaryWithPhotos) {
                                    add(
                                        BatchExportJob(
                                            label = context.getString(R.string.pdf_batch_export_summary_photos),
                                            items = scopedItems,
                                            scopeLabel = scopeLabel,
                                            isFiltered = isFiltered,
                                            reportSortOption = reportSortOption,
                                            includeThumbnails = true
                                        )
                                    )
                                }
                                val willItems = scopedItems.filter { it.willInstructions.trim().isNotBlank() }
                                if (options.willTextOnly && willItems.isNotEmpty()) {
                                    add(
                                        BatchExportJob(
                                            label = context.getString(R.string.pdf_batch_export_will_text),
                                            items = willItems,
                                            scopeLabel = "Will records | $scopeLabel",
                                            isFiltered = true,
                                            reportSortOption = reportSortOption,
                                            reportTitleOverride = context.getString(R.string.will_report_title, willOwnerName),
                                            useWillInstructions = true,
                                            includeThumbnails = false
                                        )
                                    )
                                }
                                if (options.willWithPhotos && willItems.isNotEmpty()) {
                                    add(
                                        BatchExportJob(
                                            label = context.getString(R.string.pdf_batch_export_will_photos),
                                            items = willItems,
                                            scopeLabel = "Will records | $scopeLabel",
                                            isFiltered = true,
                                            reportSortOption = reportSortOption,
                                            reportTitleOverride = context.getString(R.string.will_report_title, willOwnerName),
                                            useWillInstructions = true,
                                            includeThumbnails = true
                                        )
                                    )
                                }
                            }
                            if (jobs.isEmpty()) {
                                logExportEvent(
                                    "batch_export_no_eligible",
                                    "scope=$scopeLabel, selected=${options.summaryTextOnly || options.summaryWithPhotos || options.willTextOnly || options.willWithPhotos}"
                                )
                                Toast.makeText(context, context.getString(R.string.toast_batch_export_no_eligible), Toast.LENGTH_LONG).show()
                            } else {
                                logExportEvent("batch_export_start", "jobs=${jobs.size}, scope=$scopeLabel")
                                isExportingPdf = true
                                runBatchExportJobs(jobs)
                            }
                        },
                        onExportRecordsRequested = { scopedItems, _ ->
                            val exportIds = scopedItems.map { it.id }.filter { it > 0 }.toSet()
                            if (exportIds.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.toast_export_records_failed), Toast.LENGTH_LONG).show()
                            } else {
                                pendingScopedExportItemIds = exportIds
                                scopedExportLauncher.launch(createScopedExportFileName())
                            }
                        },
                        isExportingPdf = isExportingPdf,
                        exportProgress = exportProgress,
                        photoTargetSizeKb = photoTargetSizeKb,
                        onPhotoTargetSizeChange = { targetKb ->
                            photoTargetSizeKb = targetKb
                            PhotoUtils.setPhotoTargetSizeKb(context, targetKb)
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_photo_target_set, targetKb),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        onAboutRequested = {
                            viewModel.refreshDatabaseSize()
                            viewModel.refreshPhotoFolderSize()
                            viewModel.refreshPhotoFolderFileCount()
                        },
                        appVersionText = appVersionText,
                        appEditionLabel = appEditionLabel,
                        backupFormatVersion = backupFormatVersion,
                        restoreMinBackupFormatVersion = restoreMinBackupFormatVersion,
                        lastBackupFormatText = lastBackupFormatText,
                        databaseSizeText = Formatter.formatFileSize(context, databaseSizeBytes),
                        photoFolderSizeText = Formatter.formatFileSize(context, photoFolderSizeBytes),
                        photoFolderFileCount = photoFolderFileCount,
                        lastMergeAppliedCount = lastMergeAppliedCount,
                        lastMergePhotoCount = lastMergePhotoCount,
                        lastMergeDedupeCount = lastMergeDedupeCount,
                        lastMergeAtText = lastMergeAtText,
                        pendingDraftCount = pendingDraftCount,
                        onHowToRequested = {
                            runCatching { HowToHelper.getHowToPdfUri(context) }
                                .onSuccess { uri ->
                                    howToPdfUri = uri
                                    showHowToDialog = true
                                }
                                .onFailure { uiErrorMessage = context.getString(R.string.howto_error_load_failed) }
                        },
                        onShareDatabaseRequested = {
                            isSharingDatabase = true
                            viewModel.prepareDatabaseShareAttachment { result ->
                                isSharingDatabase = false
                                result.fold(
                                    onSuccess = { attachment -> shareDatabaseAttachment(attachment) },
                                    onFailure = { uiErrorMessage = context.getString(R.string.database_error_share_prepare_failed) }
                                )
                            }
                        },
                        isSharingDatabase = isSharingDatabase,
                        collectionActions = collectionActions,
                        tagActions = tagActions,
                        themeMode = themeMode,
                        onThemeModeSelected = updateThemeMode,
                        appTier = appTier,
                        onAppTierSelected = updateAppTier,
                        onLockPersonalNowRequested = {
                            lockPersonalTier()
                            applyTierImmediately(AppTier.INSURANCE)
                        },
                        hasSeenExportHelper = hasSeenExportHelper,
                        onExportHelperSeen = markExportHelperSeen,
                        hasSeenSelectionHelper = hasSeenSelectionHelper,
                        duplicateGroupOrderByItemId = duplicateGroupOrderByItemId,
                        onSelectionHelperSeen = markSelectionHelperSeen
                        ,
                        willOwnerName = willOwnerName,
                        onWillOwnerNameChange = onWillOwnerNameChanged,
                        onLoadSampleDataRequested = { callback ->
                            viewModel.seedSampleData(callback)
                        },
                        onRemoveSampleDataRequested = { callback ->
                            viewModel.removeSampleData(callback)
                        },
                        recordLimit = viewModel.getRecordLimit(),
                        isUnlimitedUnlocked = isUnlimitedUnlocked,
                        unlockAccountId = unlockAccountId,
                        onUnlockWithPassword = { password, accountId, callback ->
                            viewModel.unlockUnlimitedWithPassword(password, accountId, callback)
                        },
                        onRestoreUnlimited = { accountId, recoveryCode, callback ->
                            viewModel.restoreUnlimitedFromAccount(accountId, recoveryCode, callback)
                        },
                        showTotalValueBand = showTotalValueBand,
                        onToggleTotalValueBand = {
                            showTotalValueBand = !showTotalValueBand
                            settingsPrefs.edit().putBoolean(PREF_SHOW_TOTAL_VALUE_BAND, showTotalValueBand).apply()
                        }
                    )
                }

                composable(
                    route = AppDestination.Camera.routeWithArgs,
                    arguments = listOf(
                        navArgument(AppDestination.Camera.ARG_ATTACH_ITEM_ID) {
                            type = NavType.IntType
                            defaultValue = -1
                        }
                    )
                ) { entry ->
                    val attachItemId = entry.arguments?.getInt(AppDestination.Camera.ARG_ATTACH_ITEM_ID)
                        ?.takeIf { it > 0 }
                    ValuePicsCameraRoute(
                        photoTargetSizeKb = photoTargetSizeKb,
                        themeMode = themeMode,
                        onThemeModeSelected = updateThemeMode,
                        appTier = appTier,
                        onAppTierSelected = updateAppTier,
                        onPhotoTargetSizeChange = { selectedSizeKb ->
                            photoTargetSizeKb = selectedSizeKb
                            PhotoUtils.setPhotoTargetSizeKb(context, selectedSizeKb)
                        },
                        onPhotoCaptured = { photoPath, source, keepCameraOpen ->
                            if (attachItemId != null) {
                                viewModel.addPhotoToItem(attachItemId, photoPath, false)
                                if (!keepCameraOpen) {
                                    navController.navigate(AppDestination.Details.createRoute(attachItemId)) {
                                        launchSingleTop = true
                                    }
                                }
                            } else {
                                val normalizedPath = photoPath.trim()
                                if (normalizedPath.isBlank()) {
                                    return@ValuePicsCameraRoute
                                }
                                val updatedBatch = pendingNewRecordCapturedPhotos + (normalizedPath to source)
                                pendingNewRecordCapturedPhotos = updatedBatch
                                if (keepCameraOpen) {
                                    return@ValuePicsCameraRoute
                                }

                                val firstCapture = updatedBatch.firstOrNull() ?: return@ValuePicsCameraRoute
                                pendingNewRecordExtraPhotoPaths = updatedBatch.drop(1).map { it.first }
                                pendingNewRecordCapturedPhotos = emptyList()
                                navController.navigate(
                                    AppDestination.Valuation.createRoute(
                                        firstCapture.first,
                                        firstCapture.second.ifBlank { "camera" }
                                    )
                                ) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        supportsMultiGalleryImport = true,
                        onCancel = {
                            if (attachItemId != null) {
                                navController.navigate(AppDestination.Details.createRoute(attachItemId)) {
                                    launchSingleTop = true
                                }
                            } else {
                                pendingNewRecordCapturedPhotos = emptyList()
                                pendingNewRecordExtraPhotoPaths = emptyList()
                                navController.navigate(AppDestination.List.route) {
                                    popUpTo(AppDestination.List.route) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }

                composable(
                    route = AppDestination.Valuation.route,
                    arguments = listOf(
                        navArgument(AppDestination.Valuation.ARG_PHOTO_PATH) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                        navArgument(AppDestination.Valuation.ARG_PHOTO_SOURCE) {
                            type = NavType.StringType
                            defaultValue = "camera"
                        }
                    ),
                    deepLinks = listOf(
                        navDeepLink { uriPattern = AppDestination.Valuation.DEEP_LINK_ROUTE_PATTERN }
                    )
                ) { entry ->
                    val routePhotoPath = entry.arguments
                        ?.getString(AppDestination.Valuation.ARG_PHOTO_PATH)
                        .orEmpty()
                    val routePhotoSource = entry.arguments
                        ?.getString(AppDestination.Valuation.ARG_PHOTO_SOURCE)
                        .orEmpty()
                    ValuePicsValuationRoute(
                        routePhotoPath = routePhotoPath,
                        routePhotoSource = routePhotoSource,
                        existingCollections = allCollections,
                        existingTags = existingTags,
                        isValuating = isValuating,
                        viewModel = viewModel,
                        themeMode = themeMode,
                        onThemeModeSelected = updateThemeMode,
                        appTier = appTier,
                        onAppTierSelected = updateAppTier,
                        onNavigateList = {
                            pendingNewRecordCapturedPhotos = emptyList()
                            pendingNewRecordExtraPhotoPaths = emptyList()
                            navController.navigate(AppDestination.List.route) {
                                popUpTo(AppDestination.List.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onNavigateCamera = {
                            pendingNewRecordCapturedPhotos = emptyList()
                            pendingNewRecordExtraPhotoPaths = emptyList()
                            navController.navigate(AppDestination.Camera.route) {
                                popUpTo(AppDestination.List.route)
                                launchSingleTop = true
                            }
                        },
                        onSave = { item, onDone ->
                            triggerHapticFeedback(context, HapticType.SAVE)
                            val extraPhotoPaths = pendingNewRecordExtraPhotoPaths
                            pendingNewRecordExtraPhotoPaths = emptyList()
                            pendingNewRecordCapturedPhotos = emptyList()
                            viewModel.saveItem(item, additionalPhotoPaths = extraPhotoPaths) {
                                onDone()
                            }
                        },
                        currentRecordCount = items.size,
                        recordLimit = viewModel.getRecordLimit(),
                        isUnlimitedUnlocked = isUnlimitedUnlocked,
                        unlockAccountId = unlockAccountId,
                        onUnlockWithPassword = { password, accountId, callback ->
                            viewModel.unlockUnlimitedWithPassword(password, accountId, callback)
                        },
                        onRestoreUnlimited = { accountId, recoveryCode, callback ->
                            viewModel.restoreUnlimitedFromAccount(accountId, recoveryCode, callback)
                        }
                    )
                }

                composable(
                    route = AppDestination.Details.route,
                    arguments = listOf(
                        navArgument(AppDestination.Details.ARG_ITEM_ID) {
                            type = NavType.IntType
                        }
                    ),
                    deepLinks = listOf(
                        navDeepLink { uriPattern = AppDestination.Details.DEEP_LINK_ROUTE_PATTERN }
                    )
                ) { entry ->
                    val routeItemId = entry.arguments?.getInt(AppDestination.Details.ARG_ITEM_ID) ?: 0
                    ValuePicsDetailsRoute(
                        routeItemId = routeItemId,
                        items = items,
                        selectedItem = selectedItem,
                        selectedItemPhotos = selectedItemPhotos,
                        allCollections = allCollections,
                        existingTags = existingTags,
                        isFetchingFullDescription = isFetchingFullDescription,
                        themeMode = themeMode,
                        onThemeModeSelected = updateThemeMode,
                        appTier = appTier,
                        onAppTierSelected = updateAppTier,
                        onOpenSourceLink = openSourceLink,
                        onSelectItemById = { viewModel.selectItemById(it) },
                        collectionActions = collectionActions,
                        onBack = {
                            pendingDeletedItem = null
                            viewModel.clearSelection()
                            navController.navigate(AppDestination.List.route) {
                                popUpTo(AppDestination.List.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                        onDelete = {
                            pendingDeletedItem = it
                            showDeleteConfirmDialog = true
                        },
                        onExportRecordRequested = { selected ->
                            if (selected.id <= 0) {
                                Toast.makeText(context, context.getString(R.string.toast_export_records_failed), Toast.LENGTH_LONG).show()
                            } else {
                                pendingScopedExportItemIds = setOf(selected.id)
                                scopedExportLauncher.launch(createScopedExportFileName())
                            }
                        },
                        onUpdateItem = { updated -> viewModel.updateItem(updated) },
                        onFetchFullDescription = { lookupItem -> viewModel.fetchAndApplyFullDescription(lookupItem) },
                        onSetCoverPhoto = { itemId, photoId -> viewModel.setCoverPhoto(itemId, photoId) },
                        onRequestAddPhoto = { itemId ->
                            navController.navigate(AppDestination.Camera.createRoute(itemId)) {
                                launchSingleTop = true
                            }
                        },
                        onDeletePhotoFromItem = { photoId, itemId -> viewModel.deletePhotoFromItem(photoId, itemId) },
                        onCopyToOtherTier = { itemId, onResult -> viewModel.copyItemToOtherTier(itemId, onResult) }
                    )
                }
                }
            }

            if (showPersonalUnlockDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showPersonalUnlockDialog = false
                        personalPasswordError = null
                    },
                    title = { Text(stringResource(R.string.personal_unlock_title)) },
                    text = {
                        androidx.compose.foundation.layout.Column {
                            Text(stringResource(R.string.personal_unlock_message))
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                            OutlinedTextField(
                                value = personalPasswordInput,
                                onValueChange = {
                                    personalPasswordInput = it
                                    personalPasswordError = null
                                },
                                singleLine = true,
                                label = { Text(stringResource(R.string.personal_unlock_password_label)) }
                            )
                            TextButton(onClick = {
                                showPersonalUnlockDialog = false
                                personalPasswordError = null
                                showPersonalPasswordDialog = true
                            }) {
                                Text(stringResource(R.string.personal_password_change_action))
                            }
                            personalPasswordError?.let { error ->
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))
                                Text(text = error, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val savedPassword = settingsPrefs.getString(PREF_PERSONAL_TIER_PASSWORD, "").orEmpty()
                            if (personalPasswordInput == savedPassword) {
                                unlockPersonalTierAndSwitch()
                                showPersonalUnlockDialog = false
                                personalPasswordError = null
                            } else {
                                personalPasswordError = context.getString(R.string.personal_unlock_incorrect_password)
                            }
                        }) {
                            Text(stringResource(R.string.personal_unlock_action))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showPersonalUnlockDialog = false
                            personalPasswordError = null
                        }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }

            if (showPersonalPasswordDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showPersonalPasswordDialog = false
                        personalPasswordDraft = ""
                        personalPasswordConfirm = ""
                        personalPasswordError = null
                    },
                    title = { Text(stringResource(R.string.personal_password_set_title)) },
                    text = {
                        androidx.compose.foundation.layout.Column {
                            Text(stringResource(R.string.personal_password_set_message))
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                            OutlinedTextField(
                                value = personalPasswordDraft,
                                onValueChange = {
                                    personalPasswordDraft = it
                                    personalPasswordError = null
                                },
                                singleLine = true,
                                label = { Text(stringResource(R.string.personal_password_label)) }
                            )
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                            OutlinedTextField(
                                value = personalPasswordConfirm,
                                onValueChange = {
                                    personalPasswordConfirm = it
                                    personalPasswordError = null
                                },
                                singleLine = true,
                                label = { Text(stringResource(R.string.personal_password_confirm_label)) }
                            )
                            personalPasswordError?.let { error ->
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 4.dp))
                                Text(text = error, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            if (personalPasswordDraft != personalPasswordConfirm) {
                                personalPasswordError = context.getString(R.string.personal_password_mismatch)
                                return@TextButton
                            }
                            settingsPrefs.edit().putString(PREF_PERSONAL_TIER_PASSWORD, personalPasswordDraft).apply()
                            showPersonalPasswordDialog = false
                            personalPasswordDraft = ""
                            personalPasswordConfirm = ""
                            personalPasswordError = null
                            Toast.makeText(context, context.getString(R.string.personal_password_saved), Toast.LENGTH_SHORT).show()
                        }) {
                            Text(stringResource(R.string.common_save))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showPersonalPasswordDialog = false
                            personalPasswordDraft = ""
                            personalPasswordConfirm = ""
                            personalPasswordError = null
                        }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                )
            }

            pdfExportResult?.let { result ->
                PdfExportResultDialog(
                    result = result,
                    onDismiss = { pdfExportResult = null },
                    onOpen = { openPdf(result) },
                    onPrint = { printPdf(result) },
                    onShare = { sharePdf(result) }
                )
            }

            if (uiErrorMessage != null) {
                ErrorDialog(
                    message = uiErrorMessage.orEmpty(),
                    onDismiss = { uiErrorMessage = null }
                )
            }

            if (showHowToDialog) {
                howToPdfUri?.let { uri ->
                    HowToDialog(
                        uri = uri,
                        onDismiss = { showHowToDialog = false },
                        onOpen = {
                            openHowToPdf(uri)
                            showHowToDialog = false
                        },
                        onPrint = {
                            printHowToPdf(uri)
                            showHowToDialog = false
                        }
                    )
                }
            }

            if (showPreBackupCautionDialog) {
                PreBackupCautionDialog(
                    defaultBackupName = createBackupFileName(),
                    onBackupFirst = { backupName ->
                        showPreBackupCautionDialog = false
                        pendingActionAfterBackup = pendingDestructiveAction
                        pendingDestructiveAction = null
                        launchBackupWithName(backupName)
                    },
                    onProceedAnyway = {
                        showPreBackupCautionDialog = false
                        val action = pendingDestructiveAction
                        pendingDestructiveAction = null
                        action?.invoke()
                    },
                    onDismiss = {
                        showPreBackupCautionDialog = false
                        pendingDestructiveAction = null
                    }
                )
            }

            if (showAutoBackupRestoreDialog) {
                AutoBackupRestoreDialog(
                    slots = autoBackupSlots,
                    onDismiss = { showAutoBackupRestoreDialog = false },
                    onRefresh = { refreshAutoBackupSlots(null, null) },
                    isRefreshing = isRefreshingAutoBackupSlots,
                    onRestoreSlot = { slotId ->
                        showAutoBackupRestoreDialog = false
                        isBackupRestoreBusy = true
                        viewModel.restoreFromAutoBackupSlot(slotId) { result ->
                            isBackupRestoreBusy = false
                            result.fold(
                                onSuccess = { summary ->
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.toast_restore_complete,
                                            summary.itemCount,
                                            summary.photoCount
                                        ),
                                        Toast.LENGTH_LONG
                                    ).show()
                                },
                                onFailure = {
                                    val message = it.message?.takeIf { msg -> msg.isNotBlank() }
                                        ?: context.getString(R.string.toast_restore_failed)
                                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                }
                            )
                            viewModel.refreshDatabaseSize()
                            viewModel.refreshPhotoFolderSize()
                        }
                    }
                )
            }

            if (showDeleteConfirmDialog) {
                val deleteItemName = pendingDeletedItem?.itemName?.trim().orEmpty()
                    .ifBlank { stringResource(R.string.delete_confirm_item_fallback) }
                DeleteConfirmDialog(
                    itemName = deleteItemName,
                    onDismiss = {
                        showDeleteConfirmDialog = false
                        pendingDeletedItem = null
                    },
                    onConfirm = {
                        triggerHapticFeedback(context, HapticType.DELETE)
                        pendingDeletedItem?.let(viewModel::deleteItem)
                        showDeleteConfirmDialog = false
                        navController.navigate(AppDestination.List.route) {
                            popUpTo(AppDestination.List.route) { inclusive = true }
                            launchSingleTop = true
                        }
                        scope.launch {
                            val deletedName = pendingDeletedItem?.itemName
                                ?.takeIf { it.isNotBlank() }
                                ?: context.getString(R.string.delete_confirm_item_fallback)
                            val result = showDeleteUndoSnackbar(
                                snackbarHostState = snackbarHostState,
                                context = context,
                                itemName = deletedName
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                pendingDeletedItem?.let(viewModel::restoreDeletedItem)
                            }
                            pendingDeletedItem = null
                        }
                    }
                )
            }


            if (showResumeDraftDialog) {
                ResumeDraftDialog(
                    onDismiss = { showResumeDraftDialog = false },
                    onResume = {
                        val draft = pendingValuationDraft
                        if (draft != null && File(draft.photoPath).exists()) {
                            navController.navigate(AppDestination.Valuation.createRoute(draft.photoPath, draft.photoSource)) {
                                launchSingleTop = true
                            }
                        }
                        showResumeDraftDialog = false
                    },
                    onDiscard = {
                        pendingValuationDraft?.photoPath?.let(viewModel::clearValuationDraft)
                        showResumeDraftDialog = false
                    }
                )
            }

            inAppBrowserUrl?.let { url ->
                InAppWebViewerDialog(
                    initialUrl = url,
                    onDismiss = { inAppBrowserUrl = null }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InAppWebViewerDialog(
    initialUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            webChromeClient = WebChromeClient()
        }
    }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    fun refreshNavigationState() {
        canGoBack = webView.canGoBack()
        canGoForward = webView.canGoForward()
    }

    DisposableEffect(webView, initialUrl) {
        webView.webViewClient = object : WebViewClient() {
            private fun handleExternalUri(targetUri: Uri): Boolean {
                val scheme = targetUri.scheme?.lowercase(Locale.ROOT)
                if (scheme == "http" || scheme == "https") return false

                val intent = if (scheme == "intent") {
                    runCatching {
                        Intent.parseUri(targetUri.toString(), Intent.URI_INTENT_SCHEME).apply {
                            addCategory(Intent.CATEGORY_BROWSABLE)
                            component = null
                            selector = null
                        }
                    }.getOrElse {
                        Intent(Intent.ACTION_VIEW, targetUri)
                    }
                } else {
                    Intent(Intent.ACTION_VIEW, targetUri)
                }

                return runCatching {
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }.getOrElse {
                    Toast.makeText(context, context.getString(R.string.link_error_open_failed), Toast.LENGTH_SHORT).show()
                    true
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val target = url?.let(Uri::parse) ?: return false
                return handleExternalUri(target)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val target = request?.url ?: return false
                return handleExternalUri(target)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                refreshNavigationState()
            }
        }
        webView.loadUrl(initialUrl)
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
    }

    BackHandler(enabled = canGoBack) {
        webView.goBack()
        refreshNavigationState()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.in_app_browser_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_close)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                if (canGoForward) {
                                    webView.goForward()
                                    refreshNavigationState()
                                }
                            },
                            enabled = canGoForward
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = stringResource(R.string.in_app_browser_forward)
                            )
                        }
                        IconButton(
                            onClick = { webView.reload() }
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.in_app_browser_reload)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            AndroidView(
                factory = { webView },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }
}
