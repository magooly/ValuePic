package com.example.valuefinder

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ValuePicsDatabaseMigrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = ValuePicsDatabase.DATABASE_NAME

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ValuePicsDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun teardown() {
        resetDatabaseSingleton()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate8To9_preservesItemAndNormalizesCreatedAtMillis() {
        helper.createDatabase(dbName, 8).apply {
            execSQL(
                """
                INSERT INTO valued_items (
                    id, photoPath, photoSource, itemName, collectionName,
                    shortAiDescription, fullWebDescription, itemDescription, detectedLabels,
                    estimatedValue, currency, valueSource, sourceUrl, searchResults,
                    confidence, createdAtMillis, dateTaken, dateValued, notes, tags, includeInTotals
                ) VALUES (
                    1, 'photo.jpg', 'camera', 'Vintage Watch', 'Collectables',
                    'short', 'full', 'desc', 'watch,metal',
                    120.0, 'AUD', 'manual', 'https://example.com', 'result',
                    0.9, 0, '2026-04-01 10:00:00', 1711920000000, 'note', 'tag1', 1
                )
                """.trimIndent()
            )
            close()
        }

        resetDatabaseSingleton()

        val db = ValuePicsDatabase.getDatabase(context)
        runBlocking {
            val items = db.valuedItemDao().getAllItemsSnapshot(AppTier.PERSONAL.name)
            assertEquals(1, items.size)
            val item = items.first()
            assertEquals("Vintage Watch", item.itemName)
            // Migration 8->9 should backfill createdAtMillis when zero.
            assertTrue(item.createdAtMillis > 0L)
            assertEquals(1711920000000L, item.createdAtMillis)
        }

        val writable = db.openHelper.writableDatabase
        writable.query("PRAGMA index_list('valued_items')").use { cursor ->
            var foundCollectionIndex = false
            while (cursor.moveToNext()) {
                val indexName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (indexName == "index_valued_items_collectionName") {
                    foundCollectionIndex = true
                }
            }
            assertTrue("Expected collectionName index after migration", foundCollectionIndex)
        }

        db.close()
    }

    @Test
    fun migrate6To9_chainCompletesWithoutDataLoss() {
        helper.createDatabase(dbName, 6).apply {
            execSQL(
                """
                INSERT INTO valued_items (
                    id, photoPath, photoSource, itemName, collectionName,
                    shortAiDescription, fullWebDescription, itemDescription, detectedLabels,
                    estimatedValue, currency, valueSource, sourceUrl, searchResults,
                    confidence, dateTaken, dateValued, notes, tags, includeInTotals
                ) VALUES (
                    2, 'legacy.jpg', 'gallery', 'Legacy Item', 'Archive',
                    'short', 'full', 'desc', 'label',
                    55.0, 'AUD', 'legacy', '', '',
                    0.4, '2026-01-01 00:00:00', 1704067200000, '', '', 1
                )
                """.trimIndent()
            )
            close()
        }

        resetDatabaseSingleton()

        val db = ValuePicsDatabase.getDatabase(context)
        runBlocking {
            val items = db.valuedItemDao().getAllItemsSnapshot(AppTier.PERSONAL.name)
            assertEquals(1, items.size)
            val item = items.first()
            assertEquals("Legacy Item", item.itemName)
            // Column added in 7->8 migration should be present and initialized.
            assertTrue(item.createdAtMillis > 0L)
        }

        db.close()
    }

    private fun resetDatabaseSingleton() {
        runCatching {
            val field = ValuePicsDatabase::class.java.getDeclaredField("instance")
            field.isAccessible = true
            field.set(null, null)
        }
    }
}

