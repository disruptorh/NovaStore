package com.novastore.app.domain.source

import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RepositoryConfig

interface SourceRegistry {
    suspend fun getEnabledProviders(): List<AppSourceProvider>
    suspend fun register(config: RepositoryConfig, provider: AppSourceProvider)
    suspend fun getForConfig(config: RepositoryConfig): AppSourceProvider?

    /** The provider that handles a config type — the add-source flow's seam. */
    suspend fun providerForType(type: ProviderType): AppSourceProvider?
}