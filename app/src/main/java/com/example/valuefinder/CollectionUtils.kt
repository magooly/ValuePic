package com.example.valuefinder

import java.util.Locale

/**
 * Computes totals per collection, sorted by value (descending) then name (ascending).
 */
internal fun buildCollectionTotals(byCollection: Map<String, List<ValuedItem>>): List<Pair<String, Double>> {
    return byCollection
        .map { (collectionName, rows) ->
            collectionName to rows.mapNotNull { it.estimatedValue }.sum()
        }
        .sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first.lowercase(Locale.US) })
}

