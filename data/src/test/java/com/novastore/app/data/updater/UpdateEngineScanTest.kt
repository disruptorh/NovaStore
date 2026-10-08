package com.novastore.app.data.updater

import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.InstalledApp
import com.novastore.app.core.model.UpdateSettings
import com.novastore.app.core.security.DeviceProfile
import com.novastore.app.core.security.DeviceProfileProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P11-T06 — update scan is catalog-driven. [UpdateEngine] never contacts a
 * source or mirror while scanning: every candidate comes from the in-memory
 * catalog this test feeds it, so resolving a full scan takes no network and
 * no provider.
 */
class UpdateEngineScanTest {

    private val compatibility = CompatibilityChecker(
        deviceProfileProvider = object : DeviceProfileProvider {
            override fun current() = DeviceProfile(
                apiLevel = 35,
                supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64"),
                freeSpaceBytes = 2L * 1024 * 1024 * 1024,
            )
        },
    )
    private val policy = SourceResolutionPolicy()
    private val settings = UpdateSettings()

    private fun installedApp(
        packageName: String = "com.example.app",
        versionCode: Long = 1,
        signer: String? = "AAAA",
    ) = InstalledApp(
        packageName = packageName,
        appName = "App",
        versionName = "$versionCode.0",
        versionCode = versionCode,
        firstInstallTime = 0,
        lastUpdateTime = 0,
        installerSource = null,
        signingCertDigest = signer,
        isSystemApp = false,
    )

    private fun version(
        packageName: String,
        versionCode: Long,
        source: String,
        signer: String? = "AAAA",
        nativeCode: List<String> = emptyList(),
    ) = AppVersion(
        packageName = packageName,
        versionCode = versionCode,
        versionName = "$versionCode.0",
        source = source,
        size = null,
        downloadUrl = "https://$source.invalid/$packageName-$versionCode.apk",
        sha256 = null,
        minSdk = null,
        targetSdk = null,
        addedAt = null,
        signer = signer,
        nativeCode = nativeCode,
    )

    private fun resolve(
        installed: List<InstalledApp> = listOf(installedApp()),
        versionsByPackage: Map<String, List<AppVersion>>,
        preferred: Map<String, String> = emptyMap(),
        priorities: Map<String, Int> = emptyMap(),
    ) = UpdateEngine.resolveCandidates(
        installed = installed,
        versionsByPackage = versionsByPackage,
        settings = settings,
        preferredSourceByPackage = preferred,
        priorities = priorities,
        compatibilityChecker = compatibility,
        policy = policy,
    )

    @Test
    fun lowestPrioritySourceWinsScanResolution() {
        val app = installedApp(packageName = "com.example.app")
        val versions = mapOf(
            app.packageName to listOf(
                version(app.packageName, 2, source = "apkpure"),
                version(app.packageName, 3, source = "fdroid"),
            ),
        )
        val candidates = resolve(
            installed = listOf(app),
            versionsByPackage = versions,
            priorities = mapOf("fdroid" to 10, "apkpure" to 50),
        )
        assertEquals(1, candidates.size)
        assertEquals("fdroid", candidates.single().source)
        assertEquals(3, candidates.single().available.versionCode)
    }

    @Test
    fun highestVersionCodeWinsWithinOneSource() {
        val app = installedApp(packageName = "com.example.app")
        val versions = mapOf(
            app.packageName to listOf(
                version(app.packageName, 4, source = "fdroid"),
                version(app.packageName, 9, source = "fdroid"),
                version(app.packageName, 2, source = "fdroid"),
            ),
        )
        val candidates = resolve(installed = listOf(app), versionsByPackage = versions)
        assertEquals(9, candidates.single().available.versionCode)
        assertEquals("fdroid", candidates.single().source)
    }

    @Test
    fun foreignSignatureVersionsAreNeverOffered() {
        val app = installedApp(packageName = "com.example.app", signer = "installed-AAAA")
        val versions = mapOf(
            app.packageName to listOf(
                version(app.packageName, 2, source = "fdroid", signer = "other-BBBB"),
                version(app.packageName, 3, source = "fdroid", signer = "installed-AAAA"),
                // Unknown signer: allowed (verified after download).
                version(app.packageName, 5, source = "apkpure", signer = null),
            ),
        )
        val candidates = resolve(installed = listOf(app), versionsByPackage = versions)
        // Foreign signature excluded; matching and unknown signers offered:
        // the policy picks the highest versionCode (apkpure v5, unknown signer).
        assertEquals(5, candidates.single().available.versionCode)
        assertEquals("apkpure", candidates.single().source)

        val foreignOnly = resolve(
            installed = listOf(app),
            versionsByPackage = mapOf(
                app.packageName to listOf(version(app.packageName, 2, source = "fdroid", signer = "other-BBBB")),
            ),
        )
        assertTrue("a foreign-signed version must never be offered", foreignOnly.isEmpty())
    }

    @Test
    fun preferredSourceWinsOverPriority() {
        val app = installedApp(packageName = "com.example.app")
        val versions = mapOf(
            app.packageName to listOf(
                version(app.packageName, 2, source = "apkpure"),
                version(app.packageName, 2, source = "fdroid"),
            ),
        )
        val candidates = resolve(
            installed = listOf(app),
            versionsByPackage = versions,
            preferred = mapOf(app.packageName to "apkpure"),
            priorities = mapOf("fdroid" to 10, "apkpure" to 50),
        )
        assertEquals("apkpure", candidates.single().source)
    }

    @Test
    fun appsWithoutCatalogEntryProduceNoCandidate() {
        val withCatalog = installedApp(packageName = "com.example.app")
        val withoutCatalog = installedApp(packageName = "com.example.unknown")
        val versions = mapOf(
            withCatalog.packageName to listOf(version(withCatalog.packageName, 2, source = "fdroid")),
        )
        val candidates = resolve(
            installed = listOf(withCatalog, withoutCatalog),
            versionsByPackage = versions,
        )
        assertEquals(1, candidates.size)
        assertEquals("com.example.app", candidates.single().installed.packageName)
    }
}