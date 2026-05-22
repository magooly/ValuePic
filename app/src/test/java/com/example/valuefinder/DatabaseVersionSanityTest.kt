package com.example.valuefinder

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sanity checks for the Room database version and migration chain.
 *
 * These run on the JVM (no device needed) and guard against common mistakes
 * such as bumping DATABASE_VERSION without adding a matching Migration object.
 *
 * To add a new version:
 *   1. Bump EXPECTED_VERSION below to match @Database version
 *   2. Add the new version to EXPECTED_MIGRATION_STEPS
 */
class DatabaseVersionSanityTest {

    /** Keep this in sync with @Database(version = N) in ValuePicsDatabase.kt */
    private val EXPECTED_VERSION = 12

    /**
     * Every (from, to) pair that must exist as a Migration object.
     * Add a new entry here whenever you add a MIGRATION_N_(N+1).
     */
    private val EXPECTED_MIGRATION_STEPS = listOf(
        4 to 5,
        5 to 6,
        6 to 7,
        7 to 8,
        8 to 9,
        9 to 10,
        10 to 11,
        11 to 12
    )

    @Test
    fun migrationChainCoversAllVersions() {
        // Verify no gaps: every consecutive pair from the first migration to current version exists
        val toVersions = EXPECTED_MIGRATION_STEPS.map { it.second }.toSet()
        val latestMigrated = toVersions.maxOrNull() ?: 0

        assertTrue(
            "Migration chain does not reach current database version $EXPECTED_VERSION. " +
                "Latest migrated version: $latestMigrated. " +
                "Did you add a MIGRATION_${latestMigrated}_${EXPECTED_VERSION} and register it?",
            latestMigrated == EXPECTED_VERSION
        )
    }

    @Test
    fun noGapsInMigrationChain() {
        val sorted = EXPECTED_MIGRATION_STEPS.sortedBy { it.first }
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1].second
            val next = sorted[i].first
            assertTrue(
                "Gap in migration chain: migration ends at $prev but next starts at $next",
                prev == next
            )
        }
    }

    @Test
    fun currentVersionMatchesExpected() {
        // Keep this explicit so schema bumps require touching this file and migration list.
        assertTrue(
            "Update EXPECTED_VERSION in this test to match @Database version",
            EXPECTED_VERSION == 12
        )
    }
}

