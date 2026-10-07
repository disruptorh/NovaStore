package com.novastore.app.data.source

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
        return registered[config.repositoryId]?.second ?: providers.firstOrNull()
    }
}