package com.example.valuefinder

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "valuation_drafts")
data class ValuationDraftEntity(
    @PrimaryKey
    val photoPath: String,
    val schemaVersion: Int = 1,
    val photoSource: String,
    val itemName: String,
    val itemDescription: String,
    val itemTags: String,
    val editableValue: String,
    val userEditedValue: Boolean,
    val selectedCollection: String,
    val doNotIncludeInTotals: Boolean = false,
    val willInstructions: String = "",
    val detailedLookupMode: Boolean,
    val savedAtMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(): ValuationDraft = ValuationDraft(
        schemaVersion = schemaVersion,
        photoPath = photoPath,
        photoSource = photoSource,
        itemName = itemName,
        itemDescription = itemDescription,
        itemTags = itemTags,
        editableValue = editableValue,
        userEditedValue = userEditedValue,
        selectedCollection = selectedCollection,
        doNotIncludeInTotals = doNotIncludeInTotals,
        willInstructions = willInstructions,
        detailedLookupMode = detailedLookupMode,
        savedAtMillis = savedAtMillis
    )

    companion object {
        fun fromDomain(draft: ValuationDraft): ValuationDraftEntity = ValuationDraftEntity(
            photoPath = draft.photoPath,
            schemaVersion = draft.schemaVersion,
            photoSource = draft.photoSource,
            itemName = draft.itemName,
            itemDescription = draft.itemDescription,
            itemTags = draft.itemTags,
            editableValue = draft.editableValue,
            userEditedValue = draft.userEditedValue,
            selectedCollection = draft.selectedCollection,
            doNotIncludeInTotals = draft.doNotIncludeInTotals,
            willInstructions = draft.willInstructions,
            detailedLookupMode = draft.detailedLookupMode,
            savedAtMillis = draft.savedAtMillis
        )
    }
}

