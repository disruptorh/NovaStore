package com.novastore.app.domain.usecase

import com.novastore.app.core.common.AppResult
import com.novastore.app.core.model.NovaError
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.core.model.SourceTrust
import com.novastore.app.domain.source.SourcePreview
import com.novastore.app.domain.source.SourceRegistry
import javax.inject.Inject

/**
 * Probes a source form (name, provider type, URL, extras) through the matching
 * provider's validate() WITHOUT writing anything to the catalog: the user sees
 * ≤5 sample app names (or a typed error — 404, TLS, bad JSON…) before the
 * source is confirmed and added.
 */
class PreviewSourceUseCase @Inject constructor(
    private val registry: SourceRegistry,
) {
    suspend operator fun invoke(
        name: String,
        url: String,
        providerType: ProviderType,
        extraJson: String?,
    ): AppResult<SourcePreview> {
        val provider = registry.providerForType(providerType)
            ?: return AppResult.failure(
                NovaError.Repository(userMessage = "Unsupported source type: $providerType"),
            )
        return provider.validate(
            RepositoryConfig(
                repositoryId = "preview",
                name = name,
                baseUrl = url,
                metadataUrl = url,
                trust = SourceTrust.UNKNOWN,
                enabled = true,
                priority = 500,
                providerType = providerType,
                extraJson = extraJson,
            ),
        ).map { it.copy(sampleNames = it.sampleNames.take(PREVIEW_SAMPLE_LIMIT)) }
    }

    private companion object {
        const val PREVIEW_SAMPLE_LIMIT = 5
    }
}