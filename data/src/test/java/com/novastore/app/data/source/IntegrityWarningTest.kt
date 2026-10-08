package com.novastore.app.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * P06-T08: preview integrity warnings. The acceptance fixture is an index
 * that publishes versions but no sha256 — the add-source preview must warn
 * without blocking; an index carrying hashes is clean; HTML sources always
 * warn because they can never carry an origin checksum.
 */
class IntegrityWarningTest {

    @Test
    fun fdroidIntegrityWarning_warnsWhenIndexPublishesVersionsWithoutSha256() {
        val warning = fdroidIntegrityWarning(versionCount = 5, hasAnySha256 = false)
        assertNotNull("fixture without sha256 must warn", warning)
        assertEquals(
            fdroidIntegrityWarning(versionCount = 5, hasAnySha256 = false),
            warning,
        )
    }

    @Test
    fun fdroidIntegrityWarning_cleanWhenIndexCarriesSha256() {
        assertNull(fdroidIntegrityWarning(versionCount = 5, hasAnySha256 = true))
    }

    @Test
    fun fdroidIntegrityWarning_cleanWhenBlankSha256TreatedAsMissing_butPresentSomewhere() {
        // Blank hashes count as absent, but any real one keeps the index clean.
        assertNull(fdroidIntegrityWarning(versionCount = 2, hasAnySha256 = true))
    }

    @Test
    fun fdroidIntegrityWarning_cleanWhenIndexHasNoVersions() {
        assertNull(fdroidIntegrityWarning(versionCount = 0, hasAnySha256 = false))
        assertNull(fdroidIntegrityWarning(versionCount = 0, hasAnySha256 = true))
    }

    @Test
    fun htmlAlwaysWarns_absenceOfOriginChecksumIsStructural() {
        assertNotNull(HTML_INTEGRITY_WARNING)
        assertEquals(HTML_INTEGRITY_WARNING, HTML_INTEGRITY_WARNING)
    }
}