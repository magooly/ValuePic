package com.example.valuefinder

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ValuationDraftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDraft(draft: ValuationDraftEntity)

    @Query("SELECT * FROM valuation_drafts WHERE photoPath = :photoPath LIMIT 1")
    suspend fun getDraftByPhotoPath(photoPath: String): ValuationDraftEntity?

    @Query("SELECT * FROM valuation_drafts ORDER BY savedAtMillis DESC LIMIT 1")
    fun observeLatestDraft(): Flow<ValuationDraftEntity?>

    @Query("SELECT * FROM valuation_drafts ORDER BY savedAtMillis DESC LIMIT 1")
    suspend fun getLatestDraft(): ValuationDraftEntity?

    @Query("SELECT COUNT(*) FROM valuation_drafts")
    fun observeDraftCount(): Flow<Int>

    @Query("DELETE FROM valuation_drafts WHERE photoPath = :photoPath")
    suspend fun deleteDraftByPhotoPath(photoPath: String)

    @Query("DELETE FROM valuation_drafts")
    suspend fun clearAllDrafts()
}

