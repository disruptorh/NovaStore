package com.novastore.app.data.source

import com.novastore.app.core.common.AppResult
import com.novastore.app.core.common.DispatcherProvider
import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.NovaError
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RemoteApp
import com.novastore.app.core.model.RemoteAppDetails
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.core.model.SourceUrls
import com.novastore.app.domain.source.AppSourceProvider
import com.novastore.app.domain.source.SourcePreview
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

/**
 * One provider for GitLab.com and Gitea-compatible hosts (Codeberg…). The
 * host decides the API dialect: gitlab.com → v4 API, any other https host →
 * Gitea v1 API.
 */
@Singleton
class GiteaCompatibleSourceProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatcherProvider: DispatcherProvider,
) : AppSourceProvider {

    override val providerId: String = "gitea-gitlab-compat"
    override val displayName: String = "Gitea/GitLab Compatible"
    override val type: ProviderType = ProviderType.GITEA

    override suspend fun isEnabled(): Boolean = true

    /**
     * Validate a project ref at a GitLab/Gitea instance: the project must
     * exist ("owner/repo" on the configured base URL) and its releases API
     * must answer with JSON. Errors stay typed (404 / network / bad JSON);
     * preview lists up to 5 release names.
     */
    override suspend fun validate(config: RepositoryConfig): AppResult<SourcePreview> {
        val base = SourceUrls.normalizeSourceUrl(config.baseUrl.ifBlank { config.metadataUrl })
            ?: return AppResult.Failure(NovaError.Repository("The source needs an https:// host URL."))
        val apiBase = SourceUrls.apiBaseOf(base)
            ?: return AppResult.Failure(NovaError.Repository("Unusable repository host URL."))
        val host = base.removePrefix("https://").substringBefore('/')
        val ownerRepo = hostRefOf(config)
            ?: return AppResult.Failure(NovaError.Repository("Enter owner/repo (or group/project) after the host URL."))
        val probeUrl = if (host == "gitlab.com") {
            "$apiBase/projects/${URLEncoder.encode(ownerRepo, "UTF-8")}/releases?per_page=5"
        } else {
            "$apiBase/repos/${URLEncoder.encode(ownerRepo, "UTF-8")}/releases?limit=5"
        }
        return probeReleases(probeUrl)
    }

    private fun hostRefOf(config: RepositoryConfig): String? {
        val raw = config.metadataUrl.ifBlank { config.baseUrl }
        if (raw.isBlank()) return null
        val path = raw.removePrefix("https://").removePrefix("http://").substringAfter('/', "")
        return path.trim('/').ifBlank { null }
    }

    private suspend fun probeReleases(url: String): AppResult<SourcePreview> = withContext(dispatcherProvider.io) {
        try {
            okHttpClient.newCall(Request.Builder().url(url).header("User-Agent", NOVA_UA).build())
                .execute().use { response ->
                    when {
                        response.code == 404 -> AppResult.Failure(
                            NovaError.Repository("Project not found. Check the owner/repo name."),
                        )
                        !response.isSuccessful -> AppResult.Failure(
                            NovaError.Repository("The host answered with HTTP ${response.code}."),
                        )
                        else -> {
                            val body = response.body?.string()
                                ?: return@use AppResult.Failure(NovaError.Repository("The host answered with an empty body."))
                            runCatching { JSONArray(body) }
                                .fold(
                                    onSuccess = { releases ->
                                        val names = (0 until releases.length()).mapNotNull { i ->
                                            val release = releases.optJSONObject(i) ?: return@mapNotNull null
                                            release.optString("name").trim()
                                                .takeIf { it.isNotEmpty() }
                                                ?: release.optString("tag_name").takeIf { it.isNotEmpty() }
                                        }
                                        AppResult.Success(
                                            SourcePreview(
                                                appCountHint = names.size,
                                                sampleNames = names.take(5),
                                                warning = if (names.isEmpty()) "The project exists but has no releases yet." else null,
                                            ),
                                        )
                                    },
                                    onFailure = { AppResult.Failure(NovaError.Repository("The host answered with invalid JSON.")) },
                                )
                        }
                    }
                }
        } catch (t: IOException) {
            AppResult.Failure(NovaError.Network(cause = t))
        }
    }

    override suspend fun search(query: String): List<RemoteApp> = emptyList()

    override suspend fun getAppDetails(packageName: String): RemoteAppDetails? = null

    override suspend fun getVersions(packageName: String): List<AppVersion> = emptyList()

    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)

    private companion object {
        const val NOVA_UA = "NovaStore/5.0 (Android app catalog client)"
    }
}