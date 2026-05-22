package com.example.valuefinder.ui

import java.util.Locale

internal fun enforceLeadingCapitalization(input: String): String {
    val firstLetterIndex = input.indexOfFirst { it.isLetter() }
    if (firstLetterIndex < 0) return input

    val original = input[firstLetterIndex].toString()
    val upper = original.uppercase(Locale.getDefault())
    if (original == upper) return input

    return input.replaceRange(firstLetterIndex, firstLetterIndex + 1, upper)
}

