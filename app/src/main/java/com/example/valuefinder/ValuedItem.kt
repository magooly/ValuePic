package com.example.valuefinder

import android.os.Parcel
import android.os.Parcelable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

private const val NULL_DOUBLE_MARKER: Byte = 0
private const val NON_NULL_DOUBLE_MARKER: Byte = 1

@Entity(
    tableName = "valued_items",
    indices = [
        Index("collectionName"),  // Used in filtering and grouping by collection
        Index("dateValued"),      // Used in sorting by date
        Index("estimatedValue"),  // Used in statistics and sorting by value
        Index("itemName")         // Used in search operations
    ]
)
data class ValuedItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val photoPath: String,
    val photoSource: String = "camera",
    val itemName: String,
    val collectionName: String = "",
    val shortAiDescription: String = "",
    val fullWebDescription: String = "",
    val itemDescription: String,
    val detectedLabels: String, // Comma-separated list of detected object labels
    val estimatedValue: Double? = null,
    val currency: String = "AUD",
    val valueSource: String = "", // Where value came from
    val sourceUrl: String = "",
    val searchResults: String = "", // Serialized comparable search results (CSV rows)
    val confidence: Float = 0f, // Confidence in valuation
    val createdAtMillis: Long = System.currentTimeMillis(),
    val dateTaken: String,
    val dateValued: Long = System.currentTimeMillis(),
    val willInstructions: String = "",
    val notes: String = "",
    val tags: String = "",
    val billsEnteredPeriod: String = "",
    val includeInTotals: Boolean = true, // Whether to include in sum/average calculations
    val excludeFromPdfReport: Boolean = false, // Whether to exclude from summary PDF exports
    val tier: String = "" // Which app tier owns this item ("" treated as PERSONAL for legacy data)
) : Parcelable {

    private constructor(parcel: Parcel) : this(
        id = parcel.readInt(),
        photoPath = parcel.readString().orEmpty(),
        photoSource = parcel.readString().orEmpty(),
        itemName = parcel.readString().orEmpty(),
        collectionName = parcel.readString().orEmpty(),
        shortAiDescription = parcel.readString().orEmpty(),
        fullWebDescription = parcel.readString().orEmpty(),
        itemDescription = parcel.readString().orEmpty(),
        detectedLabels = parcel.readString().orEmpty(),
        estimatedValue = if (parcel.readByte() == NON_NULL_DOUBLE_MARKER) parcel.readDouble() else null,
        currency = parcel.readString().orEmpty(),
        valueSource = parcel.readString().orEmpty(),
        sourceUrl = parcel.readString().orEmpty(),
        searchResults = parcel.readString().orEmpty(),
        confidence = parcel.readFloat(),
        createdAtMillis = parcel.readLong(),
        dateTaken = parcel.readString().orEmpty(),
        dateValued = parcel.readLong(),
        willInstructions = parcel.readString().orEmpty(),
        notes = parcel.readString().orEmpty(),
        tags = parcel.readString().orEmpty(),
        billsEnteredPeriod = parcel.readString().orEmpty(),
        includeInTotals = parcel.readByte() != 0.toByte(),
        excludeFromPdfReport = parcel.readByte() != 0.toByte(),
        tier = parcel.readString().orEmpty()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(id)
        parcel.writeString(photoPath)
        parcel.writeString(photoSource)
        parcel.writeString(itemName)
        parcel.writeString(collectionName)
        parcel.writeString(shortAiDescription)
        parcel.writeString(fullWebDescription)
        parcel.writeString(itemDescription)
        parcel.writeString(detectedLabels)
        if (estimatedValue == null) {
            parcel.writeByte(NULL_DOUBLE_MARKER)
        } else {
            parcel.writeByte(NON_NULL_DOUBLE_MARKER)
            parcel.writeDouble(estimatedValue)
        }
        parcel.writeString(currency)
        parcel.writeString(valueSource)
        parcel.writeString(sourceUrl)
        parcel.writeString(searchResults)
        parcel.writeFloat(confidence)
        parcel.writeLong(createdAtMillis)
        parcel.writeString(dateTaken)
        parcel.writeLong(dateValued)
        parcel.writeString(willInstructions)
        parcel.writeString(notes)
        parcel.writeString(tags)
        parcel.writeString(billsEnteredPeriod)
        parcel.writeByte(if (includeInTotals) 1 else 0)
        parcel.writeByte(if (excludeFromPdfReport) 1 else 0)
        parcel.writeString(tier)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ValuedItem> {
        override fun createFromParcel(parcel: Parcel): ValuedItem = ValuedItem(parcel)
        override fun newArray(size: Int): Array<ValuedItem?> = arrayOfNulls(size)
    }
}
