package com.example.valuefinder

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportSortUtilsTest {

    @Test
    fun sortItemsForReport_nameAz_sortsCaseInsensitive() {
        val sorted = sortItemsForReport(
            items = listOf(
                item(name = "zebra", value = 10.0, dateValued = 1L),
                item(name = "Alpha", value = 1.0, dateValued = 2L),
                item(name = "beta", value = 5.0, dateValued = 3L)
            ),
            sortOption = ReportSortOption.NAME_AZ
        )

        assertEquals(listOf("Alpha", "beta", "zebra"), sorted.map { it.itemName })
    }

    @Test
    fun sortItemsForReport_valueHigh_sortsByValue_thenName() {
        val sorted = sortItemsForReport(
            items = listOf(
                item(name = "Bravo", value = 100.0, dateValued = 1L),
                item(name = "Alpha", value = 100.0, dateValued = 2L),
                item(name = "NoValue", value = null, dateValued = 3L)
            ),
            sortOption = ReportSortOption.VALUE_HIGH
        )

        assertEquals(listOf("Alpha", "Bravo", "NoValue"), sorted.map { it.itemName })
    }

    private fun item(name: String, value: Double?, dateValued: Long): ValuedItem {
        return ValuedItem(
            id = 0,
            photoPath = "",
            photoSource = "camera",
            itemName = name,
            collectionName = "",
            shortAiDescription = "",
            fullWebDescription = "",
            itemDescription = "",
            detectedLabels = "",
            estimatedValue = value,
            currency = "AUD",
            valueSource = "",
            sourceUrl = "",
            searchResults = "",
            confidence = 0f,
            dateTaken = "2026-01-01",
            dateValued = dateValued,
            notes = ""
        )
    }
}

