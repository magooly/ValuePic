package com.example.valuefinder.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.valuefinder.R

@Composable
fun ManageTagsDialog(
    existingTags: List<String>,
    initialSelection: String,
    onDismiss: () -> Unit,
    onAddTagRequested: (String, (Result<Boolean>) -> Unit) -> Unit,
    onRenameTagRequested: (String, String, (Result<Boolean>) -> Unit) -> Unit,
    onDeleteTagRequested: (String, (Result<Boolean>) -> Unit) -> Unit,
    onTagRenamed: (String, String) -> Unit = { _, _ -> },
    onTagDeleted: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showTagDropdown by remember { mutableStateOf(false) }
    var newTagNameInput by remember { mutableStateOf("") }
    var selectedTag by remember {
        mutableStateOf(
            existingTags.firstOrNull { it.equals(initialSelection.trim(), ignoreCase = true) }
                ?: existingTags.firstOrNull().orEmpty()
        )
    }
    var renameTagInput by remember { mutableStateOf(selectedTag) }
    var pendingRename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    val selected = selectedTag.trim()
    val targetName = renameTagInput.trim()
    val canAdd = newTagNameInput.trim().isNotBlank() &&
        existingTags.none { it.equals(newTagNameInput.trim(), ignoreCase = true) }
    val canRename = selected.isNotBlank() && targetName.isNotBlank() && !selected.equals(targetName, ignoreCase = true)
    val canDelete = selected.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tags_manage_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.tags_add_label), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = newTagNameInput,
                    onValueChange = { newTagNameInput = enforceLeadingCapitalization(it) },
                    label = { Text(stringResource(R.string.tags_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                Button(
                    onClick = {
                        val newName = newTagNameInput.trim()
                        onAddTagRequested(newName) { result ->
                            if (result.getOrDefault(false)) {
                                selectedTag = newName
                                renameTagInput = newName
                                newTagNameInput = ""
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.tags_add_success, newName),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.tags_add_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.tags_add_action))
                }

                Text(stringResource(R.string.tags_edit_label), style = MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedButton(
                        onClick = { showTagDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = existingTags.isNotEmpty()
                    ) {
                        Text(
                            if (selected.isBlank()) stringResource(R.string.tags_select_label) else selected,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showTagDropdown,
                        onDismissRequest = { showTagDropdown = false }
                    ) {
                        existingTags.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedTag = name
                                    renameTagInput = name
                                    showTagDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = renameTagInput,
                    onValueChange = { renameTagInput = enforceLeadingCapitalization(it) },
                    label = { Text(stringResource(R.string.tags_rename_to_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { pendingDelete = selected },
                        enabled = canDelete,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.tags_delete_action))
                    }
                    Button(
                        onClick = { pendingRename = selected to targetName },
                        enabled = canRename,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.tags_rename_action))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )

    pendingRename?.let { (oldName, newName) ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text(stringResource(R.string.tags_confirm_rename_title)) },
            text = { Text(stringResource(R.string.tags_confirm_rename_message, oldName, newName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameTagRequested(oldName, newName) { result ->
                            if (result.getOrDefault(false)) {
                                selectedTag = newName
                                renameTagInput = newName
                                onTagRenamed(oldName, newName)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.tags_rename_success, oldName, newName),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.tags_rename_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            pendingRename = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRename = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.tags_confirm_delete_title)) },
            text = { Text(stringResource(R.string.tags_confirm_delete_message, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTagRequested(name) { result ->
                            if (result.getOrDefault(false)) {
                                if (selectedTag.equals(name, ignoreCase = true)) {
                                    selectedTag = ""
                                    renameTagInput = ""
                                }
                                onTagDeleted(name)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.tags_delete_success, name),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.tags_delete_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            pendingDelete = null
                        }
                    }
                ) {
                    Text(stringResource(R.string.delete_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}