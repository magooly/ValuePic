package com.example.valuefinder

import kotlin.math.ceil

const val BILLS_COLLECTION_NAME = "Bills"

enum class BillsPeriod(val storageValue: String) {
    WEEKLY("WEEKLY"),
    MONTHLY("MONTHLY"),
    YEARLY("YEARLY");

    companion object {
        fun fromStorageValue(raw: String?): BillsPeriod? {
            val normalized = raw.orEmpty().trim().uppercase()
            if (normalized.isBlank()) return null
            return entries.firstOrNull { it.storageValue == normalized }
        }
    }
}

fun isBillsCollectionName(collectionName: String): Boolean =
    collectionName.trim().equals(BILLS_COLLECTION_NAME, ignoreCase = true)

fun resolveBillsEnteredPeriod(collectionName: String, rawPeriod: String?): BillsPeriod? {
    if (!isBillsCollectionName(collectionName)) return null
    return BillsPeriod.fromStorageValue(rawPeriod) ?: BillsPeriod.MONTHLY
}

fun convertBillsAmount(
    amount: Double,
    from: BillsPeriod,
    to: BillsPeriod,
    roundUpToDollar: Boolean = true
): Double {
    val annual = when (from) {
        BillsPeriod.WEEKLY -> amount * 52.0
        BillsPeriod.MONTHLY -> amount * 12.0
        BillsPeriod.YEARLY -> amount
    }
    val converted = when (to) {
        BillsPeriod.WEEKLY -> annual / 52.0
        BillsPeriod.MONTHLY -> annual / 12.0
        BillsPeriod.YEARLY -> annual
    }
    return if (roundUpToDollar) ceil(converted) else converted
}
