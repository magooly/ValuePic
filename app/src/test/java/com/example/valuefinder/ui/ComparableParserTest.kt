package com.example.valuefinder.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComparableParserTest {

    @Test
    fun parseComparableRows_parsesPipeSeparatedFields() {
        val raw = "Vintage Watch | 120 | eBay | https://example.com/watch"

        val rows = parseComparableRows(raw)

        assertEquals(1, rows.size)
        assertEquals("Vintage Watch", rows[0].title)
        assertEquals("120", rows[0].price)
        assertEquals("eBay", rows[0].source)
        assertEquals("https://example.com/watch", rows[0].url)
    }

    @Test
    fun parseComparableRows_appliesDefaultsForMissingFields() {
        val raw = "Only title"

        val rows = parseComparableRows(raw)

        assertEquals(1, rows.size)
        assertEquals("Only title", rows[0].title)
        assertEquals("", rows[0].price)
        assertEquals("", rows[0].source)
        assertEquals("", rows[0].url)
    }

    @Test
    fun parseComparableRows_ignoresBlankLinesAndTrimsFields() {
        val raw = "\n  Item A  |  10  |  Site  |  url  \n\n  \nItem B|20|Store|link\n"

        val rows = parseComparableRows(raw)

        assertEquals(2, rows.size)
        assertEquals("Item A", rows[0].title)
        assertEquals("10", rows[0].price)
        assertEquals("Site", rows[0].source)
        assertEquals("url", rows[0].url)
        assertEquals("Item B", rows[1].title)
    }

    @Test
    fun parseComparableRows_returnsEmptyListForBlankInput() {
        val rows = parseComparableRows("  \n \n  ")
        assertTrue(rows.isEmpty())
    }

    @Test
    fun parseComparableRows_defaultsBlankTitleToComparable() {
        val raw = " | 88 | Shop | https://example.com"

        val rows = parseComparableRows(raw)

        assertEquals(1, rows.size)
        assertEquals("Comparable", rows[0].title)
        assertEquals("88", rows[0].price)
    }

    @Test
    fun parseComparableRows_ignoresExtraSegmentsAfterUrl() {
        val raw = "Title | 99 | Site | https://example.com | ignored | more"

        val rows = parseComparableRows(raw)

        assertEquals(1, rows.size)
        assertEquals("Title", rows[0].title)
        assertEquals("99", rows[0].price)
        assertEquals("Site", rows[0].source)
        assertEquals("https://example.com", rows[0].url)
    }
}

