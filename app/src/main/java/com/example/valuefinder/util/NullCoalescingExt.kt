package com.example.valuefinder.util

/**
 * Standardized null-coalescing extensions to promote consistent patterns.
 */

/**
 * If T is null, return empty list; otherwise return list containing T.
 * Useful alternative to `?:` for null-to-empty-collection conversion.
 */
fun <T> T?.toListOrEmpty(): List<T> =
    if (this != null) listOf(this) else emptyList()

/**
 * Safe getOrNull wrapper: returns the value or empty string (never null).
 * Prefer over `.orEmpty()` for clarity when explicitly handling null Results.
 */
fun <T> Result<T>.getOrEmpty(mapper: (T) -> String = { "" }): String =
    getOrNull()?.let { mapper(it) }.orEmpty()

/**
 * Executes block, catches any exception, logs at debug level, and returns result.
 * Prefer over silent `runCatching { }.getOrNull()` when debugging is useful.
 */
inline fun <T> tryOrDefault(tag: String = "tryOrDefault", default: T, block: () -> T): T =
    runCatching(block).getOrElse { exception ->
        android.util.Log.d(tag, "Exception in try block; returning default", exception)
        default
    }

