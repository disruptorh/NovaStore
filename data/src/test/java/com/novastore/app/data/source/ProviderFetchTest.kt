package com.novastore.app.data.source

import com.novastore.app.core.common.AppResult
import com.novastore.app.core.common.DispatcherProvider
import com.novastore.app.core.model.ProviderPackage
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.core.model.SourceTrust
import com.novastore.app.domain.source.SourceCatalog
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P06/P07 acceptance: fetch() on the provider-backed sources materializes a
 * whole catalog — an app row plus parsed versions — from a local TLS
 * MockWebServer (no live network). GitHub is the one provider not served here:
 * its REST host is fixed to api.github.com by design, so it is covered by the
 * pure parse tests it shares and by SourceProvidersValidateTest.
 */
class ProviderFetchTest {

    private lateinit var server: MockWebServer
    private lateinit var hostCertificate: HeldCertificate
    private lateinit var client: OkHttpClient
    private val dispatcher = object : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }

    @Before
    fun setUp() {
        hostCertificate = HeldCertificate.Builder().commonName("localhost").build()
        client = clientTrustingLocalhost()
        server = MockWebServer()
        server.useHttps(serverSslSocketFactory(), false)
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val base: String get() = server.url("/").toString()

    // ------------------------------------------------------------------
    // Gitea v1 — whole-catalog materialization
    // ------------------------------------------------------------------

    @Test
    fun giteaFetchMaterializesAppAndVersions() = runBlocking {
        server.enqueue(releasesResponse(GITEA_RELEASES_FIXTURE))
        val provider = GiteaCompatibleSourceProvider(client, dispatcher)
        val projectUrl = "$base/owner/app"
        val config = config(baseUrl = projectUrl, extraJson = null)

        val catalog = fetchOk(provider, config)

        assertEquals(ProviderPackage.of(ProviderType.GITEA, projectUrl), catalog.app.packageName)
        assertEquals("Test source", catalog.app.name)
        assertEquals("owner", catalog.app.developer)
        assertEquals("2.0", catalog.versions[0].versionName)
        assertEquals(2, catalog.versions.size)
        // provider identities are synthetic catalog keys; the artifact manifest wins
        assertTrue(catalog.versions.all { it.identityFromArtifact })
        // sha256 comes from the Gitea digest object
        assertEquals("deadbeef", catalog.versions[0].sha256)
        assertNull(catalog.versions[1].sha256)
        // drafts and pre-releases are excluded by default
        assertTrue(catalog.versions.none { it.versionName == "9.9" })
        assertTrue(catalog.versions.none { it.versionName == "3.0" })
        // at least one digest exists, so no checksum warning is attached
        assertNull(catalog.warning)
    }

    @Test
    fun giteaFetchWithoutDigestsWarns() = runBlocking {
        server.enqueue(releasesResponse("""[{"tag_name":"v1.0","assets":[{"name":"app.apk","browser_download_url":"https://localhost/app.apk"}]}]"""))
        val provider = GiteaCompatibleSourceProvider(client, dispatcher)

        val catalog = fetchOk(provider, config(baseUrl = "$base/owner/app"))

        assertEquals(ReleaseCatalog.NO_CHECKSUM_WARNING, catalog.warning)
    }

    @Test
    fun giteaFetchIncludesPrereleasesWhenAsked() = runBlocking {
        server.enqueue(releasesResponse(GITEA_RELEASES_FIXTURE))
        val provider = GiteaCompatibleSourceProvider(client, dispatcher)
        val config = config(
            baseUrl = "$base/owner/app",
            extraJson = """{"includePrereleases": true}""",
        )

        val catalog = fetchOk(provider, config)

        assertTrue(catalog.versions.any { it.versionName == "3.0" })
        assertTrue(catalog.versions.none { it.versionName == "9.9" })
    }

    @Test
    fun giteaFetchAppliesNameFilter() = runBlocking {
        server.enqueue(releasesResponse(GITEA_RELEASES_FIXTURE))
        val provider = GiteaCompatibleSourceProvider(client, dispatcher)
        val config = config(
            baseUrl = "$base/owner/app",
            extraJson = """{"apkFilterRegex": "stable-1.*\\.apk"}""",
        )

        val catalog = fetchOk(provider, config)

        assertEquals(1, catalog.versions.size)
        assertEquals("1.5", catalog.versions[0].versionName)
    }

    @Test
    fun giteaFetchWithoutApksFails() = runBlocking {
        server.enqueue(releasesResponse("""[{"tag_name":"v1.0","assets":[{"name":"README.md"}]}]"""))
        val provider = GiteaCompatibleSourceProvider(client, dispatcher)

        val result = provider.fetch(config(baseUrl = "$base/owner/app"))

        assertTrue(result is AppResult.Failure)
    }

    // ------------------------------------------------------------------
    // HTML — regex extraction materialization
    // ------------------------------------------------------------------

    @Test
    fun htmlFetchMaterializesVersionsFromRegex() = runBlocking {
        val html = buildString {
            for (i in 1..3) append("<li><a href=\"https://cdn.example.com/app-$i.0.apk\">dl</a></li>")
        }
        server.enqueue(MockResponse().setBody(html))
        val provider = HtmlRegexSourceProvider(client, dispatcher)
        val config = config(
            baseUrl = base,
            extraJson = """{"apkUrlRegex":"href=\"([^\"]+\\.apk)\""}""",
        )

        val catalog = fetchOk(provider, config)

        assertEquals(ProviderPackage.of(ProviderType.HTML_REGEX, base), catalog.app.packageName)
        assertEquals(3, catalog.versions.size)
        assertEquals("app-1.0.apk", catalog.versions[0].versionName)
        assertEquals("https://cdn.example.com/app-1.0.apk", catalog.versions[0].downloadUrl)
        assertTrue(catalog.versions.all { it.identityFromArtifact })
        assertTrue(catalog.warning != null)
    }

    @Test
    fun htmlFetchWithoutMatchesFails() = runBlocking {
        server.enqueue(MockResponse().setBody("<a href=\"https://x.example/guide\">guide</a>"))
        val provider = HtmlRegexSourceProvider(client, dispatcher)

        val result = provider.fetch(config(baseUrl = base, extraJson = """{"apkUrlRegex":"<a href=\"([^\"]+\\.zip)\""}"""))

        assertTrue(result is AppResult.Failure)
    }

    // ------------------------------------------------------------------
    // ReleaseCatalog pure rules (shared by every provider + GitHubClient)
    // ------------------------------------------------------------------

    @Test
    fun parseOptionsReadsAllKeysAndNothingElse() {
        val options = ReleaseCatalog.parseOptions("""{"includePrereleases":true,"apkFilterRegex":"stable","apkUrlRegex":"href"}""")
        assertTrue(options.includePrereleases)
        assertEquals("stable", options.apkFilterRegex)
        assertEquals("href", options.apkUrlRegex)
        assertEquals(ReleaseCatalog.CatalogOptions(), ReleaseCatalog.parseOptions(null))
        assertEquals(ReleaseCatalog.CatalogOptions(), ReleaseCatalog.parseOptions("not json"))
    }

    @Test
    fun prereleaseAndDraftFiltering() {
        val releases = org.json.JSONArray(GITEA_RELEASES_FIXTURE)
        val versions = ReleaseCatalog.parseGiteaReleases(releases, "novasrc.gitea.x", "src", includePrereleases = false)
        assertEquals(setOf("2.0", "1.5"), versions.map { it.versionName }.toSet())
    }

    @Test
    fun gitlabReleasesParseLinksAsVersions() {
        val fixture = """
            [{"tag_name":"v2.0","released_at":"2026-01-01T12:00:00Z",
              "assets":{"links":[{"name":"app-2.0.apk","url":"https://gitlab.com/-/package_files/1"}]}},
             {"tag_name":"v1.0","released_at":"2026-01-02T12:00:00Z","upcoming_release":true,
              "assets":{"links":[{"name":"app-1.0.apk","url":"https://gitlab.com/-/package_files/2"}]}}]
        """.trimIndent()
        val versions = ReleaseCatalog.parseGitLabReleases(
            org.json.JSONArray(fixture), "novasrc.gitlab.x", "src", includePrereleases = false,
        )
        assertEquals(1, versions.size)
        assertEquals("2.0", versions[0].versionName)
        assertEquals("https://gitlab.com/-/package_files/1", versions[0].downloadUrl)
        assertTrue(versions.all { it.identityFromArtifact })
        assertNull(versions[0].sha256)
        assertNull(versions[0].size)
    }

    @Test
    fun versionOrderingIsNewestFirst() {
        val fixture = """
            [{"tag_name":"v2.0.1","assets":[{"name":"app.apk","size":1,"browser_download_url":"u2"}]},
             {"tag_name":"v1.9.0","assets":[{"name":"app.apk","size":1,"browser_download_url":"u1"}]}]
        """.trimIndent()
        val versions = ReleaseCatalog.parseGiteaReleases(org.json.JSONArray(fixture), "p", "s")
        assertEquals(2, versions.size)
        assertEquals(versions.sortedByDescending { it.versionCode }, versions)
        assertTrue(versions[0].versionCode > versions[1].versionCode)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private suspend fun fetchOk(provider: com.novastore.app.domain.source.AppSourceProvider, config: RepositoryConfig): SourceCatalog {
        val result = provider.fetch(config)
        return (result as? AppResult.Success)?.value ?: throw AssertionError("expected Success, got $result")
    }

    private fun config(baseUrl: String, extraJson: String? = null) = RepositoryConfig(
        repositoryId = "test-source",
        name = "Test source",
        baseUrl = baseUrl,
        metadataUrl = baseUrl,
        trust = SourceTrust.UNKNOWN,
        enabled = true,
        providerType = ProviderType.FDROID_INDEX,
        extraJson = extraJson,
    )

    private fun releasesResponse(body: String) = MockResponse().setBody(body).setHeader("Content-Type", "application/json")

    private fun serverSslSocketFactory(): SSLSocketFactory {
        val serverCerts = HandshakeCertificates.Builder()
            .heldCertificate(hostCertificate)
            .build()
        return serverCerts.sslSocketFactory()
    }

    private fun clientTrustingLocalhost(): OkHttpClient {
        val clientCerts = HandshakeCertificates.Builder()
            .addTrustedCertificate(hostCertificate.certificate)
            .build()
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .sslSocketFactory(clientCerts.sslSocketFactory(), clientCerts.trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private companion object {
        val GITEA_RELEASES_FIXTURE = """
            [{
              "tag_name": "v2.0", "prerelease": false, "draft": false,
              "published_at": "2026-02-01T10:00:00Z",
              "assets": [{
                "name": "stable-2.0.apk", "size": 2048,
                "browser_download_url": "https://localhost/stable-2.0.apk",
                "digest": {"sha256": "deadbeef"}
              }]
            }, {
              "tag_name": "v1.5", "prerelease": false, "draft": false,
              "published_at": "2026-01-01T10:00:00Z",
              "assets": [{
                "name": "stable-1.5.apk", "size": 1024,
                "browser_download_url": "https://localhost/stable-1.5.apk"
              }]
            }, {
              "tag_name": "v3.0", "prerelease": true, "draft": false,
              "published_at": "2026-03-01T10:00:00Z",
              "assets": [{
                "name": "beta-3.0.apk", "size": 3000,
                "browser_download_url": "https://localhost/beta-3.0.apk"
              }]
            }, {
              "tag_name": "v9.9", "prerelease": false, "draft": true,
              "published_at": "2026-04-01T10:00:00Z",
              "assets": [{
                "name": "draft-9.9.apk", "size": 4096,
                "browser_download_url": "https://localhost/draft-9.9.apk"
              }]
            }]
        """.trimIndent()
    }
}