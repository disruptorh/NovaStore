package com.novastore.app.domain.usecase

import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.InstalledApp
import com.novastore.app.core.model.UpdateCandidate
import com.novastore.app.core.model.UpdateConfidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P11-T01: the automatic pipeline must never install an update whose checksum
 * was computed locally instead of being offered by the repository. Candidates
 * without a well-formed source checksum are skipped by [UpdateAllUseCase.autoUpdatable].
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
    fun `skips an exact candidate without a source checksum`() {
        assertTrue(UpdateAllUseCase.autoUpdatable(listOf(candidate(checksum = null))).isEmpty())
    }

    @Test
    fun `skips an exact candidate with a malformed checksum`() {
        assertTrue(UpdateAllUseCase.autoUpdatable(listOf(candidate(checksum = "deadbeef"))).isEmpty())
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
}