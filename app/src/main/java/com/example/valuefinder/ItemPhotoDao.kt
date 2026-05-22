package com.example.valuefinder

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemPhotoDao {
    @Query("SELECT * FROM item_photos WHERE itemId = :itemId ORDER BY sortOrder ASC, id ASC")
    fun getPhotosForItem(itemId: Int): Flow<List<ItemPhoto>>

    @Query("SELECT * FROM item_photos WHERE itemId = :itemId ORDER BY sortOrder ASC, id ASC")
    suspend fun getPhotosForItemSnapshot(itemId: Int): List<ItemPhoto>

    @Query("SELECT * FROM item_photos WHERE id = :photoId LIMIT 1")
    suspend fun getPhotoById(photoId: Int): ItemPhoto?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhoto(photo: ItemPhoto): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPhotos(photos: List<ItemPhoto>): List<Long>

    @Query("DELETE FROM item_photos WHERE id = :photoId")
    suspend fun deletePhotoById(photoId: Int)

    @Query("DELETE FROM item_photos WHERE itemId = :itemId")
    suspend fun deletePhotosByItemId(itemId: Int)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM item_photos WHERE itemId = :itemId")
    suspend fun getMaxSortOrder(itemId: Int): Int

    @Query("UPDATE item_photos SET isCover = 0 WHERE itemId = :itemId")
    suspend fun clearCoverForItem(itemId: Int)

    @Query("UPDATE item_photos SET isCover = 1 WHERE id = :photoId")
    suspend fun markAsCover(photoId: Int)

    @Query("SELECT photoPath FROM item_photos")
    suspend fun getAllPhotoPaths(): List<String>

    @Query("SELECT COUNT(*) FROM item_photos")
    suspend fun countAllRows(): Int

    @Query("SELECT COUNT(DISTINCT photoPath) FROM item_photos")
    suspend fun countDistinctPhotoPaths(): Int

    @Query("SELECT DISTINCT photoPath FROM item_photos WHERE TRIM(photoPath) != ''")
    suspend fun getDistinctNonBlankPhotoPaths(): List<String>

    @Query("SELECT COUNT(*) FROM item_photos WHERE photoPath = :photoPath")
    suspend fun countByPhotoPath(photoPath: String): Int

    @Query("DELETE FROM item_photos")
    suspend fun clearAllPhotos()

    /** Returns every row ordered so dedup logic can group them predictably. */
    @Query("SELECT * FROM item_photos ORDER BY itemId ASC, isCover DESC, sortOrder ASC, id ASC")
    suspend fun getAllPhotosOrdered(): List<ItemPhoto>

    @Transaction
    suspend fun setCoverPhoto(itemId: Int, photoId: Int) {
        clearCoverForItem(itemId)
        markAsCover(photoId)
    }
}

