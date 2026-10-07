package com.novastore.app.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.novastore.app.core.database.NovaDatabase
import com.novastore.app.core.database.entity.RepositoryEntity
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.SourceTrust
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryDaoUpdateTest {

    @Test
    fun editPersistsNameUrlTypeAndExtra() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovaDatabase::class.java,
        ).build()
        val dao = db.repositoryDao()
        try {
            dao.upsert(
                RepositoryEntity(
                    repositoryId = "custom-abc",
                    name = "Old name",
                    baseUrl = "https://example.com/fdroid/repo",
                    metadataUrl = "https://example.com/fdroid/repo/index-v2.json",
                    trust = SourceTrust.UNKNOWN.name,
                    enabled = true,
                    isBuiltIn = false,
                    lastRefreshAt = null,
                    lastRefreshError = null,
                    priority = 500,
                ),
            )

            dao.updateFields(
                repositoryId = "custom-abc",
                name = "New name",
                baseUrl = "https://html.example.com/downloads",
                metadataUrl = "https://html.example.com/downloads",
                providerType = ProviderType.HTML_REGEX.name,
                extraJson = """{"apkUrlRegex":"href=\"([^\"]+\\.apk)\""}""",
            )

            val row = dao.get("custom-abc")
            assertEquals("New name", row?.name)
            assertEquals("https://html.example.com/downloads", row?.baseUrl)
            assertEquals(ProviderType.HTML_REGEX.name, row?.providerType)
            assertEquals("""{"apkUrlRegex":"href=\"([^\"]+\\.apk)\""}""", row?.extraJson)
            assertEquals(500, row?.priority)
            assertEquals(true, row?.enabled)
        } finally {
            db.close()
        }
    }

    @Test
    fun updateFieldsClearsExtraWhenNull() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovaDatabase::class.java,
        ).build()
        val dao = db.repositoryDao()
        try {
            dao.upsert(
                RepositoryEntity(
                    repositoryId = "custom-xyz",
                    name = "N",
                    baseUrl = "https://x.example",
                    metadataUrl = "https://x.example",
                    trust = SourceTrust.UNKNOWN.name,
                    enabled = true,
                    isBuiltIn = false,
                    lastRefreshAt = null,
                    lastRefreshError = null,
                    priority = 500,
                    extraJson = """{"apkUrlRegex":"x"}""",
                ),
            )
            dao.updateFields(
                repositoryId = "custom-xyz",
                name = "N",
                baseUrl = "https://x.example",
                metadataUrl = "https://x.example",
                providerType = ProviderType.FDROID_INDEX.name,
                extraJson = null,
            )
            assertNull(dao.get("custom-xyz")?.extraJson)
        } finally {
            db.close()
        }
    }
}