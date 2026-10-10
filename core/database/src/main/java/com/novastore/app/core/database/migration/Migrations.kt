package com.novastore.app.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration 1 → 2: adds an index on the download queue state column,
 * which the active queue queries filter on.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_downloads_state ON downloads(state)")
    }
}

/**
 * Migration 3 → 4: Nova Resolver v8 — update confidence levels (EXACT vs
 * DISCOVERY), the Play Web Watch freshness table and the package trust
 * cache.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE updates ADD COLUMN confidence TEXT NOT NULL DEFAULT 'EXACT'")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS play_freshness (" +
                "packageName TEXT NOT NULL PRIMARY KEY, " +
                "playUpdatedMillis INTEGER, " +
                "checkedAt INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS package_trust (" +
                "packageName TEXT NOT NULL PRIMARY KEY, " +
                "certSha256 TEXT, " +
                "source TEXT, " +
                "firstSeenAt INTEGER NOT NULL, " +
                "lastSeenAt INTEGER NOT NULL, " +
                "installs INTEGER NOT NULL DEFAULT 0)",
        )
    }
}

/**
 * Migration 4 → 5: catalog/version query indexes. All ADDs — no data moves,
 * so migration failure cannot destroy existing rows.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_remote_apps_source ON remote_apps(source)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_remote_apps_packageName ON remote_apps(packageName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_remote_apps_lastUpdatedAt ON remote_apps(lastUpdatedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_versions_packageName ON app_versions(packageName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_app_versions_source ON app_versions(source)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_updates_state ON updates(state)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_repositories_enabled_priority ON repositories(enabled, priority)")
    }
}

/**
 * Migration 5 → 6: add providerType and extraJson to repositories.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE repositories ADD COLUMN providerType TEXT NOT NULL DEFAULT 'FDROID_INDEX'")
        db.execSQL("ALTER TABLE repositories ADD COLUMN extraJson TEXT")
    }
}

/**
 * Migration 6 → 7: provider-backed version identities. Adds the
 * [AppVersionEntity.identityFromArtifact] flag so a synthetic catalog key
 * survives the DB round-trip and verification adopts the manifest identity.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE app_versions ADD COLUMN identityFromArtifact INTEGER NOT NULL DEFAULT 0")
    }
}

val ALL_MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
