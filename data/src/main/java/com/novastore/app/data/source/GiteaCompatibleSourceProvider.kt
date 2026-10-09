package com.novastore.app.data.source

import com.novastore.app.core.common.AppResult
import com.novastore.app.core.common.DispatcherProvider
import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.NovaError
import com.novastore.app.core.model.ProviderPackage
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RemoteApp
import com.novastore.app.core.model.RemoteAppDetails
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.core.model.SourceUrls
import com.novastore.app.domain.source.AppSourceProvider
import com.novastore.app.domain.source.SourceCatalog
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
    override val displayName: String = "GitLab / Gitea"
    override val type: ProviderType = ProviderType.GITEA

    override suspend fun isEnabled(): Boolean = true

    /**
     * Validate a project ref at a GitLab/Gitea instance: the project must
     * exist ("owner/repo" on the configured base URL) and its releases API
     * must answer with JSON. Errors stay typed (404 / network / bad JSON);
     * preview lists up to 5 release names.
     */
    override suspend fun validate(config: RepositoryConfig): AppResult<SourcePreview> = withContext(dispatcherProvider.io) {
        val probeUrl = releaseListUrl(config) ?: return@withContext AppResult.Failure(
            NovaError.Repository("The source needs an https:// host URL and an owner/repo (or group/project) ref."),
        )
        when (val releases = fetchReleaseArray(probeUrl)) {
            is AppResult.Failure -> releases
            is AppResult.Success -> {
                val names = (0 until releases.value.length()).mapNotNull { i ->
                    val release = releases.value.optJSONObject(i) ?: return@mapNotNull null
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
            }
        }
    }

    private fun hostRefOf(config: RepositoryConfig): String? {
        val raw = config.metadataUrl.ifBlank { config.baseUrl }
        if (raw.isBlank()) return null
        val path = raw.removePrefix("https://").removePrefix("http://").substringAfter('/', "")
        return path.trim('/').ifBlank { null }
    }

    /** The v4/v1 releases URL for a config, or null when the ref is unusable. */
    private fun releaseListUrl(config: RepositoryConfig): String? {
        val base = SourceUrls.normalizeSourceUrl(config.baseUrl.ifBlank { config.metadataUrl }) ?: return null
        val apiBase = SourceUrls.apiBaseOf(base) ?: return null
        val ownerRepo = hostRefOf(config) ?: return null
        val isGitLab = base.removePrefix("https://").substringBefore('/') == "gitlab.com"
        return if (isGitLab) {
            "$apiBase/projects/${URLEncoder.encode(ownerRepo, "UTF-8")}/releases?per_page=30"
        } else {
            "$apiBase/repos/${URLEncoder.encode(ownerRepo, "UTF-8")}/releases?limit=30"
        }
    }

    private fun isGitLab(config: RepositoryConfig): Boolean =
        SourceUrls.normalizeSourceUrl(config.baseUrl.ifBlank { config.metadataUrl })
            ?.let { it.removePrefix("https://").substringBefore('/') == "gitlab.com" }
            ?: false

    /**
     * P06/P07: materializes the whole project — the project ref becomes the app
     * row (owner as developer, the ref's last segment as name), releases become
     * versions. GitLab assets are release links, Gitea's are native attachments.
     */
    override suspend fun fetch(config: RepositoryConfig): AppResult<SourceCatalog> {
        val probeUrl = releaseListUrl(config)
            ?: return AppResult.Failure(
                NovaError.Repository(
                    userMessage = "The source needs an https:// host URL and an owner/repo (or group/project) ref.",
                    repositoryId = config.repositoryId,
                ),
            )
        val releases = when (val result = fetchReleaseArray(probeUrl)) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }
        val gitLab = isGitLab(config)
        val base = SourceUrls.normalizeSourceUrl(config.baseUrl.ifBlank { config.metadataUrl }) ?: probeUrl
        val options = ReleaseCatalog.parseOptions(config.extraJson)
        val packageName = ProviderPackage.of(
            if (gitLab) ProviderType.GITLAB else ProviderType.GITEA,
            base,
        )
        val versions = if (gitLab) {
            ReleaseCatalog.parseGitLabReleases(
                releases, packageName, config.repositoryId, options.includePrereleases, options.apkFilterRegex, VERSIONS_LIMIT,
            )
        } else {
            ReleaseCatalog.parseGiteaReleases(
                releases, packageName, config.repositoryId, options.includePrereleases, options.apkFilterRegex, VERSIONS_LIMIT,
            )
        }
        if (versions.isEmpty()) {
            return AppResult.Failure(
                NovaError.Repository(
                    userMessage = "No installable APK files were found in the releases of the project.",
                    repositoryId = config.repositoryId,
                ),
            )
        }
        val ownerRepo = hostRefOf(config) ?: base.removePrefix("https://").substringAfter('/', "project")
        val app = RemoteApp(
            packageName = packageName,
            name = config.name.trim().ifBlank { ownerRepo.substringAfterLast('/') },
            summary = null,
            developer = ownerRepo.substringBefore('/'),
            iconUrl = null,
            license = null,
            categories = listOf(if (gitLab) "GitLab" else "Gitea"),
            source = config.repositoryId,
            downloads = null,
            updatedMillis = versions.maxOfOrNull { it.addedAt ?: 0L },
        )
        val warning = if (versions.none { it.sha256 != null }) ReleaseCatalog.NO_CHECKSUM_WARNING else null
        return AppResult.Success(SourceCatalog(app = app, versions = versions, warning = warning))
    }

    private suspend fun fetchReleaseArray(url: String): AppResult<JSONArray> = withContext(dispatcherProvider.io) {
        try {
            okHttpClient.newCall(Request.Builder().url(url).header("User-Agent", NOVA_UA).build())
                .execute().use { response ->
                    when {
                        response.code == 404 -> AppResult.failure(
                            NovaError.Repository("Project not found. Check the owner/repo name."),
                        )
                        !response.isSuccessful -> AppResult.failure(
                            NovaError.Repository("The host answered with HTTP ${response.code}."),
                        )
                        else -> {
                            val body = response.body?.string()
                                ?: return@use AppResult.failure(NovaError.Repository("The host answered with an empty body."))
                            runCatching { JSONArray(body) }.fold(
                                onSuccess = { AppResult.success(it) },
                                onFailure = { AppResult.failure(NovaError.Repository("The host answered with invalid JSON.")) },
                            )
                        }
                    }
                }
        } catch (t: IOException) {
            AppResult.failure(NovaError.Network(cause = t))
        }
    }

    override suspend fun search(query: String): List<RemoteApp> = emptyList()

    override suspend fun getAppDetails(packageName: String): RemoteAppDetails? = null

    override suspend fun getVersions(packageName: String): List<AppVersion> = emptyList()

    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)

    private companion object {
        const val NOVA_UA = "NovaStore/5.0 (Android app catalog client)"
        const val VERSIONS_LIMIT = 30
    }
}