package com.example.valuefinder.ui

import android.Manifest
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.print.PrintManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.valuefinder.MoneyUtils
import com.example.valuefinder.ItemPhoto
import com.example.valuefinder.R
import com.example.valuefinder.TagUtils
import com.example.valuefinder.BillsPeriod
import com.example.valuefinder.resolveBillsEnteredPeriod
import com.example.valuefinder.ValuedItem
import com.example.valuefinder.ValueSource
import com.example.valuefinder.isBillsCollectionName
import com.example.valuefinder.ui.details.AddTagDialog
import com.example.valuefinder.ui.details.ChangeNotesPinDialog
import com.example.valuefinder.ui.details.DetectedLabelsSection
import com.example.valuefinder.ui.details.EditActionsRow
import com.example.valuefinder.ui.details.ExitWithoutSavingDialog
import com.example.valuefinder.ui.details.ManageCollectionsDialogHost
import com.example.valuefinder.ui.details.PersonalNotesSection
import com.example.valuefinder.ui.details.ResetNotesPinDialog
import com.example.valuefinder.ui.details.SavedComparablesSection
import com.example.valuefinder.ui.details.SetNotesPinDialog
import com.example.valuefinder.ui.details.ShareActionsSection
import com.example.valuefinder.ui.details.UnlockNotesPinDialog
import com.example.valuefinder.util.SecurePreferencesManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private const val AUDIO_NOTE_PREF_KEY = "audio_note_high_quality"

private fun Context.billsValueSuffix(period: BillsPeriod): String = when (period) {
    BillsPeriod.WEEKLY -> getString(R.string.bills_value_suffix_week)
    BillsPeriod.MONTHLY -> getString(R.string.bills_value_suffix_month)
    BillsPeriod.YEARLY -> getString(R.string.bills_value_suffix_year)
}

private fun Context.formatAudWithBillsSuffix(item: ValuedItem, amount: Double): String {
    val base = MoneyUtils.formatAud(amount)
    val period = resolveBillsEnteredPeriod(item.collectionName, item.billsEnteredPeriod) ?: return base
    return base + billsValueSuffix(period)
}

private data class DetailsEditState(
    val itemName: String,
    val collectionName: String,
    val description: String,
    val tags: String,
    val estimatedValue: String,
    val valueSource: String,
    val sourceUrl: String,
    val searchResults: String,
    val shortAiDescription: String,
    val fullWebDescription: String,
    val detectedLabels: String,
    val includeInTotals: Boolean,
    val willInstructions: String,
    val notes: String,
    val excludeFromPdfReport: Boolean,
    val billsEnteredPeriod: BillsPeriod
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    item: ValuedItem,
    itemPhotos: List<ItemPhoto>,
    existingCollections: List<String>,
    existingTags: List<String>,
    onAddCollectionRequested: (String, (Result<Boolean>) -> Unit) -> Unit,
    onRenameCollectionRequested: (String, String, (Result<Int>) -> Unit) -> Unit,
    onDeleteCollectionRequested: (String, (Result<Int>) -> Unit) -> Unit,
    onCountItemsInCollectionRequested: (String, (Result<Int>) -> Unit) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onExportRecordRequested: () -> Unit,
    onUpdateItem: (ValuedItem) -> Unit,
    isFetchingDescription: Boolean,
    onFetchFullDescription: (ValuedItem) -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    appTier: com.example.valuefinder.AppTier = com.example.valuefinder.AppTier.PERSONAL,
    onAppTierSelected: (com.example.valuefinder.AppTier) -> Unit = {},
    onOpenSourceLink: (String) -> Unit,
    onSetCoverPhoto: (Int, Int) -> Unit,
    onRequestAddPhoto: (Int) -> Unit,
    onDeletePhotoFromItem: (Int, Int) -> Unit,
    onCopyToOtherTier: (Int, (Result<Unit>) -> Unit) -> Unit = { _, _ -> },
) {
    val pagerScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val securePrefs = remember { SecurePreferencesManager(context) }
    var showExitWithoutSavingDialog by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var showItemNameError by remember { mutableStateOf(false) }
    var failedPhotoPaths by remember(item.id) { mutableStateOf(setOf<String>()) }
    var showCollectionDropdown by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var editState by remember {
        mutableStateOf(
            DetailsEditState(
                itemName = item.itemName,
                collectionName = item.collectionName,
                description = item.itemDescription,
                tags = item.tags,
                estimatedValue = item.estimatedValue?.toString().orEmpty(),
                valueSource = item.valueSource,
                sourceUrl = item.sourceUrl,
                searchResults = item.searchResults,
                shortAiDescription = item.shortAiDescription,
                fullWebDescription = item.fullWebDescription,
                detectedLabels = item.detectedLabels,
                includeInTotals = item.includeInTotals,
                willInstructions = item.willInstructions,
                notes = item.notes,
                excludeFromPdfReport = item.excludeFromPdfReport,
                billsEnteredPeriod = resolveBillsEnteredPeriod(item.collectionName, item.billsEnteredPeriod) ?: BillsPeriod.MONTHLY
            )
        )
    }
    var showManageCollectionsDialog by remember { mutableStateOf(false) }
    var showTagDropdown by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagText by remember { mutableStateOf("") }
    var detailsLookupProvider by remember { mutableStateOf(LookupProvider.GOOGLE) }
    var hasRequestedAutoFullDescription by remember(item.id) { mutableStateOf(false) }
    var lookupRequestedInEdit by remember(item.id) { mutableStateOf(false) }
    var notesUnlocked by remember(item.id) { mutableStateOf(false) }
    var showSetNotesPinDialog by remember { mutableStateOf(false) }
    var showUnlockNotesDialog by remember { mutableStateOf(false) }
    var showChangeNotesPinDialog by remember { mutableStateOf(false) }
    var showResetNotesPinDialog by remember { mutableStateOf(false) }
    var estimatedValueEditedManually by remember(item.id) { mutableStateOf(false) }
    var notesPinInput by remember { mutableStateOf("") }
    var notesPinConfirmInput by remember { mutableStateOf("") }
    var notesCurrentPinInput by remember { mutableStateOf("") }
    var notesNewPinInput by remember { mutableStateOf("") }
    var notesNewPinConfirmInput by remember { mutableStateOf("") }
    var notesPinError by remember { mutableStateOf("") }
    var showDeletePhotoDialog by remember { mutableStateOf(false) }
    var pendingDeletePhotoId by remember { mutableStateOf<Int?>(null) }
    var autoUpdateNameFromAi by remember(item.id) { mutableStateOf(false) }
    var itemNameManuallyEdited by remember(item.id) { mutableStateOf(false) }
    val isExistingBillsRecord = remember(item.id, item.collectionName) { isBillsCollectionName(item.collectionName) }
    fun loadEffectiveCollections(): List<String> {
        return existingCollections
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
    }

    var effectiveCollections by remember(existingCollections) {
        mutableStateOf(loadEffectiveCollections())
    }

    LaunchedEffect(showCollectionDropdown, existingCollections) {
        if (showCollectionDropdown) {
            effectiveCollections = loadEffectiveCollections()
        }
    }

    LaunchedEffect(effectiveCollections) {
        Log.i(
            "ValuePicsDetails",
            "DETAILS category options count=${effectiveCollections.size} options=${effectiveCollections.joinToString()}"
        )
    }
    val appPrefs = remember { context.applicationContext.getSharedPreferences("valuepics_settings", Context.MODE_PRIVATE) }
    var useHighQualityAudio by remember {
        mutableStateOf(appPrefs.getBoolean(AUDIO_NOTE_PREF_KEY, false))
    }
    var hasMicrophonePermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var pendingAudioRecordingPath by remember { mutableStateOf<String?>(null) }
    var isRecordingAudio by remember { mutableStateOf(false) }
    var isPlayingAudio by remember { mutableStateOf(false) }

    fun audioNotesDir(): File = File(context.filesDir, "audio_notes").apply { mkdirs() }

    fun audioFilePath(itemId: Int, highQuality: Boolean): File {
        val ext = if (highQuality) "m4a" else "3gp"
        return File(audioNotesDir(), "item_${itemId}.$ext")
    }

    fun currentAudioFile(): File = audioFilePath(item.id, useHighQualityAudio)

    fun alternateAudioFile(): File = audioFilePath(item.id, !useHighQualityAudio)

    fun resolveAudioNoteFile(): File? {
        val current = currentAudioFile()
        if (current.exists()) return current
        val alternate = alternateAudioFile()
        return alternate.takeIf { it.exists() }
    }

    fun releaseAudioPlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
        isPlayingAudio = false
    }

    fun stopAudioRecording(showSavedToast: Boolean) {
        val recorder = mediaRecorder ?: return
        val stopSucceeded = runCatching {
            recorder.setOnInfoListener(null)
            recorder.stop()
        }.isSuccess
        recorder.release()
        mediaRecorder = null
        isRecordingAudio = false

        val pendingPath = pendingAudioRecordingPath
        pendingAudioRecordingPath = null
        val pendingFile = pendingPath?.let(::File)
        if (stopSucceeded && pendingFile != null && pendingFile.exists() && pendingFile.length() > 0L) {
            val target = currentAudioFile()
            runCatching {
                target.delete()
                alternateAudioFile().delete()
                pendingFile.copyTo(target, overwrite = true)
                pendingFile.delete()
            }
            if (showSavedToast) {
                Toast.makeText(context, context.getString(R.string.details_audio_note_saved), Toast.LENGTH_SHORT).show()
            }
        } else {
            runCatching { pendingFile?.delete() }
        }
    }

    fun startAudioRecording() {
        releaseAudioPlayer()
        val target = File(
            context.cacheDir,
            "audio_note_tmp_${item.id}_${System.currentTimeMillis()}.${if (useHighQualityAudio) "m4a" else "3gp"}"
        )
        runCatching { pendingAudioRecordingPath?.let { File(it).delete() } }
        pendingAudioRecordingPath = target.absolutePath
        val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        runCatching {
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                if (useHighQualityAudio) {
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(64_000)
                    setAudioSamplingRate(22_050)
                } else {
                    setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                }
                setOutputFile(target.absolutePath)
                setMaxDuration(30_000)
                setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        stopAudioRecording(showSavedToast = true)
                    }
                }
                prepare()
                start()
            }
            mediaRecorder = recorder
            isRecordingAudio = true
        }.onFailure {
            pendingAudioRecordingPath?.let { path -> runCatching { File(path).delete() } }
            pendingAudioRecordingPath = null
            recorder.release()
            Toast.makeText(context, context.getString(R.string.details_audio_note_record_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun playAudioNote() {
        stopAudioRecording(showSavedToast = false)
        val file = resolveAudioNoteFile() ?: return
        releaseAudioPlayer()
        val player = MediaPlayer()
        runCatching {
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                releaseAudioPlayer()
            }
            player.prepare()
            player.start()
            mediaPlayer = player
            isPlayingAudio = true
        }.onFailure {
            player.release()
            Toast.makeText(context, context.getString(R.string.details_audio_note_play_failed), Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteAudioNote() {
        stopAudioRecording(showSavedToast = false)
        releaseAudioPlayer()
        pendingAudioRecordingPath?.let { runCatching { File(it).delete() } }
        pendingAudioRecordingPath = null
        runCatching {
            currentAudioFile().delete()
            alternateAudioFile().delete()
        }
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicrophonePermission = granted
        if (granted) {
            startAudioRecording()
        } else {
            Toast.makeText(context, context.getString(R.string.details_audio_note_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    val hasAudioNote = resolveAudioNoteFile() != null

    DisposableEffect(item.id, useHighQualityAudio) {
        onDispose {
            runCatching { stopAudioRecording(showSavedToast = false) }
            pendingAudioRecordingPath?.let { runCatching { File(it).delete() } }
            pendingAudioRecordingPath = null
            releaseAudioPlayer()
        }
    }
    fun isUserSourced(valueSource: String): Boolean {
        return when (ValueSource.fromKey(valueSource)) {
            is ValueSource.User, is ValueSource.Manual -> true
            else -> false
        }
    }

    fun hasWebSignals(valueSource: String, sourceUrl: String, searchResults: String, notes: String): Boolean {
        val parsed = ValueSource.fromKey(valueSource)
        if (parsed is ValueSource.User || parsed is ValueSource.Manual) return false
        val typedWebSource = when (parsed) {
            is ValueSource.EbayLive, is ValueSource.EbayAndDDG, is ValueSource.DuckDuckGo -> true
            else -> false
        }
        return sourceUrl.isNotBlank() ||
            searchResults.isNotBlank() ||
            notes.isNotBlank() ||
            typedWebSource
    }

    val hasWebData = remember(item.id, item.valueSource, item.sourceUrl, item.searchResults, item.fullWebDescription) {
        hasWebSignals(
            valueSource = item.valueSource,
            sourceUrl = item.sourceUrl,
            searchResults = item.searchResults,
            notes = item.fullWebDescription
        )
    }
    val selectedEditedTags = remember(editState.tags) {
        TagUtils.parseTags(editState.tags).take(TagUtils.MAX_TAGS)
    }

    data class DetailPhotoEntry(
        val id: Int?,
        val path: String,
        val isCover: Boolean
    )

    val detailPhotos = remember(item.id, item.photoPath, itemPhotos) {
        val mapped = itemPhotos
            .map { photo -> DetailPhotoEntry(id = photo.id, path = photo.photoPath, isCover = photo.isCover) }
        if (mapped.isNotEmpty()) {
            mapped
        } else if (item.photoPath.isNotBlank()) {
            listOf(DetailPhotoEntry(id = null, path = item.photoPath, isCover = true))
        } else {
            emptyList()
        }
    }
    LaunchedEffect(detailPhotos.map { it.path }) {
        failedPhotoPaths = emptySet()
    }
    DisposableEffect(lifecycleOwner, detailPhotos) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                failedPhotoPaths = emptySet()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val initialPhotoIndex = remember(detailPhotos) {
        detailPhotos.indexOfFirst { it.isCover }.takeIf { it >= 0 } ?: 0
    }
    val photoPagerState = rememberPagerState(
        initialPage = initialPhotoIndex,
        pageCount = { detailPhotos.size }
    )
    val fullscreenPhotoPagerState = rememberPagerState(
        initialPage = initialPhotoIndex,
        pageCount = { detailPhotos.size }
    )
    var showFullscreenPhotoViewer by remember(item.id) { mutableStateOf(false) }
    var fullscreenPhotoScale by remember(item.id) { mutableStateOf(1f) }
    var fullscreenPhotoOffset by remember(item.id) { mutableStateOf(Offset.Zero) }

    // Keep fullscreen and inline pagers in sync without sharing the same state object.
    LaunchedEffect(showFullscreenPhotoViewer, detailPhotos.size) {
        if (!showFullscreenPhotoViewer || detailPhotos.isEmpty()) return@LaunchedEffect
        val targetPage = photoPagerState.currentPage.coerceIn(0, detailPhotos.lastIndex)
        fullscreenPhotoPagerState.scrollToPage(targetPage)
        fullscreenPhotoScale = 1f
        fullscreenPhotoOffset = Offset.Zero
    }

    LaunchedEffect(fullscreenPhotoPagerState.currentPage, showFullscreenPhotoViewer) {
        if (!showFullscreenPhotoViewer) return@LaunchedEffect
        fullscreenPhotoScale = 1f
        fullscreenPhotoOffset = Offset.Zero
    }

    fun resetEdits() {
        editState = editState.copy(
            itemName = item.itemName,
            collectionName = item.collectionName,
            description = item.itemDescription,
            tags = item.tags,
            estimatedValue = item.estimatedValue?.toString().orEmpty(),
            valueSource = item.valueSource,
            sourceUrl = item.sourceUrl,
            searchResults = item.searchResults,
            shortAiDescription = item.shortAiDescription,
            fullWebDescription = item.fullWebDescription,
            detectedLabels = item.detectedLabels,
            includeInTotals = item.includeInTotals,
            willInstructions = item.willInstructions,
            notes = item.notes,
            excludeFromPdfReport = item.excludeFromPdfReport,
            billsEnteredPeriod = resolveBillsEnteredPeriod(item.collectionName, item.billsEnteredPeriod) ?: BillsPeriod.MONTHLY
        )
        estimatedValueEditedManually = false
        itemNameManuallyEdited = false
    }

    // Sync edit fields when item changes externally (e.g. after a fetch)
    LaunchedEffect(item.id, item.dateValued) {
        if (!isEditing) resetEdits()
    }

    fun clearLookedUpData() {
        editState = editState.copy(
            estimatedValue = "",
            valueSource = "",
            searchResults = "",
            shortAiDescription = "",
            fullWebDescription = ""
        )
        estimatedValueEditedManually = false
    }

    fun requestNotesUnlock() {
        notesPinError = ""
        notesPinInput = ""
        notesPinConfirmInput = ""
        if (securePrefs.hasNotesPin()) {
            showUnlockNotesDialog = true
        } else {
            showSetNotesPinDialog = true
        }
    }

    fun normalizeTagsInput(raw: String): String {
        return TagUtils.normalizeTagsInput(raw)
    }

    fun addTagToEditedItem(rawTag: String) {
        val normalizedTag = rawTag.trim()
        if (normalizedTag.isBlank()) return
        val merged = (TagUtils.parseTags(editState.tags) + normalizedTag)
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .take(TagUtils.MAX_TAGS)
        editState = editState.copy(tags = merged.joinToString(", "))
    }

    fun removeTagFromEditedItem(tag: String) {
        val remaining = TagUtils.parseTags(editState.tags)
            .filterNot { it.equals(tag, ignoreCase = true) }
            .take(TagUtils.MAX_TAGS)
        editState = editState.copy(tags = remaining.joinToString(", "))
    }

    fun firstAiLineAsItemName(raw: String): String {
        return raw
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    fun commitEdits(): Boolean {
        if (editState.itemName.trim().isBlank()) {
            showItemNameError = true
            return false
        }
        val parsedValue = editState.estimatedValue.trim().toDoubleOrNull()
        val notesWereEdited = editState.fullWebDescription.trim() != item.fullWebDescription.trim()
        val updatedSource = if (notesWereEdited) {
            "User"
        } else {
            editState.valueSource.trim()
        }

        val updatedItem = item.copy(
            itemName = editState.itemName.trim(),
            collectionName = editState.collectionName.trim(),
            itemDescription = editState.description.trim(),
            tags = TagUtils.normalizeTags(editState.tags),
            estimatedValue = parsedValue,
            valueSource = updatedSource,
            sourceUrl = editState.sourceUrl.trim(),
            searchResults = editState.searchResults.trim(),
            confidence = if (parsedValue != null && estimatedValueEditedManually) 0.95f else item.confidence,
            shortAiDescription = editState.shortAiDescription.trim(),
            fullWebDescription = editState.fullWebDescription.trim(),
            detectedLabels = editState.detectedLabels.trim(),
            includeInTotals = editState.includeInTotals,
            willInstructions = editState.willInstructions.trim(),
            notes = editState.notes.trim(),
            excludeFromPdfReport = editState.excludeFromPdfReport,
            billsEnteredPeriod = if (isBillsCollectionName(editState.collectionName)) editState.billsEnteredPeriod.storageValue else "",
            dateValued = System.currentTimeMillis()
        )
        onUpdateItem(updatedItem)
        showItemNameError = false
        isEditing = false
        
        // Auto-fetch full web description if one-line exists but full is empty
        if (editState.shortAiDescription.trim().isNotBlank() && editState.fullWebDescription.trim().isBlank()) {
            hasRequestedAutoFullDescription = false
        }
        return true
    }

    fun buildLookupDraft(): ValuedItem? {
        if (editState.itemName.trim().isBlank()) {
            showItemNameError = true
            return null
        }
        return item.copy(
            itemName = editState.itemName.trim(),
            collectionName = editState.collectionName.trim(),
            itemDescription = editState.description.trim(),
            tags = TagUtils.normalizeTags(editState.tags),
            estimatedValue = editState.estimatedValue.trim().toDoubleOrNull(),
            valueSource = editState.valueSource.trim(),
            sourceUrl = editState.sourceUrl.trim(),
            searchResults = editState.searchResults.trim(),
            shortAiDescription = editState.shortAiDescription.trim(),
            fullWebDescription = editState.fullWebDescription.trim(),
            detectedLabels = editState.detectedLabels.trim(),
            includeInTotals = editState.includeInTotals,
            willInstructions = editState.willInstructions.trim(),
            excludeFromPdfReport = editState.excludeFromPdfReport,
            billsEnteredPeriod = if (isBillsCollectionName(editState.collectionName)) editState.billsEnteredPeriod.storageValue else "",
            dateValued = System.currentTimeMillis()
        )
    }

    LaunchedEffect(item.id, item.shortAiDescription, item.fullWebDescription, isEditing, isFetchingDescription) {
        val shouldAutoFetch =
            !isEditing &&
                !isFetchingDescription &&
                !hasRequestedAutoFullDescription &&
                item.shortAiDescription.isNotBlank() &&
                item.fullWebDescription.isBlank()
        if (shouldAutoFetch) {
            hasRequestedAutoFullDescription = true
            onFetchFullDescription(item)
        }
    }

    // When lookup is triggered in edit mode, refresh web-derived editable fields after fetch completes.
    LaunchedEffect(item.id, item.dateValued, isFetchingDescription, isEditing, lookupRequestedInEdit) {
        if (lookupRequestedInEdit && isEditing && !isFetchingDescription) {
            val refreshedShortAiDescription = item.shortAiDescription
            val refreshedItemName = when {
                itemNameManuallyEdited -> editState.itemName
                autoUpdateNameFromAi -> firstAiLineAsItemName(refreshedShortAiDescription).ifBlank { editState.itemName }
                else -> editState.itemName
            }
            editState = editState.copy(
                itemName = refreshedItemName,
                estimatedValue = item.estimatedValue?.toString().orEmpty(),
                valueSource = item.valueSource,
                sourceUrl = item.sourceUrl,
                searchResults = item.searchResults,
                shortAiDescription = refreshedShortAiDescription,
                fullWebDescription = item.fullWebDescription,
                detectedLabels = editState.detectedLabels
            )
            estimatedValueEditedManually = false
            lookupRequestedInEdit = false
        }
    }

    fun shareRecord() {
        val shareText = buildString {
            appendLine(context.getString(R.string.share_label_item, item.itemName))
            if (item.collectionName.isNotBlank()) appendLine(context.getString(R.string.share_label_collection, item.collectionName))
            if (item.tags.isNotBlank()) appendLine(context.getString(R.string.share_label_tags, item.tags))
            if (item.itemDescription.isNotBlank()) appendLine(context.getString(R.string.share_label_description, item.itemDescription))
            item.estimatedValue?.let {
                appendLine(context.getString(R.string.share_label_estimated_value, context.formatAudWithBillsSuffix(item, it)))
            }
            if (item.valueSource.isNotBlank()) appendLine(context.getString(R.string.share_label_value_source, item.valueSource))
            if (item.sourceUrl.isNotBlank()) appendLine(context.getString(R.string.share_label_source_url, item.sourceUrl))
            if (item.notes.isNotBlank()) appendLine(context.getString(R.string.share_label_notes, item.notes))
        }.trim()

        val photoFile = File(item.photoPath)
        val sharePhotoUri = if (photoFile.exists()) {
            runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
            }.getOrNull()
        } else {
            null
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (sharePhotoUri != null) "image/*" else "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, item.itemName)
            putExtra(Intent.EXTRA_TEXT, shareText)
            if (sharePhotoUri != null) {
                putExtra(Intent.EXTRA_STREAM, sharePhotoUri)
                clipData = ClipData.newUri(context.contentResolver, "item_photo", sharePhotoUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.details_share_chooser_title))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(chooser) }
            .onFailure {
                Toast.makeText(context, context.getString(R.string.details_share_unavailable), Toast.LENGTH_SHORT).show()
            }
    }


    fun readClipboardText(): String {
        return clipboardManager.getText()?.text?.trim().orEmpty()
    }

    fun openManualLookupPage() {
        val currentPhotoPath = detailPhotos
            .getOrNull(photoPagerState.currentPage.coerceIn(0, (detailPhotos.size - 1).coerceAtLeast(0)))
            ?.path
            ?: item.photoPath
        val baseName = if (isEditing) editState.itemName else item.itemName
        val baseDescription = if (isEditing) editState.description else item.itemDescription
        openManualLookupPage(
            context = context,
            photoPath = currentPhotoPath,
            provider = detailsLookupProvider,
            itemName = baseName,
            itemDescription = baseDescription
        )
    }

    fun sharePhoto() {
        val file = File(item.photoPath)
        if (!file.exists()) {
            Toast.makeText(context, context.getString(R.string.details_share_photo_missing), Toast.LENGTH_SHORT).show()
            return
        }
        val photoUri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: run {
            Toast.makeText(context, context.getString(R.string.details_share_photo_missing), Toast.LENGTH_SHORT).show()
            return
        }

        val text = buildString {
            append(context.getString(R.string.share_label_item, item.itemName))
            item.estimatedValue?.let {
                append("\n${context.getString(R.string.share_label_estimated_value, context.formatAudWithBillsSuffix(item, it))}")
            }
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/*"
            putExtra(Intent.EXTRA_STREAM, photoUri)
            putExtra(Intent.EXTRA_SUBJECT, item.itemName)
            putExtra(Intent.EXTRA_TEXT, text)
            clipData = ClipData.newUri(context.contentResolver, "item_photo", photoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.details_share_chooser_title))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(chooser) }
            .onFailure {
                Toast.makeText(context, context.getString(R.string.details_share_unavailable), Toast.LENGTH_SHORT).show()
            }
    }

    fun buildItemPdfUri(): Uri? = buildItemPdfUri(item, context)

    fun printSelectedItem() {
        val uri = buildItemPdfUri() ?: run {
            Toast.makeText(context, context.getString(R.string.pdf_error_print_unable), Toast.LENGTH_SHORT).show()
            return
        }
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (printManager == null) {
            Toast.makeText(context, context.getString(R.string.pdf_error_print_service_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            val jobName = "ValuePics ${item.itemName} Details"
            val documentName = "ValuePics_${item.itemName.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_').ifBlank { "Item_${item.id}" }}_Details_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(item.createdAtMillis))}"
            printManager.print(
                jobName,
                PdfUriPrintAdapter(context, uri, documentName),
                null
            )
        }.onFailure {
            Toast.makeText(context, context.getString(R.string.pdf_error_print_start_failed), Toast.LENGTH_SHORT).show()
        }
    }

    val entryDateText = remember(item.createdAtMillis) {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.createdAtMillis))
    }
    val valuedDateText = remember(item.dateValued) {
        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(item.dateValued))
    }

    val editFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        focusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unfocusedSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        cursorColor = MaterialTheme.colorScheme.primary
    )

    fun requestBackNavigation() {
        if (isEditing) {
            showExitWithoutSavingDialog = true
        } else {
            onBack()
        }
    }

    // Keep hardware/system back behavior consistent with the app bar back button.
    BackHandler(onBack = { requestBackNavigation() })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        TopAppBar(
            title = { Text(item.itemName) },
            navigationIcon = {
                IconButton(onClick = { requestBackNavigation() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                }
            },
            actions = {
                if (isEditing) {
                    TextButton(
                        enabled = editState.itemName.trim().isNotBlank(),
                        onClick = { commitEdits() }
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                    TextButton(
                        onClick = {
                            resetEdits()
                            showItemNameError = false
                            isEditing = false
                        }
                    ) {
                        Text(stringResource(R.string.common_cancel))
                    }
                } else {
                    IconButton(onClick = { isEditing = true }) {
                        Icon(Icons.Filled.Edit, stringResource(R.string.common_edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.common_delete))
                    }
                }
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.list_cd_more_actions))
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_print)) },
                            onClick = {
                                showOverflowMenu = false
                                printSelectedItem()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details_export_record_zip)) },
                            onClick = {
                                showOverflowMenu = false
                                onExportRecordRequested()
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_theme_mode_light)) },
                            onClick = {
                                onThemeModeSelected(ThemeMode.LIGHT)
                                showOverflowMenu = false
                            },
                            trailingIcon = {
                                if (themeMode == ThemeMode.LIGHT) Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_theme_mode_dark)) },
                            onClick = {
                                onThemeModeSelected(ThemeMode.DARK)
                                showOverflowMenu = false
                            },
                            trailingIcon = {
                                if (themeMode == ThemeMode.DARK) Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_theme_mode_system)) },
                            onClick = {
                                onThemeModeSelected(ThemeMode.SYSTEM)
                                showOverflowMenu = false
                            },
                            trailingIcon = {
                                if (themeMode == ThemeMode.SYSTEM) Icon(Icons.Filled.Check, contentDescription = null)
                            }
                        )
                    }
                }
            }
        )

        Column(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
            // Photo
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clickable(enabled = detailPhotos.isNotEmpty()) { showFullscreenPhotoViewer = true }
                ,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (detailPhotos.isNotEmpty()) {
                    HorizontalPager(
                        state = photoPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val photo = detailPhotos[page]
                        if (File(photo.path).exists() && !failedPhotoPaths.contains(photo.path)) {
                            AsyncImage(
                                model = File(photo.path),
                                contentDescription = stringResource(R.string.details_item_photo),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                                onError = { failedPhotoPaths = failedPhotoPaths + photo.path }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.Image, contentDescription = stringResource(R.string.list_no_photo))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.common_image_unavailable),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Image, contentDescription = stringResource(R.string.list_no_photo))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.common_image_unavailable),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (detailPhotos.isNotEmpty()) {
                val currentIndex = photoPagerState.currentPage.coerceIn(0, detailPhotos.lastIndex)
                val currentPhoto = detailPhotos[currentIndex]
                Spacer(modifier = Modifier.height(6.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(
                            R.string.details_photo_index,
                            currentIndex + 1,
                            detailPhotos.size
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(onClick = { showFullscreenPhotoViewer = true }) {
                            Text(stringResource(R.string.details_view_full_screen))
                        }
                        if (detailPhotos.size > 1 && currentPhoto.id != null && !currentPhoto.isCover) {
                            TextButton(onClick = { onSetCoverPhoto(item.id, currentPhoto.id) }) {
                                Text(stringResource(R.string.details_set_as_cover))
                            }
                        } else if (detailPhotos.size > 1 && currentPhoto.isCover) {
                            Text(
                                text = stringResource(R.string.details_cover_photo_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (isEditing && detailPhotos.size > 1 && currentPhoto.id != null) {
                            IconButton(
                                onClick = {
                                    pendingDeletePhotoId = currentPhoto.id
                                    showDeletePhotoDialog = true
                                }
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.details_delete_current_photo),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onRequestAddPhoto(item.id) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.details_add_photo))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        val copyLabel = if (appTier == com.example.valuefinder.AppTier.INSURANCE)
                            stringResource(R.string.details_copy_to_personal)
                        else
                            stringResource(R.string.details_copy_to_insurance)
                        val addShortLabel = if (appTier == com.example.valuefinder.AppTier.INSURANCE)
                            stringResource(R.string.details_add_personal_short)
                        else
                            stringResource(R.string.details_add_insurance_short)
                        TextButton(
                            onClick = {
                                onCopyToOtherTier(item.id) { result ->
                                    val msg = result.fold(
                                        onSuccess = {
                                            if (appTier == com.example.valuefinder.AppTier.INSURANCE)
                                                context.getString(R.string.details_copy_success_personal)
                                            else
                                                context.getString(R.string.details_copy_success_insurance)
                                        },
                                        onFailure = { ex ->
                                            if (ex is com.example.valuefinder.ValuePicsRepository.ItemAlreadyInTierException)
                                                if (appTier == com.example.valuefinder.AppTier.INSURANCE)
                                                    context.getString(R.string.details_copy_already_exists_personal)
                                                else
                                                    context.getString(R.string.details_copy_already_exists_insurance)
                                            else context.getString(R.string.details_copy_failed)
                                        }
                                    )
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = copyLabel,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = addShortLabel,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                if (detailPhotos.size > 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(detailPhotos) { index, thumb ->
                            val isSelected = index == currentIndex
                            Card(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clickable {
                                        pagerScope.launch {
                                            photoPagerState.animateScrollToPage(index)
                                        }
                                    },
                                border = if (isSelected) {
                                    BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                            ) {
                                if (File(thumb.path).exists() && !failedPhotoPaths.contains(thumb.path)) {
                                    AsyncImage(
                                        model = File(thumb.path),
                                        contentDescription = stringResource(R.string.details_item_photo),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        onError = { failedPhotoPaths = failedPhotoPaths + thumb.path }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Image,
                                            contentDescription = stringResource(R.string.list_no_photo),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showFullscreenPhotoViewer && detailPhotos.isNotEmpty()) {
                Dialog(
                    onDismissRequest = {
                        val targetPage = fullscreenPhotoPagerState.currentPage.coerceIn(0, detailPhotos.lastIndex)
                        showFullscreenPhotoViewer = false
                        pagerScope.launch { photoPagerState.scrollToPage(targetPage) }
                    },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.scrim
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = fullscreenPhotoPagerState,
                                modifier = Modifier.fillMaxSize(),
                                userScrollEnabled = fullscreenPhotoScale <= 1.01f
                            ) { page ->
                                val photo = detailPhotos[page]
                                if (File(photo.path).exists() && !failedPhotoPaths.contains(photo.path)) {
                                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                                        val nextScale = (fullscreenPhotoScale * zoomChange).coerceIn(1f, 5f)
                                        fullscreenPhotoScale = nextScale
                                        fullscreenPhotoOffset = if (nextScale <= 1.01f) {
                                            Offset.Zero
                                        } else {
                                            fullscreenPhotoOffset + panChange
                                        }
                                    }
                                    AsyncImage(
                                        model = File(photo.path),
                                        contentDescription = stringResource(R.string.details_item_photo),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer {
                                                scaleX = fullscreenPhotoScale
                                                scaleY = fullscreenPhotoScale
                                                translationX = fullscreenPhotoOffset.x
                                                translationY = fullscreenPhotoOffset.y
                                            }
                                            .transformable(state = transformState),
                                        contentScale = ContentScale.Fit,
                                        onError = { failedPhotoPaths = failedPhotoPaths + photo.path }
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.common_image_unavailable),
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.details_photo_index,
                                        fullscreenPhotoPagerState.currentPage + 1,
                                        detailPhotos.size
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                IconButton(
                                    onClick = {
                                        val targetPage = fullscreenPhotoPagerState.currentPage.coerceIn(0, detailPhotos.lastIndex)
                                        showFullscreenPhotoViewer = false
                                        pagerScope.launch { photoPagerState.scrollToPage(targetPage) }
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.common_close),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Spacer(modifier = Modifier.height(8.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = editState.itemName,
                    onValueChange = {
                        itemNameManuallyEdited = true
                        editState = editState.copy(itemName = enforceLeadingCapitalization(it))
                        if (it.trim().isNotBlank()) {
                            showItemNameError = false
                        }
                    },
                    label = { Text(stringResource(R.string.details_item_name_label)) },
                    isError = showItemNameError && editState.itemName.trim().isBlank(),
                    supportingText = {
                        if (showItemNameError && editState.itemName.trim().isBlank()) {
                            Text(stringResource(R.string.validation_item_name_required))
                        }
                    },
                    trailingIcon = {
                        if (editState.itemName.isNotEmpty()) {
                            IconButton(onClick = { editState = editState.copy(itemName = "") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.list_cd_clear_search), modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = editFieldColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = editState.description,
                        onValueChange = {
                            editState = editState.copy(description = enforceLeadingCapitalization(it))
                        },
                        label = { Text(stringResource(R.string.details_description_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = editFieldColors
                    )
                    if (editState.description.isNotEmpty()) {
                        IconButton(
                            onClick = { editState = editState.copy(description = "") },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.list_cd_clear_search), modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editState.tags,
                    onValueChange = {
                        editState = editState.copy(tags = normalizeTagsInput(enforceLeadingCapitalization(it)))
                    },
                    label = { Text(stringResource(R.string.tags_label)) },
                    supportingText = { Text(stringResource(R.string.tags_help)) },
                    trailingIcon = {
                        if (editState.tags.isNotEmpty()) {
                            IconButton(onClick = { editState = editState.copy(tags = "") }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.list_cd_clear_search), modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    colors = editFieldColors
                )
                if (selectedEditedTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        selectedEditedTags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { removeTagFromEditedItem(tag) },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.tags_remove_tag, tag),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { showTagDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Tags: ${stringResource(R.string.details_add_tag_from_existing)}", modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showTagDropdown,
                        onDismissRequest = { showTagDropdown = false },
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.details_add_new_tag)) },
                            onClick = {
                                showTagDropdown = false
                                showAddTagDialog = true
                            },
                            enabled = selectedEditedTags.size < TagUtils.MAX_TAGS
                        )
                        HorizontalDivider()
                        existingTags.forEach { tag ->
                            val alreadySelected = selectedEditedTags.any { it.equals(tag, ignoreCase = true) }
                            val limitReached = selectedEditedTags.size >= TagUtils.MAX_TAGS
                            DropdownMenuItem(
                                text = { Text(tag) },
                                onClick = {
                                    addTagToEditedItem(tag)
                                    showTagDropdown = false
                                },
                                enabled = !alreadySelected && !limitReached
                            )
                        }
                    }
                }
                if (selectedEditedTags.size >= TagUtils.MAX_TAGS) {
                    Text(
                        stringResource(R.string.tags_limit_reached, TagUtils.MAX_TAGS),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.valuation_category_label),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { showCollectionDropdown = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (editState.collectionName.isBlank()) {
                                stringResource(R.string.common_select_category)
                            } else {
                                stringResource(R.string.common_category_selected_value, editState.collectionName)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                }
                if (showCollectionDropdown) {
                    AlertDialog(
                        onDismissRequest = { showCollectionDropdown = false },
                        title = { Text(stringResource(R.string.common_choose_category_title)) },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                TextButton(
                                    onClick = {
                                        editState = editState.copy(collectionName = "")
                                        showCollectionDropdown = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.collections_none_label))
                                }
                                effectiveCollections.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { name ->
                                    TextButton(
                                        onClick = {
                                            editState = editState.copy(collectionName = name)
                                            showCollectionDropdown = false
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(name)
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        showCollectionDropdown = false
                                        showManageCollectionsDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.collections_add_new_category))
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showCollectionDropdown = false }) {
                                Text(stringResource(R.string.common_close))
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.collections_picker_help),
                    style = MaterialTheme.typography.labelSmall
                )
                if (appTier == com.example.valuefinder.AppTier.PERSONAL && isBillsCollectionName(editState.collectionName)) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.bills_period_label),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val canChangeEnteredPeriod = !isExistingBillsRecord
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            BillsPeriod.WEEKLY to stringResource(R.string.bills_period_weekly),
                            BillsPeriod.MONTHLY to stringResource(R.string.bills_period_monthly),
                            BillsPeriod.YEARLY to stringResource(R.string.bills_period_yearly)
                        ).forEach { (period, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = canChangeEnteredPeriod) {
                                        editState = editState.copy(billsEnteredPeriod = period)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = editState.billsEnteredPeriod == period,
                                    onClick = if (canChangeEnteredPeriod) {
                                        { editState = editState.copy(billsEnteredPeriod = period) }
                                    } else {
                                        null
                                    },
                                    enabled = canChangeEnteredPeriod
                                )
                                Text(text = label)
                            }
                        }
                    }
                    if (!canChangeEnteredPeriod) {
                        Text(
                            text = stringResource(R.string.bills_period_locked_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Details
                Text(item.itemName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text(item.itemDescription, style = MaterialTheme.typography.bodyLarge)
                if (item.estimatedValue != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        context.formatAudWithBillsSuffix(item, item.estimatedValue),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (item.collectionName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.details_collection_value, item.collectionName),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (item.tags.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.details_tags_value, item.tags),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if ((if (isEditing) editState.shortAiDescription else item.shortAiDescription).isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.details_ai_one_line), style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isEditing) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = editState.shortAiDescription,
                                    onValueChange = {
                                        editState = editState.copy(shortAiDescription = enforceLeadingCapitalization(it))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    colors = editFieldColors
                                )
                                if (editState.shortAiDescription.isNotEmpty()) {
                                    IconButton(
                                        onClick = { editState = editState.copy(shortAiDescription = "") },
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.list_cd_clear_search), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        } else {
                            Text(item.shortAiDescription, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (isEditing) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        OutlinedTextField(
                            value = editState.estimatedValue,
                            onValueChange = {
                                editState = editState.copy(estimatedValue = it)
                                estimatedValueEditedManually = true
                            },
                            label = { Text(stringResource(R.string.details_estimated_value_aud_label)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = editFieldColors
                        )
                        Text(
                            stringResource(R.string.details_confidence_value, item.confidence * 100f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = editState.valueSource,
                            onValueChange = {
                                editState = editState.copy(valueSource = enforceLeadingCapitalization(it))
                            },
                            label = { Text(stringResource(R.string.details_value_source_label)) },
                            trailingIcon = {
                                if (editState.valueSource.isNotEmpty()) {
                                    IconButton(onClick = { editState = editState.copy(valueSource = "") }) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.list_cd_clear_search), modifier = Modifier.size(18.dp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            colors = editFieldColors
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                if (isEditing) {
                                    val otherTagLabel = stringResource(R.string.major_items_collection_name)
                                    FilterChip(
                                        selected = !editState.includeInTotals,
                                        onClick = {
                                            val nowExcluding = editState.includeInTotals // toggling to excluded
                                            val currentTags = TagUtils.parseTags(editState.tags)
                                            val updatedTags = if (nowExcluding) {
                                                // was included, now excluding → add "Other" tag
                                                (currentTags + otherTagLabel)
                                                    .distinctBy { it.lowercase(java.util.Locale.getDefault()) }
                                                    .take(TagUtils.MAX_TAGS)
                                                    .joinToString(", ")
                                            } else {
                                                // was excluded, now including → remove "Other" tag
                                                currentTags
                                                    .filterNot { it.equals(otherTagLabel, ignoreCase = true) }
                                                    .joinToString(", ")
                                            }
                                            editState = editState.copy(
                                                includeInTotals = !editState.includeInTotals,
                                                tags = updatedTags
                                            )
                                        },
                                    label = {
                                        Text(stringResource(R.string.details_exclude_from_totals_toggle))
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (!editState.includeInTotals) Icons.Filled.Check else Icons.Filled.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                                FilterChip(
                                    selected = editState.excludeFromPdfReport,
                                    onClick = {
                                        editState = editState.copy(
                                            excludeFromPdfReport = !editState.excludeFromPdfReport
                                        )
                                    },
                                    label = { Text(stringResource(R.string.details_exclude_from_pdf_report)) },
                                    leadingIcon = {
                                        Icon(
                                            if (editState.excludeFromPdfReport) Icons.Filled.Check else Icons.Filled.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            } else {
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text(stringResource(R.string.details_exclude_from_totals_toggle)) },
                                    leadingIcon = {
                                        Icon(
                                            if (!item.includeInTotals) Icons.Filled.Check else Icons.Filled.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = {
                                        Text(
                                            if (item.excludeFromPdfReport) {
                                                stringResource(R.string.details_exclude_from_pdf_report)
                                            } else {
                                                stringResource(R.string.details_include_in_pdf_report)
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (item.excludeFromPdfReport) Icons.Filled.Check else Icons.Filled.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                stringResource(R.string.details_record_entered_date, entryDateText),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(R.string.details_last_valued_date, valuedDateText),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                stringResource(R.string.details_photo_source, item.photoSource),
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = detailsLookupProvider == LookupProvider.BING,
                    onClick = { detailsLookupProvider = LookupProvider.BING },
                    label = { Text(stringResource(R.string.lookup_provider_bing)) }
                )
                FilterChip(
                    selected = detailsLookupProvider == LookupProvider.GOOGLE,
                    onClick = { detailsLookupProvider = LookupProvider.GOOGLE },
                    label = { Text(stringResource(R.string.lookup_provider_google)) }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.details_lookup_provider_help),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val selectedLookupProviderName = when (detailsLookupProvider) {
                LookupProvider.BING -> stringResource(R.string.lookup_provider_name_bing)
                LookupProvider.GOOGLE -> stringResource(R.string.lookup_provider_name_google)
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { openManualLookupPage() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.details_open_selected_image_lookup, selectedLookupProviderName))
            }
            if (isEditing) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val clipboardText = readClipboardText()
                        if (clipboardText.isNotBlank()) {
                            editState = editState.copy(description = enforceLeadingCapitalization(clipboardText))
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.details_clipboard_empty),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.details_paste_description_from_clipboard))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val clipboardText = readClipboardText()
                        val firstLine = firstAiLineAsItemName(clipboardText)
                        if (firstLine.isNotBlank()) {
                            itemNameManuallyEdited = true
                            editState = editState.copy(itemName = firstLine)
                            showItemNameError = false
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.details_clipboard_empty),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ContentPaste, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.details_set_name_from_clipboard_first_line))
                }
            }

            if ((if (isEditing) editState.sourceUrl else item.sourceUrl).isNotBlank() || isEditing) {
                Spacer(modifier = Modifier.height(8.dp))
                if (isEditing) {
                    OutlinedTextField(
                        value = editState.sourceUrl,
                        onValueChange = { editState = editState.copy(sourceUrl = it) },
                        label = { Text(stringResource(R.string.details_source_url_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        colors = editFieldColors,
                        trailingIcon = {
                            if (editState.sourceUrl.isNotBlank()) {
                                IconButton(onClick = {
                                    editState = editState.copy(sourceUrl = "")
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { onOpenSourceLink(if (isEditing) editState.sourceUrl else item.sourceUrl) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = editState.sourceUrl.isNotBlank() || (!isEditing && item.sourceUrl.isNotBlank())
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = stringResource(R.string.common_open_link))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.details_open_source_website))
                }
            }

            // Full web description moved above saved comparables
            if ((if (isEditing) editState.fullWebDescription else item.fullWebDescription).isNotBlank() || isEditing) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val notesAreWebSourced = if (isEditing) {
                            hasWebSignals(
                                valueSource = editState.valueSource,
                                sourceUrl = editState.sourceUrl,
                                searchResults = editState.searchResults,
                                notes = editState.fullWebDescription
                            )
                        } else {
                            hasWebData
                        }
                        Text(
                            text = if (notesAreWebSourced) {
                                stringResource(R.string.details_notes_web_sourced)
                            } else {
                                stringResource(R.string.details_notes_user_sourced)
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isEditing) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = editState.fullWebDescription,
                                    onValueChange = {
                                        editState = editState.copy(fullWebDescription = enforceLeadingCapitalization(it))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 3,
                                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                                    colors = editFieldColors
                                )
                                if (editState.fullWebDescription.isNotEmpty()) {
                                    IconButton(
                                        onClick = { editState = editState.copy(fullWebDescription = "") },
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.list_cd_clear_search), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        } else {
                            SelectionContainer {
                                Text(
                                    text = item.fullWebDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.fillMaxWidth(),
                                    overflow = TextOverflow.Visible
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.details_will_instructions_label),
                            style = MaterialTheme.typography.titleSmall
                        )
                        TextButton(onClick = { printSelectedItem() }) {
                            Icon(
                                Icons.Filled.Print,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.details_will_print_action))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (isEditing) {
                        OutlinedTextField(
                            value = editState.willInstructions,
                            onValueChange = {
                                editState = editState.copy(willInstructions = enforceLeadingCapitalization(it))
                            },
                            label = { Text(stringResource(R.string.details_will_instructions_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                            colors = editFieldColors
                        )
                    } else {
                        Text(
                            text = item.willInstructions.ifBlank { stringResource(R.string.details_will_instructions_empty) },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                            color = if (item.willInstructions.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            PersonalNotesSection(
                isEditing = isEditing,
                itemNotes = item.notes,
                notesUnlocked = notesUnlocked,
                editedNotes = editState.notes,
                hasNotesPin = securePrefs.hasNotesPin(),
                editFieldColors = editFieldColors,
                onRequestNotesUnlock = { requestNotesUnlock() },
                onEditedNotesChange = {
                    editState = editState.copy(notes = enforceLeadingCapitalization(it))
                },
                onOpenChangePin = {
                    notesPinError = ""
                    notesCurrentPinInput = ""
                    notesNewPinInput = ""
                    notesNewPinConfirmInput = ""
                    showChangeNotesPinDialog = true
                },
                onOpenResetPin = {
                    notesPinError = ""
                    notesCurrentPinInput = ""
                    showResetNotesPinDialog = true
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.details_audio_note_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.details_audio_note_limit_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.details_audio_note_hq_toggle),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = useHighQualityAudio,
                            onCheckedChange = {
                                useHighQualityAudio = it
                                appPrefs.edit().putBoolean(AUDIO_NOTE_PREF_KEY, it).apply()
                            },
                            enabled = !isRecordingAudio
                        )
                    }
                    Text(
                        text = if (useHighQualityAudio) {
                            stringResource(R.string.details_audio_note_quality_hq)
                        } else {
                            stringResource(R.string.details_audio_note_quality_compact)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (isRecordingAudio) {
                                    stopAudioRecording(showSavedToast = true)
                                } else if (hasMicrophonePermission) {
                                    startAudioRecording()
                                } else {
                                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (isRecordingAudio) {
                                    stringResource(R.string.details_audio_note_stop)
                                } else {
                                    stringResource(R.string.details_audio_note_record)
                                }
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (isPlayingAudio) {
                                    releaseAudioPlayer()
                                } else {
                                    playAudioNote()
                                }
                            },
                            enabled = hasAudioNote,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                if (isPlayingAudio) {
                                    stringResource(R.string.details_audio_note_stop_playback)
                                } else {
                                    stringResource(R.string.details_audio_note_play)
                                }
                            )
                        }
                    }
                    TextButton(
                        onClick = { deleteAudioNote() },
                        enabled = hasAudioNote
                    ) {
                        Text(stringResource(R.string.details_audio_note_delete))
                    }
                }
            }

            SavedComparablesSection(
                isEditing = isEditing,
                itemSearchResults = item.searchResults,
                editedSearchResults = editState.searchResults,
                editFieldColors = editFieldColors,
                onEditedSearchResultsChange = {
                    editState = editState.copy(searchResults = enforceLeadingCapitalization(it))
                },
                onOpenSourceLink = onOpenSourceLink
            )

            Spacer(modifier = Modifier.height(16.dp))
            if (isEditing) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.details_auto_update_name_from_ai),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = autoUpdateNameFromAi,
                        onCheckedChange = { autoUpdateNameFromAi = it }
                    )
                }
                Text(
                    text = stringResource(R.string.details_auto_update_name_from_ai_supporting),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        val aiName = firstAiLineAsItemName(editState.shortAiDescription)
                        if (aiName.isNotBlank()) {
                            editState = editState.copy(itemName = aiName)
                            itemNameManuallyEdited = false
                            showItemNameError = false
                        }
                    },
                    enabled = editState.shortAiDescription.isNotBlank()
                ) {
                    Text(stringResource(R.string.details_use_ai_name_now))
                }
                Spacer(modifier = Modifier.height(8.dp))
                EditActionsRow(
                    canSave = editState.itemName.trim().isNotBlank(),
                    isFetchingDescription = isFetchingDescription,
                    onSave = { commitEdits() },
                    onClearLookupData = { clearLookedUpData() },
                    onLookup = {
                        buildLookupDraft()?.let { draft ->
                            lookupRequestedInEdit = true
                            // Always open external lookup page for manual verification/copy-back.
                            openManualLookupPage()
                            onFetchFullDescription(draft)
                        }
                    }
                )
            }

            DetectedLabelsSection(
                isEditing = isEditing,
                itemDetectedLabels = item.detectedLabels,
                editedDetectedLabels = editState.detectedLabels,
                onEditedDetectedLabelsChange = {
                    editState = editState.copy(detectedLabels = enforceLeadingCapitalization(it))
                },
                editFieldColors = editFieldColors
            )

            ShareActionsSection(
                onShareRecord = { shareRecord() },
                onSharePhoto = { sharePhoto() }
            )
        }
    }


    if (showExitWithoutSavingDialog) {
        ExitWithoutSavingDialog(
            onDismiss = { showExitWithoutSavingDialog = false },
            onConfirmExit = {
                showExitWithoutSavingDialog = false
                resetEdits()
                showItemNameError = false
                isEditing = false
                onBack()
            },
            onSaveAndExit = {
                if (commitEdits()) {
                    showExitWithoutSavingDialog = false
                    onBack()
                }
            }
        )
    }

    if (showDeletePhotoDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeletePhotoDialog = false
                pendingDeletePhotoId = null
            },
            title = { Text(stringResource(R.string.details_delete_photo_title)) },
            text = { Text(stringResource(R.string.details_delete_photo_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val photoId = pendingDeletePhotoId
                    if (photoId != null) {
                        onDeletePhotoFromItem(photoId, item.id)
                    }
                    showDeletePhotoDialog = false
                    pendingDeletePhotoId = null
                }) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeletePhotoDialog = false
                    pendingDeletePhotoId = null
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showManageCollectionsDialog) {
        ManageCollectionsDialogHost(
            existingCollections = existingCollections,
            editedCollectionName = editState.collectionName,
            onDismiss = { showManageCollectionsDialog = false },
            onAddCollectionRequested = onAddCollectionRequested,
            onRenameCollectionRequested = onRenameCollectionRequested,
            onDeleteCollectionRequested = onDeleteCollectionRequested,
            onCountItemsInCollectionRequested = onCountItemsInCollectionRequested,
            onCollectionNameChange = { editState = editState.copy(collectionName = it) }
        )
    }

    if (showAddTagDialog) {
        AddTagDialog(
            newTagText = newTagText,
            onTagChange = { newTagText = enforceLeadingCapitalization(it) },
            onDismiss = {
                newTagText = ""
                showAddTagDialog = false
            },
            onAddTag = {
                addTagToEditedItem(newTagText)
                newTagText = ""
                showAddTagDialog = false
            }
        )
    }

    if (showSetNotesPinDialog) {
        SetNotesPinDialog(
            notesPinInput = notesPinInput,
            notesPinConfirmInput = notesPinConfirmInput,
            notesPinError = notesPinError,
            onNotesPinInputChange = { notesPinInput = it.filter(Char::isDigit).take(12) },
            onNotesPinConfirmInputChange = { notesPinConfirmInput = it.filter(Char::isDigit).take(12) },
            onDismiss = { showSetNotesPinDialog = false },
            onConfirm = {
                val pin = notesPinInput.trim()
                val confirm = notesPinConfirmInput.trim()
                when {
                    pin.length < 4 -> notesPinError = context.getString(R.string.details_notes_pin_error_invalid)
                    pin != confirm -> notesPinError = context.getString(R.string.details_notes_pin_error_mismatch)
                    else -> {
                        securePrefs.setNotesPin(pin)
                        notesUnlocked = true
                        notesPinError = ""
                        notesPinInput = ""
                        notesPinConfirmInput = ""
                        showSetNotesPinDialog = false
                    }
                }
            }
        )
    }

    if (showUnlockNotesDialog) {
        UnlockNotesPinDialog(
            notesPinInput = notesPinInput,
            notesPinError = notesPinError,
            onNotesPinInputChange = { notesPinInput = it.filter(Char::isDigit).take(12) },
            onDismiss = { showUnlockNotesDialog = false },
            onConfirm = {
                val pin = notesPinInput.trim()
                if (securePrefs.verifyNotesPin(pin)) {
                    notesUnlocked = true
                    notesPinError = ""
                    notesPinInput = ""
                    showUnlockNotesDialog = false
                } else {
                    notesPinError = context.getString(R.string.details_notes_pin_error_incorrect)
                }
            }
        )
    }

    if (showChangeNotesPinDialog) {
        ChangeNotesPinDialog(
            notesCurrentPinInput = notesCurrentPinInput,
            notesNewPinInput = notesNewPinInput,
            notesNewPinConfirmInput = notesNewPinConfirmInput,
            notesPinError = notesPinError,
            onCurrentPinInputChange = { notesCurrentPinInput = it.filter(Char::isDigit).take(12) },
            onNewPinInputChange = { notesNewPinInput = it.filter(Char::isDigit).take(12) },
            onConfirmPinInputChange = { notesNewPinConfirmInput = it.filter(Char::isDigit).take(12) },
            onDismiss = { showChangeNotesPinDialog = false },
            onConfirm = {
                when {
                    !securePrefs.verifyNotesPin(notesCurrentPinInput.trim()) -> {
                        notesPinError = context.getString(R.string.details_notes_pin_error_incorrect)
                    }
                    notesNewPinInput.trim().length < 4 -> {
                        notesPinError = context.getString(R.string.details_notes_pin_error_invalid)
                    }
                    notesNewPinInput.trim() != notesNewPinConfirmInput.trim() -> {
                        notesPinError = context.getString(R.string.details_notes_pin_error_mismatch)
                    }
                    else -> {
                        securePrefs.setNotesPin(notesNewPinInput.trim())
                        notesPinError = ""
                        notesCurrentPinInput = ""
                        notesNewPinInput = ""
                        notesNewPinConfirmInput = ""
                        showChangeNotesPinDialog = false
                    }
                }
            }
        )
    }

    if (showResetNotesPinDialog) {
        ResetNotesPinDialog(
            notesCurrentPinInput = notesCurrentPinInput,
            notesPinError = notesPinError,
            onCurrentPinInputChange = { notesCurrentPinInput = it.filter(Char::isDigit).take(12) },
            onDismiss = { showResetNotesPinDialog = false },
            onConfirm = {
                if (!securePrefs.verifyNotesPin(notesCurrentPinInput.trim())) {
                    notesPinError = context.getString(R.string.details_notes_pin_error_incorrect)
                } else {
                    securePrefs.clearNotesPin()
                    notesUnlocked = false
                    notesPinError = ""
                    notesCurrentPinInput = ""
                    showResetNotesPinDialog = false
                }
            }
        )
    }
}








