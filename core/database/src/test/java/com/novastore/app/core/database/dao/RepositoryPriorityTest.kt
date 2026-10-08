package com.novastore.app.core.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.novastore.app.core.database.NovaDatabase
import com.novastore.app.core.database.entity.RepositoryEntity
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.SourceTrust
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P06-T04: priority survives re-assignment across reads (process death). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryPriorityTest {

    private fun repo(repositoryId: String, priority: Int) = RepositoryEntity(
        repositoryId = repositoryId,
        name = repositoryId,
        baseUrl = "https://$repositoryId.example",
        metadataUrl = "https://$repositoryId.example/index-v2.json",
        trust = SourceTrust.UNKNOWN.name,
        enabled = true,
        isBuiltIn = false,
        lastRefreshAt = null,
        lastRefreshError = null,
        priority = priority,
    )

    @Test
    fun reorderPersistsAcrossReRead() = runTest {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NovaDatabase::class.java,
        ).build()
        val dao = db.repositoryDao()
        try {
            dao.upsert(repo("b", priority = 500))
            dao.upsert(repo("a", priority = 10))
            dao.upsert(repo("c", priority = 300))

            dao.updatePriority("b", 10)
            dao.updatePriority("a", 20)
            dao.updatePriority("c", 30)

            val order = dao.all().sortedBy { it.priority }.map { it.repositoryId }
            assertEquals(listOf("b", "a", "c"), order)
        } finally {
            db.close()
        }
    }
}