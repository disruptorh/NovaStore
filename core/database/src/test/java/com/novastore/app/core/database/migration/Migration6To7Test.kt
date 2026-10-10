package com.novastore.app.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.novastore.app.core.database.NovaDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Migration6To7Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NovaDatabase::class.java,
    )

    @Test
    fun migrate6To7_addsIdentityFlagAndKeepsData() {
        helper.createDatabase(TEST_DB, 6).apply {
            execSQL(
                "INSERT INTO app_versions (packageName, versionCode, versionName, source, size, " +
                    "downloadUrl, sha256, minSdk, targetSdk, addedAt, artifactType, signer, nativeCode) " +
                    "VALUES ('novasrc.github.abc', 1, '1.0', 'custom-1', NULL, " +
                    "'https://example.com/a.apk', NULL, NULL, NULL, NULL, 'APK', NULL, '')",
            )
            close()
        }

        helper.closeWhenFinished(
            helper.runMigrationsAndValidate(TEST_DB, 7, true, MIGRATION_6_7).also { db ->
                db.query("SELECT COUNT(*) FROM app_versions").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(1, cursor.getInt(0))
                }
                db.query("SELECT identityFromArtifact FROM app_versions").use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(0, cursor.getInt(0))
                }
            },
        )
    }

    private companion object {
        const val TEST_DB = "migration-test-6-7.db"
    }
}
