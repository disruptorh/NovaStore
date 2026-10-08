package com.novastore.app.feature.details

import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.InstalledApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * P06-T06: a preferred source must win the version choice, and an absent
 * preference must keep the existing signer-first/Play/rest logic intact.
 */
class PickBestPreferredSourceTest {

    private val packageName = "com.example.app"
    private val installed = InstalledApp(
        packageName = packageName,
        appName = "Example",
        versionName = "10.0",
        versionCode = 100,
        firstInstallTime = 1,
        lastUpdateTime = 2,
        installerSource = null,
        signingCertDigest = "aa".repeat(32),
        isSystemApp = false,
    )

    private fun version(code: Long, source: String) = AppVersion(
        packageName = packageName,
        versionCode = code,
        versionName = "v$code",
        source = source,
        size = null,
        downloadUrl = "https://example.com/$code.apk",
        sha256 = null,
        minSdk = null,
        targetSdk = null,
        addedAt = code,
        signer = "aa".repeat(32),
    )

    private lateinit var plans: List<AppVersion>

    @Before
    fun setUp() {
        plans = listOf(
            version(70, "fdroid"),
            version(80, "github"),
            version(90, "play"),
        )
    }

    @Test
    fun noPreferenceKeepsSignerGroupBehaviour() {
        // Signer matches installed cert, all three are in the signer group.
        assertEquals(90L, pickBestVersion(plans, installed, null)?.versionCode)
    }

    @Test
    fun preferredSourceWinsOverHigherVersionCode() {
        // Normal logic would pick 90 (highest in signer group) → 70 wins.
        assertEquals(70L, pickBestVersion(plans, installed, "fdroid")?.versionCode)
        assertEquals(90L, pickBestVersion(plans, installed, "play")?.versionCode)
    }

    @Test
    fun preferredSourcePicksItsHighestVersion() {
        val withTwo = plans + version(75, "fdroid")
        assertEquals(75L, pickBestVersion(withTwo, installed, "fdroid")?.versionCode)
    }

    @Test
    fun unrelatedPreferredSourceFallsBackToNormalLogic() {
        assertEquals(90L, pickBestVersion(plans, installed, "gitlab")?.versionCode)
    }

    @Test
    fun freshInstallWithPreferredPicksPreferred() {
        assertEquals(70L, pickBestVersion(plans, null, "fdroid")?.versionCode)
    }

    @Test
    fun preferredIgnoresInstalledCertMismatch() {
        val mismatched = installed.copy(signingCertDigest = "bb".repeat(32))
        assertEquals(70L, pickBestVersion(plans, mismatched, "fdroid")?.versionCode)
    }
}