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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.valuefinder.isBillsCollectionName
import com.example.valuefinder.R

@Composable
fun ManageCollectionsDialog(
    existingCollections: List<String>,
    initialSelection: String,
    onDismiss: () -> Unit,
    onAddCollectionRequested: (String, (Result<Boolean>) -> Unit) -> Unit,
    onRenameCollectionRequested: (String, String, (Result<Int>) -> Unit) -> Unit,
    onDeleteCollectionRequested: (String, (Result<Int>) -> Unit) -> Unit,
    onCountItemsInCollection: (String, (Result<Int>) -> Unit) -> Unit,
    onCollectionAdded: (String) -> Unit = {},
    onCollectionRenamed: (String, String) -> Unit = { _, _ -> },
    onCollectionDeleted: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showCollectionDropdown by remember { mutableStateOf(false) }
    var selectedCollection by remember { mutableStateOf(initialSelection.trim()) }
    var newCollectionNameInput by remember { mutableStateOf("") }
    var renameCollectionInput by remember { mutableStateOf(initialSelection.trim()) }
    var pendingRename by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    var pendingCount by remember { mutableStateOf(0) }

    LaunchedEffect(initialSelection, existingCollections) {
        if (selectedCollection.isBlank()) {
            val match = existingCollections.firstOrNull { it.equals(initialSelection, ignoreCase = true) }
            selectedCollection = match ?: existingCollections.firstOrNull().orEmpty()
            if (renameCollectionInput.isBlank()) {
                renameCollectionInput = selectedCollection
            }
        }
    }

    val selected = selectedCollection.trim()
    val isProtectedCollectionSelected = isBillsCollectionName(selected)
    val canAdd = newCollectionNameInput.trim().isNotBlank() &&
        existingCollections.none { it.equals(newCollectionNameInput.trim(), ignoreCase = true) }
    val canRename = selected.isNotBlank() && !isProtectedCollectionSelected && renameCollectionInput.trim().isNotBlank()
    val canDelete = selected.isNotBlank() && !isProtectedCollectionSelected

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collections_manage_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.collections_add_label), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = newCollectionNameInput,
                    onValueChange = { newCollectionNameInput = enforceLeadingCapitalization(it) },
                    label = { Text(stringResource(R.string.collections_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                Button(
                    onClick = {
                        val newName = newCollectionNameInput.trim()
                        onAddCollectionRequested(newName) { result ->
                            val added = result.getOrDefault(false)
                            if (added) {
                                selectedCollection = newName
                                renameCollectionInput = newName
                                newCollectionNameInput = ""
                                onCollectionAdded(newName)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.collections_add_success, newName),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.collections_add_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = canAdd,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.collections_add_action))
                }

                HorizontalDivider()

                Text(stringResource(R.string.collections_edit_label), style = MaterialTheme.typography.titleSmall)
                Box {
                    OutlinedButton(
                        onClick = { showCollectionDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = existingCollections.isNotEmpty()
                    ) {
                        Text(
                            if (selected.isBlank()) stringResource(R.string.collections_select_label) else selected,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = showCollectionDropdown,
                        onDismissRequest = { showCollectionDropdown = false }
                    ) {
                        existingCollections.sortedWith(String.CASE_INSENSITIVE_ORDER).forEach { name ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    selectedCollection = name
                                    renameCollectionInput = name
                                    showCollectionDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = renameCollectionInput,
                    onValueChange = { renameCollectionInput = enforceLeadingCapitalization(it) },
                    label = { Text(stringResource(R.string.collections_rename_to_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                if (isProtectedCollectionSelected) {
                    Text(
                        text = stringResource(R.string.collections_bills_protected_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            onCountItemsInCollection(selected) { result ->
                                pendingCount = result.getOrDefault(0)
                                pendingDelete = selected
                            }
                        },
                        enabled = canDelete,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.collections_delete_action))
                    }
                    Button(
                        onClick = {
                            val newName = renameCollectionInput.trim()
                            onCountItemsInCollection(selected) { result ->
                                pendingCount = result.getOrDefault(0)
                                pendingRename = selected to newName
                            }
                        },
                        enabled = canRename,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.collections_rename_action))
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
            title = { Text(stringResource(R.string.collections_confirm_rename_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.collections_confirm_rename_message,
                        oldName,
                        newName,
                        pendingCount
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRenameCollectionRequested(oldName, newName) { result ->
                            val changed = result.getOrDefault(0)
                            if (changed > 0) {
                                selectedCollection = newName
                                renameCollectionInput = newName
                                onCollectionRenamed(oldName, newName)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.collections_rename_success, oldName, newName, changed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.collections_rename_no_items),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            pendingRename = null
                        }
                    }
                ) { Text(stringResource(R.string.common_save)) }
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
            title = { Text(stringResource(R.string.collections_confirm_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.collections_confirm_delete_message,
                        name,
                        pendingCount
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteCollectionRequested(name) { result ->
                            val changed = result.getOrDefault(0)
                            if (changed > 0) {
                                if (selectedCollection.equals(name, ignoreCase = true)) {
                                    selectedCollection = ""
                                    renameCollectionInput = ""
                                }
                                onCollectionDeleted(name)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.collections_delete_success, name, changed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.collections_delete_no_items),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            pendingDelete = null
                        }
                    }
                ) { Text(stringResource(R.string.delete_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

