package com.example.valuefinder

internal fun sortItemsForReport(items: List<ValuedItem>, sortOption: ReportSortOption): List<ValuedItem> {
    return when (sortOption) {
        ReportSortOption.NAME_AZ -> items.sortedWith(
            compareBy<ValuedItem, String>(String.CASE_INSENSITIVE_ORDER) { it.itemName }
                .thenByDescending { it.dateValued }
        )

        ReportSortOption.VALUE_HIGH -> items.sortedWith(
            compareByDescending<ValuedItem> { it.estimatedValue ?: Double.MIN_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.itemName }
                .thenByDescending { it.dateValued }
        )
    }
}


