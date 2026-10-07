package com.novastore.app.data.source

import android.content.Context
import com.novastore.app.core.common.AppResult
import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.NovaError
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RemoteApp
import com.novastore.app.core.model.RemoteAppDetails
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.domain.source.AppSourceProvider
import com.novastore.app.domain.source.SourcePreview
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HtmlRegexSourceProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppSourceProvider {

    override val providerId: String = "html-regex"
    override val displayName: String = "HTML Regex"
    override val type: ProviderType = ProviderType.HTML_REGEX

    override suspend fun isEnabled(): Boolean = true

    override suspend fun validate(config: RepositoryConfig): AppResult<SourcePreview> {
        return runCatching {
            AppResult.Success(
                SourcePreview(
                    appCountHint = null,
                    sampleNames = emptyList(),
                    warning = null,
                ),
            )
        }.getOrElse { t ->
            if (t is CancellationException) throw t
            AppResult.Failure(NovaError.Unknown(t.message ?: "Invalid HTML regex source", t))
        }
    }

    override suspend fun search(query: String): List<RemoteApp> = emptyList()

    override suspend fun getAppDetails(packageName: String): RemoteAppDetails? = null

    override suspend fun getVersions(packageName: String): List<AppVersion> = emptyList()

    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)
}