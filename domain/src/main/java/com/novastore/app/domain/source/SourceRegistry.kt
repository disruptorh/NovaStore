package com.novastore.app.domain.source

import com.novastore.app.core.model.RepositoryConfig

interface SourceRegistry {
    suspend fun getEnabledProviders(): List<AppSourceProvider>
    suspend fun register(config: RepositoryConfig, provider: AppSourceProvider)
    suspend fun getForConfig(config: RepositoryConfig): AppSourceProvider?
}