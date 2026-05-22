package com.example.valuefinder

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "item_photos",
    foreignKeys = [
        ForeignKey(
            entity = ValuedItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("itemId"),
        Index(value = ["itemId", "sortOrder"]),
        Index(value = ["itemId", "isCover"])
    ]
)
data class ItemPhoto(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val itemId: Int,
    val photoPath: String,
    val sortOrder: Int = 0,
    val isCover: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

