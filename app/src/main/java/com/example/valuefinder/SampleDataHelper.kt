package com.example.valuefinder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SampleDataHelper {
    const val SAMPLE_COLLECTION = "Examples"
    const val SAMPLE_TAG = "sample"

    fun ensureSampleRecord(item: ValuedItem): ValuedItem {
        val existingTags = TagUtils.parseTags(item.tags)
        val tagsWithSample = (existingTags + SAMPLE_TAG)
            .distinctBy { it.lowercase(Locale.getDefault()) }
            .joinToString(", ")
        return item.copy(
            collectionName = SAMPLE_COLLECTION,
            tags = tagsWithSample
        )
    }

    fun getSampleRecords(): List<ValuedItem> {
        val now = System.currentTimeMillis()
        val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val today = dateFormatter.format(Date(now))

        return listOf(
            ValuedItem(
                itemName = "Vintage Pocket Watch",
                collectionName = SAMPLE_COLLECTION,
                itemDescription = "Gold-plated Swiss pocket watch from the 1960s. Mechanical movement, working condition. Great collectible timepiece.",
                photoPath = "",
                photoSource = "gallery",
                shortAiDescription = "Vintage pocket watch",
                fullWebDescription = "A classic Swiss-made pocket watch with intricate dial and precision movement. Highly sought by collectors.",
                detectedLabels = "watch, timepiece, antique, jewelry",
                estimatedValue = 450.0,
                currency = "AUD",
                confidence = 0.85f,
                valueSource = "eBay comparable sales",
                sourceUrl = "",
                dateTaken = today,
                tags = "$SAMPLE_TAG, collectible, jewelry",
                includeInTotals = true,
                createdAtMillis = now - (86400000 * 3) // 3 days ago
            ),
            ValuedItem(
                itemName = "Nikon D50 DSLR Camera",
                collectionName = SAMPLE_COLLECTION,
                itemDescription = "Classic digital SLR camera with 18-55mm lens. Fully functional, minor cosmetic wear. Includes original box and manual.",
                photoPath = "",
                photoSource = "gallery",
                shortAiDescription = "Nikon D50 digital camera",
                fullWebDescription = "A popular entry-level DSLR from the mid-2000s. Still widely used by hobbyists and enthusiasts.",
                detectedLabels = "camera, electronics, photography, lens",
                estimatedValue = 280.0,
                currency = "AUD",
                confidence = 0.78f,
                valueSource = "Gumtree listings",
                sourceUrl = "",
                dateTaken = today,
                tags = "$SAMPLE_TAG, electronics, camera, photography",
                includeInTotals = true,
                createdAtMillis = now - (86400000 * 2) // 2 days ago
            ),
            ValuedItem(
                itemName = "Royal Doulton Figurine",
                collectionName = SAMPLE_COLLECTION,
                itemDescription = "Porcelain figurine \"The Autumn Breeze\" HN1934. Royal Doulton, excellent condition. No chips or cracks.",
                photoPath = "",
                photoSource = "gallery",
                shortAiDescription = "Royal Doulton ceramic figurine",
                fullWebDescription = "Hand-painted porcelain collectible figurine from Royal Doulton's HN series. Highly decorative and sought by collectors.",
                detectedLabels = "figurine, ceramic, porcelain, collectible",
                estimatedValue = 185.0,
                currency = "AUD",
                confidence = 0.72f,
                valueSource = "Collectibles auction sites",
                sourceUrl = "",
                dateTaken = today,
                tags = "$SAMPLE_TAG, collectible, ceramics",
                includeInTotals = true,
                createdAtMillis = now - (86400000 * 1) // 1 day ago
            ),
            ValuedItem(
                itemName = "Sheaffer Fountain Pen",
                collectionName = SAMPLE_COLLECTION,
                itemDescription = "Vintage Sheaffer fountain pen, black barrel with gold trim. 14k gold nib, writes smoothly. Minor signs of age.",
                photoPath = "",
                photoSource = "gallery",
                shortAiDescription = "Vintage fountain pen with gold nib",
                fullWebDescription = "Classic writing instrument from the renowned pen manufacturer Sheaffer. A prized possession for pen enthusiasts.",
                detectedLabels = "pen, gold, fountain pen, vintage, writing",
                estimatedValue = 320.0,
                currency = "AUD",
                confidence = 0.81f,
                valueSource = "Fountain pen collector forums",
                sourceUrl = "",
                dateTaken = today,
                tags = "$SAMPLE_TAG, collectible, vintage, stationery",
                includeInTotals = true,
                createdAtMillis = now // Just now
            )
        )
    }

    fun isSampleRecord(item: ValuedItem): Boolean {
        return item.collectionName == SAMPLE_COLLECTION &&
            TagUtils.parseTags(item.tags).any { it.equals(SAMPLE_TAG, ignoreCase = true) }
    }
}

