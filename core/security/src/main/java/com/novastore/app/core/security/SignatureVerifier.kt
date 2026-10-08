package com.novastore.app.core.security

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.novastore.app.core.common.DispatcherProvider
import com.novastore.app.core.model.CertificateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Extracts and compares signing certificates of the installed package and
 * a downloaded APK archive. A mismatch blocks automatic updates.
 */
@Singleton
class SignatureVerifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {
    /** Digest of the certificate that signed the currently installed version. */
    suspend fun installedCertDigest(packageName: String): String? = withContext(dispatcherProvider.io) {
        try {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val info = context.packageManager.getPackageInfo(packageName, flags)
            signerDigests(info)?.firstOrNull()
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Compares two hex digests. Null-safe: when the expected certificate
     * cannot be established, callers decide the fallback (never a silent
     * privileged update).
     */
    fun matches(expected: String?, actual: String?): Boolean? {
        if (expected == null || actual == null) return null
        return expected.equals(actual, ignoreCase = true)
    }

    /** Digest of the certificate the package is currently signed with, in the
     * same form F-Droid indexes publish (lowercase hex SHA-256 of the DER
     * certificate). [info] must have been fetched with [SIGNING_FLAGS]. */
    fun digestOf(info: PackageInfo): String? = signerDigests(info)?.firstOrNull()

    /**
     * Whether ANY signer of the archive matches [expected] — an upgrade is
     * accepted as soon as one certificate in `apkContentsSigners` (or the
     * legacy `signatures` array) matches the installed certificate, so a
     * key-rotated app whose old key is in the history still verifies and the
     * comparison never collapses to "first signer only" (P11-T02).
     *
     * Null-safe: when [expected] is null (nothing installed to compare
     * against) or [info] carries no readable certificate, the result is null
     * and the caller decides the fallback — never a silent privileged update.
     */
    fun anySignerMatches(expected: String?, info: PackageInfo): Boolean? {
        if (expected == null) return null
        val digests = signerDigests(info) ?: return null
        return digests.any { it.equals(expected, ignoreCase = true) }
    }

    /** Whether a package with this name is currently installed. */
    suspend fun isInstalled(packageName: String): Boolean = withContext(dispatcherProvider.io) {
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    /** All certificate digests of the archive, in signing order. */
    private fun signerDigests(info: PackageInfo): List<String>? = try {
        val signatures: Array<out android.content.pm.Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return null
            // apkContentsSigners is the current signer, also after key rotation;
            // the rotation history starts with the oldest key.
            signingInfo.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        signatures?.map { first -> MessageDigest.getInstance("SHA-256").digest(first.toByteArray()).joinToString("") { "%02x".format(it) } }
    } catch (_: Exception) {
        null
    }

    fun certificateInfo(digest: String): CertificateInfo = CertificateInfo(digest)

    companion object {
        @Suppress("DEPRECATION")
        val SIGNING_FLAGS: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
    }
}
