package com.novastore.app.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.novastore.app.core.database.NovaDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration4To5Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NovaDatabase::class.java,
    )

    @Test
    fun migrate4To5_keepsDataAndCreatesIndexes() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                "INSERT INTO remote_apps (packageName, name, categories, source, addedAt, lastUpdatedAt) " +
                    "VALUES ('com.example.app', 'Example', 'Internet', 'builtin-fdroid', 1, 2)",
            )
            execSQL(
                "INSERT INTO updates (packageName, installedVersionCode, availableVersionCode, source, " +
                    "downloadUrl, state, confidence, createdAt, updatedAt) " +
                    "VALUES ('com.example.app', 1, 2, 'builtin-fdroid', 'https://example.com/a.apk', " +
                    "'ERROR', 'EXACT', 1, 2)",
            )
            close()
        }

        helper.closeWhenFinished(
            helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5).also { db ->
                db.query("SELECT COUNT(*) FROM remote_apps").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }
                db.query("SELECT COUNT(*) FROM updates").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }
                db.query("PRAGMA index_list('remote_apps')").use { cursor ->
                    val names = buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(1))
                    }
                    assertTrue("index_remote_apps_source", "index_remote_apps_source" in names)
                    assertTrue("index_remote_apps_packageName", "index_remote_apps_packageName" in names)
                    assertTrue("index_remote_apps_lastUpdatedAt", "index_remote_apps_lastUpdatedAt" in names)
                }
                db.query("PRAGMA index_list('app_versions')").use { cursor ->
                    val names = buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(1))
                    }
                    assertTrue("index_app_versions_packageName", "index_app_versions_packageName" in names)
                    assertTrue("index_app_versions_source", "index_app_versions_source" in names)
                }
                db.query("PRAGMA index_list('updates')").use { cursor ->
                    val names = buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(1))
                    }
                    assertTrue("index_updates_state", "index_updates_state" in names)
                }
                db.query("PRAGMA index_list('repositories')").use { cursor ->
                    val names = buildSet {
                        while (cursor.moveToNext()) add(cursor.getString(1))
                    }
                    assertTrue(
                        "index_repositories_enabled_priority",
                        "index_repositories_enabled_priority" in names,
                    )
                }
            },
        )
    }

    private companion object {
        const val TEST_DB = "migration-test-4-5.db"
    }
}