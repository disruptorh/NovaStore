package com.novastore.app.core.security

import android.content.Context
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novastore.app.core.common.DispatcherProvider
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The archive is parsed once with the signing flags attached, so the digest both
 * verify paths read must come out of that single parse. Robolectric shadows
 * [android.content.pm.SigningInfo] away, so the invariant is checked here with
 * the real PackageManager: the archive parse must return the certificate the
 * installed package has, otherwise every update would be blocked.
 */
@RunWith(AndroidJUnit4::class)
class PackageVerifierInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val packageVerifier = PackageVerifier(context, DirectDispatchers)
    private val signatureVerifier = SignatureVerifier(context, DirectDispatchers)

    @Test
    fun archiveParseCarriesTheSigningCertificate() {
        runBlocking {
            val parsed = packageVerifier.parse(File(context.applicationInfo.sourceDir))

            assertNotNull(parsed)
            assertEquals(context.packageName, parsed!!.packageName)

            val fromArchive = signatureVerifier.digestOf(parsed.signingInfo)
            val fromInstalled = signatureVerifier.installedCertDigest(context.packageName)

            assertNotNull(fromArchive)
            assertEquals(64, fromArchive!!.length)
            assertEquals(fromInstalled, fromArchive)
            assertEquals(expectedDigest(), fromArchive)
        }
    }

    @Test
    fun archiveParseExposesTheSigners() {
        runBlocking {
            val parsed = packageVerifier.parse(File(context.applicationInfo.sourceDir))
            assertNotNull(parsed)

            val signers = parsed!!.signingInfo.let { info ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.signingInfo?.apkContentsSigners
                } else {
                    @Suppress("DEPRECATION")
                    info.signatures
                }
            }

            assertNotNull("archive parse must expose the signers", signers)
            assertTrue(signers!!.isNotEmpty())
        }
    }

    @Test
    fun fileThatIsNotAnArchiveIsRejected() {
        runBlocking {
            val notAnApk = File.createTempFile("novastore", ".apk").apply { writeText("not an apk") }

            assertNull(packageVerifier.parse(notAnApk))
            notAnApk.delete()
        }
    }

    private fun expectedDigest(): String {
        val info = context.packageManager.getPackageInfo(context.packageName, SignatureVerifier.SIGNING_FLAGS)
        val signer = info.signingInfo?.apkContentsSigners?.first() ?: error("test APK must be signed")
        return MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private object DirectDispatchers : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.IO
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val main: CoroutineDispatcher = Dispatchers.Main
    }
}