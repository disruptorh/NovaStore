package com.novastore.app.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesTest {

    @Test
    fun `community mirrors are metadata only`() {
        assertFalse(isInstallSourceAllowed(SOURCE_APKPURE))
        assertFalse(isInstallSourceAllowed(SOURCE_APKCOMBO))
    }

    @Test
    fun `play and fdroid style repositories are installable`() {
        assertTrue(isInstallSourceAllowed(SOURCE_PLAY))
        assertTrue(isInstallSourceAllowed(SOURCE_FDROID))
        assertTrue(isInstallSourceAllowed(SOURCE_GITHUB))
        assertTrue(isInstallSourceAllowed(SOURCE_GITLAB))
        // Built-in and user-added F-Droid repositories use their own id.
        assertTrue(isInstallSourceAllowed("izzyondroid"))
        assertTrue(isInstallSourceAllowed("my-custom-repo"))
    }
}
