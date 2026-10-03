package com.novastore.app.domain.repository

import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.PlayDownloadFile
import com.novastore.app.core.model.RemoteApp
import com.novastore.app.core.model.RemoteAppDetails

/**
 * Google Play catalog access on top of the vendored GPlayApi module.
 *
 * All methods are safe to call while not signed in: they return empty/null
 * results instead of failing, except [purchaseDownloadFiles] which throws a
 * [com.novastore.app.core.model.PlayStoreException] describing the failure.
 */
interface PlayStoreRepository {
    /** Play search; never throws — returns an empty list on failure. */
    suspend fun search(query: String): List<RemoteApp>

    /** Play's current top-free apps chart; empty when not signed in or on failure. */
    suspend fun topFreeApps(): List<RemoteApp>

    /** Play details for one package, or null when not found / not signed in. */
    suspend fun getAppDetails(packageName: String): RemoteAppDetails?

    /**
     * Latest known version per package (bulk). Packages unknown to Play or
     * errors are simply missing from the result map.
     */
    suspend fun getVersionsFor(packageNames: Collection<String>): Map<String, List<AppVersion>>

    /** Latest Play version of one package, or null if not in Play / not signed in. */
    suspend fun resolveLatestVersion(packageName: String): AppVersion?

    /**
     * Resolves the download files (base + splits) for one package/version via
     * the Play purchase/delivery endpoints (account or anonymous session). The
     * URLs are single-use. The community mirror is never used as a fallback.
     *
     * @throws com.novastore.app.core.model.PlayStoreException when no session is
     * available, the app is paid/unavailable, or Play refuses the delivery.
     */
    suspend fun purchaseDownloadFiles(packageName: String, versionCode: Long): List<PlayDownloadFile>

    /** True while a signed-in (account) Play session is active. */
    suspend fun isLoggedIn(): Boolean

    /**
     * True when the native Play protocol is usable right now: a signed-in
     * account OR the anonymous Play session (minted on demand). This is what
     * search/details/update checks should gate on, not [isLoggedIn].
     */
    suspend fun hasPlayAccess(): Boolean

    /**
     * Latest Play version for this device through any available session
     * (account or anonymous), or null when Play does not carry the app.
     */
    suspend fun resolvePlayLatest(packageName: String): AppVersion?

    /**
     * Listing summaries (rating, downloads, size, developer, price) for many
     * packages in bulk — one request per 50 apps, cached. Missing packages
     * are simply absent.
     */
    suspend fun summaries(packageNames: Collection<String>): Map<String, RemoteApp>

    /**
     * Nova anonymous tier: latest versions from the community mirror, bulk.
     * Empty when the mirror is disabled in settings or unreachable. Works
     * without any account or server.
     */
    suspend fun mirrorVersionsFor(packageNames: Collection<String>): Map<String, List<AppVersion>>
}
