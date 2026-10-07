package com.novastore.app.domain.usecase

import com.novastore.app.core.common.AppResult
import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.InstalledApp
import com.novastore.app.core.model.NovaError
import com.novastore.app.core.model.RemoteApp
import com.novastore.app.core.model.RemoteAppDetails
import com.novastore.app.core.model.StoreLink
import com.novastore.app.core.model.UpdateCandidate
import com.novastore.app.core.model.UpdateConfidence
import com.novastore.app.core.model.UpdateSettings
import com.novastore.app.core.model.UpdateState
import com.novastore.app.domain.repository.CatalogRepository
import com.novastore.app.domain.repository.InstalledAppsRepository
import com.novastore.app.domain.repository.PlayStoreRepository
import com.novastore.app.domain.repository.RepositoriesRepository
import com.novastore.app.domain.repository.ScanProgressTracker
import com.novastore.app.domain.repository.SettingsRepository
import com.novastore.app.domain.repository.UpdateCheckService
import com.novastore.app.domain.repository.UpdateScanReport
import com.novastore.app.domain.repository.UpdatesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Workflow tests for the update pass (P05-T11): how the scan relates to the
 * Google Play pass and which rows survive the final cleanup.
 */
class CheckForUpdatesUseCaseTest {

    private fun installedApp(packageName: String = "com.example.app") = InstalledApp(
        packageName = packageName,
        appName = "App",
        versionName = "1.0",
        versionCode = 1,
        firstInstallTime = 0,
        lastUpdateTime = 0,
        installerSource = null,
        signingCertDigest = "AAAA",
        isSystemApp = false,
    )

    private fun repoCandidate(pkg: String = "com.example.app"): UpdateCandidate = UpdateCandidate(
        installed = installedApp(pkg),
        available = AppVersion(
            packageName = pkg,
            versionCode = 2,
            versionName = "2.0",
            source = "repo-a",
            size = null,
            downloadUrl = "https://repo-a.invalid/$pkg-2.apk",
            sha256 = null,
            minSdk = null,
            targetSdk = null,
            addedAt = null,
        ),
        source = "repo-a",
    )

    /** Interface fakes; only the members the use case exercises are implemented. */
    private class Harness(
        installed: List<InstalledApp>,
        settings: UpdateSettings,
        refreshResult: AppResult<Unit>,
        report: AppResult<UpdateScanReport>,
        private val playAccess: Boolean,
    ) {
        private val engineReport = report
        private val settingsFlow = MutableStateFlow(settings)

        var retainOnlyArg: Set<String>? = null
        var lastCheckedAt: Long? = null
        var catalogScanned = false
        var playPassQueried = false
        var removed = mutableListOf<String>()
        var currentCandidates = mutableListOf<UpdateCandidate>()

        private val repos = object : RepositoriesRepository {
            override fun observe(): Flow<List<com.novastore.app.core.model.RepositoryConfig>> = flowOf(emptyList())
            override fun observeAppCounts(): Flow<Map<String, Int>> = flowOf(emptyMap())
            override suspend fun get(repositoryId: String): com.novastore.app.core.model.RepositoryConfig? = null
            override suspend fun add(name: String, url: String): AppResult<Unit> = AppResult.failure(NovaError.Unknown())
            override suspend fun setEnabled(repositoryId: String, enabled: Boolean): AppResult<Unit> = AppResult.success(Unit)
            override suspend fun remove(repositoryId: String) = Unit
            override suspend fun refresh(repositoryId: String): AppResult<Unit> = AppResult.success(Unit)
            override suspend fun refreshAll(force: Boolean): AppResult<Unit> = refreshResult
            override suspend fun priorities(): Map<String, Int> = emptyMap()
        }

        private val engine = object : UpdateCheckService {
            override suspend fun checkForUpdates(): AppResult<UpdateScanReport> = engineReport
        }

        val updates = object : UpdatesRepository {
            override fun observeCandidates(): Flow<List<UpdateCandidate>> = MutableStateFlow(emptyList())
            override fun observeState(packageName: String): Flow<UpdateState?> = MutableStateFlow(null)
            override suspend fun currentCandidates(): List<UpdateCandidate> = currentCandidates
            override suspend fun saveCandidate(candidate: UpdateCandidate, state: UpdateState) = Unit
            override suspend fun transition(packageName: String, to: UpdateState) = Unit
            override suspend fun remove(packageName: String) { removed += packageName }
            override suspend fun retainOnly(packageNames: Set<String>) { retainOnlyArg = packageNames }
            override suspend fun clearFinished() = Unit
            override suspend fun lastCheckedAt(): Long = 0
            override suspend fun setLastCheckedAt(timestampMillis: Long) { lastCheckedAt = timestampMillis }
        }

        private val installedApps = object : InstalledAppsRepository {
            override suspend fun scan(): AppResult<List<InstalledApp>> = AppResult.success(installed)
            override fun observe(): Flow<List<InstalledApp>> = MutableStateFlow(installed)
            override suspend fun get(packageName: String): InstalledApp? = installed.firstOrNull { it.packageName == packageName }
            override suspend fun refresh(packageName: String): InstalledApp? = installed.firstOrNull { it.packageName == packageName }
            override suspend fun uninstall(packageName: String): AppResult<Unit> = AppResult.success(Unit)
            override suspend fun openApp(packageName: String): AppResult<Unit> = AppResult.success(Unit)
            override suspend fun openAppSettings(packageName: String): AppResult<Unit> = AppResult.success(Unit)
        }

        private val catalog = object : CatalogRepository {
            override suspend fun search(query: String): AppResult<List<RemoteApp>> = AppResult.success(emptyList())
            override suspend fun searchLocal(query: String, offset: Int, limit: Int): List<RemoteApp> = emptyList()
            override suspend fun playStorefront(shelf: String?): List<RemoteApp> = emptyList()
            override fun preview(packageName: String): RemoteApp? = null
            override suspend fun resolveStoreLink(raw: String): StoreLink? = null
            override suspend fun getAppDetails(packageName: String): AppResult<RemoteAppDetails?> = AppResult.success(null)
            override suspend fun getReviews(packageName: String): List<com.novastore.app.core.model.AppReview> = emptyList()
            override fun observeRecentlyAdded(limit: Int): Flow<List<RemoteApp>> = flowOf(emptyList())
            override fun observeCategories(): Flow<List<String>> = flowOf(emptyList())
            override suspend fun listByCategory(category: String?, offset: Int, limit: Int): List<RemoteApp> = emptyList()
            override suspend fun getVersionsFor(packageNames: Collection<String>): Map<String, List<AppVersion>> {
                catalogScanned = true
                return emptyMap()
            }
        }

        private val play = object : PlayStoreRepository {
            override suspend fun search(query: String): List<RemoteApp> = emptyList()
            override suspend fun topFreeApps(): List<RemoteApp> = emptyList()
            override suspend fun getAppDetails(packageName: String): RemoteAppDetails? = null
            override suspend fun getVersionsFor(packageNames: Collection<String>): Map<String, List<AppVersion>> {
                playPassQueried = true
                return emptyMap()
            }
            override suspend fun resolveLatestVersion(packageName: String): AppVersion? = null
            override suspend fun resolvePlayLatest(packageName: String): AppVersion? = null
            override suspend fun purchaseDownloadFiles(packageName: String, versionCode: Long): List<com.novastore.app.core.model.PlayDownloadFile> = emptyList()
            override suspend fun isLoggedIn(): Boolean = false
            override suspend fun hasPlayAccess(): Boolean = playAccess
            override suspend fun summaries(packageNames: Collection<String>): Map<String, RemoteApp> = emptyMap()
        }

        private val settingsRepo = object : SettingsRepository {
            override val settings: Flow<UpdateSettings> = settingsFlow
            override suspend fun update(transform: (UpdateSettings) -> UpdateSettings) {
                settingsFlow.value = transform(settingsFlow.value)
            }
        }

        val useCase = CheckForUpdatesUseCase(
            repositoriesRepository = repos,
            updateCheckService = engine,
            updatesRepository = updates,
            installedAppsRepository = installedApps,
            catalogRepository = catalog,
            playStoreRepository = play,
            settingsRepository = settingsRepo,
            scanProgress = ScanProgressTracker(),
        )
    }

    private fun successful(result: AppResult<UpdateScanReport>): UpdateScanReport {
        assertTrue("expected Success, got Failute: $result", result is AppResult.Success)
        return (result as AppResult.Success).value
    }

    @Test
    fun playUpdatesDisabledSkipsPlayPassEntirely() = runTest {
        val h = Harness(
            installed = listOf(installedApp()),
            settings = UpdateSettings(playUpdatesEnabled = false),
            refreshResult = AppResult.success(Unit),
            report = AppResult.success(
                UpdateScanReport(installedScanned = 1, candidatesFound = 1, candidates = listOf(repoCandidate()), errors = emptyList()),
            ),
            playAccess = true,
        )

        val result = h.useCase()

        assertEquals(1, successful(result).candidatesFound)
        assertFalse("no repository catalog lookup without the Play pass", h.catalogScanned)
        assertFalse("Play versions must not be queried when disabled", h.playPassQueried)
        // The app is repo-managed; its row is the only survivor.
        assertEquals(setOf("com.example.app"), h.retainOnlyArg)
        assertTrue("scan timestamp recorded", h.lastCheckedAt != null)
    }

    @Test
    fun playEnabledWithoutAccessKeepsUnansweredApps() = runTest {
        // com.example.other is NOT managed by any repository, and Play has no
        // session now -> its row may not be deleted by the final cleanup.
        val h = Harness(
            installed = listOf(installedApp("com.example.other")),
            settings = UpdateSettings(playUpdatesEnabled = true),
            refreshResult = AppResult.success(Unit),
            report = AppResult.success(
                UpdateScanReport(installedScanned = 1, candidatesFound = 0, candidates = emptyList(), errors = emptyList()),
            ),
            playAccess = false,
        )

        val result = h.useCase()

        assertTrue(result is AppResult.Success)
        assertTrue("play pass gated on access", !h.playPassQueried)
        assertEquals(setOf("com.example.other"), h.retainOnlyArg)
    }

    @Test
    fun repoRefreshFailureWithNoComparableSourceSurfacesError() = runTest {
        val h = Harness(
            installed = listOf(installedApp()),
            settings = UpdateSettings(playUpdatesEnabled = true),
            refreshResult = AppResult.failure(NovaError.Unknown(userMessage = "offline")),
            report = AppResult.success(
                UpdateScanReport(installedScanned = 0, candidatesFound = 0, candidates = emptyList(), errors = emptyList()),
            ),
            playAccess = false,
        )

        val result = h.useCase()

        assertTrue("search results nothing better with no source read", result is AppResult.Failure)
    }

    @Test
    fun legacyDiscoveryRowsAreRemovedBeforeAnyPass() = runTest {
        val h = Harness(
            installed = listOf(installedApp()),
            settings = UpdateSettings(playUpdatesEnabled = false),
            refreshResult = AppResult.success(Unit),
            report = AppResult.success(
                UpdateScanReport(installedScanned = 1, candidatesFound = 1, candidates = listOf(repoCandidate()), errors = emptyList()),
            ),
            playAccess = false,
        )
        h.currentCandidates.add(repoCandidate().copy(confidence = UpdateConfidence.DISCOVERY))

        h.useCase()

        assertEquals(listOf("com.example.app"), h.removed)
    }
}