package com.novastore.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceUrlsTest {

    @Test
    fun stripTrailingSlash() {
        assertEquals("https://f-droid.org/repo", SourceUrls.normalizeSourceUrl("https://f-droid.org/repo/"))
        assertEquals("https://f-droid.org/repo", SourceUrls.normalizeSourceUrl("https://f-droid.org/repo///"))
    }

    @Test
    fun trimWhitespaceAndLowercaseHost() {
        assertEquals("https://example.com/Apps", SourceUrls.normalizeSourceUrl("   https://EXAMPLE.com/Apps/  "))
    }

    @Test
    fun keepExplicitPort() {
        assertEquals("https://example.com:8443/repo", SourceUrls.normalizeSourceUrl("https://example.com:8443/repo"))
    }

    @Test
    fun rejectHttp() {
        assertNull(SourceUrls.normalizeSourceUrl("http://f-droid.org/repo"))
    }

    @Test
    fun rejectJavascriptScheme() {
        assertNull(SourceUrls.normalizeSourceUrl("javascript:alert(1)"))
    }

    @Test
    fun rejectFileScheme() {
        assertNull(SourceUrls.normalizeSourceUrl("file:///etc/passwd"))
    }

    @Test
    fun rejectDataScheme() {
        assertNull(SourceUrls.normalizeSourceUrl("data:text/html,<b>x</b>"))
    }

    @Test
    fun rejectFtpAndBlank() {
        assertNull(SourceUrls.normalizeSourceUrl("ftp://example.com/repo"))
        assertNull(SourceUrls.normalizeSourceUrl("   "))
        assertNull(SourceUrls.normalizeSourceUrl("not a url"))
        assertNull(SourceUrls.normalizeSourceUrl("a".repeat(SourceUrls.MAX_URL_LENGTH + 1)))
    }

    @Test
    fun githubFullNameFromBareRef() {
        assertEquals("owner/repo", SourceUrls.asGitHubFullName("owner/repo"))
        assertEquals("team/app", SourceUrls.asGitHubFullName("  team/app  "))
    }

    @Test
    fun githubFullNameFromUrl() {
        assertEquals("owner/repo", SourceUrls.asGitHubFullName("https://github.com/owner/repo"))
        assertEquals("owner/repo", SourceUrls.asGitHubFullName("https://github.com/owner/repo/tree/dev"))
    }

    @Test
    fun githubFullNameRejectsOtherHosts() {
        assertNull(SourceUrls.asGitHubFullName("https://codeberg.org/owner/repo"))
        assertNull(SourceUrls.asGitHubFullName("https://github.com/"))
        assertNull(SourceUrls.asGitHubFullName("owner"))
    }

    @Test
    fun apiBaseMapsGithubToApiHost() {
        assertEquals("https://api.github.com", SourceUrls.apiBaseOf("https://github.com/owner/repo"))
    }

    @Test
    fun apiBaseMapsGitlabToV4() {
        assertEquals("https://gitlab.com/api/v4", SourceUrls.apiBaseOf("https://gitlab.com/group/project"))
    }

    @Test
    fun apiBaseTreatsOtherHostsAsGiteaV1() {
        assertEquals("https://codeberg.org/api/v1", SourceUrls.apiBaseOf("https://codeberg.org/owner/repo"))
    }

    @Test
    fun apiBaseRejectsNonHttps() {
        assertNull(SourceUrls.apiBaseOf("http://codeberg.org/owner/repo"))
    }
}