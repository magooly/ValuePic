/*
 * Copyright (c) 2026 Wally Horsman.
 * All rights reserved.
 */

package com.example.valuefinder.ui

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.valuefinder.ExportProgress
import com.example.valuefinder.BILLS_COLLECTION_NAME
import com.example.valuefinder.BillsPeriod
import com.example.valuefinder.BuildConfig
import com.example.valuefinder.convertBillsAmount
import com.example.valuefinder.MoneyUtils
import com.example.valuefinder.PhotoUtils
import com.example.valuefinder.R
import com.example.valuefinder.ReportSortOption
import com.example.valuefinder.TagUtils
import com.example.valuefinder.UnlockResult
import com.example.valuefinder.ValuedItem
import com.example.valuefinder.resolveBillsEnteredPeriod
import com.example.valuefinder.isBillsCollectionName
import java.io.File
import java.util.Locale

enum class ListSortMode {
    NEWEST,
    OLDEST,
    VALUE_HIGH,
    VALUE_LOW,
    NAME_AZ,
    FIND_DUPLICATES
}

/**
 * Enhanced empty state message with clear guidance for different scenarios
 */
@Composable
private fun EmptyStateMessage(
    totalItems: Int,
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    onAddItem: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Inbox,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when {
                    totalItems == 0 -> stringResource(R.string.list_empty_title)
                    hasActiveFilters -> stringResource(R.string.list_filtered_empty_title)
                    else -> "No records match your search"
                },
                style = MaterialTheme.typography.headlineSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when {
                    totalItems == 0 -> stringResource(R.string.list_empty_message)
                    hasActiveFilters -> "Found $totalItems item${if (totalItems == 1) "" else "s"} in total, but none match your current filters"
                    else -> "Try adjusting your search or filters"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (hasActiveFilters) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onClearFilters) {
                    Text(stringResource(R.string.list_filtered_empty_return))
                }
            } else if (totalItems == 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onAddItem) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.list_start_new_valuation))
                }
            }
        }
    }
}

private sealed interface PendingReportAction {
    val items: List<ValuedItem>
    val scopeLabel: String
    val reportSortOption: ReportSortOption
    val includeThumbnails: Boolean
}

data class BatchExportOptions(
    val summaryTextOnly: Boolean = false,
    val summaryWithPhotos: Boolean = false,
    val willTextOnly: Boolean = false,
    val willWithPhotos: Boolean = false
) {
    fun hasAnySelected(): Boolean = summaryTextOnly || summaryWithPhotos || willTextOnly || willWithPhotos
}

private data class PendingSummaryExport(
    override val items: List<ValuedItem>,
    override val scopeLabel: String,
    override val reportSortOption: ReportSortOption,
    override val includeThumbnails: Boolean,
    val isFiltered: Boolean
) : PendingReportAction

private data class PendingWillPrint(
    override val items: List<ValuedItem>,
    override val scopeLabel: String,
    override val reportSortOption: ReportSortOption,
    override val includeThumbnails: Boolean
) : PendingReportAction

private fun eligibleRecordCount(action: PendingReportAction): Int = when (action) {
    is PendingSummaryExport -> action.items.count { !it.excludeFromPdfReport }
    is PendingWillPrint -> action.items.count { it.willInstructions.trim().isNotBlank() && !it.excludeFromPdfReport }
}

@Composable
private fun rememberExportWarnings(action: PendingReportAction): List<String> {
    val excludedPdfCount = action.items.count { it.excludeFromPdfReport }
    val missingPhotoCount = if (action.includeThumbnails) {
        action.items.count { it.photoPath.trim().isBlank() || !File(it.photoPath.trim()).exists() }
    } else {
        0
    }
    val missingWillCount = when (action) {
        is PendingWillPrint -> action.items.count { it.willInstructions.trim().isBlank() }
        else -> 0
    }
    val eligibleCount = eligibleRecordCount(action)

    return buildList {
        if (eligibleCount <= 0) add(stringResource(R.string.pdf_export_warning_no_eligible_records))
        if (excludedPdfCount > 0) add(stringResource(R.string.pdf_export_warning_pdf_excluded, excludedPdfCount))
        if (missingWillCount > 0) add(stringResource(R.string.pdf_export_warning_missing_will, missingWillCount))
        if (missingPhotoCount > 0) add(stringResource(R.string.pdf_export_warning_missing_photos, missingPhotoCount))
        if (action.includeThumbnails && eligibleCount >= 75) add(stringResource(R.string.pdf_export_warning_large_photo_export))
    }
}

private fun estimateExportSizeMb(itemCount: Int, includeThumbnails: Boolean): Double {
    val kbPerItem = if (includeThumbnails) 65.0 else 18.0
    return (itemCount * kbPerItem) / 1024.0
}

private fun estimateExportTimeSeconds(itemCount: Int): Int = ((itemCount * 50) / 1000).coerceAtLeast(1)

@Composable
private fun ExportHelperDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val helperDialogCd = stringResource(R.string.pdf_export_helper_dialog_cd)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.pdf_export_helper_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Text(
                stringResource(R.string.pdf_export_helper_message),
                modifier = Modifier.semantics { contentDescription = helperDialogCd }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.pdf_export_helper_action))
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
private fun ExportProgressDialog(progress: ExportProgress) {
    val progressLiveRegionCd = stringResource(R.string.pdf_export_progress_live_region_cd)
    val progressBarCd = stringResource(R.string.pdf_export_progress_bar_cd)
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.pdf_export_progress_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = progressLiveRegionCd
                        liveRegion = LiveRegionMode.Polite
                    }
            ) {
                LinearProgressIndicator(
                    progress = { (progress.percentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = progressBarCd
                        }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.pdf_export_progress_percent, progress.percentage))
                Spacer(modifier = Modifier.height(8.dp))
                Text(progress.phase, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.pdf_export_progress_step, progress.current.coerceAtLeast(0), progress.total.coerceAtLeast(0)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = {}, enabled = false) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun ExportConfirmationDialog(
    action: PendingReportAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val confirmDialogCd = stringResource(R.string.pdf_export_confirm_dialog_cd)
    val warnings = rememberExportWarnings(action)
    val formatLabel = when (action) {
        is PendingSummaryExport -> if (action.includeThumbnails) {
            stringResource(R.string.about_button_export_pdf_with_thumbnails)
        } else {
            stringResource(R.string.about_button_export_pdf)
        }
        is PendingWillPrint -> if (action.includeThumbnails) {
            stringResource(R.string.list_button_print_will)
        } else {
            stringResource(R.string.list_button_print_will_text_only)
        }
    }
    val recordCount = eligibleRecordCount(action)
    val estimatedSizeMb = estimateExportSizeMb(recordCount, action.includeThumbnails)
    val estimatedSeconds = estimateExportTimeSeconds(recordCount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.pdf_export_confirm_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = confirmDialogCd }
            ) {
                Text(stringResource(R.string.pdf_export_confirm_format, formatLabel))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.pdf_export_confirm_records, recordCount))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.pdf_export_confirm_estimated_size, estimatedSizeMb))
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.pdf_export_confirm_estimated_time, estimatedSeconds))
                if (warnings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.pdf_export_warning_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    warnings.forEach { warning ->
                        Text(
                            text = "• $warning",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(R.string.pdf_export_confirm_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = recordCount > 0) {
                Text(stringResource(R.string.pdf_export_confirm_action))
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
private fun BatchExportDialog(
    selection: BatchExportOptions,
    onSelectionChange: (BatchExportOptions) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val batchDialogCd = stringResource(R.string.pdf_batch_export_dialog_cd)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.pdf_batch_export_title),
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = batchDialogCd }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selection.summaryTextOnly,
                        onCheckedChange = { onSelectionChange(selection.copy(summaryTextOnly = it)) }
                    )
                    Text(stringResource(R.string.pdf_batch_export_summary_text))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selection.summaryWithPhotos,
                        onCheckedChange = { onSelectionChange(selection.copy(summaryWithPhotos = it)) }
                    )
                    Text(stringResource(R.string.pdf_batch_export_summary_photos))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selection.willTextOnly,
                        onCheckedChange = { onSelectionChange(selection.copy(willTextOnly = it)) }
                    )
                    Text(stringResource(R.string.pdf_batch_export_will_text))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selection.willWithPhotos,
                        onCheckedChange = { onSelectionChange(selection.copy(willWithPhotos = it)) }
                    )
                    Text(stringResource(R.string.pdf_batch_export_will_photos))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = selection.hasAnySelected()) {
                Text(stringResource(R.string.pdf_batch_export_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

private enum class SectionedMenuSection {
    EXPORT_REPORTS,
    BACKUP_TRANSFER,
    ORGANIZATION,
    APPEARANCE_HELP
}

private fun mapItemForBillsView(item: ValuedItem, selectedBillsView: BillsPeriod): ValuedItem {
    val enteredPeriod = resolveBillsEnteredPeriod(item.collectionName, item.billsEnteredPeriod) ?: return item
    val amount = item.estimatedValue ?: return item
    val converted = convertBillsAmount(amount = amount, from = enteredPeriod, to = selectedBillsView, roundUpToDollar = true)
    return item.copy(estimatedValue = converted)
}

@Composable
private fun SectionedMenuSelectableAction(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text)
            if (selected) Icon(Icons.Filled.Check, contentDescription = null)
        }
    }
}

@Composable
private fun SectionedMenuDialog(
    isExportingPdf: Boolean,
    hasWillRecordsInScope: Boolean,
    isBackupRestoreBusy: Boolean,
    isMergingDatabase: Boolean,
    isSharingDatabase: Boolean,
    isSelectionMode: Boolean,
    selectedCount: Int,
    lastBackupText: String,
    reportSortOption: ReportSortOption,
    themeMode: ThemeMode,
    onDismiss: () -> Unit,
    onUseClassicMenu: () -> Unit,
    onSelectSection: (SectionedMenuSection?) -> Unit,
    selectedSection: SectionedMenuSection?,
    onExportSummaryText: () -> Unit,
    onExportSummaryPhotos: () -> Unit,
    onPrintWillPhotos: () -> Unit,
    onPrintWillText: () -> Unit,
    onBatchExport: () -> Unit,
    onReportSortName: () -> Unit,
    onReportSortValue: () -> Unit,
    onExportRecords: () -> Unit,
    onExportSelectedRecords: () -> Unit,
    onQuickBackup: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onRestoreAuto: () -> Unit,
    onImportRecords: () -> Unit,
    onMergeDatabase: () -> Unit,
    onShareDatabase: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onDeleteSelected: () -> Unit,
    onSetWillOwner: () -> Unit,
    onManageCollections: () -> Unit,
    onManageTags: () -> Unit,
    onThemeLight: () -> Unit,
    onThemeDark: () -> Unit,
    onThemeSystem: () -> Unit,
    appTier: com.example.valuefinder.AppTier,
    onTierPersonal: () -> Unit,
    onTierInsurance: () -> Unit,
    onLockPersonalNow: () -> Unit,
    onHowTo: () -> Unit,
    onAbout: () -> Unit,
    isLoadingSampleData: Boolean,
    isRemovingSampleData: Boolean,
    onLoadSampleData: () -> Unit,
    onRemoveSampleData: () -> Unit
) {
    val title = when (selectedSection) {
        null -> "Menu sections"
        SectionedMenuSection.EXPORT_REPORTS -> "Export & reports"
        SectionedMenuSection.BACKUP_TRANSFER -> "Backup & transfer"
        SectionedMenuSection.ORGANIZATION -> "Organization"
        SectionedMenuSection.APPEARANCE_HELP -> "Appearance & help"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when (selectedSection) {
                    null -> {
                        TextButton(onClick = { onSelectSection(SectionedMenuSection.EXPORT_REPORTS) }, modifier = Modifier.fillMaxWidth()) { Text("Export & reports") }
                        TextButton(onClick = { onSelectSection(SectionedMenuSection.BACKUP_TRANSFER) }, modifier = Modifier.fillMaxWidth()) { Text("Backup & transfer") }
                        TextButton(onClick = { onSelectSection(SectionedMenuSection.ORGANIZATION) }, modifier = Modifier.fillMaxWidth()) { Text("Organization") }
                        TextButton(onClick = { onSelectSection(SectionedMenuSection.APPEARANCE_HELP) }, modifier = Modifier.fillMaxWidth()) { Text("Appearance & help") }
                        HorizontalDivider()
                        TextButton(onClick = onUseClassicMenu, modifier = Modifier.fillMaxWidth()) {
                            Text("Open classic menu (legacy)")
                        }
                        HorizontalDivider()
                        TextButton(
                            onClick = onLoadSampleData,
                            enabled = !isLoadingSampleData && !isRemovingSampleData,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isLoadingSampleData) "Loading samples..." else "Load 4 example records")
                        }
                        TextButton(
                            onClick = onRemoveSampleData,
                            enabled = !isLoadingSampleData && !isRemovingSampleData,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isRemovingSampleData) "Removing samples..." else "Remove example records")
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\u00A9 Wally Horsman 2026",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Row(
                                modifier = Modifier
                                    .clickable { onTierPersonal() }
                                    .padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = appTier == com.example.valuefinder.AppTier.PERSONAL,
                                    onClick = onTierPersonal
                                )
                                Text(
                                    text = "P",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Row(
                                modifier = Modifier
                                    .clickable { onTierInsurance() }
                                    .padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = appTier == com.example.valuefinder.AppTier.INSURANCE,
                                    onClick = onTierInsurance
                                )
                                Text(
                                    text = "I",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    SectionedMenuSection.EXPORT_REPORTS -> {
                        SectionedMenuSelectableAction(
                            text = stringResource(R.string.list_report_sort_name_az),
                            selected = reportSortOption == ReportSortOption.NAME_AZ,
                            onClick = onReportSortName
                        )
                        SectionedMenuSelectableAction(
                            text = stringResource(R.string.list_report_sort_value_high),
                            selected = reportSortOption == ReportSortOption.VALUE_HIGH,
                            onClick = onReportSortValue
                        )
                        TextButton(onClick = onExportSummaryText, enabled = !isExportingPdf, modifier = Modifier.fillMaxWidth()) { Text("Summary (Text Only)") }
                        TextButton(onClick = onExportSummaryPhotos, enabled = !isExportingPdf, modifier = Modifier.fillMaxWidth()) { Text("Summary (with Photos)") }
                        TextButton(onClick = onPrintWillPhotos, enabled = !isExportingPdf && hasWillRecordsInScope, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.list_button_print_will)) }
                        TextButton(onClick = onPrintWillText, enabled = !isExportingPdf && hasWillRecordsInScope, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.list_button_print_will_text_only)) }
                        TextButton(onClick = onBatchExport, enabled = !isExportingPdf, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pdf_batch_export_button)) }
                        TextButton(onClick = onExportRecords, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.list_button_export_records_zip)) }
                        TextButton(onClick = onExportSelectedRecords, enabled = isSelectionMode && selectedCount > 0, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.list_button_export_selected_records_zip)) }
                    }
                    SectionedMenuSection.BACKUP_TRANSFER -> {
                        Text(
                            text = stringResource(R.string.list_last_backup_value, lastBackupText),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        TextButton(onClick = onBackup, enabled = !isBackupRestoreBusy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.list_button_backup)) }
                        TextButton(onClick = onRestore, enabled = !isBackupRestoreBusy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.list_button_restore)) }
                        TextButton(onClick = onRestoreAuto, enabled = !isBackupRestoreBusy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.list_button_restore_auto_ab)) }
                        TextButton(onClick = onImportRecords, enabled = !isBackupRestoreBusy && !isMergingDatabase, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.list_button_import_records_zip)) }
                        TextButton(onClick = onMergeDatabase, enabled = !isBackupRestoreBusy && !isMergingDatabase, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.list_button_merge_database)) }
                        TextButton(onClick = onShareDatabase, enabled = !isSharingDatabase, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_button_share_database)) }
                    }
                    SectionedMenuSection.ORGANIZATION -> {
                        TextButton(onClick = onToggleSelectionMode, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (isSelectionMode) stringResource(R.string.list_selection_cancel)
                                else stringResource(R.string.list_selection_start)
                            )
                        }
                        TextButton(onClick = onDeleteSelected, enabled = isSelectionMode && selectedCount > 0, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.list_button_delete_selected_records)) }
                        TextButton(onClick = onSetWillOwner, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.list_set_will_owner_name)) }
                        TextButton(onClick = onManageCollections, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.collections_manage_title)) }
                        TextButton(onClick = onManageTags, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.tags_manage_title)) }
                    }
                    SectionedMenuSection.APPEARANCE_HELP -> {
                        SectionedMenuSelectableAction(
                            text = stringResource(R.string.list_theme_mode_light),
                            selected = themeMode == ThemeMode.LIGHT,
                            onClick = onThemeLight
                        )
                        SectionedMenuSelectableAction(
                            text = stringResource(R.string.list_theme_mode_dark),
                            selected = themeMode == ThemeMode.DARK,
                            onClick = onThemeDark
                        )
                        SectionedMenuSelectableAction(
                            text = stringResource(R.string.list_theme_mode_system),
                            selected = themeMode == ThemeMode.SYSTEM,
                            onClick = onThemeSystem
                        )
                        if (appTier == com.example.valuefinder.AppTier.PERSONAL) {
                            TextButton(onClick = onLockPersonalNow, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.list_lock_personal_now))
                            }
                        }
                        TextButton(onClick = onHowTo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_button_how_to)) }
                        TextButton(onClick = onAbout, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.about_title)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (selectedSection == null) onDismiss() else onSelectSection(null)
            }) {
                Text(if (selectedSection == null) stringResource(R.string.common_close) else "Back")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun ItemListScreen(
    items: List<ValuedItem>,
    allCollections: List<String>,
    allTags: List<String>,
    onAddItem: () -> Unit,
    onItemClick: (ValuedItem) -> Unit,
    onOpenSourceLink: (String) -> Unit,
    onBackupRequested: () -> Unit,
    onRestoreRequested: () -> Unit,
    onRestoreAutoBackupRequested: () -> Unit,
    onMergeDatabaseRequested: () -> Unit,
    onImportRecordsRequested: () -> Unit,
    onDeleteSelectedRequested: (Set<Int>) -> Unit,
    isBackupRestoreBusy: Boolean,
    isMergingDatabase: Boolean,
    onExportPdfRequested: (List<ValuedItem>, String, Boolean, ReportSortOption, Boolean) -> Unit,
    onPrintWillRequested: (List<ValuedItem>, String, ReportSortOption, Boolean) -> Unit,
    onBatchExportRequested: (List<ValuedItem>, String, Boolean, ReportSortOption, BatchExportOptions) -> Unit,
    onExportRecordsRequested: (List<ValuedItem>, String) -> Unit,
    isExportingPdf: Boolean,
    exportProgress: ExportProgress?,
    lastBackupText: String,
    onQuickBackupRequested: () -> Unit,
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
    onAddCollectionRequested: (String, (Result<Boolean>) -> Unit) -> Unit,
    onRenameCollectionRequested: (String, String, (Result<Int>) -> Unit) -> Unit,
    onDeleteCollectionRequested: (String, (Result<Int>) -> Unit) -> Unit,
    onCountItemsInCollectionRequested: (String, (Result<Int>) -> Unit) -> Unit,
    onAddTagRequested: (String, (Result<Boolean>) -> Unit) -> Unit,
    onRenameTagRequested: (String, String, (Result<Boolean>) -> Unit) -> Unit,
    onDeleteTagRequested: (String, (Result<Boolean>) -> Unit) -> Unit,
    themeMode: ThemeMode,
    onThemeModeSelected: (ThemeMode) -> Unit,
    appTier: com.example.valuefinder.AppTier = com.example.valuefinder.AppTier.PERSONAL,
    onAppTierSelected: (com.example.valuefinder.AppTier) -> Unit = {},
    onLockPersonalNowRequested: () -> Unit = {},
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
    onRestoreUnlimited: (String, String, (Result<Boolean>) -> Unit) -> Unit
    ,
    showTotalValueBand: Boolean,
    onToggleTotalValueBand: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val isCompactListChrome = configuration.screenHeightDp <= 700 || configuration.screenWidthDp <= 360
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showCollectionMenu by remember { mutableStateOf(false) }
    var showTagMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showSearchControls by remember { mutableStateOf(false) }
    val sectionedMenuEnabled = remember { true }
    var preferSectionedMenu by rememberSaveable { mutableStateOf(true) }
    var showSectionedMenuPreview by remember { mutableStateOf(false) }
    var activeSectionedMenuSection by remember { mutableStateOf<SectionedMenuSection?>(null) }
    var showManageCollectionsDialog by remember { mutableStateOf(false) }
    var showManageTagsDialog by remember { mutableStateOf(false) }
    var showWillOwnerDialog by remember { mutableStateOf(false) }
    var pendingWillOwnerName by rememberSaveable { mutableStateOf("") }
    var isLoadingSampleData by remember { mutableStateOf(false) }
    var isRemovingSampleData by remember { mutableStateOf(false) }
    var showAboutUnlockDialog by remember { mutableStateOf(false) }
    var unlockPasswordInput by remember { mutableStateOf("") }
    var unlockAccountInput by remember { mutableStateOf(unlockAccountId) }
    var restoreAccountInput by remember { mutableStateOf(unlockAccountId) }
    var restoreCodeInput by remember { mutableStateOf("") }
    var unlockErrorText by remember { mutableStateOf("") }
    var unlockInfoText by remember { mutableStateOf("") }
    var isUnlockBusy by remember { mutableStateOf(false) }
    var pendingReportAction by remember { mutableStateOf<PendingReportAction?>(null) }
    var pendingAfterHelper by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showExportHelperDialog by remember { mutableStateOf(false) }
    var showBatchExportDialog by remember { mutableStateOf(false) }
    var batchExportOptions by remember { mutableStateOf(BatchExportOptions()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf(setOf<Int>()) }
    var showDeleteSelectedConfirm by remember { mutableStateOf(false) }
    var sortMode by remember { mutableStateOf(ListSortMode.NEWEST) }
    var reportSortOption by rememberSaveable { mutableStateOf(ReportSortOption.NAME_AZ) }
    var systemInfoBandHeightPx by remember { mutableIntStateOf(0) }
    var includeAllRecordsInHomeTotal by rememberSaveable { mutableStateOf(false) }
    val allCollectionsLabel = stringResource(R.string.list_all_collections)
    val allTagsLabel = stringResource(R.string.list_all_tags)
    val majorItemsCollectionName = stringResource(R.string.major_items_collection_name)
    val legacyExcludedCollectionName = "Not Included In Totals"
    var selectedCollection by remember { mutableStateOf(allCollectionsLabel) }
    var selectedBillsView by rememberSaveable { mutableStateOf(BillsPeriod.MONTHLY.storageValue) }
    var selectedTag by remember { mutableStateOf(allTagsLabel) }
    var searchQuery by remember { mutableStateOf("") }
    val isGlobalSearchActive = searchQuery.trim().isNotBlank()
    val hasActiveFilters = searchQuery.isNotBlank() ||
        selectedCollection != allCollectionsLabel ||
        selectedTag != allTagsLabel
    val reportSortLabel = when (reportSortOption) {
        ReportSortOption.NAME_AZ -> stringResource(R.string.list_report_sort_name_az)
        ReportSortOption.VALUE_HIGH -> stringResource(R.string.list_report_sort_value_high)
    }
    val selectedBillsPeriod = BillsPeriod.fromStorageValue(selectedBillsView) ?: BillsPeriod.MONTHLY
    val isBillsCollectionActive = appTier == com.example.valuefinder.AppTier.PERSONAL && isBillsCollectionName(selectedCollection)
    val compactVersionText = remember(appVersionText) {
        val normalized = Regex("""\d+(?:\.\d+)+""").find(appVersionText)?.value ?: appVersionText.trim()
        normalized.ifBlank { appVersionText.trim() }
    }
    val exportScopeLabel = buildString {
        if (selectedCollection != allCollectionsLabel) append("Collection: $selectedCollection")
        if (isBillsCollectionActive) {
            if (isNotEmpty()) append(" | ")
            append(
                when (selectedBillsPeriod) {
                    BillsPeriod.WEEKLY -> "View: Bill: Weekly"
                    BillsPeriod.MONTHLY -> "View: Bill: Monthly"
                    BillsPeriod.YEARLY -> "View: Bill: Yearly"
                }
            )
        }
        if (selectedTag != allTagsLabel) {
            if (isNotEmpty()) append(" | ")
            append("Tag: $selectedTag")
        }
        val query = searchQuery.trim()
        if (query.isNotBlank()) {
            if (isNotEmpty()) append(" | ")
            append("Search: $query")
        }
        if (isEmpty()) append("All records")
        if (isNotEmpty()) append(" | ")
        append("Report sort: $reportSortLabel")
    }

    fun isExcludedFromTotalsBucket(item: ValuedItem): Boolean {
        return !item.includeInTotals || item.collectionName.equals(legacyExcludedCollectionName, ignoreCase = true)
    }

    val collectionOptions = remember(allCollections, majorItemsCollectionName, appTier) {
        listOf(allCollectionsLabel, majorItemsCollectionName) + allCollections
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot {
                it.equals(majorItemsCollectionName, ignoreCase = true) ||
                    it.equals(legacyExcludedCollectionName, ignoreCase = true)
            }
            .let { source -> if (appTier == com.example.valuefinder.AppTier.PERSONAL) source else source.filterNot { isBillsCollectionName(it) } }
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .sortedBy { it.lowercase(Locale.getDefault()) }
    }
    LaunchedEffect(collectionOptions) {
        Log.i(
            "ValuePicsList",
            "LIST options count=${collectionOptions.size} options=${collectionOptions.joinToString()}"
        )
        if (selectedCollection != allCollectionsLabel && collectionOptions.none { it.equals(selectedCollection, ignoreCase = true) }) {
            selectedCollection = allCollectionsLabel
        }
    }


    val tagOptions = remember(allTags, allTagsLabel) {
        listOf(allTagsLabel) + allTags
    }

    val selectedCollectionItems = remember(items, selectedCollection, majorItemsCollectionName) {
        if (selectedCollection == allCollectionsLabel) {
            items
        } else if (selectedCollection.equals(majorItemsCollectionName, ignoreCase = true)) {
            items.filter { isExcludedFromTotalsBucket(it) }
        } else {
            // Regular collection tabs include items assigned to that collection,
            // even when they are excluded from the global totals roll-up.
            items.filter { it.collectionName.equals(selectedCollection, ignoreCase = true) }
        }
    }

    val displayedItems = remember(items, searchQuery, selectedCollection) {
        val query = searchQuery.trim().lowercase(Locale.getDefault())
        if (query.isNotBlank()) {
            // Search is global by requirement: scan items across all collections.
            items.filter { item ->
                listOf(
                    item.itemName,
                    item.itemDescription,
                    item.collectionName,
                    item.tags,
                    item.shortAiDescription,
                    item.fullWebDescription,
                    item.detectedLabels,
                    item.searchResults,
                    item.valueSource,
                    item.sourceUrl,
                    item.notes
                ).any { field -> field.lowercase(Locale.getDefault()).contains(query) }
            }
        } else {
            selectedCollectionItems
        }
    }.filter { item ->
        selectedTag == allTagsLabel || TagUtils.hasTag(item.tags, selectedTag)
    }

    val billsViewAdjustedItems = remember(displayedItems, isBillsCollectionActive, selectedBillsPeriod) {
        if (!isBillsCollectionActive) {
            displayedItems
        } else {
            displayedItems.map { mapItemForBillsView(it, selectedBillsPeriod) }
        }
    }

    val sortedDisplayedItems = remember(billsViewAdjustedItems, sortMode, duplicateGroupOrderByItemId) {
        when (sortMode) {
            ListSortMode.NEWEST -> billsViewAdjustedItems.sortedByDescending { it.dateValued }
            ListSortMode.OLDEST -> billsViewAdjustedItems.sortedBy { it.dateValued }
            ListSortMode.VALUE_HIGH -> billsViewAdjustedItems.sortedWith(
                compareByDescending<ValuedItem> { it.estimatedValue ?: Double.MIN_VALUE }
                    .thenByDescending { it.dateValued }
            )
            ListSortMode.VALUE_LOW -> billsViewAdjustedItems.sortedWith(
                compareBy<ValuedItem> { it.estimatedValue ?: Double.MAX_VALUE }
                    .thenByDescending { it.dateValued }
            )
            ListSortMode.NAME_AZ -> billsViewAdjustedItems.sortedBy { it.itemName.lowercase(Locale.getDefault()) }
            ListSortMode.FIND_DUPLICATES -> billsViewAdjustedItems.sortedWith(
                compareBy<ValuedItem> { duplicateGroupOrderByItemId[it.id] ?: Int.MAX_VALUE }
                    .thenBy { it.itemName.lowercase(Locale.getDefault()) }
                    .thenByDescending { it.dateValued }
            )
        }
    }
    val duplicateCountByItemId = remember(duplicateGroupOrderByItemId) {
        val groupSizes = duplicateGroupOrderByItemId.values.groupingBy { it }.eachCount()
        duplicateGroupOrderByItemId.mapValues { (_, groupOrder) -> groupSizes[groupOrder] ?: 1 }
    }
    val isDuplicateSortActive = sortMode == ListSortMode.FIND_DUPLICATES
    val selectedCount = selectedItemIds.size
    val hasWillRecordsInScope = remember(sortedDisplayedItems) {
        sortedDisplayedItems.any { it.willInstructions.trim().isNotBlank() }
    }

    fun triggerLoadSampleData() {
        isLoadingSampleData = true
        onLoadSampleDataRequested { result ->
            isLoadingSampleData = false
            result.onFailure {
                Log.e("ItemListScreen", "Failed to load sample data", it)
            }
        }
    }

    fun triggerRemoveSampleData() {
        isRemovingSampleData = true
        onRemoveSampleDataRequested { result ->
            isRemovingSampleData = false
            result.onFailure {
                Log.e("ItemListScreen", "Failed to remove sample data", it)
            }
        }
    }

    val totalModeItems = remember(sortedDisplayedItems, hasActiveFilters, includeAllRecordsInHomeTotal) {
        sortedDisplayedItems.filter {
            // Unfiltered home view keeps global includeInTotals logic.
            // Any active filter (collection/tag/search) shows subset totals.
            hasActiveFilters || includeAllRecordsInHomeTotal || it.includeInTotals
        }
    }
    val displayedValuedItems = remember(totalModeItems) {
        totalModeItems.mapNotNull { it.estimatedValue }
    }
    val displayedTotalValue = remember(displayedValuedItems) {
        displayedValuedItems.sum()
    }
    val billsTotalSuffix = if (isBillsCollectionActive) {
        when (selectedBillsPeriod) {
            BillsPeriod.WEEKLY -> stringResource(R.string.bills_total_suffix_per_week)
            BillsPeriod.MONTHLY -> stringResource(R.string.bills_total_suffix_per_month)
            BillsPeriod.YEARLY -> stringResource(R.string.bills_total_suffix_per_year)
        }
    } else {
        ""
    }
    val displayedTotalValueText = remember(displayedTotalValue, billsTotalSuffix) {
        MoneyUtils.formatAud(displayedTotalValue) + billsTotalSuffix
    }
    val totalModeLabel = when {
        hasActiveFilters -> stringResource(R.string.list_total_mode_filtered_subset)
        includeAllRecordsInHomeTotal -> stringResource(R.string.list_total_mode_all_records)
        else -> stringResource(R.string.list_total_mode_excluding_major_items)
    }

    BackHandler(enabled = showSearchControls || hasActiveFilters || isSelectionMode) {
        if (isSelectionMode) {
            isSelectionMode = false
            selectedItemIds = emptySet()
            return@BackHandler
        }
        if (searchQuery.isNotBlank()) searchQuery = ""
        if (selectedCollection != allCollectionsLabel) selectedCollection = allCollectionsLabel
        if (selectedTag != allTagsLabel) selectedTag = allTagsLabel
        if (showSearchControls) showSearchControls = false
        focusManager.clearFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(
                            text = stringResource(R.string.list_selected_count, selectedCount),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                text = stringResource(R.string.list_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (compactVersionText.isNotBlank()) {
                                Text(
                                    text = compactVersionText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (appEditionLabel.isNotBlank()) {
                                Text(
                                    text = appEditionLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        TextButton(
                            onClick = {
                                isSelectionMode = false
                                selectedItemIds = emptySet()
                            }
                        ) {
                            Text(stringResource(R.string.list_selection_done))
                        }
                        IconButton(
                            onClick = {
                                isSelectionMode = false
                                selectedItemIds = emptySet()
                            }
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.list_selection_cancel)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            showSearchControls = !showSearchControls
                            if (!showSearchControls) {
                                focusManager.clearFocus()
                            }
                        }
                    ) {
                        Icon(
                            if (showSearchControls) Icons.Filled.ExpandLess else Icons.Filled.Search,
                            contentDescription = if (showSearchControls) {
                                stringResource(R.string.list_cd_hide_search)
                            } else {
                                stringResource(R.string.list_cd_show_search)
                            }
                        )
                    }
                    IconButton(onClick = onToggleTotalValueBand) {
                        Icon(
                            if (showTotalValueBand) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (showTotalValueBand) {
                                stringResource(R.string.list_cd_hide_total_value)
                            } else {
                                stringResource(R.string.list_cd_show_total_value)
                            }
                        )
                    }
                    Box {
                        IconButton(
                            onClick = {
                                if (sectionedMenuEnabled && preferSectionedMenu) {
                                    showOverflowMenu = false
                                    activeSectionedMenuSection = null
                                    showSectionedMenuPreview = true
                                } else {
                                    showOverflowMenu = true
                                }
                            }
                        ) {
                            Icon(Icons.Filled.MoreVert, stringResource(R.string.list_cd_more_actions))
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            modifier = Modifier.navigationBarsPadding()
                        ) {
                            if (sectionedMenuEnabled && !preferSectionedMenu) {
                                DropdownMenuItem(
                                    text = { Text("Sectioned menu") },
                                    onClick = {
                                        preferSectionedMenu = true
                                        showOverflowMenu = false
                                        activeSectionedMenuSection = null
                                        showSectionedMenuPreview = true
                                    }
                                )
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_menu_section_tools)) },
                                onClick = {},
                                enabled = false
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_last_backup_value, lastBackupText)) },
                                onClick = {},
                                enabled = false
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isSelectionMode) stringResource(R.string.list_selection_cancel)
                                        else stringResource(R.string.list_selection_start)
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    isSelectionMode = !isSelectionMode
                                    if (!isSelectionMode) selectedItemIds = emptySet()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (showTotalValueBand) {
                                            stringResource(R.string.list_cd_hide_total_value)
                                        } else {
                                            stringResource(R.string.list_cd_show_total_value)
                                        }
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onToggleTotalValueBand()
                                },
                                trailingIcon = {
                                    Icon(
                                        if (showTotalValueBand) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about_title)) },
                                onClick = {
                                    showOverflowMenu = false
                                    onAboutRequested()
                                    showAboutDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about_button_how_to)) },
                                onClick = {
                                    showOverflowMenu = false
                                    onHowToRequested()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_menu_section_report)) },
                                onClick = {},
                                enabled = false
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_report_sort_name_az)) },
                                onClick = {
                                    reportSortOption = ReportSortOption.NAME_AZ
                                    showOverflowMenu = false
                                },
                                trailingIcon = {
                                    if (reportSortOption == ReportSortOption.NAME_AZ) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_report_sort_value_high)) },
                                onClick = {
                                    reportSortOption = ReportSortOption.VALUE_HIGH
                                    showOverflowMenu = false
                                },
                                trailingIcon = {
                                    if (reportSortOption == ReportSortOption.VALUE_HIGH) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_set_will_owner_name)) },
                                onClick = {
                                    showOverflowMenu = false
                                    pendingWillOwnerName = willOwnerName
                                    showWillOwnerDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.collections_manage_title)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showManageCollectionsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.tags_manage_title)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showManageTagsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_theme_mode_light)) },
                                onClick = {
                                    onThemeModeSelected(ThemeMode.LIGHT)
                                    showOverflowMenu = false
                                },
                                trailingIcon = {
                                    if (themeMode == ThemeMode.LIGHT) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_theme_mode_dark)) },
                                onClick = {
                                    onThemeModeSelected(ThemeMode.DARK)
                                    showOverflowMenu = false
                                },
                                trailingIcon = {
                                    if (themeMode == ThemeMode.DARK) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_theme_mode_system)) },
                                onClick = {
                                    onThemeModeSelected(ThemeMode.SYSTEM)
                                    showOverflowMenu = false
                                },
                                trailingIcon = {
                                    if (themeMode == ThemeMode.SYSTEM) {
                                        Icon(Icons.Filled.Check, contentDescription = null)
                                    }
                                }
                            )
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "\u00A9 Wally Horsman 2026",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            onAppTierSelected(com.example.valuefinder.AppTier.PERSONAL)
                                            showOverflowMenu = false
                                        }
                                        .padding(horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = appTier == com.example.valuefinder.AppTier.PERSONAL,
                                        onClick = {
                                            onAppTierSelected(com.example.valuefinder.AppTier.PERSONAL)
                                            showOverflowMenu = false
                                        }
                                    )
                                    Text(
                                        text = "P",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Row(
                                    modifier = Modifier
                                        .clickable {
                                            onAppTierSelected(com.example.valuefinder.AppTier.INSURANCE)
                                            showOverflowMenu = false
                                        }
                                        .padding(horizontal = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = appTier == com.example.valuefinder.AppTier.INSURANCE,
                                        onClick = {
                                            onAppTierSelected(com.example.valuefinder.AppTier.INSURANCE)
                                            showOverflowMenu = false
                                        }
                                    )
                                    Text(
                                        text = "I",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (appTier == com.example.valuefinder.AppTier.PERSONAL) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.list_lock_personal_now)) },
                                    onClick = {
                                        onLockPersonalNowRequested()
                                        showOverflowMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isExportingPdf) {
                                            if (hasActiveFilters) {
                                                stringResource(R.string.about_button_creating_pdf_filtered)
                                            } else {
                                                stringResource(R.string.about_button_creating_pdf)
                                            }
                                        } else {
                                            if (hasActiveFilters) {
                                                stringResource(R.string.about_button_export_pdf_filtered)
                                            } else {
                                                stringResource(R.string.about_button_export_pdf)
                                            }
                                        }
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    pendingReportAction = PendingSummaryExport(
                                        items = sortedDisplayedItems,
                                        scopeLabel = exportScopeLabel,
                                        reportSortOption = reportSortOption,
                                        includeThumbnails = false,
                                        isFiltered = hasActiveFilters
                                    )
                                },
                                enabled = !isExportingPdf
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isExportingPdf) {
                                            stringResource(R.string.about_button_creating_pdf_with_thumbnails)
                                        } else {
                                            stringResource(R.string.about_button_export_pdf_with_thumbnails)
                                        }
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    pendingReportAction = PendingSummaryExport(
                                        items = sortedDisplayedItems,
                                        scopeLabel = exportScopeLabel,
                                        reportSortOption = reportSortOption,
                                        includeThumbnails = true,
                                        isFiltered = hasActiveFilters
                                    )
                                },
                                enabled = !isExportingPdf
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isExportingPdf) {
                                            stringResource(R.string.list_button_print_will_running)
                                        } else {
                                            stringResource(R.string.list_button_print_will)
                                        }
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    pendingReportAction = PendingWillPrint(
                                        items = sortedDisplayedItems,
                                        scopeLabel = exportScopeLabel,
                                        reportSortOption = reportSortOption,
                                        includeThumbnails = true
                                    )
                                },
                                enabled = !isExportingPdf && hasWillRecordsInScope
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isExportingPdf) {
                                            stringResource(R.string.list_button_print_will_running)
                                        } else {
                                            stringResource(R.string.list_button_print_will_text_only)
                                        }
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    pendingReportAction = PendingWillPrint(
                                        items = sortedDisplayedItems,
                                        scopeLabel = exportScopeLabel,
                                        reportSortOption = reportSortOption,
                                        includeThumbnails = false
                                    )
                                },
                                enabled = !isExportingPdf && hasWillRecordsInScope
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isExportingPdf) {
                                            stringResource(R.string.pdf_batch_export_running)
                                        } else {
                                            stringResource(R.string.pdf_batch_export_button)
                                        }
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    batchExportOptions = BatchExportOptions()
                                    showBatchExportDialog = true
                                },
                                enabled = !isExportingPdf
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_menu_section_transfer)) },
                                onClick = {},
                                enabled = false
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_button_backup)) },
                                onClick = {
                                    showOverflowMenu = false
                                    onBackupRequested()
                                },
                                enabled = !isBackupRestoreBusy
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_button_restore)) },
                                onClick = {
                                    showOverflowMenu = false
                                    onRestoreRequested()
                                },
                                enabled = !isBackupRestoreBusy
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_button_restore_auto_ab)) },
                                onClick = {
                                    showOverflowMenu = false
                                    onRestoreAutoBackupRequested()
                                },
                                enabled = !isBackupRestoreBusy
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.list_button_export_records_zip))
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onExportRecordsRequested(sortedDisplayedItems, exportScopeLabel)
                                },
                                enabled = sortedDisplayedItems.isNotEmpty()
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_button_export_selected_records_zip)) },
                                onClick = {
                                    showOverflowMenu = false
                                    val selectedItems = sortedDisplayedItems.filter { it.id in selectedItemIds }
                                    onExportRecordsRequested(selectedItems, "Selected records")
                                },
                                enabled = isSelectionMode && selectedCount > 0
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.list_button_delete_selected_records)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showDeleteSelectedConfirm = true
                                },
                                enabled = isSelectionMode && selectedCount > 0
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.list_button_import_records_zip))
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onImportRecordsRequested()
                                },
                                enabled = !isBackupRestoreBusy && !isMergingDatabase
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isMergingDatabase) {
                                            stringResource(R.string.list_button_merge_database_running)
                                        } else {
                                            stringResource(R.string.list_button_merge_database)
                                        }
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onMergeDatabaseRequested()
                                },
                                enabled = !isBackupRestoreBusy && !isMergingDatabase
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isSharingDatabase) {
                                            stringResource(R.string.about_button_preparing_database)
                                        } else {
                                            stringResource(R.string.about_button_share_database)
                                        }
                                    )
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    onShareDatabaseRequested()
                                },
                                enabled = !isSharingDatabase
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(if (isLoadingSampleData) "Loading samples..." else "Load 4 example records")
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    triggerLoadSampleData()
                                },
                                enabled = !isLoadingSampleData && !isRemovingSampleData
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(if (isRemovingSampleData) "Removing samples..." else "Remove example records")
                                },
                                onClick = {
                                    showOverflowMenu = false
                                    triggerRemoveSampleData()
                                },
                                enabled = !isLoadingSampleData && !isRemovingSampleData
                            )
                            HorizontalDivider()
                            Text(
                                text = "\u00A9 Wally Horsman 2026",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Filled.Add, stringResource(R.string.list_add_item))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {

            if (showSearchControls) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = {
                        Text("Keyword")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() }
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.list_cd_clear_search))
                                }
                            }
                            Box {
                                IconButton(onClick = { showCollectionMenu = true }) {
                                    Icon(Icons.Filled.FilterList, contentDescription = stringResource(R.string.list_cd_choose_collection))
                                }
                                DropdownMenu(
                                    expanded = showCollectionMenu,
                                    onDismissRequest = { showCollectionMenu = false },
                                    modifier = Modifier.navigationBarsPadding()
                                ) {
                                    collectionOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                selectedCollection = option
                                                showCollectionMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            Box {
                                IconButton(onClick = { showTagMenu = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Label, contentDescription = stringResource(R.string.list_cd_choose_tag))
                                }
                                DropdownMenu(
                                    expanded = showTagMenu,
                                    onDismissRequest = { showTagMenu = false },
                                    modifier = Modifier.navigationBarsPadding()
                                ) {
                                    tagOptions.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option) },
                                            onClick = {
                                                selectedTag = option
                                                showTagMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.list_cd_sort_items))
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false },
                                    modifier = Modifier.navigationBarsPadding()
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.list_sort_newest)) },
                                        onClick = { sortMode = ListSortMode.NEWEST; showSortMenu = false },
                                        trailingIcon = { if (sortMode == ListSortMode.NEWEST) Icon(Icons.Filled.Check, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.list_sort_oldest)) },
                                        onClick = { sortMode = ListSortMode.OLDEST; showSortMenu = false },
                                        trailingIcon = { if (sortMode == ListSortMode.OLDEST) Icon(Icons.Filled.Check, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.list_sort_value_high)) },
                                        onClick = { sortMode = ListSortMode.VALUE_HIGH; showSortMenu = false },
                                        trailingIcon = { if (sortMode == ListSortMode.VALUE_HIGH) Icon(Icons.Filled.Check, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.list_sort_value_low)) },
                                        onClick = { sortMode = ListSortMode.VALUE_LOW; showSortMenu = false },
                                        trailingIcon = { if (sortMode == ListSortMode.VALUE_LOW) Icon(Icons.Filled.Check, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.list_sort_name_az)) },
                                        onClick = { sortMode = ListSortMode.NAME_AZ; showSortMenu = false },
                                        trailingIcon = { if (sortMode == ListSortMode.NAME_AZ) Icon(Icons.Filled.Check, contentDescription = null) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.list_sort_find_duplicates)) },
                                        onClick = { sortMode = ListSortMode.FIND_DUPLICATES; showSortMenu = false },
                                        trailingIcon = { if (sortMode == ListSortMode.FIND_DUPLICATES) Icon(Icons.Filled.Check, contentDescription = null) }
                                    )
                                }
                            }
                            if (hasActiveFilters) {
                                IconButton(
                                    onClick = {
                                        searchQuery = ""
                                        selectedCollection = allCollectionsLabel
                                        selectedTag = allTagsLabel
                                        focusManager.clearFocus()
                                    }
                                ) {
                                    Icon(Icons.Filled.RestartAlt, contentDescription = stringResource(R.string.list_cd_clear_filters))
                                }
                            }
                        }
                    }
                )
                Text(
                    text = if (isCompactListChrome) {
                        stringResource(R.string.list_filters_value, selectedCollection, selectedTag) + " | " + reportSortLabel
                    } else {
                        stringResource(R.string.list_filters_value, selectedCollection, selectedTag)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (!isCompactListChrome) {
                    Text(
                        text = reportSortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
                if (isBillsCollectionActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedBillsPeriod == BillsPeriod.WEEKLY,
                            onClick = { selectedBillsView = BillsPeriod.WEEKLY.storageValue },
                            label = { Text(stringResource(R.string.bills_view_weekly_tab)) }
                        )
                        FilterChip(
                            selected = selectedBillsPeriod == BillsPeriod.MONTHLY,
                            onClick = { selectedBillsView = BillsPeriod.MONTHLY.storageValue },
                            label = { Text(stringResource(R.string.bills_view_monthly_tab)) }
                        )
                        FilterChip(
                            selected = selectedBillsPeriod == BillsPeriod.YEARLY,
                            onClick = { selectedBillsView = BillsPeriod.YEARLY.storageValue },
                            label = { Text(stringResource(R.string.bills_view_yearly_tab)) }
                        )
                    }
                }
                if (isGlobalSearchActive && !isCompactListChrome) {
                    Text(
                        text = stringResource(R.string.list_search_global_active),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
            }

            if (isSelectionMode && !hasSeenSelectionHelper) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.list_selection_helper_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.list_selection_helper_message),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onSelectionHelperSeen) {
                            Text(stringResource(R.string.list_selection_helper_dismiss))
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                val topInset = if (showTotalValueBand) {
                    with(density) { systemInfoBandHeightPx.toDp() } + if (isCompactListChrome) 6.dp else 12.dp
                } else {
                    12.dp
                }
                if (sortedDisplayedItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topInset)
                    ) {
                        EmptyStateMessage(
                            totalItems = items.size,
                            hasActiveFilters = hasActiveFilters,
                            onClearFilters = {
                                searchQuery = ""
                                selectedCollection = allCollectionsLabel
                                selectedTag = allTagsLabel
                                showSearchControls = false
                            },
                            onAddItem = onAddItem
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(top = topInset, bottom = 96.dp)
                    ) {
                        items(sortedDisplayedItems, key = { it.id }) { item ->
                            ItemCard(
                                item = item,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedItemIds.contains(item.id),
                                onToggleSelection = {
                                    selectedItemIds = selectedItemIds.toMutableSet().apply {
                                        if (!add(item.id)) remove(item.id)
                                    }
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedItemIds = selectedItemIds.toMutableSet().apply {
                                            if (!add(item.id)) remove(item.id)
                                        }
                                    } else {
                                        onItemClick(item)
                                    }
                                },
                                onLongPress = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedItemIds = setOf(item.id)
                                    }
                                },
                                showDuplicateBadge = isDuplicateSortActive,
                                duplicateCount = duplicateCountByItemId[item.id] ?: 1,
                                onEstimatedValueClick = { onOpenSourceLink(item.sourceUrl) }
                            )
                        }
                    }
                }

                if (showTotalValueBand) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                            .onSizeChanged { systemInfoBandHeightPx = it.height }
                            .zIndex(1f)
                    ) {
                        if (isCompactListChrome) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (hasActiveFilters) stringResource(R.string.list_total_value_filtered) else stringResource(R.string.list_total_value),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = displayedTotalValueText,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.list_items) + ": " + totalModeItems.size,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    IconButton(
                                        onClick = { includeAllRecordsInHomeTotal = !includeAllRecordsInHomeTotal },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.SwapHoriz,
                                            contentDescription = stringResource(R.string.list_cd_toggle_total_mode),
                                            modifier = Modifier.size(16.dp),
                                            tint = if (includeAllRecordsInHomeTotal) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        if (hasActiveFilters) {
                                            stringResource(R.string.list_total_value_filtered)
                                        } else {
                                            stringResource(R.string.list_total_value)
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(
                                        displayedTotalValueText,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = totalModeLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(stringResource(R.string.list_items), style = MaterialTheme.typography.labelSmall)
                                        IconButton(
                                            onClick = { includeAllRecordsInHomeTotal = !includeAllRecordsInHomeTotal },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.SwapHoriz,
                                                contentDescription = stringResource(R.string.list_cd_toggle_total_mode),
                                                modifier = Modifier.size(16.dp),
                                                tint = if (includeAllRecordsInHomeTotal) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                    Text(
                                        totalModeItems.size.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManageCollectionsDialog) {
        ManageCollectionsDialog(
            existingCollections = allCollections,
            initialSelection = selectedCollection.takeUnless { it == allCollectionsLabel }.orEmpty(),
            onDismiss = { showManageCollectionsDialog = false },
            onAddCollectionRequested = onAddCollectionRequested,
            onRenameCollectionRequested = onRenameCollectionRequested,
            onDeleteCollectionRequested = onDeleteCollectionRequested,
            onCountItemsInCollection = onCountItemsInCollectionRequested,
            onCollectionRenamed = { oldName, newName ->
                if (selectedCollection.equals(oldName, ignoreCase = true)) {
                    selectedCollection = newName
                }
            },
            onCollectionDeleted = { name ->
                if (selectedCollection.equals(name, ignoreCase = true)) {
                    selectedCollection = allCollectionsLabel
                }
            }
        )
    }

    if (showManageTagsDialog) {
        ManageTagsDialog(
            existingTags = allTags,
            initialSelection = selectedTag.takeUnless { it == allTagsLabel }.orEmpty(),
            onDismiss = { showManageTagsDialog = false },
            onAddTagRequested = onAddTagRequested,
            onRenameTagRequested = onRenameTagRequested,
            onDeleteTagRequested = onDeleteTagRequested,
            onTagRenamed = { oldName, newName ->
                if (selectedTag.equals(oldName, ignoreCase = true)) {
                    selectedTag = newName
                }
            },
            onTagDeleted = { name ->
                if (selectedTag.equals(name, ignoreCase = true)) {
                    selectedTag = allTagsLabel
                }
            }
        )
    }

    if (showAboutDialog) {
        val lastMergeStatsText = if (lastMergeAtText.isNotBlank()) {
            stringResource(
                R.string.about_last_merge_stats_value,
                lastMergeAppliedCount,
                lastMergePhotoCount,
                lastMergeDedupeCount,
                lastMergeAtText
            )
        } else {
            stringResource(R.string.about_last_merge_stats_never)
        }
        val aboutBody = stringResource(
            R.string.about_summary_body,
            stringResource(R.string.about_developer_name),
            stringResource(R.string.about_developer_phone),
            stringResource(R.string.about_app_version, compactVersionText, BuildConfig.APK_BUILD_COUNTER),
            stringResource(R.string.about_backup_format_version, backupFormatVersion),
            stringResource(
                R.string.about_restore_supports_backup_format,
                restoreMinBackupFormatVersion,
                backupFormatVersion
            ),
            stringResource(R.string.about_last_backup_format, lastBackupFormatText),
            stringResource(R.string.about_database_size, databaseSizeText),
            stringResource(R.string.about_photo_folder_size, photoFolderSizeText),
            stringResource(R.string.about_photo_file_count, photoFolderFileCount),
            stringResource(R.string.about_storage_note),
            stringResource(R.string.list_last_backup_value, lastBackupText),
            stringResource(R.string.about_last_merge_stats, lastMergeStatsText),
            stringResource(R.string.about_pending_drafts, pendingDraftCount),
            buildString {
                append(stringResource(R.string.about_copyright))
                append("\n")
                append(stringResource(R.string.about_legal_notice))
            },
            stringResource(R.string.about_dedication)
        )
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = stringResource(R.string.list_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (compactVersionText.isNotBlank()) {
                            Text(
                                text = compactVersionText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (appEditionLabel.isNotBlank()) {
                        Text(
                            text = appEditionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(aboutBody)
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.about_unlock_status_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val unlockStatusText = if (isUnlimitedUnlocked) {
                        val normalizedAccount = unlockAccountId.trim()
                        if (normalizedAccount.isBlank()) {
                            stringResource(R.string.about_unlock_status_unlimited)
                        } else {
                            stringResource(R.string.about_unlock_status_unlimited_account, normalizedAccount)
                        }
                    } else {
                        stringResource(R.string.about_unlock_status_limited, recordLimit)
                    }
                    Text(
                        text = unlockStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            unlockPasswordInput = ""
                            unlockAccountInput = unlockAccountId
                            restoreAccountInput = unlockAccountId
                            restoreCodeInput = ""
                            unlockErrorText = ""
                            unlockInfoText = ""
                            showAboutUnlockDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.about_unlock_manage_button))
                    }
                    if (unlockInfoText.isNotBlank()) {
                        Text(
                            text = unlockInfoText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider()
                    Text(
                        text = "Demo Data",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = {
                            triggerLoadSampleData()
                        },
                        enabled = !isLoadingSampleData && !isRemovingSampleData,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isLoadingSampleData) "Loading samples..." else "Load 4 example records",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    TextButton(
                        onClick = {
                            triggerRemoveSampleData()
                        },
                        enabled = !isLoadingSampleData && !isRemovingSampleData,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isRemovingSampleData) "Removing samples..." else "Remove example records",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }

    if (showAboutUnlockDialog) {
        AlertDialog(
            onDismissRequest = { if (!isUnlockBusy) showAboutUnlockDialog = false },
            title = { Text(stringResource(R.string.unlock_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.unlock_dialog_limit_message, recordLimit))
                    Text(stringResource(R.string.unlock_dialog_current_records, items.size))
                    Text(
                        stringResource(R.string.unlock_dialog_feature_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = unlockPasswordInput,
                        onValueChange = { unlockPasswordInput = it },
                        label = { Text(stringResource(R.string.unlock_dialog_password_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = unlockAccountInput,
                        onValueChange = { unlockAccountInput = it },
                        label = { Text(stringResource(R.string.unlock_dialog_account_optional_label)) },
                        supportingText = { Text(stringResource(R.string.unlock_dialog_account_help)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        enabled = !isUnlockBusy && unlockPasswordInput.isNotBlank(),
                        onClick = {
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
                                            showAboutUnlockDialog = false
                                        } else {
                                            unlockErrorText = context.getString(R.string.unlock_error_incorrect_password)
                                        }
                                    },
                                    onFailure = {
                                        unlockErrorText = context.getString(R.string.unlock_error_unlock_failed)
                                    }
                                )
                            }
                        }
                    ) {
                        Text(
                            if (isUnlockBusy) stringResource(R.string.unlock_dialog_unlocking)
                            else stringResource(R.string.unlock_dialog_unlock_now)
                        )
                    }

                    HorizontalDivider()
                    Text(
                        stringResource(R.string.unlock_dialog_restore_title),
                        style = MaterialTheme.typography.titleSmall
                    )
                    OutlinedTextField(
                        value = restoreAccountInput,
                        onValueChange = { restoreAccountInput = it },
                        label = { Text(stringResource(R.string.unlock_dialog_account_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = restoreCodeInput,
                        onValueChange = { restoreCodeInput = it.uppercase(Locale.getDefault()) },
                        label = { Text(stringResource(R.string.unlock_dialog_recovery_code_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        enabled = !isUnlockBusy && restoreAccountInput.isNotBlank() && restoreCodeInput.isNotBlank(),
                        onClick = {
                            isUnlockBusy = true
                            unlockErrorText = ""
                            unlockInfoText = ""
                            onRestoreUnlimited(restoreAccountInput, restoreCodeInput) { result ->
                                isUnlockBusy = false
                                result.fold(
                                    onSuccess = { restored ->
                                        if (restored) {
                                            unlockInfoText = context.getString(R.string.unlock_result_restored)
                                            showAboutUnlockDialog = false
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
                TextButton(onClick = { if (!isUnlockBusy) showAboutUnlockDialog = false }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }
    if (showDeleteSelectedConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectedConfirm = false },
            title = { Text(stringResource(R.string.list_delete_selected_title)) },
            text = { Text(stringResource(R.string.list_delete_selected_message, selectedCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteSelectedRequested(selectedItemIds)
                        selectedItemIds = emptySet()
                        isSelectionMode = false
                        showDeleteSelectedConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectedConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showWillOwnerDialog) {
        AlertDialog(
            onDismissRequest = { showWillOwnerDialog = false },
            title = { Text(stringResource(R.string.list_set_will_owner_name)) },
            text = {
                OutlinedTextField(
                    value = pendingWillOwnerName,
                    onValueChange = { pendingWillOwnerName = it },
                    label = { Text(stringResource(R.string.list_will_owner_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onWillOwnerNameChange(pendingWillOwnerName)
                        showWillOwnerDialog = false
                    }
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showWillOwnerDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (sectionedMenuEnabled && showSectionedMenuPreview) {
        SectionedMenuDialog(
            isExportingPdf = isExportingPdf,
            hasWillRecordsInScope = hasWillRecordsInScope,
            isBackupRestoreBusy = isBackupRestoreBusy,
            isMergingDatabase = isMergingDatabase,
            isSharingDatabase = isSharingDatabase,
            isSelectionMode = isSelectionMode,
            selectedCount = selectedCount,
            lastBackupText = lastBackupText,
            reportSortOption = reportSortOption,
            themeMode = themeMode,
            onDismiss = {
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onUseClassicMenu = {
                preferSectionedMenu = false
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
                showOverflowMenu = true
            },
            onSelectSection = { activeSectionedMenuSection = it },
            selectedSection = activeSectionedMenuSection,
            onExportSummaryText = {
                pendingReportAction = PendingSummaryExport(
                    items = sortedDisplayedItems,
                    scopeLabel = exportScopeLabel,
                    reportSortOption = reportSortOption,
                    includeThumbnails = false,
                    isFiltered = hasActiveFilters
                )
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onExportSummaryPhotos = {
                pendingReportAction = PendingSummaryExport(
                    items = sortedDisplayedItems,
                    scopeLabel = exportScopeLabel,
                    reportSortOption = reportSortOption,
                    includeThumbnails = true,
                    isFiltered = hasActiveFilters
                )
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onPrintWillPhotos = {
                pendingReportAction = PendingWillPrint(
                    items = sortedDisplayedItems,
                    scopeLabel = exportScopeLabel,
                    reportSortOption = reportSortOption,
                    includeThumbnails = true
                )
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onPrintWillText = {
                pendingReportAction = PendingWillPrint(
                    items = sortedDisplayedItems,
                    scopeLabel = exportScopeLabel,
                    reportSortOption = reportSortOption,
                    includeThumbnails = false
                )
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onBatchExport = {
                batchExportOptions = BatchExportOptions()
                showBatchExportDialog = true
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onReportSortName = {
                reportSortOption = ReportSortOption.NAME_AZ
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onReportSortValue = {
                reportSortOption = ReportSortOption.VALUE_HIGH
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onExportRecords = {
                onExportRecordsRequested(sortedDisplayedItems, exportScopeLabel)
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onExportSelectedRecords = {
                val selectedItems = sortedDisplayedItems.filter { it.id in selectedItemIds }
                onExportRecordsRequested(selectedItems, "Selected records")
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onQuickBackup = {
                onQuickBackupRequested()
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onBackup = {
                onBackupRequested()
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onRestore = {
                onRestoreRequested()
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onRestoreAuto = {
                onRestoreAutoBackupRequested()
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onImportRecords = {
                onImportRecordsRequested()
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onMergeDatabase = {
                onMergeDatabaseRequested()
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onShareDatabase = {
                onShareDatabaseRequested()
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onToggleSelectionMode = {
                isSelectionMode = !isSelectionMode
                if (!isSelectionMode) selectedItemIds = emptySet()
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onDeleteSelected = {
                showDeleteSelectedConfirm = true
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onSetWillOwner = {
                pendingWillOwnerName = willOwnerName
                showWillOwnerDialog = true
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onManageCollections = {
                showManageCollectionsDialog = true
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onManageTags = {
                showManageTagsDialog = true
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onThemeLight = {
                onThemeModeSelected(ThemeMode.LIGHT)
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onThemeDark = {
                onThemeModeSelected(ThemeMode.DARK)
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onThemeSystem = {
                onThemeModeSelected(ThemeMode.SYSTEM)
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            appTier = appTier,
            onTierPersonal = {
                onAppTierSelected(com.example.valuefinder.AppTier.PERSONAL)
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onTierInsurance = {
                onAppTierSelected(com.example.valuefinder.AppTier.INSURANCE)
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onLockPersonalNow = {
                onLockPersonalNowRequested()
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onHowTo = {
                onHowToRequested()
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onAbout = {
                onAboutRequested()
                showAboutDialog = true
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            isLoadingSampleData = isLoadingSampleData,
            isRemovingSampleData = isRemovingSampleData,
            onLoadSampleData = {
                triggerLoadSampleData()
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            },
            onRemoveSampleData = {
                triggerRemoveSampleData()
                activeSectionedMenuSection = null
                showSectionedMenuPreview = false
            }
        )
    }

    if (showBatchExportDialog) {
        BatchExportDialog(
            selection = batchExportOptions,
            onSelectionChange = { batchExportOptions = it },
            onDismiss = { showBatchExportDialog = false },
            onConfirm = {
                val runBatch = {
                    onBatchExportRequested(
                        sortedDisplayedItems,
                        exportScopeLabel,
                        hasActiveFilters,
                        reportSortOption,
                        batchExportOptions
                    )
                    showBatchExportDialog = false
                }
                if (!batchExportOptions.hasAnySelected()) {
                    showBatchExportDialog = false
                } else if (!hasSeenExportHelper) {
                    showBatchExportDialog = false
                    pendingAfterHelper = {
                        onExportHelperSeen()
                        runBatch()
                    }
                    showExportHelperDialog = true
                } else {
                    runBatch()
                }
            }
        )
    }

    if (showExportHelperDialog) {
        ExportHelperDialog(
            onDismiss = {
                showExportHelperDialog = false
                pendingAfterHelper = null
            },
            onConfirm = {
                showExportHelperDialog = false
                val pending = pendingAfterHelper
                pendingAfterHelper = null
                pending?.invoke()
            }
        )
    }

    pendingReportAction?.let { pendingAction ->
        ExportConfirmationDialog(
            action = pendingAction,
            onDismiss = { pendingReportAction = null },
            onConfirm = {
                val runSingleAction = {
                    when (pendingAction) {
                        is PendingSummaryExport -> onExportPdfRequested(
                            pendingAction.items,
                            pendingAction.scopeLabel,
                            pendingAction.isFiltered,
                            pendingAction.reportSortOption,
                            pendingAction.includeThumbnails
                        )
                        is PendingWillPrint -> onPrintWillRequested(
                            pendingAction.items,
                            pendingAction.scopeLabel,
                            pendingAction.reportSortOption,
                            pendingAction.includeThumbnails
                        )
                    }
                    pendingReportAction = null
                }
                if (!hasSeenExportHelper) {
                    pendingAfterHelper = {
                        onExportHelperSeen()
                        runSingleAction()
                    }
                    showExportHelperDialog = true
                } else {
                    runSingleAction()
                }
            }
        )
    }

    exportProgress?.let { progress ->
        ExportProgressDialog(progress = progress)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun ItemCard(
    item: ValuedItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelection: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    showDuplicateBadge: Boolean,
    duplicateCount: Int,
    onEstimatedValueClick: () -> Unit
) {
    var photoLoadFailed by remember(item.photoPath) { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, item.photoPath) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                photoLoadFailed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .size(100.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(8.dp)
            ) {
                if (File(item.photoPath).exists() && !photoLoadFailed) {
                    AsyncImage(
                        model = File(item.photoPath),
                        contentDescription = stringResource(R.string.list_item_photo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
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
                            Icon(Icons.Filled.Image, stringResource(R.string.list_no_photo))
                            Text(
                                stringResource(R.string.common_image_unavailable),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterVertically)
            ) {
                if (isSelectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() })
                        Text(
                            text = if (isSelected) stringResource(R.string.list_selected_badge) else stringResource(R.string.list_select_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    item.itemName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showDuplicateBadge && duplicateCount > 1) {
                    Text(
                        text = stringResource(R.string.list_duplicate_count_badge, duplicateCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    item.itemDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.collectionName.isNotBlank()) {
                    Text(
                        item.collectionName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (item.shortAiDescription.isNotBlank()) {
                    Text(
                        item.shortAiDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                if (item.estimatedValue != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable(enabled = item.sourceUrl.isNotBlank()) {
                                onEstimatedValueClick()
                            }
                    ) {
                        Text(
                            MoneyUtils.formatAud(item.estimatedValue),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (item.sourceUrl.isNotBlank()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = stringResource(R.string.common_open_link),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
