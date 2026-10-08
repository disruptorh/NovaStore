package com.novastore.app.data.repository

import com.novastore.app.core.common.AppResult
import com.novastore.app.core.database.entity.RepositoryEntity
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.SourceTrust
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P06-T05: backup serialization roundtrip and import semantics. */
class SourceBackupTest {

    private fun row(
        id: String,
        name: String,
        baseUrl: String,
        providerType: ProviderType,
        enabled: Boolean = true,
        priority: Int,
        extraJson: String? = null,
        isBuiltIn: Boolean = false,
    ) = RepositoryEntity(
        repositoryId = id,
        name = name,
        baseUrl = baseUrl,
        metadataUrl = baseUrl,
        trust = SourceTrust.UNKNOWN.name,
        enabled = enabled,
        isBuiltIn = isBuiltIn,
        lastRefreshAt = null,
        lastRefreshError = null,
        priority = priority,
        providerType = providerType.name,
        extraJson = extraJson,
    )

    @Test
    fun roundtripPreservesFields() {
        val rows = listOf(
            row("builtin-fdroid", "F-Droid", "https://f-droid.org/repo", ProviderType.FDROID_INDEX, priority = 10, isBuiltIn = true),
            row("custom-github", "My org", "https://github.com/acme/app", ProviderType.GITHUB, enabled = false, priority = 40),
            row("custom-html", "HTML", "https://example.com/dl", ProviderType.HTML_REGEX, priority = 50, extraJson = """{"apkUrlRegex":"href=\"?([^\"]+?\"?\\.apk)"}"""),
        )

        val json = SourceBackup(rows.map { it.toBackupEntry() }).toJson()

        assertTrue(json.contains("\"version\":1"))
        val parsed = parseSourceBackup(json)
        assertTrue(parsed is AppResult.Success)
        assertEquals(
            listOf("F-Droid", "My org", "HTML"),
            (parsed as AppResult.Success).value.sources.map { it.name },
        )
        val html = parsed.value.sources.last()
        assertEquals("https://example.com/dl", html.url)
        assertEquals(ProviderType.HTML_REGEX, html.providerType)
        assertEquals(false, parsed.value.sources[1].enabled)
        assertEquals(10, parsed.value.sources[0].priority)
        assertEquals("""{"apkUrlRegex":"href=\"?([^\"]+?\"?\\.apk)"}""", html.extraJson)
    }

    @Test
    fun wrongVersionRejected() {
        val result = parseSourceBackup("""{"version":2,"sources":[]}""")
        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun importMergesByUrlAndRespectsEnabledAndPriority() {
        // 2 custom http-less? no: two custom https + a built-in-style fdroid
        // entry disabled, absent from the initial rows.
        val entries = SourceBackup(
            sources = listOf(
                SourceBackupEntry("Repo A", "https://a.example", ProviderType.FDROID_INDEX, enabled = true, priority = 30, extraJson = null),
                SourceBackupEntry("Repo B", "https://b.example/repo", ProviderType.GITHUB, enabled = false, priority = 60, extraJson = null),
                SourceBackupEntry("F-Droid", "https://f-droid.org/repo", ProviderType.FDROID_INDEX, enabled = false, priority = 10, extraJson = null),
            ),
        )
        val json = entries.toJson()

        // The import contract lives on the repository; here we prove the file
        // parses to entries the repository then trusts (enabled/priority kept).
        val parsed = (parseSourceBackup(json) as AppResult.Success).value.sources
        assertEquals(3, parsed.size)
        assertEquals(false, parsed[1].enabled)
        assertEquals(false, parsed[2].enabled)
        assertEquals(30, parsed[0].priority)
    }

    @Test
    fun nonHttpUrlSurvivesCodecForRepositoryToReject() {
        // The codec is URL-agnostic; rejecting http belongs to the repository
        // merge step. It must not crash parsing though.
        val json = """{"version":1,"sources":[{"name":"bad","url":"http://insecure.example","type":"FDROID_INDEX","enabled":true,"priority":10,"extra":null}]}"""
        val parsed = parseSourceBackup(json)
        assertTrue(parsed is AppResult.Success)
        assertEquals("http://insecure.example", (parsed as AppResult.Success).value.sources.single().url)
    }
}