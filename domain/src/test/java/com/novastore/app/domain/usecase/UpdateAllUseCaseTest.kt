package com.novastore.app.domain.usecase

import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.InstalledApp
import com.novastore.app.core.model.UpdateCandidate
import com.novastore.app.core.model.UpdateConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The automatic pipeline touches every confirmed (EXACT) release, whatever the
 * source. Confidence is the gate: verification (identity, versionCode,
 * signature, checksum-when-offered) still runs on every artifact before install,
 * so a missing source checksum never bypasses it.
 */
class UpdateAllUseCaseTest {

    private val validChecksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    private fun candidate(
        confidence: UpdateConfidence = UpdateConfidence.EXACT,
        checksum: String? = validChecksum,
    ) = UpdateCandidate(
        installed = InstalledApp(
            packageName = "app.pkg",
            appName = "App",
            versionName = "1",
            versionCode = 1,
            firstInstallTime = 0L,
            lastUpdateTime = 0L,
            installerSource = null,
            signingCertDigest = "aa",
            isSystemApp = false,
        ),
        available = AppVersion(
            packageName = "app.pkg",
            versionCode = 2,
            versionName = "2",
            source = "f-droid",
            size = null,
            downloadUrl = "https://example.com/app.apk",
            sha256 = checksum,
            minSdk = null,
            targetSdk = null,
            addedAt = null,
        ),
        source = "f-droid",
        confidence = confidence,
    )

    @Test
    fun `keeps an exact candidate without a source checksum`() {
        assertEquals(
            listOf("app.pkg"),
            UpdateAllUseCase.autoUpdatable(listOf(candidate(checksum = null))).map { it.installed.packageName },
        )
    }

    @Test
    fun `keeps an exact candidate with a malformed checksum`() {
        assertEquals(
            listOf("app.pkg"),
            UpdateAllUseCase.autoUpdatable(listOf(candidate(checksum = "deadbeef"))).map { it.installed.packageName },
        )
    }

    @Test
    fun `keeps an exact candidate carrying a valid source checksum`() {
        assertEquals(
            listOf("app.pkg"),
            UpdateAllUseCase.autoUpdatable(listOf(candidate())).map { it.installed.packageName },
        )
    }

    @Test
    fun `skips discovery candidates even when they carry a checksum`() {
        assertTrue(
            UpdateAllUseCase.autoUpdatable(
                listOf(candidate(confidence = UpdateConfidence.DISCOVERY)),
            ).isEmpty(),
        )
    }

    @Test
    fun `skips paid candidates`() {
        assertTrue(
            UpdateAllUseCase.autoUpdatable(
                listOf(candidate(confidence = UpdateConfidence.PAID)),
            ).isEmpty(),
        )
    }

    @Test
    fun `skips foreign signature candidates`() {
        assertTrue(
            UpdateAllUseCase.autoUpdatable(
                listOf(candidate(confidence = UpdateConfidence.FOREIGN_SIGNATURE)),
            ).isEmpty(),
        )
    }
}