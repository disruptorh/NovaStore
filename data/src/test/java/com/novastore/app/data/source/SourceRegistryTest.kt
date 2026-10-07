package com.novastore.app.data.source

import com.novastore.app.core.common.AppResult
import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RemoteApp
import com.novastore.app.core.model.RemoteAppDetails
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.core.model.SourceTrust
import com.novastore.app.domain.source.AppSourceProvider
import com.novastore.app.domain.source.SourcePreview
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRegistryTest {

    private class FakeProvider(
        override val type: ProviderType,
        private val enabled: Boolean,
    ) : AppSourceProvider {
        override val providerId: String = "fake-${type}"
        override val displayName: String = "Fake ${type}"
        override suspend fun isEnabled(): Boolean = enabled
        override suspend fun validate(config: RepositoryConfig): AppResult<SourcePreview> =
            AppResult.Success(SourcePreview(0, emptyList(), null))
        override suspend fun search(query: String): List<RemoteApp> = emptyList()
        override suspend fun getAppDetails(packageName: String): RemoteAppDetails? = null
        override suspend fun getVersions(packageName: String): List<AppVersion> = emptyList()
        override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)
    }

    private fun config(id: String, providerType: ProviderType) = RepositoryConfig(
        repositoryId = id,
        name = id,
        baseUrl = "https://example.com",
        metadataUrl = "https://example.com/index",
        trust = SourceTrust.UNKNOWN,
        enabled = true,
        providerType = providerType,
    )

    @Test
    fun enabledProvidersExcludeDisabled() = runTest {
        val registry = SourceRegistryImpl(
            setOf(FakeProvider(ProviderType.FDROID_INDEX, enabled = true), FakeProvider(ProviderType.GITHUB, enabled = false)),
        )
        assertEquals(listOf(ProviderType.FDROID_INDEX), registry.getEnabledProviders().map { it.type })
    }

    @Test
    fun providerForTypeResolvesDirectType() = runTest {
        val registry = SourceRegistryImpl(setOf(FakeProvider(ProviderType.GITHUB, enabled = true)))
        assertEquals(ProviderType.GITHUB, registry.providerForType(ProviderType.GITHUB)?.type)
    }

    @Test
    fun gitlabFallsBackToGiteaProvider() = runTest {
        val registry = SourceRegistryImpl(setOf(FakeProvider(ProviderType.GITEA, enabled = true)))
        assertEquals(ProviderType.GITEA, registry.providerForType(ProviderType.GITLAB)?.type)
        assertNull(registry.providerForType(ProviderType.HTML_REGEX))
    }

    @Test
    fun getForConfigPrefersRegisteredThenTypeFallback() = runTest {
        val github = FakeProvider(ProviderType.GITHUB, enabled = true)
        val gitea = FakeProvider(ProviderType.GITEA, enabled = true)
        val registry = SourceRegistryImpl(setOf(github, gitea))
        val registered = config("repos/a", ProviderType.GITHUB)

        registry.register(registered, gitea)
        assertEquals(gitea, registry.getForConfig(registered))

        val unregistered = config("repos/b", ProviderType.GITEA)
        assertEquals(gitea, registry.getForConfig(unregistered))
        assertTrue(registry.getForConfig(config("repos/c", ProviderType.GITLAB)) === gitea)
    }
}