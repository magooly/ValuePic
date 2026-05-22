package com.example.valuefinder

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

// ════════════════════════════════════════════════════════════════════════
// SCHEMA CHANGE CHECKLIST — follow this every time you add/change an
// @Entity field, or the app will crash on devices that already have data.
//
//  1. Bump the version number below  (e.g. 9 → 10)
//  2. Add a new MIGRATION_N_(N+1) object in the companion object below
//     with the matching ALTER TABLE (or CREATE TABLE) SQL
//  3. Register it in .addMigrations(...) inside getDatabase()
//  4. Rebuild and test on a device that already has the old version
//
// Current version: 14
// ════════════════════════════════════════════════════════════════════════
@Database(
    entities = [ValuedItem::class, ValuationDraftEntity::class, ItemPhoto::class],
    version = 14,
    exportSchema = true
)
abstract class ValuePicsDatabase : RoomDatabase() {
    abstract fun valuedItemDao(): ValuedItemDao
    abstract fun valuationDraftDao(): ValuationDraftDao
    abstract fun itemPhotoDao(): ItemPhotoDao

    companion object {
        const val DATABASE_NAME = "value_finder_database"

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE valued_items ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE valued_items ADD COLUMN includeInTotals INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS valuation_drafts (
                        photoPath TEXT NOT NULL,
                        schemaVersion INTEGER NOT NULL,
                        photoSource TEXT NOT NULL,
                        itemName TEXT NOT NULL,
                        itemDescription TEXT NOT NULL,
                        itemTags TEXT NOT NULL,
                        editableValue TEXT NOT NULL,
                        userEditedValue INTEGER NOT NULL,
                        selectedCollection TEXT NOT NULL,
                        doNotIncludeInTotals INTEGER NOT NULL,
                        detailedLookupMode INTEGER NOT NULL,
                        savedAtMillis INTEGER NOT NULL,
                        PRIMARY KEY(photoPath)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE valued_items ADD COLUMN createdAtMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE valued_items SET createdAtMillis = CASE WHEN dateValued > 0 THEN dateValued ELSE CAST(strftime('%s','now') AS INTEGER) * 1000 END WHERE createdAtMillis = 0"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Rebuild valued_items to match the current Room schema exactly.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `valued_items_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `photoPath` TEXT NOT NULL,
                        `photoSource` TEXT NOT NULL,
                        `itemName` TEXT NOT NULL,
                        `collectionName` TEXT NOT NULL,
                        `shortAiDescription` TEXT NOT NULL,
                        `fullWebDescription` TEXT NOT NULL,
                        `itemDescription` TEXT NOT NULL,
                        `detectedLabels` TEXT NOT NULL,
                        `estimatedValue` REAL,
                        `currency` TEXT NOT NULL,
                        `valueSource` TEXT NOT NULL,
                        `sourceUrl` TEXT NOT NULL,
                        `searchResults` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `dateTaken` TEXT NOT NULL,
                        `dateValued` INTEGER NOT NULL,
                        `notes` TEXT NOT NULL,
                        `tags` TEXT NOT NULL,
                        `includeInTotals` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    INSERT INTO `valued_items_new` (
                        `id`, `photoPath`, `photoSource`, `itemName`, `collectionName`,
                        `shortAiDescription`, `fullWebDescription`, `itemDescription`, `detectedLabels`,
                        `estimatedValue`, `currency`, `valueSource`, `sourceUrl`, `searchResults`,
                        `confidence`, `createdAtMillis`, `dateTaken`, `dateValued`, `notes`, `tags`, `includeInTotals`
                    )
                    SELECT
                        `id`,
                        IFNULL(`photoPath`, ''),
                        IFNULL(`photoSource`, 'camera'),
                        IFNULL(`itemName`, ''),
                        IFNULL(`collectionName`, ''),
                        IFNULL(`shortAiDescription`, ''),
                        IFNULL(`fullWebDescription`, ''),
                        IFNULL(`itemDescription`, ''),
                        IFNULL(`detectedLabels`, ''),
                        `estimatedValue`,
                        IFNULL(`currency`, 'AUD'),
                        IFNULL(`valueSource`, ''),
                        IFNULL(`sourceUrl`, ''),
                        IFNULL(`searchResults`, ''),
                        IFNULL(`confidence`, 0),
                        CASE WHEN IFNULL(`createdAtMillis`, 0) > 0
                             THEN `createdAtMillis`
                             ELSE CASE WHEN IFNULL(`dateValued`, 0) > 0
                                       THEN `dateValued`
                                       ELSE CAST(strftime('%s','now') AS INTEGER) * 1000 END
                        END,
                        IFNULL(`dateTaken`, ''),
                        IFNULL(`dateValued`, CAST(strftime('%s','now') AS INTEGER) * 1000),
                        IFNULL(`notes`, ''),
                        IFNULL(`tags`, ''),
                        IFNULL(`includeInTotals`, 1)
                    FROM `valued_items`
                    """.trimIndent()
                )

                db.execSQL("DROP TABLE `valued_items`")
                db.execSQL("ALTER TABLE `valued_items_new` RENAME TO `valued_items`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_valued_items_collectionName` ON `valued_items` (`collectionName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_valued_items_dateValued` ON `valued_items` (`dateValued`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_valued_items_estimatedValue` ON `valued_items` (`estimatedValue`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_valued_items_itemName` ON `valued_items` (`itemName`)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `item_photos` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `itemId` INTEGER NOT NULL,
                        `photoPath` TEXT NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `isCover` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        FOREIGN KEY(`itemId`) REFERENCES `valued_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_photos_itemId` ON `item_photos` (`itemId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_photos_itemId_sortOrder` ON `item_photos` (`itemId`, `sortOrder`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_item_photos_itemId_isCover` ON `item_photos` (`itemId`, `isCover`)")
                db.execSQL(
                    """
                    INSERT INTO `item_photos` (`itemId`, `photoPath`, `sortOrder`, `isCover`, `createdAtMillis`)
                    SELECT
                        `id`,
                        `photoPath`,
                        0,
                        1,
                        CASE WHEN IFNULL(`createdAtMillis`, 0) > 0
                             THEN `createdAtMillis`
                             ELSE CAST(strftime('%s','now') AS INTEGER) * 1000 END
                    FROM `valued_items`
                    WHERE TRIM(IFNULL(`photoPath`, '')) != ''
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE valued_items ADD COLUMN excludeFromPdfReport INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE valued_items ADD COLUMN willInstructions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE valuation_drafts ADD COLUMN willInstructions TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE valued_items ADD COLUMN tier TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE valued_items ADD COLUMN billsEnteredPeriod TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var instance: ValuePicsDatabase? = null

        fun getDatabase(context: Context): ValuePicsDatabase {
            return instance ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    ValuePicsDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                    // Versions 1-3 pre-date migration tracking; destructive migration is
                    // acceptable because those builds were internal/dev-only.
                    .fallbackToDestructiveMigrationFrom(false, 1, 2, 3)
                    .build()
                instance = db
                db
            }
        }
    }
}
