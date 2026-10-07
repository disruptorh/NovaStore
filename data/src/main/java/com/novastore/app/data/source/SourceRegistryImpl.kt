package com.novastore.app.data.source

import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.domain.source.AppSourceProvider
import com.novastore.app.domain.source.SourceRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SourceRegistryImpl @Inject constructor(
    private val providers: Set<AppSourceProvider>,
) : SourceRegistry {

    private val registered = mutableMapOf<String, Pair<RepositoryConfig, AppSourceProvider>>()

    override suspend fun getEnabledProviders(): List<AppSourceProvider> {
        return providers.filter { it.isEnabled() }
    }

    override suspend fun register(config: RepositoryConfig, provider: AppSourceProvider) {
        registered[config.repositoryId] = config to provider
    }

    override suspend fun getForConfig(config: RepositoryConfig): AppSourceProvider? {
        registered[config.repositoryId]?.second?.let { return it }
        return providerForType(config.providerType) ?: providers.firstOrNull()
    }

    override suspend fun providerForType(type: ProviderType): AppSourceProvider? {
        val direct = providers.firstOrNull { it.type == type }
        if (direct != null) return direct
        // GitLab configs are served by the Gitea-compatible provider (host detection).
        if (type == ProviderType.GITLAB) return providers.firstOrNull { it.type == ProviderType.GITEA }
        return null
    }
}