package com.novastore.app.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.novastore.app.core.database.NovaDatabase
import com.novastore.app.core.database.entity.AppVersionEntity
import com.novastore.app.core.database.entity.RepositoryEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogDaoEnabledSourcesTest {

    private fun db(): NovaDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        NovaDatabase::class.java,
    ).build()

    private fun version(pkg: String, code: Long, source: String) = AppVersionEntity(
        packageName = pkg,
        versionCode = code,
        versionName = "1.$code",
        source = source,
        size = null,
        downloadUrl = "https://example.invalid/$source/$pkg/$code.apk",
        sha256 = null,
        minSdk = 21,
        targetSdk = null,
        addedAt = code,
        artifactType = "APK",
        signer = null,
        nativeCode = "",
    )

    private fun repository(id: String, enabled: Boolean, priority: Int) = RepositoryEntity(
        repositoryId = id,
        name = id.uppercase(),
        baseUrl = "https://$id.invalid",
        metadataUrl = "https://$id.invalid/index-v2.json",
        trust = "TRUSTED",
        enabled = enabled,
        isBuiltIn = false,
        lastRefreshAt = null,
        lastRefreshError = null,
        priority = priority,
    )

    @Test
    fun getVersionsFor_returnsEnabledSourcesOnly() = runTest {
        val db = db()
        try {
            val dao = db.catalogDao()
            val repos = db.repositoryDao()

            repos.upsert(repository("repo-a", enabled = true, priority = 10))
            repos.upsert(repository("repo-b", enabled = false, priority = 20))

            dao.upsertVersions(
                listOf(
                    version("com.example.app", code = 100, source = "repo-a"),
                    version("com.example.app", code = 200, source = "repo-b"),
                ),
            )

            val rows = dao.getVersionsFor(listOf("com.example.app"))

            assertEquals("disabled repo must not contribute versions", 1, rows.size)
            assertEquals("repo-a", rows.single().source)
            assertTrue(rows.none { it.source == "repo-b" })
        } finally {
            db.close()
        }
    }

    @Test
    fun getVersionsFor_missingRepositoriesYieldsNothing() = runTest {
        val db = db()
        try {
            val dao = db.catalogDao()
            db.repositoryDao().upsert(repository("repo-a", enabled = true, priority = 10))

            // Version row pointing at a repo that no longer exists.
            dao.upsertVersions(listOf(version("com.example.ghost", code = 1, source = "deleted-repo")))

            assertTrue(dao.getVersionsFor(listOf("com.example.ghost")).isEmpty())
        } finally {
            db.close()
        }
    }
}