package com.example.valuefinder

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ValuedItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: ValuedItem): Long

    @Update
    suspend fun updateItem(item: ValuedItem)

    @Delete
    suspend fun deleteItem(item: ValuedItem)

    @Query("SELECT * FROM valued_items WHERE id = :id")
    suspend fun getItemById(id: Int): ValuedItem?

    // Tier-scoped queries — tier="" is treated as PERSONAL for legacy data
    @Query("SELECT * FROM valued_items WHERE (tier = :tier OR (:tier = 'PERSONAL' AND tier = '')) ORDER BY dateValued DESC")
    fun getAllItems(tier: String): Flow<List<ValuedItem>>

    @Query("SELECT * FROM valued_items WHERE (tier = :tier OR (:tier = 'PERSONAL' AND tier = '')) ORDER BY dateValued DESC")
    suspend fun getAllItemsSnapshot(tier: String): List<ValuedItem>

    // Unscoped snapshot for internal use (backup reads its own tier's items separately)
    @Query("SELECT * FROM valued_items ORDER BY dateValued DESC")
    suspend fun getAllItemsSnapshotUnscoped(): List<ValuedItem>

    @Query("SELECT * FROM valued_items WHERE (tier = :tier OR (:tier = 'PERSONAL' AND tier = '')) AND (itemName LIKE '%' || :query || '%' OR itemDescription LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') ORDER BY dateValued DESC")
    fun searchItems(query: String, tier: String): Flow<List<ValuedItem>>

    @Query("SELECT * FROM valued_items WHERE (tier = :tier OR (:tier = 'PERSONAL' AND tier = '')) AND estimatedValue IS NOT NULL ORDER BY estimatedValue DESC")
    fun getItemsByValue(tier: String): Flow<List<ValuedItem>>

    @Query("DELETE FROM valued_items WHERE id = :id")
    suspend fun deleteItemById(id: Int)

    @Query("SELECT COUNT(*) FROM valued_items WHERE (tier = :tier OR (:tier = 'PERSONAL' AND tier = ''))")
    fun getTotalItems(tier: String): Flow<Int>

    @Query("SELECT SUM(estimatedValue) FROM valued_items WHERE (tier = :tier OR (:tier = 'PERSONAL' AND tier = '')) AND estimatedValue IS NOT NULL AND includeInTotals = 1")
    fun getTotalValue(tier: String): Flow<Double?>

    @Query("SELECT AVG(estimatedValue) FROM valued_items WHERE (tier = :tier OR (:tier = 'PERSONAL' AND tier = '')) AND estimatedValue IS NOT NULL AND includeInTotals = 1")
    fun getAverageValue(tier: String): Flow<Double?>

    @Query("SELECT DISTINCT collectionName FROM valued_items WHERE (tier = :tier OR (:tier = 'PERSONAL' AND tier = '')) AND collectionName IS NOT NULL AND TRIM(collectionName) != '' ORDER BY collectionName COLLATE NOCASE")
    fun getAllCollections(tier: String): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM valued_items WHERE TRIM(collectionName) = TRIM(:name) COLLATE NOCASE AND (tier = :tier OR (:tier = 'PERSONAL' AND tier = ''))")
    suspend fun countItemsInCollection(name: String, tier: String): Int

    @Query("UPDATE valued_items SET collectionName = :newName WHERE TRIM(collectionName) = TRIM(:oldName) COLLATE NOCASE AND (tier = :tier OR (:tier = 'PERSONAL' AND tier = ''))")
    suspend fun renameCollection(oldName: String, newName: String, tier: String): Int

    @Query("UPDATE valued_items SET collectionName = '' WHERE TRIM(collectionName) = TRIM(:name) COLLATE NOCASE AND (tier = :tier OR (:tier = 'PERSONAL' AND tier = ''))")
    suspend fun clearCollectionFromItems(name: String, tier: String): Int

    @Query("DELETE FROM valued_items WHERE (tier = :tier OR (:tier = 'PERSONAL' AND tier = ''))")
    suspend fun clearAllItemsByTier(tier: String)

    @Query("DELETE FROM valued_items")
    suspend fun clearAllItems()

    @Query("UPDATE valued_items SET photoPath = :photoPath WHERE id = :itemId")
    suspend fun updateCoverPhotoPath(itemId: Int, photoPath: String)

    @Query("SELECT COUNT(*) FROM valued_items WHERE photoPath = :photoPath")
    suspend fun countByPhotoPath(photoPath: String): Int

        /** Returns the number of items with the given name in a specific tier (used for duplicate-copy guard). */
        @Query("SELECT COUNT(*) FROM valued_items WHERE itemName = :itemName AND (tier = :tier OR (:tier = 'PERSONAL' AND tier = ''))")
        suspend fun countByNameInTier(itemName: String, tier: String): Int
    }
