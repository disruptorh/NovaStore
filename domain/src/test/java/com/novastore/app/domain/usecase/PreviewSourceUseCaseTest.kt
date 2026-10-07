package com.novastore.app.domain.usecase

import com.novastore.app.core.common.AppResult
import com.novastore.app.core.model.NovaError
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.domain.source.AppSourceProvider
import com.novastore.app.domain.source.SourcePreview
import com.novastore.app.domain.source.SourceRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P06-T02: preview never writes the catalog and shows ≤5 names or the error. */
class PreviewSourceUseCaseTest {

    private class FakeProvider(
        override val type: ProviderType,
        private val result: AppResult<SourcePreview>,
    ) : AppSourceProvider {
        override val providerId: String = "fake-${type.name.lowercase()}"
        override val displayName: String = "Fake"
        var lastValidated: RepositoryConfig? = null

        override suspend fun isEnabled(): Boolean = true
        override suspend fun validate(config: RepositoryConfig): AppResult<SourcePreview> {
            lastValidated = config
            return result
        }

        override suspend fun search(query: String): List<com.novastore.app.core.model.RemoteApp> = emptyList()
        override suspend fun getAppDetails(packageName: String): com.novastore.app.core.model.RemoteAppDetails? = null
        override suspend fun getVersions(packageName: String): List<com.novastore.app.core.model.AppVersion> = emptyList()
        override suspend fun refresh(): AppResult<Unit> = AppResult.success(Unit)
    }

    private class FakeRegistry(private val provider: AppSourceProvider) : SourceRegistry {
        override suspend fun getEnabledProviders(): List<AppSourceProvider> = listOf(provider)
        override suspend fun register(config: RepositoryConfig, provider: AppSourceProvider) = Unit
        override suspend fun getForConfig(config: RepositoryConfig): AppSourceProvider? = null
        override suspend fun providerForType(type: ProviderType): AppSourceProvider? =
            if (type == provider.type) provider else null
    }

    @Test
    fun validateSuccessShowsUpToFiveSampleNames() = runTest {
        val provider = FakeProvider(
            ProviderType.GITHUB,
            AppResult.success(
                SourcePreview(
                    appCountHint = 12,
                    sampleNames = (1..7).map { "release-$it" }.toList(),
                    warning = null,
                ),
            ),
        )
        val useCase = PreviewSourceUseCase(FakeRegistry(provider))

        val result = useCase("Repo", "https://github.com/owner/repo", ProviderType.GITHUB, null)

        assertTrue(result is AppResult.Success)
        val preview = (result as AppResult.Success).value
        assertEquals(12, preview.appCountHint)
        assertEquals(5, preview.sampleNames.size)
        assertNotNull("the form-built config reaches the provider", provider.lastValidated)
        assertEquals("https://github.com/owner/repo", provider.lastValidated?.metadataUrl)
    }

    @Test
    fun validateFailureSurfacesTypedError() = runTest {
        val provider = FakeProvider(
            ProviderType.GITEA,
            AppResult.failure(NovaError.Repository(userMessage = "Project not found. Check the owner/repo name.")),
        )
        val useCase = PreviewSourceUseCase(FakeRegistry(provider))

        val result = useCase("Repo", "https://codeberg.org/owner/repo", ProviderType.GITEA, null)

        assertTrue(result is AppResult.Failure)
        assertEquals(
            "Project not found. Check the owner/repo name.",
            (result as AppResult.Failure).error.userMessage,
        )
    }

    @Test
    fun unsupportedProviderTypeFails() = runTest {
        val provider = FakeProvider(ProviderType.FDROID_INDEX, AppResult.success(SourcePreview(0, emptyList(), null)))
        val useCase = PreviewSourceUseCase(FakeRegistry(provider))

        val result = useCase("Repo", "https://x.example", ProviderType.HTML_REGEX, null)

        assertTrue(result is AppResult.Failure)
    }
}