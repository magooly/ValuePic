package com.example.valuefinder.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.valuefinder.R
import com.example.valuefinder.ui.ManageCollectionsDialog
import com.example.valuefinder.ui.enforceLeadingCapitalization


@Composable
internal fun ExitWithoutSavingDialog(
    onDismiss: () -> Unit,
    onConfirmExit: () -> Unit,
    onSaveAndExit: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_unsaved_exit_title)) },
        text = { Text(stringResource(R.string.details_unsaved_exit_message)) },
        confirmButton = {
            TextButton(onClick = onConfirmExit) {
                Text(stringResource(R.string.details_unsaved_exit_confirm))
            }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(onClick = onSaveAndExit) {
                    Text(stringResource(R.string.details_unsaved_exit_save_and_exit))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.details_unsaved_exit_stay))
                }
            }
        }
    )
}
@Composable
internal fun AddTagDialog(
    newTagText: String,
    onTagChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAddTag: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_new_tag_title)) },
        text = {
            OutlinedTextField(
                value = newTagText,
                onValueChange = { onTagChange(enforceLeadingCapitalization(it)) },
                label = { Text(stringResource(R.string.details_new_tag_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onAddTag,
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
internal fun SetNotesPinDialog(
    notesPinInput: String,
    notesPinConfirmInput: String,
    notesPinError: String,
    onNotesPinInputChange: (String) -> Unit,
    onNotesPinConfirmInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_notes_set_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.details_notes_set_pin_message),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = notesPinInput,
                    onValueChange = onNotesPinInputChange,
                    label = { Text(stringResource(R.string.details_notes_pin_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = notesPinConfirmInput,
                    onValueChange = onNotesPinConfirmInputChange,
                    label = { Text(stringResource(R.string.details_notes_pin_confirm_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (notesPinError.isNotBlank()) {
                    Text(notesPinError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_ok))
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
internal fun UnlockNotesPinDialog(
    notesPinInput: String,
    notesPinError: String,
    onNotesPinInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_notes_unlock_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = notesPinInput,
                    onValueChange = onNotesPinInputChange,
                    label = { Text(stringResource(R.string.details_notes_pin_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (notesPinError.isNotBlank()) {
                    Text(notesPinError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_open))
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
internal fun ChangeNotesPinDialog(
    notesCurrentPinInput: String,
    notesNewPinInput: String,
    notesNewPinConfirmInput: String,
    notesPinError: String,
    onCurrentPinInputChange: (String) -> Unit,
    onNewPinInputChange: (String) -> Unit,
    onConfirmPinInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_notes_change_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = notesCurrentPinInput,
                    onValueChange = onCurrentPinInputChange,
                    label = { Text(stringResource(R.string.details_notes_current_pin_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = notesNewPinInput,
                    onValueChange = onNewPinInputChange,
                    label = { Text(stringResource(R.string.details_notes_new_pin_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                OutlinedTextField(
                    value = notesNewPinConfirmInput,
                    onValueChange = onConfirmPinInputChange,
                    label = { Text(stringResource(R.string.details_notes_pin_confirm_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (notesPinError.isNotBlank()) {
                    Text(notesPinError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
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
internal fun ResetNotesPinDialog(
    notesCurrentPinInput: String,
    notesPinError: String,
    onCurrentPinInputChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.details_notes_reset_pin_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.details_notes_reset_pin_message),
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = notesCurrentPinInput,
                    onValueChange = onCurrentPinInputChange,
                    label = { Text(stringResource(R.string.details_notes_current_pin_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (notesPinError.isNotBlank()) {
                    Text(notesPinError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.common_delete))
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
internal fun ManageCollectionsDialogHost(
    existingCollections: List<String>,
    editedCollectionName: String,
    onDismiss: () -> Unit,
    onAddCollectionRequested: (String, (Result<Boolean>) -> Unit) -> Unit,
    onRenameCollectionRequested: (String, String, (Result<Int>) -> Unit) -> Unit,
    onDeleteCollectionRequested: (String, (Result<Int>) -> Unit) -> Unit,
    onCountItemsInCollectionRequested: (String, (Result<Int>) -> Unit) -> Unit,
    onCollectionNameChange: (String) -> Unit,
) {
    ManageCollectionsDialog(
        existingCollections = existingCollections,
        initialSelection = editedCollectionName,
        onDismiss = onDismiss,
        onAddCollectionRequested = onAddCollectionRequested,
        onRenameCollectionRequested = onRenameCollectionRequested,
        onDeleteCollectionRequested = onDeleteCollectionRequested,
        onCountItemsInCollection = onCountItemsInCollectionRequested,
        onCollectionAdded = { newName -> onCollectionNameChange(newName) },
        onCollectionRenamed = { oldName, newName ->
            if (editedCollectionName.equals(oldName, ignoreCase = true)) {
                onCollectionNameChange(newName)
            }
        },
        onCollectionDeleted = { name ->
            if (editedCollectionName.equals(name, ignoreCase = true)) {
                onCollectionNameChange("")
            }
        }
    )
}

