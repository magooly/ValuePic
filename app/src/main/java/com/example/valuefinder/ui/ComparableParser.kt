package com.example.valuefinder.ui

import android.util.Log
import java.net.URI

data class ComparableRow(
    val title: String,
    val price: String,
    val source: String,
    val url: String
)

fun parseComparableRows(raw: String): List<ComparableRow> {
    val urlRegex = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
    return raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { line ->
            val parts = line.split("|").map { it.trim() }
            val urlFromLine = urlRegex.find(line)?.value.orEmpty()
            val explicitUrl = parts.getOrElse(3) { "" }.trim()
            val resolvedUrl = explicitUrl.ifBlank { urlFromLine }
            val baseTitle = parts.getOrElse(0) { "Comparable" }.ifBlank { "Comparable" }
            val normalizedTitle = if (resolvedUrl.isNotBlank() && baseTitle.equals(resolvedUrl, ignoreCase = true)) {
                "Comparable link"
            } else {
                baseTitle
            }
            val sourceFromUrl = extractHostFromUrl(resolvedUrl)
            ComparableRow(
                title = normalizedTitle,
                price = parts.getOrElse(1) { "" },
                source = parts.getOrElse(2) { "" }.ifBlank { sourceFromUrl },
                url = resolvedUrl
            )
        }
        .toList()
}

private fun extractHostFromUrl(url: String): String {
    if (url.isBlank()) return ""
    return runCatching {
        URI(url).host?.removePrefix("www.").orEmpty()
    }.getOrElse { exception ->
        Log.d("ComparableParser", "Failed to parse host from URL: $url", exception)
        ""
    }
}

