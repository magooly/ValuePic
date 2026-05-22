package com.example.valuefinder

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object MoneyUtils {
    // NumberFormat is not thread-safe; use a ThreadLocal so each thread gets
    // its own instance and concurrent coroutine rendering cannot corrupt output.
    private val audFormatter: ThreadLocal<NumberFormat> = ThreadLocal.withInitial {
        NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-AU")).apply {
            currency = Currency.getInstance("AUD")
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }
    }

    fun formatAud(value: Double): String = audFormatter.get()!!.format(value)

    /** Returns "—" for null/unknown values so they are visually distinct from a genuine $0.00 */
    fun formatAud(value: Double?): String = if (value == null) "—" else formatAud(value)
}

