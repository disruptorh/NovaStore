package com.novastore.app.data.repository

import android.content.Context
import com.novastore.app.core.common.AppResult
import com.novastore.app.core.database.dao.CatalogDao
import com.novastore.app.core.database.dao.RepositoryDao
import com.novastore.app.core.database.entity.AppVersionEntity
import com.novastore.app.core.database.entity.RemoteAppEntity
import com.novastore.app.core.database.entity.RepositoryEntity
import com.novastore.app.core.model.ArtifactType
import com.novastore.app.core.model.ImportReport
import com.novastore.app.core.model.NovaError
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.core.model.SourceTrust
import com.novastore.app.core.model.SourceUrls
import com.novastore.app.core.network.fdroid.FdroidIndexClient
import com.novastore.app.core.network.fdroid.IndexValidators
import com.novastore.app.core.network.fdroid.ParsedIndex
import com.novastore.app.data.mapper.toModel
import com.novastore.app.domain.repository.RepositoriesRepository
import com.novastore.app.domain.repository.ScanProgress
import com.novastore.app.domain.repository.ScanProgressTracker
import com.novastore.app.domain.source.SourceCatalog
import com.novastore.app.domain.source.SourceRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the repository list and the local catalog in sync. Every repository
 * (built-in or user added) is a row in the database; its apps are stored in
 * the catalog under the repository id.
 */
@Singleton
class RepositoriesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repositoryDao: RepositoryDao,
    private val catalogDao: CatalogDao,
    private val indexClient: FdroidIndexClient,
    private val scanProgress: ScanProgressTracker,
    private val sourceRegistry: SourceRegistry,
) : RepositoriesRepository {

    /** One lock per repository: different repositories refresh in parallel. */
    private val repositoryLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * Index parsing + catalog replacement stays serialized: a parsed F-Droid
     * index lives fully in memory, two at once would double peak RAM.
     */
    private val storeMutex = Mutex()

    private fun lockFor(repositoryId: String): Mutex = repositoryLocks.getOrPut(repositoryId) { Mutex() }
    private val seedMutex = Mutex()
    @Volatile private var seeded = false

    override fun observe(): Flow<List<RepositoryConfig>> = flow {
        ensureBuiltIns()
        emitAll(repositoryDao.observeAll().map { entities -> entities.map { it.toModel() } })
    }

    override fun observeAppCounts(): Flow<Map<String, Int>> =
        catalogDao.observeCountsBySource().map { counts -> counts.associate { it.source to it.count } }

    override suspend fun get(repositoryId: String): RepositoryConfig? =
        repositoryDao.get(repositoryId)?.toModel()

    override suspend fun priorities(): Map<String, Int> {
        ensureBuiltIns()
        return repositoryDao.all().associate { it.repositoryId to it.priority }
    }

    override suspend fun exportSources(): AppResult<String> {
        ensureBuiltIns()
        return AppResult.success(SourceBackup(repositoryDao.all().map { it.toBackupEntry() }).toJson())
    }

    override suspend fun importSources(json: String): AppResult<ImportReport> {
        ensureBuiltIns()
        val entries = when (val parsed = parseSourceBackup(json)) {
            is AppResult.Failure -> return AppResult.failure(parsed.error)
            is AppResult.Success -> parsed.value.sources
        }
        var added = 0
        var updated = 0
        var rejected = 0
        entries.forEach { entry ->
            val normalized = when (entry.providerType) {
                ProviderType.FDROID_INDEX ->
                    FdroidIndexClient.normalizeRepoUrl(entry.url).takeIf { it.startsWith("https://") }
                else -> SourceUrls.normalizeSourceUrl(entry.url.ifBlank { "" })
            }
            if (normalized == null || !normalized.startsWith("https://")) {
                rejected++
                return@forEach
            }
            val existing = repositoryDao.all().firstOrNull {
                (it.metadataUrl.ifBlank { it.baseUrl }).equals(normalized, ignoreCase = true)
            }
            if (existing == null) {
                repositoryDao.upsert(
                    RepositoryEntity(
                        repositoryId = "custom-" + sha256(normalized.lowercase()).take(12),
                        name = entry.name.ifBlank { providerDefaultName(entry.providerType, normalized) },
                        baseUrl = normalized,
                        metadataUrl = if (entry.providerType == ProviderType.FDROID_INDEX) {
                            "$normalized/index-v2.json"
                        } else {
                            normalized
                        },
                        trust = SourceTrust.UNKNOWN.name,
                        enabled = entry.enabled,
                        isBuiltIn = false,
                        lastRefreshAt = null,
                        lastRefreshError = null,
                        priority = entry.priority,
                        providerType = entry.providerType.name,
                        extraJson = entry.extraJson,
                    ),
                )
                added++
            } else {
                // Merge by URL: apply the file's enabled state and priority,
                // but never clobber local name/type/extras or built-in status.
                repositoryDao.upsert(
                    existing.copy(
                        enabled = entry.enabled,
                        priority = entry.priority,
                        isBuiltIn = existing.isBuiltIn,
                    ),
                )
                updated++
            }
        }
        return AppResult.success(ImportReport(added = added, updated = updated, rejected = rejected))
    }

    override suspend fun add(
        name: String,
        url: String,
        providerType: ProviderType,
        extraJson: String?,
    ): AppResult<Unit> {
        ensureBuiltIns()
        if (providerType == ProviderType.FDROID_INDEX) {
            return addFdroid(name, url)
        }
        // Provider-backed source: an https reference on the provider's own
        // validate() contract. The row is written (P06-T02 preview proved the
        // source works), then refresh() materializes the catalog (P06/P07).
        val canonical = SourceUrls.normalizeSourceUrl(url.ifBlank { "" })?.takeIf {
            when (providerType) {
                ProviderType.GITEA -> it.removePrefix("https://").contains('/')
                ProviderType.GITLAB -> it.removePrefix("https://").contains('/')
                else -> true
            }
        }
            ?: return AppResult.failure(
                NovaError.Repository(
                    userMessage = if (providerType == ProviderType.HTML_REGEX) {
                        "HTML sources need an https:// base URL."
                    } else {
                        "Provide a project: an https:// URL ending in owner/repo (GitHub, GitLab, Gitea)."
                    },
                ),
            )
        val existing = repositoryDao.all().firstOrNull {
            (it.metadataUrl.ifBlank { it.baseUrl }).equals(canonical, ignoreCase = true)
        }
        val id = existing?.repositoryId ?: ("custom-" + sha256(canonical.lowercase()).take(12))
        if (existing == null) {
            repositoryDao.upsert(
                RepositoryEntity(
                    repositoryId = id,
                    name = name.trim().ifBlank { providerDefaultName(providerType, canonical) },
                    baseUrl = canonical,
                    metadataUrl = canonical,
                    trust = SourceTrust.UNKNOWN.name,
                    enabled = true,
                    isBuiltIn = false,
                    lastRefreshAt = null,
                    lastRefreshError = null,
                    priority = CUSTOM_PRIORITY,
                    providerType = providerType.name,
                    extraJson = extraJson,
                ),
            )
        } else {
            repositoryDao.setEnabled(id, true)
        }
        // The row is persisted and re-enabled; fetch the provider catalog now
        // so the apps show up right after the add is confirmed.
        return refresh(id)
    }

    override suspend fun update(
        repositoryId: String,
        name: String,
        url: String,
        providerType: ProviderType,
        extraJson: String?,
    ): AppResult<Unit> {
        val row = repositoryDao.get(repositoryId)
            ?: return AppResult.failure(
                NovaError.Repository(userMessage = "Unknown repository.", repositoryId = repositoryId),
            )
        if (providerType == ProviderType.FDROID_INDEX) {
            val normalized = FdroidIndexClient.normalizeRepoUrl(url)
            if (!normalized.startsWith("https://")) {
                return AppResult.failure(NovaError.Repository(userMessage = "Repository URLs must use HTTPS."))
            }
            repositoryDao.updateFields(
                repositoryId = repositoryId,
                name = name.trim().ifBlank { row.name },
                baseUrl = normalized,
                metadataUrl = "$normalized/index-v2.json",
                providerType = providerType.name,
                extraJson = null,
            )
        } else {
            val canonical = SourceUrls.normalizeSourceUrl(url.ifBlank { "" })
                ?: return AppResult.failure(
                    NovaError.Repository(
                        userMessage = "The source needs a usable https:// reference (owner/repo for GitHub, GitLab and Gitea; a page URL for HTML).",
                    ),
                )
            repositoryDao.updateFields(
                repositoryId = repositoryId,
                name = name.trim().ifBlank { row.name },
                baseUrl = canonical,
                metadataUrl = canonical,
                providerType = providerType.name,
                extraJson = extraJson,
            )
        }
        return AppResult.success(Unit)
    }

    private suspend fun addFdroid(name: String, url: String): AppResult<Unit> {
        val normalized = FdroidIndexClient.normalizeRepoUrl(url)
        if (!normalized.startsWith("https://")) {
            return AppResult.failure(NovaError.Repository(userMessage = "Repository URLs must use HTTPS."))
        }
        val existing = repositoryDao.all().firstOrNull {
            FdroidIndexClient.normalizeRepoUrl(it.baseUrl).equals(normalized, ignoreCase = true)
        }
        val id = existing?.repositoryId ?: ("custom-" + sha256(normalized.lowercase()).take(12))
        if (existing == null) {
            repositoryDao.upsert(
                RepositoryEntity(
                    repositoryId = id,
                    name = name.trim().ifBlank { normalized.substringAfter("://").substringBefore('/') },
                    baseUrl = normalized,
                    metadataUrl = "$normalized/index-v2.json",
                    trust = SourceTrust.UNKNOWN.name,
                    enabled = true,
                    isBuiltIn = false,
                    lastRefreshAt = null,
                    lastRefreshError = null,
                    priority = CUSTOM_PRIORITY,
                ),
            )
        } else {
            repositoryDao.setEnabled(id, true)
        }
        return refresh(id)
    }

    override suspend fun setEnabled(repositoryId: String, enabled: Boolean): AppResult<Unit> {
        repositoryDao.setEnabled(repositoryId, enabled)
        return if (enabled) {
            refresh(repositoryId)
        } else {
            catalogDao.clearSource(repositoryId)
            AppResult.success(Unit)
        }
    }

    override suspend fun reorder(idsInOrder: List<String>) {
        idsInOrder.forEachIndexed { index, repositoryId ->
            repositoryDao.updatePriority(repositoryId, (index + 1) * 10)
        }
    }

    override suspend fun remove(repositoryId: String) {
        val repository = repositoryDao.get(repositoryId) ?: return
        catalogDao.clearSource(repositoryId)
        if (repository.isBuiltIn) {
            repositoryDao.setEnabled(repositoryId, false)
        } else {
            repositoryDao.delete(repositoryId)
        }
    }

    override suspend fun refresh(repositoryId: String): AppResult<Unit> {
        ensureBuiltIns()
        return lockFor(repositoryId).withLock {
            val repository = repositoryDao.get(repositoryId)
                ?: return@withLock AppResult.failure(
                    NovaError.Repository(userMessage = "Unknown repository.", repositoryId = repositoryId),
                )
            refreshLocked(repository, force = true)
        }
    }

    override suspend fun refreshAll(force: Boolean): AppResult<Unit> {
        ensureBuiltIns()
        val enabled = repositoryDao.all().filter { it.enabled }.sortedBy { it.priority }
        if (enabled.isEmpty()) {
            return AppResult.failure(NovaError.Repository(userMessage = "All repositories are disabled. Enable at least one in Settings."))
        }
        // Index downloads run in parallel (bounded) — the old one-by-one loop
        // made every scan wait for the slowest repository of the set.
        val finished = AtomicInteger(0)
        val results = scanProgress.track(ScanProgress(ScanProgress.Stage.REPOSITORIES, 0, enabled.size)) {
            coroutineScope {
                val gate = Semaphore(REFRESH_PARALLELISM)
                enabled.map { repository ->
                    async {
                        gate.withPermit {
                            val result = lockFor(repository.repositoryId).withLock {
                                // Re-read: a concurrent refresh may have just updated this repository.
                                val current = repositoryDao.get(repository.repositoryId) ?: return@withLock null
                                if (!current.enabled) return@withLock null
                                refreshLocked(current, force)
                            }
                            scanProgress.update(
                                ScanProgress(
                                    ScanProgress.Stage.REPOSITORIES,
                                    finished.incrementAndGet(),
                                    enabled.size,
                                    repository.name,
                                ),
                            )
                            result
                        }
                    }
                }.awaitAll()
            }
        }.filterNotNull()
        val succeeded = results.count { it is AppResult.Success }
        val firstError = results.filterIsInstance<AppResult.Failure>().firstOrNull()?.error
        return if (succeeded == 0 && firstError != null) AppResult.failure(firstError) else AppResult.success(Unit)
    }

    private suspend fun refreshLocked(repository: RepositoryEntity, force: Boolean): AppResult<Unit> {
        val id = repository.repositoryId
        val now = System.currentTimeMillis()
        val hasCatalog = catalogDao.appCount(id) > 0
        val lastOk = repository.lastRefreshAt?.takeIf { repository.lastRefreshError == null }
        if (!force && hasCatalog && lastOk != null && now - lastOk < AUTO_REFRESH_INTERVAL_MS) {
            return AppResult.success(Unit)
        }

        // Provider-backed sources (GitHub / GitLab / Gitea / HTML) have no
        // F-Droid index behind their base URL — the whole catalog is built from
        // the provider's fetch() instead.
        val providerType = runCatching { ProviderType.valueOf(repository.providerType) }
            .getOrDefault(ProviderType.FDROID_INDEX)
        if (providerType != ProviderType.FDROID_INDEX) {
            return refreshProviderLocked(repository, providerType, now)
        }

        val indexFile = File(context.cacheDir, "index_$id.json")
        try {
            val validators = if (hasCatalog) {
                IndexValidators(repository.httpLastModified, repository.httpEtag)
                    .takeIf { it.lastModified != null || it.etag != null }
            } else {
                null
            }
            val fetched = when (
                val download = indexClient.fetchIndex(
                    repoUrl = repository.baseUrl,
                    targetFile = indexFile,
                    knownIndexUrl = repository.metadataUrl,
                    validators = validators,
                )
            ) {
                is AppResult.Failure -> {
                    repositoryDao.setRefreshResult(id, now, download.error.userMessage)
                    return AppResult.failure(download.error)
                }
                is AppResult.Success -> download.value
            }
            if (fetched == null) {
                // 304 Not Modified: the cached catalog is current.
                repositoryDao.setRefreshSuccess(id, now, repository.metadataUrl, repository.httpLastModified, repository.httpEtag)
                return AppResult.success(Unit)
            }

            val parseError = storeMutex.withLock {
                val parsed = when (val result = indexClient.parseIndex(indexFile, fetched, preferredLocales())) {
                    is AppResult.Failure -> return@withLock result.error
                    is AppResult.Success -> result.value
                }
                if (parsed.apps.isEmpty()) {
                    return@withLock NovaError.Repository(
                        userMessage = "The repository index contains no installable apps.",
                        repositoryId = id,
                    )
                }
                storeCatalog(id, parsed)
                if (!repository.isBuiltIn && parsed.repoName != null && repository.name == repository.baseUrl.substringAfter("://").substringBefore('/')) {
                    repositoryDao.get(id)?.let { repositoryDao.upsert(it.copy(name = parsed.repoName!!)) }
                }
                null
            }
            if (parseError != null) {
                repositoryDao.setRefreshResult(id, now, parseError.userMessage)
                return AppResult.failure(parseError)
            }

            repositoryDao.setRefreshSuccess(id, now, fetched.indexUrl, fetched.validators.lastModified, fetched.validators.etag)
            return AppResult.success(Unit)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            val error = NovaError.Repository(
                userMessage = "Updating ${repository.name} failed: ${t::class.simpleName}: ${t.message}",
                repositoryId = id,
            )
            repositoryDao.setRefreshResult(id, now, error.userMessage)
            return AppResult.failure(error)
        } finally {
            indexFile.delete()
        }
    }

    /**
     * Materializes a provider-backed repository (no F-Droid index): the
     * provider's fetch() returns the whole catalog in one call — an app row
     * plus its versions — which is persisted exactly like a parsed index
     * would be. The refresh state columns stay in sync so the settings row
     * shows success/errors like any F-Droid repository.
     */
    private suspend fun refreshProviderLocked(repository: RepositoryEntity, providerType: ProviderType, now: Long): AppResult<Unit> {
        val id = repository.repositoryId
        val provider = sourceRegistry.providerForType(providerType)
        if (provider == null) {
            val error = NovaError.Repository(
                userMessage = "No source provider handles ${providerType.name} repositories.",
                repositoryId = id,
            )
            repositoryDao.setRefreshResult(id, now, error.userMessage)
            return AppResult.failure(error)
        }
        val fetched = runCatching { provider.fetch(repository.toModel()) }.getOrElse { t ->
            if (t is kotlinx.coroutines.CancellationException) throw t
            AppResult.failure(
                NovaError.Repository(
                    userMessage = "Updating ${repository.name} failed: ${t::class.simpleName}: ${t.message}",
                    repositoryId = id,
                ),
            )
        }
        return when (fetched) {
            is AppResult.Failure -> {
                repositoryDao.setRefreshResult(id, now, fetched.error.userMessage)
                AppResult.failure(fetched.error)
            }
            is AppResult.Success -> {
                val catalog = fetched.value
                if (catalog.versions.isEmpty()) {
                    val error = NovaError.Repository(
                        userMessage = "The source contains no installable APK files.",
                        repositoryId = id,
                    )
                    repositoryDao.setRefreshResult(id, now, error.userMessage)
                    return AppResult.failure(error)
                }
                storeSourceCatalog(id, catalog)
                repositoryDao.setRefreshSuccess(id, now, repository.metadataUrl, null, null)
                AppResult.success(Unit)
            }
        }
    }

    private suspend fun storeSourceCatalog(source: String, catalog: SourceCatalog) {
        val app = catalog.app
        val appEntity = RemoteAppEntity(
            packageName = app.packageName,
            name = app.name,
            summary = app.summary,
            developer = app.developer,
            iconUrl = app.iconUrl,
            license = app.license,
            description = null,
            changelog = null,
            website = null,
            sourceCodeUrl = null,
            categories = app.categories.joinToString(SEPARATOR),
            source = source,
            addedAt = app.updatedMillis,
            lastUpdatedAt = app.updatedMillis,
        )
        val versions = catalog.versions.map { version ->
            AppVersionEntity(
                packageName = version.packageName,
                versionCode = version.versionCode,
                versionName = version.versionName,
                source = source,
                size = version.size,
                downloadUrl = version.downloadUrl,
                sha256 = version.sha256,
                minSdk = version.minSdk,
                targetSdk = version.targetSdk,
                addedAt = version.addedAt,
                artifactType = version.artifactType.name,
                signer = version.signer,
                nativeCode = version.nativeCode.joinToString(SEPARATOR),
                identityFromArtifact = version.identityFromArtifact,
            )
        }
        catalogDao.replaceSource(source, listOf(appEntity), versions)
    }

    private suspend fun storeCatalog(source: String, parsed: ParsedIndex) {
        val apps = parsed.apps.map { app ->
            RemoteAppEntity(
                packageName = app.packageName,
                name = app.name,
                summary = app.summary,
                developer = app.developer,
                iconUrl = app.iconUrl,
                license = app.license,
                description = app.description,
                changelog = app.changelog,
                website = app.website,
                sourceCodeUrl = app.sourceCode,
                categories = app.categories.joinToString(SEPARATOR),
                source = source,
                addedAt = app.added,
                lastUpdatedAt = app.lastUpdated,
            )
        }
        val versions = parsed.versions.map { version ->
            AppVersionEntity(
                packageName = version.packageName,
                versionCode = version.versionCode,
                versionName = version.versionName,
                source = source,
                size = version.size,
                downloadUrl = version.downloadUrl,
                sha256 = version.sha256,
                minSdk = version.minSdk,
                targetSdk = version.targetSdk,
                addedAt = version.added,
                artifactType = ArtifactType.APK.name,
                signer = version.signer,
                nativeCode = version.nativeCode.joinToString(SEPARATOR),
            )
        }
        catalogDao.replaceSource(source, apps, versions)
    }

    /** Inserts built-in repositories that are not in the database yet. */
    private suspend fun ensureBuiltIns() {
        if (seeded) return
        seedMutex.withLock {
            if (seeded) return
            val existing = repositoryDao.all().associateBy { it.repositoryId }
            // Existing rows are never updated here: user-renamed names and any
            // user reordering of priority survive (P06-T04).
            missingBuiltInRows(existing).forEach { repositoryDao.upsert(it) }
            seeded = true
        }
    }

    private fun preferredLocales(): List<String> {
        val list = context.resources.configuration.locales
        return (0 until list.size()).map { list[it].toLanguageTag() }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun providerDefaultName(providerType: ProviderType, canonical: String): String {
        val host = canonical.removePrefix("https://").substringBefore('/').substringBefore(':')
        val project = canonical.removePrefix("https://").substringAfter('/', "missing").substringBefore('/')
            .takeIf { it.isNotBlank() && it != "missing" }
        val label = project?.takeIf { providerType != ProviderType.HTML_REGEX }
            ?: host
        return "${providerType.name.lowercase().substringBefore('_')} · $label"
    }

    private companion object {
        const val SEPARATOR = "|"
        const val CUSTOM_PRIORITY = 500
        const val AUTO_REFRESH_INTERVAL_MS = 3L * 60 * 60 * 1000

        /** Concurrent index downloads. */
        const val REFRESH_PARALLELISM = 4
    }
}
