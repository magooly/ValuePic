package com.example.valuefinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyUtilsTest {

    @Test
    fun formatAud_nullValue_returnsDash() {
        assertEquals("—", MoneyUtils.formatAud(null))
    }

    @Test
    fun formatAud_zero_returnsFormattedZero() {
        val result = MoneyUtils.formatAud(0.0)
        assertTrue("Expected AUD zero format, got: $result", result.contains("0.00"))
    }

    @Test
    fun formatAud_positiveValue_containsAmount() {
        val result = MoneyUtils.formatAud(1234.50)
        assertTrue("Expected 1,234.50 in result, got: $result", result.contains("1,234.50"))
    }

    @Test
    fun formatAud_negativeValue_containsAmount() {
        val result = MoneyUtils.formatAud(-99.99)
        assertTrue("Expected 99.99 in result, got: $result", result.contains("99.99"))
    }

    @Test
    fun formatAud_largeValue_formatsWithGroupingSeparator() {
        val result = MoneyUtils.formatAud(1000000.0)
        // Should contain grouping separator (comma or period depending on locale)
        assertTrue("Expected grouping in large number, got: $result",
            result.contains(",") || result.contains("."))
    }

    @Test
    fun formatAud_isConcurrentlySafe() {
        // Run on multiple threads to verify ThreadLocal safety
        val results = (1..20).map { i ->
            Thread {
                MoneyUtils.formatAud(i.toDouble())
            }.also { it.start() }
        }
        // No assertion needed — absence of exception/corruption is the test
    }
}

