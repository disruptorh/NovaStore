package com.novastore.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.novastore.app.core.database.entity.AppVersionEntity
import com.novastore.app.core.database.entity.RemoteAppEntity
import kotlinx.coroutines.flow.Flow

data class SourceCount(val source: String, val count: Int)

private const val BATCH = 500

@Dao
interface CatalogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApps(apps: List<RemoteAppEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVersions(versions: List<AppVersionEntity>)

    @Query("DELETE FROM remote_apps WHERE source = :source")
    suspend fun deleteAppsBySource(source: String)

    @Query("DELETE FROM app_versions WHERE source = :source")
    suspend fun deleteVersionsBySource(source: String)

    @Transaction
    suspend fun replaceSource(source: String, apps: List<RemoteAppEntity>, versions: List<AppVersionEntity>) {
        deleteAppsBySource(source)
        deleteVersionsBySource(source)
        apps.chunked(BATCH).forEach { upsertApps(it) }
        versions.chunked(BATCH).forEach { upsertVersions(it) }
    }

    @Transaction
    suspend fun clearSource(source: String) {
        deleteAppsBySource(source)
        deleteVersionsBySource(source)
    }

    @Query(
        """
        SELECT a.* FROM remote_apps a
        LEFT JOIN repositories r ON r.repositoryId = a.source
        WHERE (a.name LIKE '%' || :query || '%'
            OR a.packageName LIKE '%' || :query || '%'
            OR a.summary LIKE '%' || :query || '%')
        ORDER BY
            CASE WHEN a.name LIKE :query || '%' THEN 0 ELSE 1 END,
            a.name COLLATE NOCASE,
            COALESCE(r.priority, 1000)
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun search(query: String, limit: Int = 200, offset: Int = 0): List<RemoteAppEntity>

    /** The listing from the highest-priority repository that carries the package. */
    @Query(
        """
        SELECT a.* FROM remote_apps a
        LEFT JOIN repositories r ON r.repositoryId = a.source
        WHERE a.packageName = :packageName
        ORDER BY COALESCE(r.priority, 1000)
        LIMIT 1
        """,
    )
    suspend fun getApp(packageName: String): RemoteAppEntity?

    @Query("SELECT * FROM app_versions WHERE packageName = :packageName ORDER BY versionCode DESC")
    suspend fun getVersions(packageName: String): List<AppVersionEntity>

    /**
     * Versions from ENABLED repositories only. A disabled repo's rows never
     * produce an update candidate (P05-T06).
     */
    @Query(
        """
        SELECT * FROM app_versions
        WHERE packageName IN (:packageNames)
          AND source IN (SELECT repositoryId FROM repositories WHERE enabled = 1)
        """,
    )
    suspend fun getVersionsFor(packageNames: List<String>): List<AppVersionEntity>

    /**
     * Most recently updated packages, one row per package. The row (header)
     * comes from the highest-priority repository that carries the package —
     * same rule as search/category/getApp (P06-T07). Ordering is the
     * package-wide recency so a fresher, lower-priority row still surfaces.
     */
    @Query(
        """
        SELECT a.*
        FROM remote_apps a
        LEFT JOIN repositories r ON r.repositoryId = a.source
        JOIN (
            SELECT packageName AS pkg, MIN(COALESCE(r.priority, 1000)) AS pr
            FROM remote_apps x
            LEFT JOIN repositories r ON r.repositoryId = x.source
            GROUP BY packageName
        ) best ON best.pkg = a.packageName AND COALESCE(r.priority, 1000) = best.pr
        GROUP BY a.packageName
        ORDER BY (
            SELECT MAX(COALESCE(x.lastUpdatedAt, x.addedAt, 0))
            FROM remote_apps x WHERE x.packageName = a.packageName
        ) DESC
        LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<RemoteAppEntity>>

    @Query("SELECT source, COUNT(*) AS count FROM remote_apps GROUP BY source")
    fun observeCountsBySource(): Flow<List<SourceCount>>

    @Query("SELECT COUNT(*) FROM remote_apps WHERE source = :source")
    suspend fun appCount(source: String): Int

    @Query("SELECT categories FROM remote_apps")
    fun observeCategoryStrings(): Flow<List<String>>

    /**
     * One page of the catalog for an exact category (categories are stored
     * pipe-separated). One row per package — the listing from the
     * highest-priority repository wins (SQLite bare-column rule with MIN).
     */
    @Query(
        """
        SELECT a.*, MIN(COALESCE(r.priority, 1000)) AS pr FROM remote_apps a
        LEFT JOIN repositories r ON r.repositoryId = a.source
        WHERE ('|' || a.categories || '|') LIKE '%|' || :category || '|%'
        GROUP BY a.packageName
        ORDER BY a.name COLLATE NOCASE
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listByCategory(category: String, offset: Int, limit: Int): List<RemoteAppEntity>

    /** One page of the freshest apps overall ("All" category), min-priority row per package. */
    @Query(
        """
        SELECT a.*
        FROM remote_apps a
        LEFT JOIN repositories r ON r.repositoryId = a.source
        JOIN (
            SELECT packageName AS pkg, MIN(COALESCE(r.priority, 1000)) AS pr
            FROM remote_apps x
            LEFT JOIN repositories r ON r.repositoryId = x.source
            GROUP BY packageName
        ) best ON best.pkg = a.packageName AND COALESCE(r.priority, 1000) = best.pr
        GROUP BY a.packageName
        ORDER BY (
            SELECT MAX(COALESCE(x.lastUpdatedAt, x.addedAt, 0))
            FROM remote_apps x WHERE x.packageName = a.packageName
        ) DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun listRecent(offset: Int, limit: Int): List<RemoteAppEntity>
}
