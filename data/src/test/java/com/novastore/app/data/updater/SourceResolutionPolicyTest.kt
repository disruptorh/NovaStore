package com.novastore.app.data.updater

import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.InstalledApp
import com.novastore.app.core.model.CompatibilityVerdict
import com.novastore.app.core.model.IncompatibilityReason
import com.novastore.app.core.model.UpdateCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SourceResolutionPolicyTest {

    private val policy = SourceResolutionPolicy()

    private val installed = InstalledApp(
        packageName = "com.example.app",
        appName = "App",
        versionName = "1.0",
        versionCode = 1,
        firstInstallTime = 0,
        lastUpdateTime = 0,
        installerSource = null,
        signingCertDigest = null,
        isSystemApp = false,
    )

    private fun candidate(source: String, versionCode: Long, compatible: Boolean = true) =
        UpdateCandidate(
            installed = installed,
            available = AppVersion(
                packageName = installed.packageName,
                versionCode = versionCode,
                versionName = null,
                source = source,
                size = null,
                downloadUrl = "https://example.invalid/$source.apk",
                sha256 = null,
                minSdk = null,
                targetSdk = null,
                addedAt = null,
            ),
            source = source,
            compatibility = if (compatible) {
                CompatibilityVerdict.compatible()
            } else {
                CompatibilityVerdict.incompatible(IncompatibilityReason.PACKAGE_CONFLICT)
            },
        )

    @Test
    fun lowerPriorityWinsOverHigherVersionCode() {
        val winner = policy.select(
            mapOf(
                "repo-b" to candidate("repo-b", versionCode = 200),
                "repo-a" to candidate("repo-a", versionCode = 100),
            ),
            priorityOf = { if (it == "repo-a") 10 else 20 },
        )

        // Priority 10 with v100 beats priority 20 with v200: a better-placed
        // repo keeps ownership of the catalog entry.
        assertEquals("repo-a", winner?.source)
        assertEquals(100L, winner?.available?.versionCode)
    }

    @Test
    fun equalPriorityPreferHigherVersionCode() {
        val winner = policy.select(
            mapOf(
                "repo-b" to candidate("repo-b", versionCode = 200),
                "repo-a" to candidate("repo-a", versionCode = 100),
            ),
            priorityOf = { 10 },
        )

        assertEquals("repo-b", winner?.source)
        assertEquals(200L, winner?.available?.versionCode)
    }

    @Test
    fun fullTieBreaksBySourceId() {
        val winner = policy.select(
            mapOf(
                "repo-b" to candidate("repo-b", versionCode = 100),
                "repo-a" to candidate("repo-a", versionCode = 100),
            ),
            priorityOf = { 10 },
        )

        assertEquals("repo-a", winner?.source)
    }

    @Test
    fun incompatibleCandidatesAreExcluded() {
        val winner = policy.select(
            mapOf(
                "repo-a" to candidate("repo-a", versionCode = 100, compatible = false),
                "repo-b" to candidate("repo-b", versionCode = 200),
            ),
            priorityOf = { 10 },
        )

        assertEquals("repo-b", winner?.source)
    }

    @Test
    fun allIncompatibleOrEmptyReturnsNull() {
        assertNull(
            policy.select(
                mapOf("repo-a" to candidate("repo-a", versionCode = 100, compatible = false)),
                priorityOf = { 10 },
            ),
        )
        assertNull(policy.select(emptyMap(), priorityOf = { 10 }))
    }
}