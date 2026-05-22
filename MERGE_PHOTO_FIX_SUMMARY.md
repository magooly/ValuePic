# Multi-Photo Merge Issue - Fix Summary

## Problem Description
When merging a backup from build 15 (that contains records with multiple photos) into another device also running build 15, the extra/additional photos were being lost during the merge operation.

## Root Cause
The `mergeFromUri()` function in `ValuePicsRepository.kt` was only handling the cover photo stored in the `valued_items` table, but it was completely ignoring the additional photos stored in the separate `item_photos` table.

### Technical Details:
1. **ValueFinder's photo storage structure:**
   - Each item has ONE cover photo stored in `valued_items.photoPath`
   - Each item can have MULTIPLE additional photos stored in the `item_photos` table
   - The `item_photos` table tracks: itemId, photoPath, sortOrder, isCover, and createdAtMillis

2. **What the old code did:**
   - Read imported items from the backup
   - Matched existing items by "mergeFingerprint" (name, description, collection, date taken, source URL)
   - For new items: inserted them with only the cover photo
   - For existing items: updated them with only the cover photo
   - **Missing:** No attempt to restore additional photos from the `item_photos` table

3. **What the restore function did differently:**
   - The existing `restoreDatabaseZip()` function correctly called `readItemPhotosFromDatabaseZip()` to get ALL additional photos
   - It then rebuilt the item_photos table with proper ID mappings

## Solution Implemented
Updated the `mergeFromUri()` function (lines 677-833) to properly handle multi-photo merge:

### Key Changes:
1. **Added photo reading during merge:**
   ```kotlin
   val importedItemPhotos: List<ImportedItemPhotoRecord>
   // ... in the database branch:
   importedItemPhotos = readItemPhotosFromDatabaseZip(importedDb)
   ```

2. **Created ID mapping for database relationships:**
   ```kotlin
   val oldToNewItemIdMapping = mutableMapOf<Int, Int>()
   // Track both new and existing items
   oldToNewItemIdMapping[imported.id] = insertedId  // for new items
   oldToNewItemIdMapping[imported.id] = existing.id // for existing items
   ```

3. **Added photo merge loop after item merge:**
   ```kotlin
   importedItemPhotos.forEach { importedPhoto ->
       val newItemId = oldToNewItemIdMapping[importedPhoto.itemId] ?: return@forEach
       // ... map photo path, check for duplicates, and insert if unique
   }
   ```

4. **Implemented duplicate detection:**
   - Before adding a photo, checks if the exact same photo (by path and sort order) already exists
   - This prevents duplicate photo entries when merging items
   - Properly handles cover photo designation if needed

### Photo Handling Logic:
- **New items (not in target device):** All additional photos are imported with their sort order preserved
- **Existing items (already in target):** Only non-duplicate photos are added
- **Cover photos:** If an imported photo is marked as cover, appropriate updates are made
- **Path remapping:** Imported photo paths are correctly mapped to the new extracted photo locations

## Testing Recommendations
1. Create a backup on Device A with an item that has 3+ photos
2. Test merging this backup into Device B (clean or with existing data)
3. Verify all photos appear in the item on Device B
4. Verify photo order (sortOrder field) is preserved
5. Verify cover photo designation is correct
6. Test merging when both devices have the same item (deduplication works)

## Files Modified
- `C:\wrhor\DataBase\app\src\main\java\com\example\valuefinder\ValuePicsRepository.kt`
  - Function: `mergeFromUri()` (lines 677-833)

## Build Compatibility
This fix applies to build 15+ which has the multi-photo feature. Older builds won't be affected as they didn't have the `item_photos` table.

## Notes
- The fix maintains backward compatibility with JSON-based backups (newer format) which don't include item_photos data
- It only applies the additional photo merging when importing from a database ZIP that contains the `item_photos` table
- Error handling is wrapped in `runCatching` to ensure one photo failure doesn't break the entire merge

