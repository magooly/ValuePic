package com.example.valuefinder.ui.dialogs

import android.content.Context
import android.text.format.Formatter
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import com.example.valuefinder.R
import com.example.valuefinder.PdfExportResult
import com.example.valuefinder.AutoBackupSlotInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Renders a single page lazily. Returns null while loading. */
@Composable
private fun PdfPage(context: Context, uri: Uri, pageIndex: Int) {
    var bitmap by remember(uri, pageIndex) { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(uri, pageIndex) {
        val thread = Thread {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@Thread
                pfd.use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        if (pageIndex < renderer.pageCount) {
                            renderer.openPage(pageIndex).use { page ->
                                // Cap width to 1200px to avoid OOM on low-RAM devices
                                val scale = (1200f / page.width).coerceAtMost(1.6f)
                                val w = (page.width * scale).toInt()
                                val h = (page.height * scale).toInt()
                                val bmp = createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(android.graphics.Color.WHITE)
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmap = bmp
                            }
                        }
                    }
                }
            } catch (_: Exception) { /* skip page on error */ }
        }
        thread.start()
        onDispose {
            thread.interrupt()
            bitmap?.let { if (!it.isRecycled) it.recycle() }
            bitmap = null
        }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = stringResource(R.string.howto_page_content_description, pageIndex + 1),
            modifier = Modifier.fillMaxWidth()
                .padding(bottom = 12.dp)
        )
    } ?: Text(
        text = stringResource(R.string.howto_page_loading, pageIndex + 1),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

/** Returns total page count for the PDF, or 0 on error. */
private fun pdfPageCount(context: Context, uri: Uri): Int = runCatching {
    context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
        PdfRenderer(pfd).use { it.pageCount }
    } ?: 0
}.getOrDefault(0)

/**
 * Dialog for PDF export result
 */
@Composable
fun PdfExportResultDialog(
    result: PdfExportResult,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pdf_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.pdf_scope_label, result.scopeLabel))
                Text(stringResource(R.string.pdf_record_count_label, result.itemCount))
            }
        },
        confirmButton = {
            TextButton(onClick = onOpen) {
                Text(stringResource(R.string.common_open))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onPrint) {
                    Text(stringResource(R.string.common_print))
                }
                TextButton(onClick = onShare) {
                    Text(stringResource(R.string.common_share))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    )
}

/**
 * Error dialog
 */
@Composable
fun ErrorDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_error_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_ok))
            }
        }
    )
}

/**
 * How-to PDF dialog
 */
@Composable
fun HowToDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onPrint: () -> Unit,
) {
    val context = LocalContext.current
    val pageCount by produceState(initialValue = 0, uri) {
        value = runCatching { pdfPageCount(context, uri) }.getOrDefault(0)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(
                        text = stringResource(R.string.howto_dialog_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onOpen) { Text(stringResource(R.string.common_open)) }
                    TextButton(onClick = onPrint) { Text(stringResource(R.string.common_print)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
                }
                HorizontalDivider()

                if (pageCount == 0) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.howto_error_load_failed))
                        TextButton(onClick = onOpen) { Text(stringResource(R.string.common_open)) }
                    }
                } else {
                    // Pages are rendered one-at-a-time as they scroll into view,
                    // avoiding OOM on low-RAM devices (e.g. Tab S3).
                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                        items(pageCount) { index ->
                            Text(
                                text = stringResource(R.string.howto_page_label, index + 1),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            PdfPage(context = context, uri = uri, pageIndex = index)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pre-backup caution dialog — shown before any action that replaces or merges
 * the existing database. Lets the user name a backup first (or skip it).
 */
@Composable
fun PreBackupCautionDialog(
    defaultBackupName: String,
    onBackupFirst: (backupName: String) -> Unit,
    onProceedAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    var backupName by rememberSaveable { mutableStateOf(defaultBackupName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pre_backup_caution_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.pre_backup_caution_message))
                OutlinedTextField(
                    value = backupName,
                    onValueChange = { backupName = it },
                    label = { Text(stringResource(R.string.pre_backup_caution_backup_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    stringResource(R.string.pre_backup_caution_backup_name_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onBackupFirst(backupName.trim().ifBlank { defaultBackupName })
            }) {
                Text(stringResource(R.string.pre_backup_caution_action_backup_first))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                TextButton(onClick = onProceedAnyway) { Text(stringResource(R.string.pre_backup_caution_action_proceed_anyway)) }
            }
        }
    )
}

/**
 * Restore confirmation dialog
 */
@Composable
fun RestoreConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.restore_confirm_title)) },
        text = { Text(stringResource(R.string.restore_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.restore_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * Merge confirmation dialog
 */
@Composable
fun MergeConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.merge_confirm_title)) },
        text = { Text(stringResource(R.string.merge_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.merge_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * Delete item confirmation dialog
 */
@Composable
fun DeleteConfirmDialog(
    itemName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = { Text(stringResource(R.string.delete_confirm_message_named, itemName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.delete_confirm_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * Resume draft dialog
 */
@Composable
fun ResumeDraftDialog(
    onDismiss: () -> Unit,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.resume_draft_title)) },
        text = { Text(stringResource(R.string.resume_draft_message)) },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text(stringResource(R.string.resume_draft_action_resume))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) {
                    Text(stringResource(R.string.resume_draft_action_discard))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    )
}

/**
 * Backup reminder dialog
 */
@Suppress("unused")
@Composable
fun BackupReminderDialog(
    lastBackupText: String,
    onDismiss: () -> Unit,
    onBackupNow: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_reminder_title)) },
        text = { Text(stringResource(R.string.backup_reminder_message, lastBackupText)) },
        confirmButton = {
            TextButton(onClick = onBackupNow) {
                Text(stringResource(R.string.list_backup_now))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
fun AutoBackupRestoreDialog(
    slots: List<AutoBackupSlotInfo>,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
    onRestoreSlot: (String) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auto_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.auto_restore_message))
                slots.forEach { slot ->
                    val timestampText = slot.createdAtMillis
                        ?.takeIf { it > 0L }
                        ?.let { dateFormatter.format(Date(it)) }
                        ?: stringResource(R.string.auto_restore_unknown_time)
                    val counts = if (slot.itemCount != null && slot.photoCount != null) {
                        stringResource(R.string.auto_restore_counts, slot.itemCount, slot.photoCount)
                    } else {
                        stringResource(R.string.auto_restore_counts_unknown)
                    }
                    val sizeText = Formatter.formatFileSize(context, slot.fileSizeBytes)
                    val status = when {
                        slot.isRestorable && slot.isLatest -> stringResource(R.string.auto_restore_status_latest)
                        slot.isRestorable -> stringResource(R.string.auto_restore_status_available)
                        else -> stringResource(R.string.auto_restore_status_corrupt)
                    }
                    Surface(
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(stringResource(R.string.auto_restore_slot_title, slot.slotId, status))
                            Text(stringResource(R.string.auto_restore_created_at, timestampText), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.auto_restore_size, sizeText), style = MaterialTheme.typography.bodySmall)
                            Text(counts, style = MaterialTheme.typography.bodySmall)
                            slot.warningMessage?.let {
                                Text(
                                    text = stringResource(R.string.auto_restore_warning_prefix, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            slot.corruptionMessage?.let {
                                Text(
                                    text = stringResource(R.string.auto_restore_corrupt_prefix, it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            TextButton(
                                onClick = { onRestoreSlot(slot.slotId) },
                                enabled = slot.isRestorable
                            ) {
                                Text(
                                    if (slot.warningMessage.isNullOrBlank()) {
                                        stringResource(R.string.auto_restore_action)
                                    } else {
                                        stringResource(R.string.auto_restore_action_anyway)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onRefresh, enabled = !isRefreshing) {
                    Text(
                        if (isRefreshing) {
                            stringResource(R.string.auto_restore_refreshing)
                        } else {
                            stringResource(R.string.auto_restore_refresh)
                        }
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    )
}

/**
 * Shows undo snackbar for deleted items
 */
suspend fun showDeleteUndoSnackbar(
    snackbarHostState: SnackbarHostState,
    context: Context,
    itemName: String,
): SnackbarResult {
    return snackbarHostState.showSnackbar(
        message = context.getString(R.string.delete_undo_message, itemName),
        actionLabel = context.getString(R.string.delete_undo_action),
        withDismissAction = true,
        duration = SnackbarDuration.Long
    )
}

