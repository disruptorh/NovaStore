package com.novastore.app.domain.source

import com.novastore.app.core.common.AppResult
import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.NovaError
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RemoteApp
import com.novastore.app.core.model.RemoteAppDetails
import com.novastore.app.core.model.RepositoryConfig

data class SourcePreview(
    val appCountHint: Int?,
    val sampleNames: List<String>,
    val warning: String?,
)

/**
 * The full catalog a single-repository provider can materialize from one
 * source config at once: the [app] row plus every installable [versions].
 * [warning] carries non-blocking caveats (e.g. "no checksums" for HTML).
 */
data class SourceCatalog(
    val app: RemoteApp,
    val versions: List<AppVersion>,
    val warning: String? = null,
)

interface AppSourceProvider {
    val providerId: String
    val displayName: String
    val type: ProviderType

    suspend fun isEnabled(): Boolean
    suspend fun validate(config: RepositoryConfig): AppResult<SourcePreview>
    suspend fun search(query: String): List<RemoteApp>
    suspend fun getAppDetails(packageName: String): RemoteAppDetails?
    suspend fun getVersions(packageName: String): List<AppVersion>
    suspend fun refresh(): AppResult<Unit>

    /**
     * Materializes the whole catalog for [config] in one shot — the seam the
     * repository refresh/add flow calls so a GitHub / GitLab / Gitea / HTML
     * source actually populates the local catalog. Providers without a direct
     * catalog fetch (F-Droid index) never implement it and inherit a failure.
     */
    suspend fun fetch(config: RepositoryConfig): AppResult<SourceCatalog> = AppResult.Failure(
        NovaError.Repository(
            userMessage = "This source type has no direct catalog fetch.",
            repositoryId = config.repositoryId,
        ),
    )
}