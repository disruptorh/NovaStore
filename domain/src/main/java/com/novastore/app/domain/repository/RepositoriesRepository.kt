package com.novastore.app.domain.repository

import com.novastore.app.core.common.AppResult
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RepositoryConfig
import kotlinx.coroutines.flow.Flow

/**
 * Manages the app sources (F-Droid compatible repositories): the built-in
 * list plus any the user adds, and keeps the local catalog in sync with them.
 */
interface RepositoriesRepository {
    fun observe(): Flow<List<RepositoryConfig>>

    /** Number of catalog apps currently cached per repository id. */
    fun observeAppCounts(): Flow<Map<String, Int>>

    suspend fun get(repositoryId: String): RepositoryConfig?

    /**
     * Adds a source (any F-Droid compatible or provider-backed URL) and loads
     * it. [extraJson] carries provider-specific settings (e.g. the HTML
     * source's "apkUrlRegex"). Re-adding an existing URL only re-enables it.
     */
    suspend fun add(
        name: String,
        url: String,
        providerType: ProviderType = ProviderType.FDROID_INDEX,
        extraJson: String? = null,
    ): AppResult<Unit>

    /**
     * Persists an in-place edit (name, provider type, URL, extras). Built-in
     * sources only ever change their local name — the URL stays locked.
     */
    suspend fun update(
        repositoryId: String,
        name: String,
        url: String,
        providerType: ProviderType,
        extraJson: String?,
    ): AppResult<Unit>

    /** Enabling loads the repository; disabling removes its apps from the catalog. */
    suspend fun setEnabled(repositoryId: String, enabled: Boolean): AppResult<Unit>

    suspend fun remove(repositoryId: String)

    /**
     * Reassigns priorities along [idsInOrder] (10, 20, 30, …). The order is
     * persisted, so it survives process death. Built-in sources that were
     * already in the database are never re-positioned afterwards.
     */
    suspend fun reorder(idsInOrder: List<String>)

    suspend fun refresh(repositoryId: String): AppResult<Unit>

    /**
     * Refreshes every enabled repository. Without [force], repositories
     * refreshed recently are skipped. Succeeds when at least one repository
     * provides data; per-repository errors are kept on each repository.
     */
    suspend fun refreshAll(force: Boolean = false): AppResult<Unit>

    /** Repository id → priority (lower wins) for choosing between duplicates. */
    suspend fun priorities(): Map<String, Int>
}
