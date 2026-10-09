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
import com.novastore.app.data.websource.GitHubClient
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

@Singleton
class GitHubReleaseSourceProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatcherProvider: DispatcherProvider,
    private val gitHubClient: GitHubClient,
) : AppSourceProvider {

    override val providerId: String = "github-releases"
    override val displayName: String = "GitHub"
    override val type: ProviderType = ProviderType.GITHUB

    override suspend fun isEnabled(): Boolean = true

    /**
     * Validate a GitHub repo ref without touching the local catalog: the repo
     * must exist ("owner/repo" or a github.com URL) and its releases API must
     * answer with JSON. Errors stay typed — 404 vs network/TLS vs bad JSON —
     * and the preview lists up to 5 release names.
     */
    override suspend fun validate(config: RepositoryConfig): AppResult<SourcePreview> {
        val ref = config.metadataUrl.ifBlank { config.baseUrl }.trim()
        if (ref.isEmpty()) return AppResult.Failure(NovaError.Repository("The GitHub source has no repository URL."))
        val fullName = SourceUrls.asGitHubFullName(ref)
            ?: return AppResult.Failure(NovaError.Repository("Not a GitHub repository: enter owner/repo or https://github.com/owner/repo."))
        val apiBase = SourceUrls.apiBaseOf("https://github.com/$fullName") ?: return AppResult.Failure(NovaError.Repository("Unusable GitHub repository URL."))
        val probeUrl = "$apiBase/repos/${URLEncoder.encode(fullName, "UTF-8")}/releases?per_page=5"
        return probeReleases(probeUrl)
    }

    private suspend fun probeReleases(url: String): AppResult<SourcePreview> = withContext(dispatcherProvider.io) {
        try {
            okHttpClient.newCall(Request.Builder().url(url).header("User-Agent", NOVA_UA).build())
                .execute().use { response ->
                    when {
                        response.code == 404 -> AppResult.Failure(
                            NovaError.Repository("Repository not found on GitHub. Check the owner/repo name."),
                        )
                        !response.isSuccessful -> AppResult.Failure(
                            NovaError.Repository("GitHub answered with HTTP ${response.code}."),
                        )
                        else -> {
                            val body = response.body?.string()
                                ?: return@use AppResult.Failure(NovaError.Repository("GitHub answered with an empty body."))
                            runCatching { JSONArray(body) }
                                .fold(
                                    onSuccess = { releases ->
                                        val names = (0 until releases.length()).mapNotNull { i ->
                                            releases.optJSONObject(i)?.optString("name")?.trim()?.takeIf { it.isNotEmpty() }
                                        }
                                        AppResult.Success(
                                            SourcePreview(
                                                appCountHint = names.size,
                                                sampleNames = names.take(5),
                                                warning = if (names.isEmpty()) "The repository exists but has no releases yet." else null,
                                            ),
                                        )
                                    },
                                    onFailure = { AppResult.Failure(NovaError.Repository("GitHub answered with invalid JSON.")) },
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

    /**
     * P06/P07: materializes the whole repository from GitHub's public API —
     * repo metadata feeds the app row, releases become the version list. The
     * heavy lifting is shared with the live GitHub catalog (GitHubClient), so
     * UA, caching and error handling stay in ONE place.
     */
    override suspend fun fetch(config: RepositoryConfig): AppResult<SourceCatalog> {
        val ref = config.metadataUrl.ifBlank { config.baseUrl }.trim()
        val fullName = SourceUrls.asGitHubFullName(ref)
            ?: return AppResult.Failure(
                NovaError.Repository(
                    userMessage = "Not a GitHub repository: enter owner/repo or https://github.com/owner/repo.",
                    repositoryId = config.repositoryId,
                ),
            )
        val options = ReleaseCatalog.parseOptions(config.extraJson)
        val versions = gitHubClient.catalogVersions(
            fullName = fullName,
            source = config.repositoryId,
            includePrereleases = options.includePrereleases,
            apkFilterRegex = options.apkFilterRegex,
        )
        if (versions.isEmpty()) {
            return AppResult.Failure(
                NovaError.Repository(
                    userMessage = "No installable APK files were found in the releases of $fullName.",
                    repositoryId = config.repositoryId,
                ),
            )
        }
        val canonical = SourceUrls.normalizeSourceUrl(config.baseUrl.ifBlank { "https://github.com/$fullName" })
            ?: "https://github.com/$fullName"
        val packageName = ProviderPackage.of(ProviderType.GITHUB, canonical)
        val app = gitHubClient.details(fullName)?.app?.copy(packageName = packageName, source = config.repositoryId)
            ?: RemoteApp(
                packageName = packageName,
                name = fullName.substringAfter('/'),
                summary = null,
                developer = fullName.substringBefore('/'),
                iconUrl = null,
                license = null,
                categories = listOf("GitHub"),
                source = config.repositoryId,
                downloads = null,
                updatedMillis = versions.maxOfOrNull { it.addedAt ?: 0L },
            )
        return AppResult.Success(
            SourceCatalog(
                app = app,
                versions = versions.map { it.copy(packageName = packageName) },
                warning = if (versions.none { it.sha256 != null }) ReleaseCatalog.NO_CHECKSUM_WARNING else null,
            ),
        )
    }

    private companion object {
        const val NOVA_UA = "NovaStore/5.0 (Android app catalog client)"
    }
}