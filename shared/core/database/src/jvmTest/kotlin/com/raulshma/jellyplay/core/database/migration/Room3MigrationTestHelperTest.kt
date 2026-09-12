package com.raulshma.jellyplay.core.database.migration

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

/**
 * room3 spike smoke test (criterion c): proves androidx.room3:room3-testing's
 * [MigrationTestHelper] works in the jvmTest lane AND doubles as a
 * room3-native schema-identity gate (criterion b): the helper deserializes
 * the tracked Room 2.8-format schema JSONs (52.json, 53.json) with room3's
 * own [androidx.room3.migration.bundle.SchemaBundle] reader, creates a v52
 * database from the historical DDL, then validates the migrated v53 database
 * against the tracked v53 schema using room3's TableInfo comparison — the
 * same algorithm Room 3 itself uses when opening a migrated database
 * on device.
 */
class Room3MigrationTestHelperTest {

    @Test
    fun helperCreatesV52AndValidatesMigrationToV53AgainstTrackedSchemas() = runTest {
        val dbDir = createTempDirectory("room3-helper")
        val helper = MigrationTestHelper(
            // Gradle's jvmTest working directory is the module directory;
            // schemas/ sits directly under it (same tree the KSP arg points
            // at).
            schemaDirectoryPath = Path.of("schemas"),
            databasePath = dbDir.resolve("helper-test.db"),
            driver = BundledSQLiteDriver(),
            databaseClass = JellyPlayDatabase::class,
        )
        // Create at v52 from the tracked 52.json — fails loudly if room3
        // cannot parse the Room 2.8 schema JSON format.
        helper.createDatabase(52).close()
        // Migrate v52 -> v53 and validate the result against the tracked
        // 53.json — fails loudly if room3's expected schema drifted from
        // what Room 2.8.4 exported.
        helper.runMigrationsAndValidate(53, listOf(MIGRATION_52_53)).close()
    }
}
