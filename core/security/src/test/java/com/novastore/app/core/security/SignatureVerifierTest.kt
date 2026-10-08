package com.novastore.app.core.security

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.content.pm.SigningInfo
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.novastore.app.core.common.DispatcherProvider
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The archive parse now carries the certificate, so both verify paths call
 * [SignatureVerifier.digestOf] on the same [PackageInfo]. These tests pin the
 * legacy extraction path and the flags the archive parse must request; the
 * Android 9+ path needs a real [SigningInfo], which Robolectric shadows away, so
 * it is covered by an instrumented test instead.
 */
@RunWith(RobolectricTestRunner::class)
class SignatureVerifierTest {

    private val verifier = SignatureVerifier(
        ApplicationProvider.getApplicationContext(),
        DirectDispatchers,
    )

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `digest is null when the archive carries no signature`() {
        assertNull(verifier.digestOf(PackageInfo()))
        assertNull(verifier.digestOf(PackageInfo().apply { signingInfo = SigningInfo() }))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `anySignerMatches compares every signer, not just the first`() {
        val first = ByteArray(48) { (it * 3).toByte() }
        val second = ByteArray(48) { (it * 3 + 1).toByte() }
        @Suppress("DEPRECATION")
        val info = PackageInfo().apply { signatures = arrayOf(Signature(first), Signature(second)) }

        // digestOf pins the "current" signer (first), anySignerMatches accepts
        // a match on ANY certificate in the archive (key rotation).
        assertEquals(sha256Hex(first), verifier.digestOf(info))
        assertEquals(true, verifier.anySignerMatches(sha256Hex(first), info))
        assertEquals(true, verifier.anySignerMatches(sha256Hex(second), info))
        assertEquals(false, verifier.anySignerMatches(sha256Hex(ByteArray(48) { (it * 5).toByte() }), info))
    }

    @Test
    fun `anySignerMatches is null safe`() {
        assertNull(verifier.anySignerMatches(null, PackageInfo()))
        assertNull(verifier.anySignerMatches("aabb", PackageInfo()))
    }

    @Test
    fun `isInstalled is false for packages the system does not know`() {
        runTest {
            assertFalse(verifier.isInstalled("com.example.definitely.not.installed"))
        }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `digest reads the legacy signatures array below Android 9`() {
        val certificate = ByteArray(48) { (it * 3).toByte() }
        @Suppress("DEPRECATION")
        val info = PackageInfo().apply { signatures = arrayOf(Signature(certificate)) }

        assertEquals(sha256Hex(certificate), verifier.digestOf(info))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE])
    fun `signing flags request the signing certificates on Android 9 and later`() {
        assertEquals(PackageManager.GET_SIGNING_CERTIFICATES, SignatureVerifier.SIGNING_FLAGS)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O_MR1])
    fun `signing flags request the legacy signatures below Android 9`() {
        @Suppress("DEPRECATION")
        assertEquals(PackageManager.GET_SIGNATURES, SignatureVerifier.SIGNING_FLAGS)
    }

    @Test
    fun `matches is null safe and case insensitive`() {
        assertNull(verifier.matches(null, "aabb"))
        assertNull(verifier.matches("aabb", null))
        assertTrue(verifier.matches("AABB", "aabb") == true)
        assertFalse(verifier.matches("aabb", "aabc") == true)
    }

    /** [SignatureVerifier] only touches the dispatcher inside suspend calls. */
    private object DirectDispatchers : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }
}