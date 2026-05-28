package com.example.valuefinder.ui

import androidx.activity.compose.BackHandler
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.valuefinder.MoneyUtils
import com.example.valuefinder.PhotoUtils
import com.example.valuefinder.R
import com.example.valuefinder.TagUtils
import com.example.valuefinder.BILLS_COLLECTION_NAME
import com.example.valuefinder.BillsPeriod
import com.example.valuefinder.UnlockResult
import com.example.valuefinder.ValuedItem
import com.example.valuefinder.ValuationDraft
import com.example.valuefinder.ValuationResult
import com.example.valuefinder.ValueSource
import com.example.valuefinder.ValuePicsViewModel
import com.example.valuefinder.isBillsCollectionName
import java.io.File
import java.util.Locale

private const val DEFAULT_QUICK_ENTRY_VALUE = ""

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ValuationScreen(
    photoPath: String,
    photoSource: String,
    existingCollections: List<String>,
    existingTags: List<String>,
    isValuating: Boolean,
    viewModel: ValuePicsViewModel,
    onSave: (ValuedItem) -> Unit,
    onSaveAndAddAnother: (ValuedItem) -> Unit,
    currentRecordCount: Int,
    recordLimit: Int,
    isUnlimitedUnlocked: Boolean,
    unlockAccountId: String,
    onUnlockWithPassword: (String, String, (Result<UnlockResult>) -> Unit) -> Unit,
    onRestoreUnlimited: (String, String, (Result<Boolean>) -> Unit) -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    appTier: com.example.valuefinder.AppTier = com.example.valuefinder.AppTier.PERSONAL,
    onAppTierSelected: (com.example.valuefinder.AppTier) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()
    var itemNameField by remember { mutableStateOf(TextFieldValue("")) }
    var itemDescription by remember { mutableStateOf("") }
    var willInstructions by remember { mutableStateOf("") }
    var userEditedDescription by remember { mutableStateOf(false) }
    var lastAutoDescription by remember { mutableStateOf("") }
    var isDescriptionFocused by remember { mutableStateOf(false) }
    val descriptionInteractionSource = remember { MutableInteractionSource() }
    val isDescriptionHovered by descriptionInteractionSource.collectIsHoveredAsState()
    var itemTags by remember { mutableStateOf("") }
    var editableValue by remember { mutableStateOf(DEFAULT_QUICK_ENTRY_VALUE) }
    var manualSourceUrl by remember { mutableStateOf("") }
    var manualLookupProvider by remember { mutableStateOf(LookupProvider.GOOGLE) }
    var userEditedValue by remember { mutableStateOf(false) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var unlockPasswordInput by remember { mutableStateOf("") }
    var unlockAccountInput by remember { mutableStateOf(unlockAccountId) }
    var restoreAccountInput by remember { mutableStateOf(unlockAccountId) }
    var restoreCodeInput by remember { mutableStateOf("") }
    var unlockErrorText by remember { mutableStateOf("") }
    var unlockInfoText by remember { mutableStateOf("") }
    var isUnlockBusy by remember { mutableStateOf(false) }
    var saveAndContinueRequested by remember { mutableStateOf(false) }
    var showExitWithoutSavingDialog by remember { mutableStateOf(false) }
    var showCollectionMenu by remember { mutableStateOf(false) }
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showTagDropdown by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagText by remember { mutableStateOf("") }
    var showManageCollectionsDialog by remember { mutableStateOf(false) }
    var reopenCollectionDialogAfterManage by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var selectedCollection by remember { mutableStateOf("") }
    var selectedBillsEnteredPeriod by remember { mutableStateOf(BillsPeriod.MONTHLY) }
    var doNotIncludeInTotals by remember { mutableStateOf(false) }
    var showItemNameError by remember { mutableStateOf(false) }
    var photoLoadFailed by remember(photoPath) { mutableStateOf(false) }
    var detailedLookupMode by remember { mutableStateOf(false) }
    var hasUserInteracted by remember(photoPath) { mutableStateOf(false) }
    var hasAppliedDraftForPhoto by remember(photoPath) { mutableStateOf(false) }
    
    val valuationResult by viewModel.valuationResult.collectAsStateWithLifecycle()
    val detectedLabels by viewModel.detectedLabels.collectAsStateWithLifecycle()
    val aiOneLineDescription by viewModel.aiOneLineDescription.collectAsStateWithLifecycle()
    val isFetchingFullDescription by viewModel.isFetchingFullDescription.collectAsStateWithLifecycle()
    val draftWebDescription by viewModel.draftWebDescription.collectAsStateWithLifecycle()
    val currentDraft by viewModel.currentValuationDraft.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    val selectedTags = remember(itemTags) {
        TagUtils.parseTags(itemTags).take(TagUtils.MAX_TAGS)
    }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isCompactScreen = screenWidthDp < 600
    val itemName = itemNameField.text
    val majorItemsCollectionName = stringResource(R.string.major_items_collection_name)
    val isBillsCollectionSelected = appTier == com.example.valuefinder.AppTier.PERSONAL && isBillsCollectionName(selectedCollection)
    fun loadEffectiveCollections(): List<String> {
        val normalized = existingCollections
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
        return if (appTier == com.example.valuefinder.AppTier.PERSONAL) {
            (normalized + BILLS_COLLECTION_NAME)
                .distinctBy { it.lowercase(Locale.getDefault()) }
        } else {
            normalized.filterNot { isBillsCollectionName(it) }
        }
    }

    var effectiveCollections by remember(existingCollections) {
        mutableStateOf(loadEffectiveCollections())
    }

    LaunchedEffect(showCategoryDropdown, showCollectionDialog, existingCollections) {
        if (showCategoryDropdown || showCollectionDialog) {
            effectiveCollections = loadEffectiveCollections()
        }
    }

    LaunchedEffect(effectiveCollections) {
        Log.i(
            "ValuePicsValuation",
            "VALUATION category options count=${effectiveCollections.size} options=${effectiveCollections.joinToString()}"
        )
    }
    val canSaveMoreRecords = isUnlimitedUnlocked || currentRecordCount < recordLimit

    DisposableEffect(lifecycleOwner, photoPath) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                photoLoadFailed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }


    fun addTag(rawTag: String) {
        val normalizedTag = rawTag.trim()
        if (normalizedTag.isBlank()) return
        itemTags = ((TagUtils.parseTags(itemTags) + normalizedTag)
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .take(TagUtils.MAX_TAGS))
            .joinToString(", ")
    }

    fun removeTag(tag: String) {
        itemTags = TagUtils.parseTags(itemTags)
            .filterNot { it.equals(tag, ignoreCase = true) }
            .take(TagUtils.MAX_TAGS)
            .joinToString(", ")
    }

    fun setItemName(rawValue: String, selectAll: Boolean = false) {
        val normalizedValue = enforceLeadingCapitalization(rawValue)
        itemNameField = TextFieldValue(
            text = normalizedValue,
            selection = if (selectAll && normalizedValue.isNotBlank()) {
                TextRange(0, normalizedValue.length)
            } else {
                TextRange(normalizedValue.length)
            }
        )
    }

    fun hasMeaningfulDraftContent(): Boolean {
        return itemName.isNotBlank() ||
            itemDescription.isNotBlank() ||
            willInstructions.isNotBlank() ||
            TagUtils.parseTags(itemTags).isNotEmpty() ||
            editableValue.isNotBlank() ||
            selectedCollection.isNotBlank() ||
            doNotIncludeInTotals ||
            valuationResult != null ||
            draftWebDescription != null
    }

    fun requestExitWithoutSaving() {
        if (hasUserInteracted && hasMeaningfulDraftContent()) {
            showExitWithoutSavingDialog = true
        } else {
            onBack()
        }
    }

    fun openManualLookupPage() {
        openManualLookupPage(
            context = context,
            photoPath = photoPath,
            provider = manualLookupProvider,
            itemName = itemName,
            itemDescription = itemDescription
        )
    }

    fun readClipboardText(): String {
        return clipboardManager.getText()?.text?.trim().orEmpty()
    }

    BackHandler(onBack = { requestExitWithoutSaving() })

    fun buildDescriptionFromValuation(result: ValuationResult?): String {
        val topResult = result?.results?.firstOrNull() ?: return ""
        return topResult.description.trim().ifBlank { topResult.title.trim() }
    }

    fun shouldApplyAutoDescription(): Boolean {
        return !userEditedDescription || itemDescription.isBlank() || itemDescription == lastAutoDescription
    }

    fun firstLineOfAiOverview(text: String): String {
        return text
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    // When value lookup returns comparables, use the top internet match title as the draft description.
    LaunchedEffect(valuationResult?.estimatedValue, valuationResult?.results?.firstOrNull()?.url) {
        val lookedUpDescription = buildDescriptionFromValuation(valuationResult)
        if (lookedUpDescription.isBlank() || !shouldApplyAutoDescription()) return@LaunchedEffect
        itemDescription = lookedUpDescription
        lastAutoDescription = lookedUpDescription
        userEditedDescription = false
    }

    // If user taps Lookup, prefer the fetched one-line web description.
    LaunchedEffect(draftWebDescription?.sourceUrl, draftWebDescription?.oneLine) {
        val lookedUpDescription = draftWebDescription?.oneLine?.trim().orEmpty()
        if (lookedUpDescription.isBlank() || !shouldApplyAutoDescription()) return@LaunchedEffect
        itemDescription = lookedUpDescription
        lastAutoDescription = lookedUpDescription
        userEditedDescription = false
    }

    LaunchedEffect(photoPath) {
        if (photoPath.isNotBlank()) {
            hasAppliedDraftForPhoto = false
            hasUserInteracted = false
            userEditedDescription = false
            lastAutoDescription = ""
            editableValue = DEFAULT_QUICK_ENTRY_VALUE
            userEditedValue = false
            viewModel.clearTransientState()
            viewModel.analyzePhoto(photoPath)
            viewModel.loadValuationDraftForPhoto(photoPath)
        }
    }

    LaunchedEffect(photoPath, currentDraft?.savedAtMillis) {
        val draft = currentDraft ?: return@LaunchedEffect
        if (draft.photoPath != photoPath || hasAppliedDraftForPhoto) return@LaunchedEffect
        setItemName(draft.itemName)
        itemDescription = draft.itemDescription
        willInstructions = draft.willInstructions
        userEditedDescription = draft.itemDescription.isNotBlank()
        lastAutoDescription = ""
        itemTags = draft.itemTags
        editableValue = draft.editableValue
        userEditedValue = draft.userEditedValue
        selectedCollection = draft.selectedCollection
        doNotIncludeInTotals = draft.doNotIncludeInTotals
        detailedLookupMode = draft.detailedLookupMode
            hasUserInteracted = false
        hasAppliedDraftForPhoto = true
    }

    LaunchedEffect(aiOneLineDescription, detectedLabels) {
        if (itemName.isNotBlank()) return@LaunchedEffect
        val aiFirstLine = firstLineOfAiOverview(aiOneLineDescription)
        if (aiFirstLine.isNotBlank()) {
            setItemName(aiFirstLine)
            return@LaunchedEffect
        }
        if (detectedLabels.isNotEmpty()) {
            setItemName(detectedLabels.first().label)
        }
    }

    LaunchedEffect(valuationResult?.estimatedValue) {
        if (!userEditedValue && valuationResult?.estimatedValue != null) {
            editableValue = String.format(Locale.US, "%.2f", valuationResult!!.estimatedValue)
        }
    }

    LaunchedEffect(appTier, selectedCollection) {
        if (appTier == com.example.valuefinder.AppTier.INSURANCE && isBillsCollectionName(selectedCollection)) {
            selectedCollection = ""
        }
    }

    // Debounced draft save — waits 500ms after the last change before writing to DB
    LaunchedEffect(
        photoPath,
        photoSource,
        itemName,
        itemDescription,
        willInstructions,
        itemTags,
        editableValue,
        userEditedValue,
        selectedCollection,
        doNotIncludeInTotals,
        detailedLookupMode
    ) {
        if (photoPath.isBlank()) return@LaunchedEffect
        kotlinx.coroutines.delay(500L)
        viewModel.saveValuationDraft(
            ValuationDraft(
                photoPath = photoPath,
                photoSource = photoSource,
                itemName = itemName,
                itemDescription = itemDescription,
                willInstructions = willInstructions,
                itemTags = itemTags,
                editableValue = editableValue,
                userEditedValue = userEditedValue,
                selectedCollection = selectedCollection,
                doNotIncludeInTotals = doNotIncludeInTotals,
                detailedLookupMode = detailedLookupMode
            )
        )
    }

    fun buildItem(collectionName: String): ValuedItem {
        val value = editableValue.toDoubleOrNull()
            ?: valuationResult?.estimatedValue
        val confidence = if (value != null && userEditedValue) 0.95f else (valuationResult?.confidence ?: 0f)
        val resolvedSourceUrl = draftWebDescription?.sourceUrl
            ?.takeIf { it.isNotBlank() }
            ?: manualSourceUrl.trim().takeIf { it.isNotBlank() }
            ?: valuationResult?.results?.firstOrNull()?.visitUrl
            ?: valuationResult?.results?.firstOrNull()?.url
            .orEmpty()
        return ValuedItem(
            photoPath = photoPath,
            photoSource = photoSource,
            itemName = itemName,
            collectionName = collectionName,
            shortAiDescription = aiOneLineDescription,
            itemDescription = itemDescription,
            willInstructions = willInstructions.trim(),
            detectedLabels = detectedLabels.joinToString(",") { it.label },
            estimatedValue = value,
            currency = "AUD",
            valueSource = valuationResult?.source ?: ValueSource.Manual.key,
            sourceUrl = resolvedSourceUrl,
            fullWebDescription = draftWebDescription?.fullDescription.orEmpty(),
            searchResults = valuationResult?.results
                ?.joinToString("\n") { result ->
                    listOf(
                        result.title,
                        result.price?.toString().orEmpty(),
                        result.source,
                        result.visitUrl.ifBlank { result.url }
                    ).joinToString(" | ")
                }.orEmpty(),
            confidence = confidence,
            dateTaken = PhotoUtils.getCurrentDateTime(),
            tags = TagUtils.normalizeTags(itemTags),
            billsEnteredPeriod = if (isBillsCollectionName(collectionName)) selectedBillsEnteredPeriod.storageValue else "",
            includeInTotals = !doNotIncludeInTotals
        )
    }

    fun updateDoNotIncludeInTotals(checked: Boolean) {
        doNotIncludeInTotals = checked
        val otherTag = majorItemsCollectionName
        val currentTags = TagUtils.parseTags(itemTags)
        itemTags = if (checked) {
            (currentTags + otherTag)
                .distinctBy { it.lowercase(Locale.getDefault()) }
                .take(TagUtils.MAX_TAGS)
                .joinToString(", ")
        } else {
            currentTags
                .filterNot { it.equals(otherTag, ignoreCase = true) }
                .joinToString(", ")
        }
    }

    val hasParseableManualValue = editableValue.trim().toDoubleOrNull() != null
    val hasConfirmedValuationResult = valuationResult?.estimatedValue != null
    val hasValidValueForSave = hasParseableManualValue || hasConfirmedValuationResult
    val shouldShowValueParseError = editableValue.trim().isNotBlank() && !hasParseableManualValue && !hasConfirmedValuationResult
    var showValueValidationError by remember { mutableStateOf(false) }
    var showCategoryValidationError by remember { mutableStateOf(false) }
    val hasSelectedCategory = selectedCollection.trim().isNotBlank()
    val showCategoryRequiredHint = !hasSelectedCategory && (screenWidthDp >= 600 || showCategoryValidationError)
    val canSaveCurrentItem = itemName.trim().isNotBlank() && hasValidValueForSave && hasSelectedCategory

    fun runSaveAction(saveAndAddAnother: Boolean) {
        if (itemName.trim().isBlank()) {
            showItemNameError = true
            return
        }
        if (!hasValidValueForSave) {
            showValueValidationError = true
            return
        }
        if (!canSaveMoreRecords) {
            showUnlockDialog = true
            return
        }
        saveAndContinueRequested = saveAndAddAnother
        if (!hasSelectedCategory) {
            showCategoryValidationError = true
            return
        }
        val item = buildItem(selectedCollection.trim())
        if (saveAndAddAnother) {
            onSaveAndAddAnother(item)
        } else {
            onSave(item)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.valuation_title)) },
            navigationIcon = {
                IconButton(onClick = { requestExitWithoutSaving() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.list_cd_more_actions))
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        modifier = Modifier.navigationBarsPadding()
                    ) {
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
            // Photo Display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (File(photoPath).exists() && !photoLoadFailed) {
                    AsyncImage(
                        model = File(photoPath),
                        contentDescription = stringResource(R.string.details_item_photo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        onSuccess = { photoLoadFailed = false },
                        onError = { photoLoadFailed = true }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Image, stringResource(R.string.list_no_photo), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.common_image_unavailable),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Detected Labels
            if (detectedLabels.isNotEmpty()) {
                Text(stringResource(R.string.valuation_detected_items), style = MaterialTheme.typography.titleSmall)
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detectedLabels.forEach { label ->
                        SuggestionChip(
                            onClick = { setItemName(label.label) },
                            label = {
                                Text("${label.label} (${String.format(Locale.US, "%.0f%%", label.confidence * 100)})")
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                if (aiOneLineDescription.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.valuation_ai_one_line), style = MaterialTheme.typography.titleSmall)
                                IconButton(onClick = { viewModel.clearAiOneLineDescription() }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.common_close)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(aiOneLineDescription, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Item Details Form
            OutlinedTextField(
                value = itemNameField,
                onValueChange = {
                    hasUserInteracted = true
                    setItemName(it.text)
                    if (it.text.trim().isNotBlank()) {
                        showItemNameError = false
                    }
                },
                label = { Text(stringResource(R.string.valuation_item_name_required)) },
                trailingIcon = if (!isCompactScreen && itemName.isNotBlank()) {
                    {
                        IconButton(
                            onClick = {
                                hasUserInteracted = true
                                setItemName("")
                                showItemNameError = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.valuation_clear_item_name)
                            )
                        }
                    }
                } else {
                    null
                },
                isError = showItemNameError && itemName.trim().isBlank(),
                supportingText = {
                    if (showItemNameError && itemName.trim().isBlank()) {
                        Text(stringResource(R.string.validation_item_name_required))
                    }
                },
                modifier = Modifier
                    .onFocusChanged { focusState ->
                        if (isCompactScreen && focusState.isFocused && itemName.isNotBlank()) {
                            itemNameField = itemNameField.copy(
                                selection = TextRange(0, itemName.length)
                            )
                        }
                    }
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )

            ValueAndQuickSaveRow(
                editableValue = editableValue,
                shouldShowValueParseError = shouldShowValueParseError,
                showValueValidationError = showValueValidationError,
                hasValidValueForSave = hasValidValueForSave,
                doNotIncludeInTotals = doNotIncludeInTotals,
                canSaveCurrentItem = canSaveCurrentItem,
                onValueChange = {
                    hasUserInteracted = true
                    editableValue = it
                    userEditedValue = true
                    if (editableValue.trim().toDoubleOrNull() != null || hasConfirmedValuationResult) {
                        showValueValidationError = false
                    }
                },
                onToggleExcludeFromTotals = { checked ->
                    hasUserInteracted = true
                    updateDoNotIncludeInTotals(checked)
                },
                onQuickSave = { runSaveAction(saveAndAddAnother = false) }
            )

            CategorySelectorSection(
                selectedCollection = selectedCollection,
                effectiveCollections = effectiveCollections,
                showCategoryDropdown = showCategoryDropdown,
                showCategoryValidationError = showCategoryValidationError,
                hasSelectedCategory = hasSelectedCategory,
                showCategoryRequiredHint = showCategoryRequiredHint,
                onExpandedChange = { showCategoryDropdown = it },
                onSelectNone = {
                    hasUserInteracted = true
                    selectedCollection = ""
                    showCategoryDropdown = false
                },
                onSelectCollection = { collection ->
                    hasUserInteracted = true
                    selectedCollection = collection
                    showCategoryValidationError = false
                    showCategoryDropdown = false
                },
                onAddNewCategory = {
                    showCategoryDropdown = false
                    showManageCollectionsDialog = true
                }
            )

            if (isBillsCollectionSelected) {
                BillsPeriodSelectorSection(
                    selectedBillsEnteredPeriod = selectedBillsEnteredPeriod,
                    onPeriodSelected = { selectedBillsEnteredPeriod = it }
                )
            }

            OutlinedTextField(
                value = willInstructions,
                onValueChange = {
                    hasUserInteracted = true
                    willInstructions = enforceLeadingCapitalization(it)
                },
                label = { Text(stringResource(R.string.valuation_will_instructions_label)) },
                supportingText = { Text(stringResource(R.string.valuation_will_instructions_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
            )

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = manualLookupProvider == LookupProvider.BING,
                    onClick = { manualLookupProvider = LookupProvider.BING },
                    label = { Text(stringResource(R.string.lookup_provider_bing)) }
                )
                FilterChip(
                    selected = manualLookupProvider == LookupProvider.GOOGLE,
                    onClick = { manualLookupProvider = LookupProvider.GOOGLE },
                    label = { Text(stringResource(R.string.lookup_provider_google)) }
                )
            }

            Text(
                text = stringResource(R.string.valuation_lookup_provider_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            val selectedLookupProviderName = when (manualLookupProvider) {
                LookupProvider.BING -> stringResource(R.string.lookup_provider_name_bing)
                LookupProvider.GOOGLE -> stringResource(R.string.lookup_provider_name_google)
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = {
                    hasUserInteracted = true
                    openManualLookupPage()
                }) {
                    Text(stringResource(R.string.valuation_open_selected_image_lookup, selectedLookupProviderName))
                }
                OutlinedButton(onClick = {
                    val clipboardText = readClipboardText()
                    if (clipboardText.isNotBlank()) {
                        hasUserInteracted = true
                        manualSourceUrl = clipboardText
                    }
                }) {
                    Text(stringResource(R.string.valuation_paste_link_from_clipboard))
                }
                OutlinedButton(onClick = {
                    val clipboardText = readClipboardText()
                    if (clipboardText.isNotBlank()) {
                        hasUserInteracted = true
                        itemDescription = clipboardText
                        userEditedDescription = true
                    }
                }) {
                    Text(stringResource(R.string.valuation_paste_description_from_clipboard))
                }
                OutlinedButton(onClick = {
                    val clipboardText = readClipboardText()
                    val firstLine = firstLineOfAiOverview(clipboardText)
                    if (firstLine.isNotBlank()) {
                        hasUserInteracted = true
                        setItemName(firstLine)
                        showItemNameError = false
                    }
                }) {
                    Text(stringResource(R.string.valuation_set_name_from_clipboard_first_line))
                }
            }

            OutlinedTextField(
                value = manualSourceUrl,
                onValueChange = {
                    hasUserInteracted = true
                    manualSourceUrl = it
                },
                label = { Text(stringResource(R.string.valuation_source_url_optional)) },
                supportingText = { Text(stringResource(R.string.valuation_source_url_hint)) },
                trailingIcon = {
                    if (manualSourceUrl.isNotBlank()) {
                        IconButton(onClick = {
                            hasUserInteracted = true
                            manualSourceUrl = ""
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.common_close))
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )

            TextButton(
                onClick = { openExternalUrl(context, manualSourceUrl) },
                enabled = manualSourceUrl.isNotBlank(),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(stringResource(R.string.common_open_link))
            }

            OutlinedTextField(
                value = itemTags,
                onValueChange = {
                    hasUserInteracted = true
                    itemTags = TagUtils.normalizeTagsInput(enforceLeadingCapitalization(it))
                },
                label = { Text(stringResource(R.string.tags_label)) },
                supportingText = { Text(stringResource(R.string.tags_help)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
            if (selectedTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    selectedTags.forEach { tag ->
                        InputChip(
                            selected = true,
                            onClick = {
                                hasUserInteracted = true
                                removeTag(tag)
                            },
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

            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                OutlinedButton(
                    onClick = { showTagDropdown = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.details_add_tag_from_existing), modifier = Modifier.weight(1f))
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
                        enabled = selectedTags.size < TagUtils.MAX_TAGS
                    )
                    HorizontalDivider()
                    existingTags.forEach { tag ->
                        val alreadySelected = selectedTags.any { it.equals(tag, ignoreCase = true) }
                        val limitReached = selectedTags.size >= TagUtils.MAX_TAGS
                        DropdownMenuItem(
                            text = { Text(tag) },
                            onClick = {
                                hasUserInteracted = true
                                addTag(tag)
                                showTagDropdown = false
                            },
                            enabled = !alreadySelected && !limitReached
                        )
                    }
                }
            }
            if (selectedTags.size >= TagUtils.MAX_TAGS) {
                Text(
                    stringResource(R.string.tags_limit_reached, TagUtils.MAX_TAGS),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Lookup Button
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                FilterChip(
                    selected = !detailedLookupMode,
                    onClick = {
                        hasUserInteracted = true
                        detailedLookupMode = false
                    },
                    label = { Text(stringResource(R.string.valuation_lookup_mode_fast)) }
                )
                FilterChip(
                    selected = detailedLookupMode,
                    onClick = {
                        hasUserInteracted = true
                        detailedLookupMode = true
                    },
                    label = { Text(stringResource(R.string.valuation_lookup_mode_detailed)) }
                )
            }
            Text(
                text = stringResource(R.string.valuation_lookup_mode_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Button(
                onClick = {
                    if (itemName.trim().isBlank()) {
                        showItemNameError = true
                        return@Button
                    }
                    hasUserInteracted = true
                    // Always open an external lookup page so users can manually verify/copy links.
                    openManualLookupPage()
                    userEditedValue = false
                    viewModel.valuateItem(itemName, itemDescription, detailedLookupMode)
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 8.dp),
                enabled = !isValuating && itemName.isNotBlank()
            ) {
                if (isValuating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.valuation_search_online))
            }

            // Show loading indicator with text during search
            if (isValuating) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.valuation_searching_web),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.valuation_searching_web_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    if (itemName.trim().isBlank()) {
                        showItemNameError = true
                        return@OutlinedButton
                    }
                    hasUserInteracted = true
                    val labels = detectedLabels.map { it.label }
                    viewModel.lookupDraftFullDescription(
                        itemName = itemName,
                        hint = itemDescription,
                        labels = labels
                    )
                },
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp),
                enabled = !isFetchingFullDescription && itemName.isNotBlank()
            ) {
                if (isFetchingFullDescription) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.details_lookup_action))
            }

            // Valuation Results
            if (valuationResult != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.valuation_estimated_value),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            MoneyUtils.formatAud(valuationResult!!.estimatedValue),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            stringResource(R.string.valuation_confidence_value, valuationResult!!.confidence * 100f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            stringResource(
                                R.string.valuation_source_value,
                                ValueSource.fromKey(valuationResult!!.source).displayName
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        if (valuationResult!!.results.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                stringResource(R.string.valuation_comparable_listings),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            valuationResult!!.results.take(5).forEach { result ->
                                val priceSuffix = result.price?.let { " (${MoneyUtils.formatAud(it)})" } ?: ""
                                Text(
                                    stringResource(
                                        R.string.valuation_result_bullet,
                                        result.title,
                                        result.source,
                                        priceSuffix
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (doNotIncludeInTotals) {
                Text(
                    text = stringResource(R.string.valuation_do_not_include_hint, majorItemsCollectionName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            SaveActionsRow(
                canSaveCurrentItem = canSaveCurrentItem,
                onSave = { runSaveAction(saveAndAddAnother = false) },
                onSaveAndAddAnother = { runSaveAction(saveAndAddAnother = true) }
            )
        }
    }

    if (showCollectionDialog) {
        val finalCollection = selectedCollection.trim()
        CollectionChoiceDialog(
            effectiveCollections = effectiveCollections,
            onDismiss = {
                saveAndContinueRequested = false
                showCollectionDialog = false
            },
            onSelectNone = {
                selectedCollection = ""
                showCollectionDialog = false
            },
            onSelectCollection = { collection ->
                selectedCollection = collection
                showCategoryValidationError = false
                showCollectionDialog = false
            },
            onConfirmSave = {
                if (!canSaveMoreRecords) {
                    showUnlockDialog = true
                    return@CollectionChoiceDialog
                }
                val builtItem = buildItem(finalCollection)
                if (saveAndContinueRequested) {
                    onSaveAndAddAnother(builtItem)
                } else {
                    onSave(builtItem)
                }
                saveAndContinueRequested = false
                showCollectionDialog = false
            }
        )
    }

    if (showManageCollectionsDialog) {
        ManageCollectionsDialog(
            existingCollections = existingCollections,
            initialSelection = selectedCollection,
            onDismiss = {
                showManageCollectionsDialog = false
                showCollectionDialog = reopenCollectionDialogAfterManage
                reopenCollectionDialogAfterManage = false
            },
            onAddCollectionRequested = { name, onResult -> viewModel.addCollection(name, onResult) },
            onRenameCollectionRequested = { oldName, newName, onResult -> viewModel.renameCollection(oldName, newName, onResult) },
            onDeleteCollectionRequested = { name, onResult -> viewModel.deleteCollection(name, onResult) },
            onCountItemsInCollection = { name, onResult -> viewModel.countItemsInCollection(name, onResult) },
            onCollectionAdded = { newName ->
                hasUserInteracted = true
                selectedCollection = newName
                showManageCollectionsDialog = false
                showCollectionDialog = reopenCollectionDialogAfterManage
                reopenCollectionDialogAfterManage = false
            },
            onCollectionRenamed = { oldName, newName ->
                if (selectedCollection.equals(oldName, ignoreCase = true)) {
                    hasUserInteracted = true
                    selectedCollection = newName
                }
            },
            onCollectionDeleted = { name ->
                if (selectedCollection.equals(name, ignoreCase = true)) {
                    hasUserInteracted = true
                    selectedCollection = ""
                }
            }
        )
    }

    

    if (showExitWithoutSavingDialog) {
        UnsavedExitDialog(
            onDismiss = { showExitWithoutSavingDialog = false },
            onConfirmExit = {
                showExitWithoutSavingDialog = false
                onBack()
            }
        )
    }

    if (showAddTagDialog) {
        AddTagDialog(
            newTagText = newTagText,
            onValueChange = { newTagText = enforceLeadingCapitalization(it) },
            onDismiss = {
                newTagText = ""
                showAddTagDialog = false
            },
            onConfirm = {
                hasUserInteracted = true
                addTag(newTagText)
                newTagText = ""
                showAddTagDialog = false
            }
        )
    }

    if (showUnlockDialog) {
        UnlockDialog(
            isUnlockBusy = isUnlockBusy,
            recordLimit = recordLimit,
            currentRecordCount = currentRecordCount,
            unlockPasswordInput = unlockPasswordInput,
            onUnlockPasswordChange = { unlockPasswordInput = it },
            unlockAccountInput = unlockAccountInput,
            onUnlockAccountChange = { unlockAccountInput = it },
            restoreAccountInput = restoreAccountInput,
            onRestoreAccountChange = { restoreAccountInput = it },
            restoreCodeInput = restoreCodeInput,
            onRestoreCodeChange = { restoreCodeInput = it.uppercase(Locale.getDefault()) },
            unlockErrorText = unlockErrorText,
            unlockInfoText = unlockInfoText,
            onDismiss = {
                if (!isUnlockBusy) {
                    showUnlockDialog = false
                }
            },
            onUnlockNow = {
                isUnlockBusy = true
                unlockErrorText = ""
                unlockInfoText = ""
                onUnlockWithPassword(unlockPasswordInput, unlockAccountInput) { result ->
                    isUnlockBusy = false
                    result.fold(
                        onSuccess = { unlockResult ->
                            if (unlockResult.unlocked) {
                                unlockPasswordInput = ""
                                unlockInfoText = unlockResult.recoveryCode
                                    ?.let { context.getString(R.string.unlock_result_unlocked_with_code, it) }
                                    ?: context.getString(R.string.unlock_result_unlocked)
                                showUnlockDialog = false
                            } else {
                                unlockErrorText = context.getString(R.string.unlock_error_incorrect_password)
                            }
                        },
                        onFailure = {
                            unlockErrorText = context.getString(R.string.unlock_error_unlock_failed)
                        }
                    )
                }
            },
            onRestoreNow = {
                isUnlockBusy = true
                unlockErrorText = ""
                unlockInfoText = ""
                onRestoreUnlimited(restoreAccountInput, restoreCodeInput) { result ->
                    isUnlockBusy = false
                    result.fold(
                        onSuccess = { restored ->
                            if (restored) {
                                unlockInfoText = context.getString(R.string.unlock_result_restored)
                                showUnlockDialog = false
                            } else {
                                unlockErrorText = context.getString(R.string.unlock_error_restore_invalid)
                            }
                        },
                        onFailure = {
                            unlockErrorText = context.getString(R.string.unlock_error_restore_failed)
                        }
                    )
                }
            }
        )
    }

    // Show errors from failed photo analysis or valuation lookups
    if (operationError != null) {
        val errorMessage = when (val err = operationError!!) {
            is UiError.GeneralError -> err.message
            is UiError.ImportError -> err.message
            is UiError.PdfError -> err.message
            is UiError.DatabaseError -> err.message
        }
        OperationErrorDialog(
            errorMessage = errorMessage,
            onDismiss = { viewModel.clearOperationError() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySelectorSection(
    selectedCollection: String,
    effectiveCollections: List<String>,
    showCategoryDropdown: Boolean,
    showCategoryValidationError: Boolean,
    hasSelectedCategory: Boolean,
    showCategoryRequiredHint: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectNone: () -> Unit,
    onSelectCollection: (String) -> Unit,
    onAddNewCategory: () -> Unit
) {
    Text(
        text = stringResource(R.string.valuation_category_label),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    ExposedDropdownMenuBox(
        expanded = showCategoryDropdown,
        onExpandedChange = onExpandedChange,
        modifier = Modifier.padding(bottom = 12.dp)
    ) {
        OutlinedTextField(
            value = if (selectedCollection.isBlank()) {
                stringResource(R.string.valuation_category_none_selected)
            } else {
                stringResource(R.string.common_category_selected_value, selectedCollection)
            },
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            isError = showCategoryValidationError && !hasSelectedCategory,
            label = { Text(stringResource(R.string.valuation_category_label)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown)
            },
            modifier = Modifier
                .menuAnchor(
                    type = MenuAnchorType.PrimaryNotEditable,
                    enabled = true
                )
                .testTag("valuation_category_dropdown")
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = showCategoryDropdown,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.navigationBarsPadding()
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.collections_none_label)) },
                onClick = onSelectNone
            )
            effectiveCollections.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { collection ->
                DropdownMenuItem(
                    text = { Text(collection) },
                    onClick = { onSelectCollection(collection) }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.collections_add_new_category)) },
                onClick = onAddNewCategory
            )
        }
    }

    if (showCategoryRequiredHint) {
        Text(
            text = stringResource(R.string.valuation_category_required_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    } else {
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun BillsPeriodSelectorSection(
    selectedBillsEnteredPeriod: BillsPeriod,
    onPeriodSelected: (BillsPeriod) -> Unit
) {
    Text(
        text = stringResource(R.string.bills_period_label),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(bottom = 4.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
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
                    .toggleable(
                        value = selectedBillsEnteredPeriod == period,
                        role = Role.RadioButton,
                        onValueChange = { onPeriodSelected(period) }
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedBillsEnteredPeriod == period,
                    onClick = null
                )
                Text(text = label)
            }
        }
    }
}

@Composable
private fun UnsavedExitDialog(
    onDismiss: () -> Unit,
    onConfirmExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.valuation_unsaved_exit_title)) },
        text = { Text(stringResource(R.string.valuation_unsaved_exit_message)) },
        confirmButton = {
            TextButton(onClick = onConfirmExit) {
                Text(stringResource(R.string.valuation_unsaved_exit_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.valuation_unsaved_exit_stay))
            }
        }
    )
}

@Composable
private fun AddTagDialog(
    newTagText: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_new_tag_title)) },
        text = {
            OutlinedTextField(
                value = newTagText,
                onValueChange = onValueChange,
                label = { Text(stringResource(R.string.details_new_tag_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = newTagText.trim().isNotBlank()
            ) {
                Text(stringResource(R.string.details_new_tag_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun OperationErrorDialog(
    errorMessage: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.valuation_error_title)) },
        text = { Text(errorMessage) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
            }
        }
    )
}

@Composable
private fun CollectionChoiceDialog(
    effectiveCollections: List<String>,
    onDismiss: () -> Unit,
    onSelectNone: () -> Unit,
    onSelectCollection: (String) -> Unit,
    onConfirmSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_choose_category_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSelectNone,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.collections_none_label))
                }
                effectiveCollections.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { collection ->
                    OutlinedButton(
                        onClick = { onSelectCollection(collection) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(collection)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirmSave) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun ValueAndQuickSaveRow(
    editableValue: String,
    shouldShowValueParseError: Boolean,
    showValueValidationError: Boolean,
    hasValidValueForSave: Boolean,
    doNotIncludeInTotals: Boolean,
    canSaveCurrentItem: Boolean,
    onValueChange: (String) -> Unit,
    onToggleExcludeFromTotals: (Boolean) -> Unit,
    onQuickSave: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        OutlinedTextField(
            value = editableValue,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.valuation_estimated_value)) },
            isError = shouldShowValueParseError || (showValueValidationError && !hasValidValueForSave),
            supportingText = {
                when {
                    shouldShowValueParseError -> Text(stringResource(R.string.valuation_value_parse_error))
                    (showValueValidationError || editableValue.isBlank()) && !hasValidValueForSave -> {
                        Text(stringResource(R.string.valuation_value_required_hint))
                    }
                }
            },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .toggleable(
                    value = doNotIncludeInTotals,
                    role = Role.Checkbox,
                    onValueChange = onToggleExcludeFromTotals
                )
                .padding(horizontal = 2.dp)
        ) {
            Checkbox(
                checked = doNotIncludeInTotals,
                onCheckedChange = null
            )
            Text(
                text = stringResource(R.string.valuation_exclude_totals_compact),
                style = MaterialTheme.typography.labelSmall
            )
        }

        Button(
            onClick = onQuickSave,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
            enabled = canSaveCurrentItem
        ) {
            Text(stringResource(R.string.valuation_save_button))
        }
    }
}

@Composable
private fun SaveActionsRow(
    canSaveCurrentItem: Boolean,
    onSave: () -> Unit,
    onSaveAndAddAnother: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            enabled = canSaveCurrentItem
        ) {
            Text(stringResource(R.string.valuation_save_button))
        }

        OutlinedButton(
            onClick = onSaveAndAddAnother,
            modifier = Modifier
                .weight(1f)
                .height(48.dp),
            enabled = canSaveCurrentItem
        ) {
            Text(stringResource(R.string.valuation_save_and_add_another))
        }
    }

    Spacer(modifier = Modifier.height(48.dp))
}

@Composable
private fun UnlockDialog(
    isUnlockBusy: Boolean,
    recordLimit: Int,
    currentRecordCount: Int,
    unlockPasswordInput: String,
    onUnlockPasswordChange: (String) -> Unit,
    unlockAccountInput: String,
    onUnlockAccountChange: (String) -> Unit,
    restoreAccountInput: String,
    onRestoreAccountChange: (String) -> Unit,
    restoreCodeInput: String,
    onRestoreCodeChange: (String) -> Unit,
    unlockErrorText: String,
    unlockInfoText: String,
    onDismiss: () -> Unit,
    onUnlockNow: () -> Unit,
    onRestoreNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.unlock_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.unlock_dialog_limit_message, recordLimit))
                Text(stringResource(R.string.unlock_dialog_current_records, currentRecordCount))
                Text(
                    stringResource(R.string.unlock_dialog_feature_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = unlockPasswordInput,
                    onValueChange = onUnlockPasswordChange,
                    label = { Text(stringResource(R.string.unlock_dialog_password_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = unlockAccountInput,
                    onValueChange = onUnlockAccountChange,
                    label = { Text(stringResource(R.string.unlock_dialog_account_optional_label)) },
                    supportingText = { Text(stringResource(R.string.unlock_dialog_account_help)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    enabled = !isUnlockBusy && unlockPasswordInput.isNotBlank(),
                    onClick = onUnlockNow
                ) {
                    Text(
                        if (isUnlockBusy) {
                            stringResource(R.string.unlock_dialog_unlocking)
                        } else {
                            stringResource(R.string.unlock_dialog_unlock_now)
                        }
                    )
                }

                HorizontalDivider()
                Text(
                    stringResource(R.string.unlock_dialog_restore_title),
                    style = MaterialTheme.typography.titleSmall
                )
                OutlinedTextField(
                    value = restoreAccountInput,
                    onValueChange = onRestoreAccountChange,
                    label = { Text(stringResource(R.string.unlock_dialog_account_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = restoreCodeInput,
                    onValueChange = onRestoreCodeChange,
                    label = { Text(stringResource(R.string.unlock_dialog_recovery_code_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    enabled = !isUnlockBusy && restoreAccountInput.isNotBlank() && restoreCodeInput.isNotBlank(),
                    onClick = onRestoreNow
                ) {
                    Text(stringResource(R.string.unlock_dialog_restore_action))
                }

                if (unlockErrorText.isNotBlank()) {
                    Text(unlockErrorText, color = MaterialTheme.colorScheme.error)
                }
                if (unlockInfoText.isNotBlank()) {
                    Text(unlockInfoText, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}







