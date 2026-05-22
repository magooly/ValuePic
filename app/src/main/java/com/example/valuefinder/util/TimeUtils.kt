package com.example.valuefinder.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized time and date formatting utilities.
 */
object TimeUtils {
    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val dateFormatWithMs = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)

    /** Returns formatted timestamp (yyyyMMdd_HHmmss) */
    fun getCurrentTimestamp(): String = dateFormat.format(Date())

    /** Returns formatted timestamp with milliseconds (yyyyMMdd_HHmmss_SSS) for collision-free filenames */
    fun getCurrentTimestampWithMs(): String = dateFormatWithMs.format(Date())

    /** Formats a given date to yyyyMMdd_HHmmss */
    fun formatTimestamp(date: Date): String = dateFormat.format(date)

    /** Formats a given date to yyyyMMdd_HHmmss_SSS */
    fun formatTimestampWithMs(date: Date): String = dateFormatWithMs.format(date)
}

