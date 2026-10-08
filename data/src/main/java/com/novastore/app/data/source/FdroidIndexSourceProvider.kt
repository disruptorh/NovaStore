package com.novastore.app.data.source

import android.content.Context
import com.novastore.app.core.common.AppResult
import com.novastore.app.core.model.NovaError
import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RemoteApp
import com.novastore.app.core.model.RemoteAppDetails
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.domain.source.AppSourceProvider
import com.novastore.app.domain.source.SourcePreview
import com.novastore.app.core.network.fdroid.FdroidIndexClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FdroidIndexSourceProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fdroidIndexClient: FdroidIndexClient,
) : AppSourceProvider {

    override val providerId: String = "fdroid-index"
    override val displayName: String = "F-Droid Index"
    override val type: ProviderType = ProviderType.FDROID_INDEX

    override suspend fun isEnabled(): Boolean = true

    /**
     * Validate an F-Droid index without touching the local catalog: download
     * the index, parse it, and preview up to 5 app names. A failed download
     * (TLS/404) and an unparseable index are surfaced as typed failures — an
     * index that cannot be fetched is never reported as "ok with 0 apps".
     */
    override suspend fun validate(config: RepositoryConfig): AppResult<SourcePreview> {
        val cache = File(context.cacheDir, "fdroid-index-validate")
        runCatching {
            if (cache.exists()) cache.delete()
            cache.parentFile?.mkdirs()
        }
        val fetched = fdroidIndexClient.fetchIndex(config.metadataUrl, cache).getOrNull()
            ?: return AppResult.Failure(NovaError.Repository("Could not fetch the F-Droid index: check the URL or your connection."))
        val parsed = fdroidIndexClient.parseIndex(cache, fetched, listOf("en")).getOrNull()
            ?: return AppResult.Failure(NovaError.Repository("Could not parse the F-Droid index JSON."))
        val names = parsed.apps.mapNotNull { it.name }
        val integrityNote = fdroidIntegrityWarning(
            versionCount = parsed.versions.size,
            hasAnySha256 = parsed.versions.any { !it.sha256.isNullOrBlank() },
        )
        return AppResult.Success(
            SourcePreview(
                appCountHint = names.size,
                sampleNames = names.take(5),
                warning = integrityNote,
            ),
        )
    }

    override suspend fun search(query: String): List<RemoteApp> = emptyList()

    override suspend fun getAppDetails(packageName: String): RemoteAppDetails? = null

    override suspend fun getVersions(packageName: String): List<AppVersion> = emptyList()

    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)
}

/**
 * P06-T08: an index that publishes versions but no sha256 hashes can only be
 * verified locally after download — surface that in the add-source preview
 * (never blocks adding). Null when the index is empty or carries hashes.
 */
internal fun fdroidIntegrityWarning(versionCount: Int, hasAnySha256: Boolean): String? =
    if (versionCount > 0 && !hasAnySha256) {
        "This index provides no sha256 hashes: integrity is verified locally after download."
    } else {
        null
    }