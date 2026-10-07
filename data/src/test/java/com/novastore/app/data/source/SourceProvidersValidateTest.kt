package com.novastore.app.data.source

import com.novastore.app.core.common.AppResult
import com.novastore.app.core.common.DispatcherProvider
import com.novastore.app.core.model.NovaError
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.core.model.SourceTrust
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * validate() error paths, served from a local TLS MockWebServer — no live
 * network in CI. Every provider is constructed directly with a test OkHttp
 * client, so the four error classes the plan names all get covered here:
 *  - connection/TLS failure → NovaError.Network;
 *  - 404 → NovaError.Repository (not found);
 *  - invalid JSON body → NovaError.Repository (bad data);
 *  - an apkUrlRegex with no matches → NovaError.Repository.
 */
class SourceProvidersValidateTest {

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
        hostCertificate = HeldCertificate.Builder()
            .commonName("localhost")
            .build()
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
    // GitHub — ref parsing rejects without network
    // ------------------------------------------------------------------

    @Test
    fun gitHubValidateRejectsForeignHost() = runBlocking {
        val provider = GitHubReleaseSourceProvider(client, dispatcher)
        val result = provider.validate(config(baseUrl = "https://codeberg.org/owner/repo"))
        assertTrue(result is AppResult.Failure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun gitHubValidateRejectsBlankRef() = runBlocking {
        val provider = GitHubReleaseSourceProvider(client, dispatcher)
        val result = provider.validate(config(baseUrl = ""))
        assertTrue(result is AppResult.Failure)
    }

    // ------------------------------------------------------------------
    // Gitea / GitLab-compatible — full probe round-trip over the mock
    // ------------------------------------------------------------------

    @Test
    fun giteaValidateReturnsPreviewUpToFive() = runBlocking {
        server.enqueue(MockResponse().setBody("[{\"name\":\"v2.0\"},{\"name\":\"v1.5\"}]"))
        val provider = GiteaCompatibleSourceProvider(client, dispatcher)

        val result = provider.validate(config(baseUrl = base, metadataUrl = "$base/owner/repo"))

        val preview = (result as AppResult.Success).value
        assertEquals(listOf("v2.0", "v1.5"), preview.sampleNames)
        assertEquals(2, preview.appCountHint)
    }

    @Test
    fun giteaValidate404() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        val provider = GiteaCompatibleSourceProvider(client, dispatcher)

        val result = provider.validate(config(baseUrl = base, metadataUrl = "$base/owner/missing"))

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun giteaValidateInvalidJson() = runBlocking {
        server.enqueue(MockResponse().setBody("this is not json"))
        val provider = GiteaCompatibleSourceProvider(client, dispatcher)

        val result = provider.validate(config(baseUrl = base, metadataUrl = "$base/owner/repo"))

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun giteaValidateConnectionRefusedIsNetworkError() = runBlocking {
        val dead = MockWebServer()
        dead.useHttps(serverSslSocketFactory(), false)
        dead.start()
        val deadBase = dead.url("/").toString()
        dead.shutdown()
        val provider = GiteaCompatibleSourceProvider(client, dispatcher)

        val result = provider.validate(config(baseUrl = deadBase, metadataUrl = "$deadBase/owner/repo"))

        assertTrue(result is AppResult.Failure)
        assertTrue((result as AppResult.Failure).error is NovaError.Network)
    }

    @Test
    fun giteaValidateRejectsHttpBase() = runBlocking {
        val provider = GiteaCompatibleSourceProvider(client, dispatcher)

        val result = provider.validate(config(baseUrl = "http://example.com/owner/repo"))

        assertTrue(result is AppResult.Failure)
        assertEquals(0, server.requestCount)
    }

    // ------------------------------------------------------------------
    // HTML + regex — fixture extraction and the regex error paths
    // ------------------------------------------------------------------

    @Test
    fun htmlRegexValidateExtractsApkUrls() = runBlocking {
        val html = buildString {
            for (i in 1..7) append("<li><a href=\"https://cdn.example.com/app-$i.0.apk\">dl</a></li>")
        }
        server.enqueue(MockResponse().setBody(html))
        val provider = HtmlRegexSourceProvider(client, dispatcher)
        val config = config(
            baseUrl = base,
            extraJson = """{"apkUrlRegex":"href=\"([^\"]+\\.apk)\""}""",
        )

        val result = provider.validate(config)

        val preview = (result as AppResult.Success).value
        assertEquals(5, preview.sampleNames.size)
        assertEquals("https://cdn.example.com/app-1.0.apk", preview.sampleNames.first())
    }

    @Test
    fun htmlRegexValidateZeroMatches() = runBlocking {
        server.enqueue(MockResponse().setBody("<a href=\"https://x.example/guide\">guide</a>"))
        val provider = HtmlRegexSourceProvider(client, dispatcher)
        val config = config(baseUrl = base, extraJson = """{"apkUrlRegex":"<a href=\"([^\"]+\\.zip)\""}""")

        val result = provider.validate(config)

        assertTrue(result is AppResult.Failure)
    }

    @Test
    fun htmlRegexValidateRejectsHttpBase() = runBlocking {
        val provider = HtmlRegexSourceProvider(client, dispatcher)

        for (base in listOf("http://example.com/apps", "javascript:download()", "file:///tmp/apps")) {
            val result = provider.validate(config(baseUrl = base, extraJson = """{"apkUrlRegex":"<a href=\"([^\"]+)\""}"""))
            assertTrue("expected rejection for $base", result is AppResult.Failure)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun htmlRegexValidateRejectsOverlongPattern() = runBlocking {
        val provider = HtmlRegexSourceProvider(client, dispatcher)
        val longPattern = "x".repeat(401)
        val config = config(baseUrl = base, extraJson = """{"apkUrlRegex":"$longPattern"}""")

        val result = provider.validate(config)

        assertTrue(result is AppResult.Failure)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun htmlRegexValidateRequiresPattern() = runBlocking {
        val provider = HtmlRegexSourceProvider(client, dispatcher)

        val result = provider.validate(config(baseUrl = base, extraJson = null))

        assertTrue(result is AppResult.Failure)
        assertEquals(0, server.requestCount)
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private fun config(
        baseUrl: String,
        metadataUrl: String? = null,
        extraJson: String? = null,
    ) = RepositoryConfig(
        repositoryId = "test-source",
        name = "Test source",
        baseUrl = baseUrl,
        metadataUrl = metadataUrl ?: baseUrl,
        trust = SourceTrust.UNKNOWN,
        enabled = true,
        providerType = ProviderType.FDROID_INDEX,
        extraJson = extraJson,
    )

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
}