package com.novastore.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic identity for provider-backed apps: stable, URL-derived, typed. */
class ProviderPackageTest {

    @Test
    fun ofBuildsTypedSlugFromUrl() {
        assertEquals(
            "novasrc.github.github.com.owner.repo",
            ProviderPackage.of(ProviderType.GITHUB, "https://github.com/owner/repo"),
        )
        assertEquals(
            "novasrc.gitea.codeberg.org.owner.repo",
            ProviderPackage.of(ProviderType.GITEA, "https://codeberg.org/owner/repo"),
        )
        assertEquals(
            "novasrc.html.example.com.downloads",
            ProviderPackage.of(ProviderType.HTML_REGEX, "https://example.com/downloads"),
        )
    }

    @Test
    fun ofSanitizesPathAndCase() {
        assertEquals(
            "novasrc.github.github.com.owner.weird.q.1.frag.name",
            ProviderPackage.of(ProviderType.GITHUB, "https://GitHub.com/Owner/Weird?q=1#frag/.name/"),
        )
    }

    @Test
    fun ofFallsBackWhenUnusable() {
        assertTrue(
            ProviderPackage.of(ProviderType.GITHUB, "").startsWith("novasrc.github."),
        )
    }

    @Test
    fun typeTagIsStablePerType() {
        assertEquals("github", ProviderPackage.typeTag(ProviderType.GITHUB))
        assertEquals("gitlab", ProviderPackage.typeTag(ProviderType.GITLAB))
        assertEquals("gitea", ProviderPackage.typeTag(ProviderType.GITEA))
        assertEquals("html", ProviderPackage.typeTag(ProviderType.HTML_REGEX))
        assertEquals("fdroid", ProviderPackage.typeTag(ProviderType.FDROID_INDEX))
    }

    @Test
    fun isProviderPackageOnlyMatchesOwnPrefix() {
        assertTrue(ProviderPackage.isProviderPackage("novasrc.github.github.com.owner.repo"))
        assertFalse(ProviderPackage.isProviderPackage("com.example.app"))
        assertFalse(ProviderPackage.isProviderPackage("github.owner.repo"))
    }
}