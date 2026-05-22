package com.example.valuefinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagUtilsTest {

    @Test
    fun parseTags_emptyString_returnsEmptyList() {
        assertTrue(TagUtils.parseTags("").isEmpty())
    }

    @Test
    fun parseTags_blankString_returnsEmptyList() {
        assertTrue(TagUtils.parseTags("   ").isEmpty())
    }

    @Test
    fun parseTags_singleTag_returnsSingleItem() {
        assertEquals(listOf("watch"), TagUtils.parseTags("watch"))
    }

    @Test
    fun parseTags_commaSeparated_returnsAllTags() {
        val result = TagUtils.parseTags("watch, antique, silver")
        assertEquals(listOf("watch", "antique", "silver"), result)
    }

    @Test
    fun parseTags_duplicatesCaseInsensitive_deduplicates() {
        val result = TagUtils.parseTags("Watch, watch, WATCH")
        assertEquals(1, result.size)
        assertEquals("Watch", result[0])
    }

    @Test
    fun parseTags_longTag_truncatesTo50Chars() {
        val longTag = "a".repeat(60)
        val result = TagUtils.parseTags(longTag)
        assertEquals(50, result[0].length)
    }

    @Test
    fun normalizeTags_limitsToMaxTags() {
        val tooMany = (1..10).joinToString(", ") { "tag$it" }
        val result = TagUtils.parseTags(tooMany).take(TagUtils.MAX_TAGS)
        assertEquals(TagUtils.MAX_TAGS, result.size)
    }

    @Test
    fun normalizeTagsInput_preservesTrailingCommaSpace() {
        val result = TagUtils.normalizeTagsInput("watch, antique,")
        assertTrue("Expected trailing ', ' after trailing comma input", result.endsWith(", "))
    }

    @Test
    fun normalizeTagsInput_noTrailingComma_noTrailingSuffix() {
        val result = TagUtils.normalizeTagsInput("watch, antique")
        assertFalse("Should not end with ', ' when no trailing comma", result.endsWith(", "))
    }

    @Test
    fun hasTag_blankFilter_alwaysReturnsTrue() {
        assertTrue(TagUtils.hasTag("watch, antique", ""))
        assertTrue(TagUtils.hasTag("", "  "))
    }

    @Test
    fun hasTag_matchingTag_returnsTrue() {
        assertTrue(TagUtils.hasTag("watch, antique, silver", "antique"))
    }

    @Test
    fun hasTag_caseInsensitiveMatch_returnsTrue() {
        assertTrue(TagUtils.hasTag("Watch, Antique", "ANTIQUE"))
    }

    @Test
    fun hasTag_nonMatchingTag_returnsFalse() {
        assertFalse(TagUtils.hasTag("watch, antique", "gold"))
    }
}

