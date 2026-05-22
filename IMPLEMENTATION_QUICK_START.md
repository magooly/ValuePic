# Implementation Quick-Start Guide - PDF Export Improvements

**Created**: April 30, 2026  
**Target**: Phase 1 (Critical Fixes) - 1 Week Sprint

---

## Overview

This guide provides step-by-step implementation for the highest-impact fixes from the logic and UX analysis. Each section is modular and can be implemented independently.

---

## 1. RENAME MENU ITEMS (⏱️ 15 Minutes)

### Files to Change
- `C:\wrhor\DataBase\app\src\main\res\values\strings.xml`
- `C:\wrhor\DataBase\app\src\main\java\com\example\valuefinder\ui\ItemListScreen.kt`

### Step 1: Update strings.xml

Find these lines:
```xml
<string name="about_button_export_pdf">PDF Report</string>
<string name="about_button_creating_pdf">Creating PDF Report...</string>
<string name="about_button_export_pdf_with_thumbnails">PDF Report (with thumbnails)</string>
<string name="about_button_creating_pdf_with_thumbnails">Creating PDF Report (with thumbnails)...</string>
```

Replace with:
```xml
<string name="about_button_export_pdf">Summary (Text Only)</string>
<string name="about_button_creating_pdf">Creating Summary...</string>
<string name="about_button_export_pdf_with_thumbnails">Summary (with Photos)</string>
<string name="about_button_creating_pdf_with_thumbnails">Creating Summary (with photos)...</string>
<string name="list_button_print_will">Will Record (with Photos)</string>
<string name="list_button_print_will_text_only">Will Record (Text Only)</string>
<string name="list_button_print_will_running">Creating Will Record...</string>
```

### Step 2: Add Help Text (Optional but Recommended)

Add to strings.xml:
```xml
<string name="export_help_text_summary">
    <b>Summary Report</b>\n
    • <b>Text Only:</b> Fast, small file\n
    • <b>With Photos:</b> Includes thumbnails
</string>
<string name="export_help_text_will">
    <b>Will Record</b>\n
    Shows items and where they should go.\n
    • Only includes items with will instructions\n
    • Shares your distribution wishes
</string>
```

---

## 2. ADD PROGRESS CALLBACK (⏱️ 3-4 Hours)

### Files to Modify
- `ValuePicsRepository.kt` (exportCollectionSummaryPdf)
- `ValuePicsViewModel.kt` (exportCollectionSummaryPdf)
- `ValuePicsApp.kt` (onExportPdfRequested)
- `ItemListScreen.kt` (add progress UI)
- `PdfReportBuilder.kt` (emit progress)

### Step 1: Create Progress Data Classes

Add to `ValuePicsRepository.kt` or new `PdfExportProgress.kt`:

```kotlin
data class ExportProgress(
    val current: Int,
    val total: Int,
    val phase: String,
    val percentage: Int = if (total > 0) (current * 100) / total else 0
)

sealed class ExportPhase(val label: String) {
    object Sorting : ExportPhase("Sorting items...")
    object BuildingLayout : ExportPhase("Building layout...")
    class Rendering(val page: Int, val total: Int) : ExportPhase("Rendering page $page of $total...")
    object Writing : ExportPhase("Writing to disk...")
}
```

### Step 2: Update Repository Signature

In `ValuePicsRepository.kt`, find the `exportCollectionSummaryPdf` function:

```kotlin
suspend fun exportCollectionSummaryPdf(
    items: List<ValuedItem>,
    scopeLabel: String,
    isFiltered: Boolean,
    reportSortOption: ReportSortOption = ReportSortOption.NAME_AZ,
    reportTitleOverride: String? = null,
    useWillInstructions: Boolean = false,
    includeThumbnails: Boolean = false,
    onProgress: (ExportProgress) -> Unit = { }  // ← ADD THIS
): PdfExportResult = withContext(Dispatchers.IO) {
```

### Step 3: Emit Progress in Repository

Add progress calls at key points:

```kotlin
suspend fun exportCollectionSummaryPdf(
    // ... params ...
    onProgress: (ExportProgress) -> Unit = { }
): PdfExportResult = withContext(Dispatchers.IO) {
    onProgress(ExportProgress(0, 1, ExportPhase.Sorting.label))
    val reportItems = items
        .filterNot { it.excludeFromPdfReport }
        .let { sortItemsForReport(it, reportSortOption) }
    
    onProgress(ExportProgress(1, 2, ExportPhase.BuildingLayout.label))
    PhotoUtils.ensureCollectionFolders(appContext)
    val normalized = reportItems.map { item ->
        item to item.collectionName.trim().ifBlank { "Uncategorized" }
    }
    val byCollection = normalized.groupBy({ it.second }, { it.first })
    val collectionTotals = buildCollectionTotals(byCollection)
    
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val fileName = "valuepics-summary-$stamp.pdf"
    
    onProgress(ExportProgress(1, 3, "Generating PDF..."))
    val bytes = PdfReportBuilder().buildPdf(
        items = reportItems,
        byCollection = byCollection,
        collectionTotals = collectionTotals,
        reportTitleOverride = reportTitleOverride,
        useWillInstructions = useWillInstructions,
        includeThumbnails = includeThumbnails,
        onProgress = { current, total, phase ->
            onProgress(ExportProgress(
                current = (current + 3) / 3,  // Normalize to 0-3 range
                total = 3,
                phase = phase
            ))
        }
    )
    
    onProgress(ExportProgress(2, 3, ExportPhase.Writing.label))
    // ... rest of function
    
    onProgress(ExportProgress(3, 3, "Complete"))
    return@withContext result
}
```

### Step 4: Update ViewModel

In `ValuePicsViewModel.kt`:

```kotlin
fun exportCollectionSummaryPdf(
    items: List<ValuedItem>,
    scopeLabel: String,
    isFiltered: Boolean,
    reportSortOption: ReportSortOption,
    reportTitleOverride: String? = null,
    useWillInstructions: Boolean = false,
    includeThumbnails: Boolean = false,
    onProgress: (ExportProgress) -> Unit = { },  // ← ADD THIS
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
                    onProgress = onProgress  // ← PASS IT THROUGH
                )
            }
        )
    }
}
```

### Step 5: Update PdfReportBuilder

In `PdfReportBuilder.kt`, add callback parameter:

```kotlin
internal class PdfReportBuilder {
    fun buildPdf(
        items: List<ValuedItem>,
        byCollection: Map<String, List<ValuedItem>>,
        collectionTotals: List<Pair<String, Double>>,
        reportTitleOverride: String? = null,
        useWillInstructions: Boolean = false,
        includeThumbnails: Boolean = false,
        onProgress: (current: Int, total: Int, phase: String) -> Unit = { _, _, _ -> }  // ← ADD
    ): ByteArray {
        val totalPages = estimatedPageCount(items, collectionTotals)
        var currentPage = 0
        
        // ... existing code ...
        
        // In drawSection, before drawing each section:
        onProgress(++currentPage, totalPages, "Rendering page $currentPage of $totalPages...")
        
        // ... rest of function
    }
    
    private fun estimatedPageCount(items: List<ValuedItem>, collections: List<Pair<String, Double>>): Int {
        // Rough estimate: ~30-40 items per page
        return maxOf(1, (items.size / 35) + 2)  // +2 for metadata/overview
    }
}
```

### Step 6: Update ValuePicsApp UI Handler

Replace the `onExportPdfRequested` lambda:

```kotlin
var exportProgress by remember { mutableStateOf<ExportProgress?>(null) }

onExportPdfRequested = { scopedItems, scopeLabel, hasFilters, reportSortOption, includeThumbnails ->
    isExportingPdf = true
    exportProgress = ExportProgress(0, 1, "Starting...")
    viewModel.exportCollectionSummaryPdf(
        items = scopedItems,
        scopeLabel = scopeLabel,
        isFiltered = hasFilters,
        reportSortOption = reportSortOption,
        includeThumbnails = includeThumbnails,
        onProgress = { progress ->
            exportProgress = progress  // ← UPDATE PROGRESS STATE
        }
    ) { result ->
        isExportingPdf = false
        exportProgress = null
        result.fold(
            onSuccess = { export -> printPdf(export) },
            onFailure = { uiErrorMessage = context.getString(R.string.pdf_error_export_failed) }
        )
    }
}
```

### Step 7: Add Progress UI in ItemListScreen

Add this composable before ItemListScreen composable:

```kotlin
@Composable
private fun ExportProgressDialog(
    progress: ExportProgress,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Generating Report...") },
        text = {
            Column(modifier = Modifier.padding(16.dp)) {
                LinearProgressIndicator(
                    progress = progress.percentage / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                )
                Text("${progress.percentage}% Complete", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                Text(progress.phase, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("${progress.current} of ${progress.total} steps", 
                    style = MaterialTheme.typography.labelSmall)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
        confirmButton = {}
    )
}
```

Then in ItemListScreen, show it when exporting:

```kotlin
// In the LazyColumn or main content:
if (exportProgress != null) {
    ExportProgressDialog(
        progress = exportProgress!!,
        onCancel = { /* TODO: Implement cancellation */ }
    )
}
```

---

## 3. FIX SILENT BITMAP FAILURES (⏱️ 2-3 Hours)

### Files to Modify
- `PdfReportBuilder.kt`
- Optionally: `logcat` or create `Log.kt` utility

### Step 1: Create Result Type

Add to `PdfReportBuilder.kt` or new `BitmapResult.kt`:

```kotlin
sealed class BitmapLoadResult {
    data class Success(val bitmap: Bitmap) : BitmapLoadResult()
    data class NotFound(val path: String) : BitmapLoadResult()
    data class CorruptedOrUnreadable(val path: String, val reason: String) : BitmapLoadResult()
    data class OutOfMemory(val path: String) : BitmapLoadResult()
}
```

### Step 2: Update decodeThumbnail

Replace the current method:

```kotlin
private fun decodeThumbnail(path: String, targetPx: Int): BitmapLoadResult {
    val file = File(path.trim())
    
    // Check file exists
    if (!file.exists()) {
        Log.w("PdfBuilder", "Thumbnail file not found: $path")
        return BitmapLoadResult.NotFound(path)
    }
    if (!file.isFile) {
        Log.w("PdfBuilder", "Thumbnail path is not a file: $path")
        return BitmapLoadResult.NotFound(path)
    }
    
    try {
        // Decode bounds
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w("PdfBuilder", "Thumbnail has invalid dimensions: ${bounds.outWidth}x${bounds.outHeight}")
            return BitmapLoadResult.CorruptedOrUnreadable(path, "Invalid dimensions")
        }
        
        // Calculate sample size
        var inSampleSize = 1
        val halfWidth = bounds.outWidth / 2
        val halfHeight = bounds.outHeight / 2
        while ((halfWidth / inSampleSize) >= targetPx && (halfHeight / inSampleSize) >= targetPx) {
            inSampleSize *= 2
        }
        
        // Decode with downsampling
        val options = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: return BitmapLoadResult.CorruptedOrUnreadable(path, "Failed to decode")
        
        Log.d("PdfBuilder", "Loaded thumbnail: $path (${bitmap.width}x${bitmap.height})")
        return BitmapLoadResult.Success(bitmap)
        
    } catch (e: OutOfMemoryError) {
        Log.e("PdfBuilder", "OOM loading thumbnail: $path", e)
        return BitmapLoadResult.OutOfMemory(path)
    } catch (e: Exception) {
        Log.e("PdfBuilder", "Error loading thumbnail: $path", e)
        return BitmapLoadResult.CorruptedOrUnreadable(path, e.message ?: "Unknown error")
    }
}
```

### Step 3: Update drawSection to Handle Results

Replace this section in `drawSection`:

```kotlin
// OLD:
if (includeThumbnails) {
    val imageTop = y - imageSize + 8f
    val imageRect = RectF(imageLeftX, imageTop, imageLeftX + imageSize, imageTop + imageSize)
    val bitmap = decodeThumbnail(item.photoPath, imageSize.toInt())
    if (bitmap != null) {
        draw { drawBitmap(bitmap, null, imageRect, null) }
        bitmap.recycle()
    }
    draw { drawRect(imageRect, imageBorderPaint) }
}
```

With this:

```kotlin
if (includeThumbnails) {
    val imageTop = y - imageSize + 8f
    val imageRect = RectF(imageLeftX, imageTop, imageLeftX + imageSize, imageTop + imageSize)
    
    when (val result = decodeThumbnail(item.photoPath, imageSize.toInt())) {
        is BitmapLoadResult.Success -> {
            draw { drawBitmap(result.bitmap, null, imageRect, null) }
            result.bitmap.recycle()
            draw { drawRect(imageRect, imageBorderPaint) }
        }
        is BitmapLoadResult.NotFound,
        is BitmapLoadResult.CorruptedOrUnreadable,
        is BitmapLoadResult.OutOfMemory -> {
            // Draw placeholder with error indicator
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFE0E0E0.toInt()  // Light gray
            }
            draw { drawRect(imageRect, bgPaint) }
            draw { drawRect(imageRect, imageBorderPaint) }
            
            // Draw "?" or "!" indicator
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 20f
                color = 0xFF999999.toInt()
                textAlign = Paint.Align.CENTER
            }
            val indicator = when (result) {
                is BitmapLoadResult.NotFound -> "?"
                is BitmapLoadResult.OutOfMemory -> "⚠"
                else -> "!"
            }
            draw { 
                drawText(
                    indicator,
                    imageRect.centerX(),
                    imageRect.centerY() + 6f,
                    textPaint
                )
            }
        }
    }
}
```

---

## 4. EXTRACT LAYOUT CONFIG (⏱️ 2-3 Hours)

### Files to Modify
- Create new: `PdfLayoutConfig.kt` or add to `PdfReportBuilder.kt`

### Step 1: Create Config Class

```kotlin
data class PdfLayoutConfig(
    val pageWidth: Int = 595,
    val pageHeight: Int = 842,
    val topMargin: Float = 48f,
    val bottomMargin: Float = 42f,
    val leftMargin: Float = 40f,
    val rightMargin: Float = 40f,
    val imageSize: Float = 44f,
    val imageSpacing: Float = 10f,
    val padding: Float = 16f,
    val lineHeight: Float = 16f,
    val sectionSpacing: Float = 24f,
    val includeThumbnails: Boolean = false
) {
    // Computed properties
    val contentWidth: Float 
        get() = pageWidth - leftMargin - rightMargin
    
    val imageLeftX: Float 
        get() = leftMargin
    
    val textLeftX: Float 
        get() = if (includeThumbnails) imageLeftX + imageSize + imageSpacing else leftMargin
    
    val descriptionX: Float 
        get() = if (includeThumbnails) leftMargin + 150f else leftMargin + 200f
    
    val valueRightX: Float 
        get() = pageWidth - rightMargin
    
    val bottomLimit: Float 
        get() = pageHeight - bottomMargin
    
    val minColumnWidth: Float = 80f
    
    fun validateLayout() {
        val textToDesc = descriptionX - textLeftX
        val descToValue = valueRightX - descriptionX - 60f
        
        require(textToDesc >= minColumnWidth) { 
            "Item column too narrow: $textToDesc < $minColumnWidth" 
        }
        require(descToValue >= minColumnWidth) { 
            "Description column too narrow: $descToValue < $minColumnWidth" 
        }
    }
}
```

### Step 2: Update PdfReportBuilder

Replace hardcoded values in `buildPdf`:

```kotlin
fun buildPdf(
    items: List<ValuedItem>,
    byCollection: Map<String, List<ValuedItem>>,
    collectionTotals: List<Pair<String, Double>>,
    reportTitleOverride: String? = null,
    useWillInstructions: Boolean = false,
    includeThumbnails: Boolean = false,
    onProgress: (current: Int, total: Int, phase: String) -> Unit = { _, _, _ -> }
): ByteArray {
    val layout = PdfLayoutConfig(includeThumbnails = includeThumbnails)
    layout.validateLayout()  // Crash early if config is wrong
    
    val document = PdfDocument()
    // ... rest stays same, but reference layout instead of hardcoded values
    
    // Instead of:
    // val totalsLeftX = 40f
    // val valueRightX = 555f
    // val imageLeftX = 40f
    
    // Use:
    val totalsLeftX = layout.leftMargin
    val valueRightX = layout.valueRightX
    val imageLeftX = layout.imageLeftX
    val imageSize = layout.imageSize
    val textLeftX = layout.textLeftX
    val descriptionX = layout.descriptionX
    val bottomLimit = layout.bottomLimit
    
    // ... rest of function
}
```

---

## 5. ADD CONFIRMATION DIALOG (⏱️ 2-3 Hours)

### Files to Modify
- `ItemListScreen.kt`
- Create new: `ExportConfirmationDialog.kt` or add composable to ItemListScreen

### Step 1: Create Confirmation Composable

```kotlin
@Composable
private fun ExportConfirmationDialog(
    exportType: String,
    itemCount: Int,
    estimatedSizeMb: Double,
    estimatedSeconds: Int,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Confirm Export") },
        text = {
            Column(modifier = Modifier.padding(vertical = 16.dp)) {
                Text(
                    "Export '$exportType'?",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Records:", fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                    Text("$itemCount items")
                }
                
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Est. size:", fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                    Text(String.format("%.1f MB", estimatedSizeMb))
                }
                
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Est. time:", fontWeight = FontWeight.Bold, modifier = Modifier.width(100.dp))
                    Text("~$estimatedSeconds seconds")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    "This may take a moment if photos are included.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Export") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    )
}

private fun estimateFileSize(itemCount: Int, includeThumbnails: Boolean): Double {
    val perItem = if (includeThumbnails) 65.0 else 18.0
    return (itemCount * perItem) / 1024.0
}

private fun estimateTime(itemCount: Int): Int {
    return ((itemCount * 50) / 1000).coerceAtLeast(1)
}
```

### Step 2: Add State to ItemListScreen

```kotlin
var showExportConfirmation by remember { mutableStateOf(false) }
var pendingExportConfig by remember { 
    mutableStateOf<ExportConfig?>(null) 
}

data class ExportConfig(
    val items: List<ValuedItem>,
    val scopeLabel: String,
    val hasActiveFilters: Boolean,
    val reportSortOption: ReportSortOption,
    val includeThumbnails: Boolean,
    val type: String
)
```

### Step 3: Show Dialog Before Export

In the export menu item onClick:

```kotlin
onClick = {
    showOverflowMenu = false
    // Don't export immediately, show confirmation
    val type = if (includeThumbnails) "Summary (with Photos)" else "Summary (Text Only)"
    pendingExportConfig = ExportConfig(
        items = sortedDisplayedItems,
        scopeLabel = exportScopeLabel,
        hasActiveFilters = hasActiveFilters,
        reportSortOption = reportSortOption,
        includeThumbnails = includeThumbnails,
        type = type
    )
    showExportConfirmation = true
}
```

### Step 4: Handle Confirmation

```kotlin
if (showExportConfirmation && pendingExportConfig != null) {
    val config = pendingExportConfig!!
    ExportConfirmationDialog(
        exportType = config.type,
        itemCount = config.items.size,
        estimatedSizeMb = estimateFileSize(config.items.size, config.includeThumbnails),
        estimatedSeconds = estimateTime(config.items.size),
        onCancel = {
            showExportConfirmation = false
            pendingExportConfig = null
        },
        onConfirm = {
            showExportConfirmation = false
            onExportPdfRequested(
                config.items,
                config.scopeLabel,
                config.hasActiveFilters,
                config.reportSortOption,
                config.includeThumbnails
            )
            pendingExportConfig = null
        }
    )
}
```

---

## 6. IMPLEMENTATION CHECKLIST

- [ ] Rename menu strings in `strings.xml`
- [ ] Update menu item labels in `ItemListScreen.kt`
- [ ] Create `ExportProgress` data class
- [ ] Add progress callback to `ValuePicsRepository.exportCollectionSummaryPdf()`
- [ ] Add progress callback to `ValuePicsViewModel.exportCollectionSummaryPdf()`
- [ ] Pass progress through in `ValuePicsApp.kt` export handler
- [ ] Add `ExportProgressDialog` composable to `ItemListScreen.kt`
- [ ] Create `BitmapLoadResult` sealed class
- [ ] Update `PdfReportBuilder.decodeThumbnail()` to use sealed class
- [ ] Update `PdfReportBuilder.drawSection()` to handle bitmap errors
- [ ] Create `PdfLayoutConfig` data class
- [ ] Replace hardcoded layout values in `PdfReportBuilder.buildPdf()`
- [ ] Add `ExportConfirmationDialog` composable
- [ ] Add state management for confirmation dialog
- [ ] Add estimation functions
- [ ] Wire up confirmation flow

---

## 7. TESTING STEPS

### Manual Testing
1. **Test Progress Display**
   - Export 100+ item summary
   - Verify progress bar updates smoothly
   - Verify "phase" messages change

2. **Test Bitmap Errors**
   - Delete a photo file from disk
   - Export with thumbnails
   - Verify placeholder appears instead of crash

3. **Test Layout Config**
   - Verify column widths are validated
   - Try different `includeThumbnails` values
   - Check PDF renders correctly

4. **Test Confirmation Dialog**
   - Click export → Dialog should appear
   - Verify numbers are calculated correctly
   - Click Cancel → No export
   - Click Confirm → Export proceeds

### Automated Testing (Optional)
```kotlin
@Test
fun exportProgress_emitsCorrectPercentages() {
    val progress = mutableListOf<ExportProgress>()
    
    // ... call export with onProgress = { progress.add(it) }
    
    assert(progress.first().percentage >= 0)
    assert(progress.last().percentage == 100)
    assert(progress.map { it.percentage }.isSorted())
}

@Test
fun thumbnailDecoding_handlesCorruptedFiles() {
    val result = decodeThumbnail("/nonexistent/file.jpg", 44)
    assert(result is BitmapLoadResult.NotFound)
}
```

---

## 8. DEPLOYMENT NOTES

1. **Backward Compatibility**: All changes are additive - old exports still work
2. **Database**: No schema changes, no migration needed
3. **Strings**: Old string keys removed, new ones added (handles gracefully)
4. **Performance**: Progress callback is lightweight (just updates UI state)
5. **Rollback**: Can disable progress by passing empty `onProgress = {}`

---

## 9. KNOWN LIMITATIONS & FUTURE WORK

### Currently Out of Scope
- Batch export (Phase 2+)
- PDF metadata headers (Phase 2+)
- Scheduled/recurring exports (Phase 3+)
- Streaming for 1000+ items (Phase 4+)

### Known Issues
- Very small bitmaps may not show placeholder "?" clearly
- Progress granularity depends on data size (coarse for small exports)
- Cancellation not implemented yet (use exception handling if needed)

---

## 10. SUCCESS CRITERIA

After implementation, verify:
- ✅ User can see progress during export
- ✅ Missing thumbnails show placeholders, not crashes
- ✅ Users get confirmation before 20+ second operations
- ✅ Column widths validated on startup
- ✅ Menu labels are clear and consistent
- ✅ Export completes successfully 100% of test runs

---


