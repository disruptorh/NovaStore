package com.novastore.app.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.novastore.app.core.database.NovaDatabase
import com.novastore.app.core.database.entity.RemoteAppEntity
import com.novastore.app.core.database.entity.RepositoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * P06-T07: every listing is deduplicated the same way — one row per package,
 * and the row comes from the highest-priority (min priority) repository.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogDaoDedupTest {

    private fun db(): NovaDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        NovaDatabase::class.java,
    ).build()

    private fun repository(id: String, priority: Int) = RepositoryEntity(
        repositoryId = id,
        name = id.uppercase(),
        baseUrl = "https://$id.invalid",
        metadataUrl = "https://$id.invalid/index-v2.json",
        trust = "TRUSTED",
        enabled = true,
        isBuiltIn = false,
        lastRefreshAt = null,
        lastRefreshError = null,
        priority = priority,
    )

    private fun app(pkg: String, name: String, source: String, lastUpdatedAt: Long?) = RemoteAppEntity(
        packageName = pkg,
        name = name,
        summary = null,
        developer = null,
        iconUrl = null,
        license = null,
        description = null,
        changelog = null,
        website = null,
        sourceCodeUrl = null,
        categories = "Tools",
        source = source,
        addedAt = lastUpdatedAt,
        lastUpdatedAt = lastUpdatedAt,
    )

    @Test
    fun observeRecent_returnsMinPriorityRowPerPackage() = runTest {
        val db = db()
        try {
            val dao = db.catalogDao()
            val repos = db.repositoryDao()
            repos.upsert(repository("repo-low", priority = 10))
            repos.upsert(repository("repo-high", priority = 50))

            // Same package in both repositories; the low-priority row is the header that must win.
            dao.upsertApps(
                listOf(
                    app("com.example.app", name = "FromLow", source = "repo-low", lastUpdatedAt = 100),
                    app("com.example.app", name = "FromHigh", source = "repo-high", lastUpdatedAt = 500),
                ),
            )

            val rows = dao.observeRecent(limit = 10).first()
            assertEquals("one row per package", 1, rows.size)
            assertEquals("min-priority row wins", "FromLow", rows.single().name)
            assertEquals("repo-low", rows.single().source)
        } finally {
            db.close()
        }
    }

    @Test
    fun listRecent_ordersByMaxRecencyButPicksMinPriorityRow() = runTest {
        val db = db()
        try {
            val dao = db.catalogDao()
            val repos = db.repositoryDao()
            repos.upsert(repository("repo-low", priority = 10))
            repos.upsert(repository("repo-high", priority = 50))

            dao.upsertApps(
                listOf(
                    app("com.example.stale", name = "StaleLow", source = "repo-low", lastUpdatedAt = 100),
                    app("com.example.stale", name = "StaleHigh", source = "repo-high", lastUpdatedAt = 600),
                    app("com.example.fresh", name = "FreshOnly", source = "repo-high", lastUpdatedAt = 900),
                ),
            )

            val rows = dao.listRecent(offset = 0, limit = 10)
            assertEquals(2, rows.size)
            // Fresh package surfaces first (recency), stale second.
            assertEquals("com.example.fresh", rows[0].packageName)
            assertEquals("com.example.stale", rows[1].packageName)
            // Header of the stale package comes from the min-priority repo despite the fresher row.
            assertEquals("StaleLow", rows[1].name)
            assertEquals("repo-low", rows[1].source)
        } finally {
            db.close()
        }
    }

    @Test
    fun observeRecent_equalPriorityTiesStillOneRow() = runTest {
        val db = db()
        try {
            val dao = db.catalogDao()
            val repos = db.repositoryDao()
            repos.upsert(repository("repo-a", priority = 10))
            repos.upsert(repository("repo-b", priority = 10))

            dao.upsertApps(
                listOf(
                    app("com.example.app", name = "FromA", source = "repo-a", lastUpdatedAt = 100),
                    app("com.example.app", name = "FromB", source = "repo-b", lastUpdatedAt = 200),
                ),
            )

            val rows = dao.observeRecent(limit = 10).first()
            assertEquals("tie yields exactly one row", 1, rows.size)
            assertTrue(
                "either source is a valid min-priority winner",
                rows.single().source == "repo-a" || rows.single().source == "repo-b",
            )
        } finally {
            db.close()
        }
    }
}