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
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * A from-HTML source: the user gives a base https:// page and an
 * [apkUrlRegex] whose capture group 1 is the download URL. validate() fetches
 * the page and proves the regex extracts URLs — without touching Room.
 *
 * Guard rails, all enforced in validate()/fetch(): the base URL must be
 * https://, the pattern is length-capped against pathological regexes, and
 * extraction runs under a hard 2 s timeout (a pathological pattern fails the
 * source instead of hanging the add flow).
 */
@Singleton
class HtmlRegexSourceProvider @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dispatcherProvider: DispatcherProvider,
) : AppSourceProvider {

    override val providerId: String = "html-regex"
    override val displayName: String = "HTML"
    override val type: ProviderType = ProviderType.HTML_REGEX

    override suspend fun isEnabled(): Boolean = true

    override suspend fun validate(config: RepositoryConfig): AppResult<SourcePreview> {
        val base = SourceUrls.normalizeSourceUrl(config.baseUrl.ifBlank { config.metadataUrl })
            ?: return AppResult.Failure(NovaError.Repository("The HTML source needs an https:// base URL (http, file and javascript: are not supported)."))
        val pattern = ReleaseCatalog.parseOptions(config.extraJson).apkUrlRegex
            ?: return AppResult.Failure(NovaError.Repository("Provide an apkUrlRegex (JSON {\"apkUrlRegex\": \"…\"} in the source config) whose group 1 is the download URL."))
        if (pattern.length > MAX_PATTERN_LENGTH) {
            return AppResult.Failure(NovaError.Repository("apkUrlRegex is too long ($pattern.length > $MAX_PATTERN_LENGTH chars)."))
        }
        val html = fetch(base) ?: return AppResult.Failure(NovaError.Network(cause = null))
        val urls = extractUrls(html, pattern)
            ?: return AppResult.Failure(NovaError.Repository("apkUrlRegex took too long to match. Simplify the pattern."))
        if (urls.isEmpty()) {
            return AppResult.Failure(NovaError.Repository("apkUrlRegex found no APK URLs on the page."))
        }
        return AppResult.Success(
            SourcePreview(
                appCountHint = urls.size,
                sampleNames = urls.take(5),
                warning = HTML_INTEGRITY_WARNING,
            ),
        )
    }

    private fun apkUrlRegex(config: RepositoryConfig): String? =
        ReleaseCatalog.parseOptions(config.extraJson).apkUrlRegex

    private suspend fun fetch(url: String): String? = withContext(dispatcherProvider.io) {
        runCatching {
            okHttpClient.newCall(Request.Builder().url(url).header("User-Agent", NOVA_UA).build())
                .execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()
                }
        }.getOrNull()
    }

    /** Extracts group 1 of [pattern] from [html] under a hard timeout. */
    private fun extractUrls(html: String, pattern: String): List<String>? {
        val executor = Executors.newSingleThreadExecutor()
        return try {
            val future: Future<List<String>> = executor.submit(Callable {
                val regex = runCatching { Regex(pattern) }.getOrNull() ?: return@Callable null
                regex.findAll(html).mapNotNull { it.groupValues.getOrNull(1)?.trim()?.takeIf { url -> url.isNotEmpty() } }.toList()
            })
            try {
                future.get(MATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
        } finally {
            executor.shutdownNow()
        }
    }

    /**
     * P06/P07: materializes the page — each matched URL becomes one version
     * (the file name is the version name; codes are page-order based since a
     * plain page has no real version metadata). No checksums exist by
     * construction, so the integrity warning is always attached.
     */
    override suspend fun fetch(config: RepositoryConfig): AppResult<SourceCatalog> {
        val base = SourceUrls.normalizeSourceUrl(config.baseUrl.ifBlank { config.metadataUrl })
            ?: return AppResult.Failure(
                NovaError.Repository(
                    userMessage = "The HTML source needs an https:// base URL.",
                    repositoryId = config.repositoryId,
                ),
            )
        val pattern = ReleaseCatalog.parseOptions(config.extraJson).apkUrlRegex
            ?: return AppResult.Failure(
                NovaError.Repository(
                    userMessage = "Provide an apkUrlRegex (JSON {\"apkUrlRegex\": \"…\"}) whose group 1 is the download URL.",
                    repositoryId = config.repositoryId,
                ),
            )
        if (pattern.length > MAX_PATTERN_LENGTH) {
            return AppResult.Failure(
                NovaError.Repository(
                    userMessage = "apkUrlRegex is too long (${pattern.length} > $MAX_PATTERN_LENGTH chars).",
                    repositoryId = config.repositoryId,
                ),
            )
        }
        val html = fetch(base) ?: return AppResult.Failure(NovaError.Network(cause = null))
        val urls = extractUrls(html, pattern)
            ?: return AppResult.Failure(
                NovaError.Repository(
                    userMessage = "apkUrlRegex took too long to match. Simplify the pattern.",
                    repositoryId = config.repositoryId,
                ),
            )
        if (urls.isEmpty()) {
            return AppResult.Failure(
                NovaError.Repository(
                    userMessage = "apkUrlRegex found no APK URLs on the page.",
                    repositoryId = config.repositoryId,
                ),
            )
        }
        val packageName = ProviderPackage.of(ProviderType.HTML_REGEX, base)
        val versions = urls.distinct().mapIndexed { index, downloadUrl ->
            AppVersion(
                packageName = packageName,
                versionCode = (999 - index).toLong(),
                versionName = downloadUrl.substringAfterLast('/').substringBefore('?')
                    .takeIf { it.isNotBlank() && !it.endsWith('/') },
                source = config.repositoryId,
                size = null,
                downloadUrl = downloadUrl,
                sha256 = null,
                minSdk = null,
                targetSdk = null,
                addedAt = null,
                artifactType = ReleaseCatalog.artifactType(downloadUrl),
                signer = null,
                nativeCode = emptyList(),
            )
        }
        val app = RemoteApp(
            packageName = packageName,
            name = config.name.trim().ifBlank { base.removePrefix("https://") },
            summary = null,
            developer = null,
            iconUrl = null,
            license = null,
            categories = listOf("HTML"),
            source = config.repositoryId,
            downloads = versions.size.toLong(),
            updatedMillis = null,
        )
        return AppResult.Success(SourceCatalog(app = app, versions = versions, warning = HTML_INTEGRITY_WARNING))
    }

    override suspend fun search(query: String): List<RemoteApp> = emptyList()

    override suspend fun getAppDetails(packageName: String): RemoteAppDetails? = null

    override suspend fun getVersions(packageName: String): List<AppVersion> = emptyList()

    override suspend fun refresh(): AppResult<Unit> = AppResult.Success(Unit)

    private companion object {
        const val NOVA_UA = "NovaStore/5.0 (Android app catalog client)"
        const val MAX_PATTERN_LENGTH = 400
        const val MATCH_TIMEOUT_MS = 2_000L
    }
}

/**
 * P06-T08: a from-HTML source has no origin checksums by construction —
 * hashes are computed after download and there is nothing to compare the
 * local file against. Always shown in the add-source preview; never blocks.
 */
internal const val HTML_INTEGRITY_WARNING =
    "This source provides no checksums: sha256 is computed after download and there is no origin digest to compare."