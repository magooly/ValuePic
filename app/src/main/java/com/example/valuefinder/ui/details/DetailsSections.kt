package com.example.valuefinder.ui.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.valuefinder.R
import com.example.valuefinder.ui.parseComparableRows

@Composable
internal fun EditActionsRow(
    canSave: Boolean,
    isFetchingDescription: Boolean,
    onSave: () -> Unit,
    onClearLookupData: () -> Unit,
    onLookup: () -> Unit,
) {
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.common_save))
        }
        OutlinedButton(
            onClick = onClearLookupData,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.details_clear_lookup_data))
        }
        OutlinedButton(
            onClick = onLookup,
            enabled = !isFetchingDescription,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.details_lookup_action),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

@Composable
internal fun DetectedLabelsSection(
    isEditing: Boolean,
    itemDetectedLabels: String,
    editedDetectedLabels: String,
    onEditedDetectedLabelsChange: (String) -> Unit,
    editFieldColors: TextFieldColors,
) {
    if ((if (isEditing) editedDetectedLabels else itemDetectedLabels).isNotBlank() || isEditing) {
        Spacer(modifier = Modifier.height(16.dp))
        if (isEditing) {
            OutlinedTextField(
                value = editedDetectedLabels,
                onValueChange = onEditedDetectedLabelsChange,
                label = { Text(stringResource(R.string.details_detected_labels_label)) },
                trailingIcon = {
                    if (editedDetectedLabels.isNotEmpty()) {
                        IconButton(onClick = { onEditedDetectedLabelsChange("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.list_cd_clear_search),
                                modifier = Modifier.height(18.dp)
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = editFieldColors
            )
        } else {
            Text(
                stringResource(R.string.details_detected_labels_value, itemDetectedLabels),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
internal fun ShareActionsSection(
    onShareRecord: () -> Unit,
    onSharePhoto: () -> Unit,
) {
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringResource(R.string.details_share_section_title), style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onShareRecord,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.details_share_record))
        }
        OutlinedButton(
            onClick = onSharePhoto,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Filled.Image, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.details_share_photo))
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
internal fun PersonalNotesSection(
    isEditing: Boolean,
    itemNotes: String,
    notesUnlocked: Boolean,
    editedNotes: String,
    hasNotesPin: Boolean,
    editFieldColors: TextFieldColors,
    onRequestNotesUnlock: () -> Unit,
    onEditedNotesChange: (String) -> Unit,
    onOpenChangePin: () -> Unit,
    onOpenResetPin: () -> Unit,
) {
    // Personal notes — gated by a phase-1 PIN unlock.
    if (isEditing || itemNotes.isNotBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        if (!notesUnlocked && !isEditing) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.details_personal_notes_label),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedCard(
                        onClick = onRequestNotesUnlock,
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 88.dp),
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Spacer(modifier = Modifier.fillMaxWidth().height(72.dp))
                    }
                }
            }
        } else if (!notesUnlocked) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        stringResource(R.string.details_notes_locked_compact),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = onRequestNotesUnlock) {
                        Text(stringResource(R.string.details_notes_unlock_action), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        } else if (isEditing) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = editedNotes,
                    onValueChange = onEditedNotesChange,
                    label = { Text(stringResource(R.string.details_personal_notes_label)) },
                    placeholder = { Text(stringResource(R.string.details_personal_notes_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = editFieldColors
                )
                if (editedNotes.isNotEmpty()) {
                    IconButton(
                        onClick = { onEditedNotesChange("") },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.list_cd_clear_search),
                            modifier = Modifier.height(18.dp)
                        )
                    }
                }
            }
            if (hasNotesPin) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenChangePin) {
                        Text(stringResource(R.string.details_notes_change_pin_action))
                    }
                    TextButton(onClick = onOpenResetPin) {
                        Text(stringResource(R.string.details_notes_reset_pin_action))
                    }
                }
            }
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.details_personal_notes_label), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            text = itemNotes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                            overflow = TextOverflow.Visible
                        )
                    }
                    if (hasNotesPin) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onOpenChangePin) {
                                Text(stringResource(R.string.details_notes_change_pin_action))
                            }
                            TextButton(onClick = onOpenResetPin) {
                                Text(stringResource(R.string.details_notes_reset_pin_action))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SavedComparablesSection(
    isEditing: Boolean,
    itemSearchResults: String,
    editedSearchResults: String,
    editFieldColors: TextFieldColors,
    onEditedSearchResultsChange: (String) -> Unit,
    onOpenSourceLink: (String) -> Unit,
) {
    if ((if (isEditing) editedSearchResults else itemSearchResults).isNotBlank() || isEditing) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.details_saved_comparables), style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        if (isEditing) {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = editedSearchResults,
                    onValueChange = onEditedSearchResultsChange,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = editFieldColors
                )
                if (editedSearchResults.isNotEmpty()) {
                    IconButton(
                        onClick = { onEditedSearchResultsChange("") },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.list_cd_clear_search),
                            modifier = Modifier.height(18.dp)
                        )
                    }
                }
            }
        } else {
            parseComparableRows(itemSearchResults).forEach { comparable ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            comparable.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (comparable.price.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.details_value_value, comparable.price),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (comparable.source.isNotBlank()) {
                            Text(
                                stringResource(R.string.details_source_value, comparable.source),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (comparable.url.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(onClick = { onOpenSourceLink(comparable.url) }) {
                                Icon(Icons.Filled.OpenInBrowser, contentDescription = stringResource(R.string.common_open_link))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.details_open_comparable))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

