package com.novastore.app.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.novastore.app.core.database.NovaDatabase
import com.novastore.app.core.database.entity.RemoteAppEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogDaoOffsetTest {

    @Test
    fun search_pagesByOffset() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovaDatabase::class.java,
        ).build()
        val dao = db.catalogDao()
        try {
            dao.upsertApps(
                (0 until 45).map { i ->
                    val id = i.toString().padStart(2, '0')
                    RemoteAppEntity(
                        packageName = "com.example.app$id",
                        name = "App $id",
                        summary = "Sample $id",
                        developer = null,
                        iconUrl = null,
                        license = null,
                        description = null,
                        changelog = null,
                        website = null,
                        sourceCodeUrl = null,
                        categories = "Tools",
                        source = "builtin-fdroid",
                        addedAt = i.toLong(),
                        lastUpdatedAt = i.toLong(),
                    )
                },
            )

            val page0 = dao.search("App", limit = 40, offset = 0)
            val page1 = dao.search("App", limit = 40, offset = 40)

            assertEquals(40, page0.size)
            assertEquals(5, page1.size)
            assertEquals("App 00", page0.first().name)
            assertEquals("App 40", page1.first().name)
            assertTrue("offset pages must be disjoint", page0.intersect(page1).isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun search_offsetDefaultsToStart() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovaDatabase::class.java,
        ).build()
        val dao = db.catalogDao()
        try {
            dao.upsertApps(
                listOf(
                    RemoteAppEntity(
                        packageName = "com.example.one",
                        name = "One",
                        summary = null,
                        developer = null,
                        iconUrl = null,
                        license = null,
                        description = null,
                        changelog = null,
                        website = null,
                        sourceCodeUrl = null,
                        categories = "Tools",
                        source = "builtin-fdroid",
                        addedAt = 1,
                        lastUpdatedAt = 2,
                    ),
                ),
            )

            val default = dao.search("One")
            val explicit = dao.search("One", limit = 200, offset = 0)

            assertEquals(1, default.size)
            assertEquals(default, explicit)
        } finally {
            db.close()
        }
    }
}