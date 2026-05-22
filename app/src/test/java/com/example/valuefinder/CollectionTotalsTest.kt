package com.example.valuefinder

import org.junit.Assert.assertEquals
import org.junit.Test

class CollectionTotalsTest {

    @Test
    fun buildCollectionTotals_sortsByTotalDescending_thenName() {
        val byCollection = mapOf(
            "Beta" to listOf(item(10.0), item(5.0)),
            "alpha" to listOf(item(15.0)),
            "Gamma" to listOf(item(2.0), item(null))
        )

        val totals = buildCollectionTotals(byCollection)

        assertEquals(3, totals.size)
        assertEquals("alpha", totals[0].first)
        assertEquals(15.0, totals[0].second, 0.001)
        assertEquals("Beta", totals[1].first)
        assertEquals(15.0, totals[1].second, 0.001)
        assertEquals("Gamma", totals[2].first)
        assertEquals(2.0, totals[2].second, 0.001)
    }

    @Test
    fun buildCollectionTotals_treatsNullValuesAsZeroContribution() {
        val byCollection = mapOf(
            "OnlyNulls" to listOf(item(null), item(null))
        )

        val totals = buildCollectionTotals(byCollection)

        assertEquals(1, totals.size)
        assertEquals("OnlyNulls", totals[0].first)
        assertEquals(0.0, totals[0].second, 0.001)
    }

    private fun item(value: Double? = null, collection: String = ""): ValuedItem {
        return ValuedItem(
            id = 0,
            photoPath = "",
            photoSource = "camera",
            itemName = "TestItem",
            collectionName = collection,
            shortAiDescription = "",
            fullWebDescription = "",
            itemDescription = "Test description",
            detectedLabels = "test",
            estimatedValue = value,
            currency = "AUD",
            valueSource = "test",
            sourceUrl = "",
            searchResults = "",
            confidence = 0f,
            dateTaken = "2026-01-01",
            dateValued = 0L,
            notes = ""
        )
    }
}

