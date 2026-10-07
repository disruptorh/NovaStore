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
import com.novastore.app.core.network.fdroid.ParsedIndex
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
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

    override suspend fun validate(config: RepositoryConfig): AppResult<SourcePreview> {
        return runCatching {
            val cache = File(context.cacheDir, "fdroid-index-validate")
            if (cache.exists()) cache.delete()
            cache.parentFile?.mkdirs()
            val fetched = fdroidIndexClient.fetchIndex(config.metadataUrl, cache)
            val parsed = fetched.getOrNull()?.let { f ->
                fdroidIndexClient.parseIndex(cache, f, listOf("en")).getOrNull()
            } ?: ParsedIndex(
                repoName = config.name,
                apps = emptyList(),
                versions = emptyList(),
            )
            AppResult.Success(
                SourcePreview(
                    appCountHint = parsed.apps.size,
                    sampleNames = parsed.apps.take(3).mapNotNull { it.name },
                    warning = null,
                ),
            )
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            val msg = t.message ?: "Invalid F-Droid index"
            AppResult.Failure(NovaError.Unknown(msg, t))
        }
    }

    override suspend fun search(query: String): List<RemoteApp> = emptyList()

    override suspend fun getAppDetails(packageName: String): RemoteAppDetails? = null

    override suspend fun getVersions(packageName: String): List<AppVersion> = emptyList()

    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)
}