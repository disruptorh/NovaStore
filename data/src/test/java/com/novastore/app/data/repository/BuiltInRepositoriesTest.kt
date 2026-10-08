package com.novastore.app.data.repository

import com.novastore.app.core.database.entity.RepositoryEntity
import com.novastore.app.core.model.SourceTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P06-T04: seeding built-ins never repositions or renames existing rows. */
class BuiltInRepositoriesTest {

    private fun row(id: String, priority: Int, name: String = "mine") = RepositoryEntity(
        repositoryId = id,
        name = name,
        baseUrl = "https://$id.example",
        metadataUrl = "https://$id.example/index-v2.json",
        trust = SourceTrust.TRUSTED.name,
        enabled = true,
        isBuiltIn = id == "fdroid",
        lastRefreshAt = null,
        lastRefreshError = null,
        priority = priority,
    )

    @Test
    fun insertIfAbsentCoversEveryMissingBuiltIn() {
        val rows = missingBuiltInRows(emptyMap())
        assertEquals(BUILT_IN_REPOSITORIES.size, rows.size)
        assertEquals("fdroid", rows.first().repositoryId)
        assertEquals("https://f-droid.org/repo/index-v2.json", rows.first().metadataUrl)
        assertEquals(0, rows.first().priority)
        assertEquals("kde", rows[7].repositoryId)
        assertEquals(7, rows[7].priority)
    }

    @Test
    fun existingRowIsNeverReturnedSoPriorityAndNameSurvive() {
        val existing = mapOf("fdroid" to row("fdroid", priority = 21, name = "My F-Droid"))
        val rows = missingBuiltInRows(existing)
        assertTrue(rows.none { it.repositoryId == "fdroid" })
        assertTrue(rows.any { it.repositoryId == "izzyondroid" })
        // The existing row itself is untouched.
        assertEquals("My F-Droid", existing.getValue("fdroid").name)
        assertEquals(21, existing.getValue("fdroid").priority)
    }

    @Test
    fun disabledByDefaultFlagPreserved() {
        val rows = missingBuiltInRows(emptyMap())
        assertEquals(false, rows.last { it.repositoryId == "nethunter" }.enabled)
    }
}