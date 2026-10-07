package com.novastore.app.core.model

enum class SourceTrust {
    TRUSTED,
    UNKNOWN,
    DISABLED,
    INVALID,
}

data class RepositoryConfig(
    val repositoryId: String,
    val name: String,
    val baseUrl: String,
    val metadataUrl: String,
    val trust: SourceTrust,
    val enabled: Boolean,
    val isBuiltIn: Boolean = false,
    val lastRefreshAt: Long? = null,
    val lastRefreshError: String? = null,
    val priority: Int = 500,
    val providerType: ProviderType = ProviderType.FDROID_INDEX,
    val extraJson: String? = null,
)