package com.novastore.app.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P11-T01 decision logic. Pure predicates extracted from
 * [DefaultArtifactVerifier.verify] so the checksum trust boundary and the
 * mismatch → [NovaError.ChecksumMismatch] path are unit-tested without a
 * device; the Android-backed archive parse stays an integration concern.
 */
class DefaultArtifactVerifierTest {

    private val validChecksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    @Test
    fun `a source-offered sha256 counts as checksum-from-source`() {
        assertTrue(DefaultArtifactVerifier.sourceProvidingChecksum(validChecksum))
    }

    @Test
    fun `a missing sha256 is not checksum-from-source`() {
        assertFalse(DefaultArtifactVerifier.sourceProvidingChecksum(null))
    }

    @Test
    fun `a malformed sha256 is not checksum-from-source`() {
        assertFalse(DefaultArtifactVerifier.sourceProvidingChecksum("not-a-sha"))
        assertFalse(DefaultArtifactVerifier.sourceProvidingChecksum(validChecksum.dropLast(2)))
    }

    @Test
    fun `a matching hash verifies case insensitively`() {
        assertTrue(DefaultArtifactVerifier.checksumStillMatches(validChecksum.uppercase(), validChecksum))
    }

    @Test
    fun `a wrong expected checksum is a mismatch (Invalid)`() {
        val corrupted = validChecksum.replaceFirstChar { if (it == 'e') 'f' else it }
        assertFalse(DefaultArtifactVerifier.checksumStillMatches(corrupted, validChecksum))
    }
}