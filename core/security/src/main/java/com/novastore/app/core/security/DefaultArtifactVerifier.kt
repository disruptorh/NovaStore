package com.novastore.app.core.security

import com.novastore.app.core.common.DispatcherProvider
import com.novastore.app.core.model.CertificateInfo
import com.novastore.app.core.model.NovaError
import com.novastore.app.core.model.VerificationResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * The full verification pipeline executed before every installation.
 * Trusted source or not, root or not — verification is never skipped.
 */
@Singleton
class DefaultArtifactVerifier @Inject constructor(
    private val hashVerifier: HashVerifier,
    private val signatureVerifier: SignatureVerifier,
    private val packageVerifier: PackageVerifier,
    private val dispatcherProvider: DispatcherProvider,
) : ArtifactVerifier {

    override suspend fun verify(
        packageName: String,
        versionCode: Long,
        file: File,
        expectedSha256: String?,
        installedCertDigest: String?,
        identityFromArtifact: Boolean,
    ): VerificationResult = withContext(dispatcherProvider.io) {
        // 1. File exists
        if (!file.exists() || file.length() == 0L) {
            return@withContext VerificationResult.Invalid(NovaError.InvalidPackage)
        }

        // 2. Size sanity (a valid APK is at least a few KB)
        if (file.length() < MIN_APK_BYTES) {
            return@withContext VerificationResult.Invalid(NovaError.InvalidPackage)
        }

        // 3. SHA-256 against source-provided checksum. A missing or malformed
        //    source checksum is not a failure, but it marks the artifact as
        //    not checksum-from-source: it may still be installed manually.
        if (sourceProvidingChecksum(expectedSha256)) {
            val actual = hashVerifier.sha256(file)
            if (!checksumStillMatches(expectedSha256, actual)) {
                return@withContext VerificationResult.Invalid(NovaError.ChecksumMismatch)
            }
        }

        // 4. Package identity and version. Synthetic catalog identities
        //    (provider sources) cannot be compared — the real identity is
        //    adopted from the manifest below.
        val parsed = packageVerifier.parse(file)
            ?: return@withContext VerificationResult.Invalid(NovaError.InvalidPackage)
        if (!identityFromArtifact && parsed.packageName != packageName) {
            return@withContext VerificationResult.Invalid(NovaError.InvalidPackage)
        }
        if (!identityFromArtifact && parsed.versionCode != versionCode) {
            return@withContext VerificationResult.Invalid(
                NovaError.Metadata(
                    userMessage = "The downloaded artifact declares a different version than expected.",
                    packageName = packageName,
                ),
            )
        }

        // 5. The artifact MUST carry a readable signing certificate. A missing
        //    signature is never "nothing to compare" — installation is blocked
        //    instead of silently skipping verification. The certificate comes
        //    from the archive parse above, so PackageManager is not asked to
        //    parse the same APK twice.
        val archiveDigest = signatureVerifier.digestOf(parsed.signingInfo)
            ?: return@withContext VerificationResult.Invalid(NovaError.UnsignedPackage)

        // 6. Signing certificate compatibility with the installed version.
        //    An installed app is never upgraded "sight unseen": when we cannot
        //    establish its current certificate, installation is blocked
        //    (never skipped). When a certificate IS known, any of the archive
        //    signers may match (key rotation), not just the first one.
        val installedDigest = when {
            installedCertDigest != null -> installedCertDigest
            signatureVerifier.isInstalled(packageName) ->
                signatureVerifier.installedCertDigest(packageName)
            else -> null
        }
        if (installedDigest != null &&
            signatureVerifier.anySignerMatches(installedDigest, parsed.signingInfo) != true
        ) {
            return@withContext VerificationResult.Invalid(NovaError.SignatureMismatch)
        }

        VerificationResult.Valid(
            packageName = parsed.packageName ?: packageName,
            versionCode = parsed.versionCode,
            sha256 = expectedSha256 ?: hashVerifier.sha256(file),
            certificate = CertificateInfo(archiveDigest),
            checksumFromSource = sourceProvidingChecksum(expectedSha256),
        )
    }

    companion object {
        private const val MIN_APK_BYTES = 4096L

        /**
         * True when the repository offered a well-formed SHA-256 for this
         * release (so a computed/local hash is not silently trusted as the
         * source's). Exposed as pure for direct unit-testing.
         */
        internal fun sourceProvidingChecksum(expectedSha256: String?): Boolean =
            expectedSha256 != null && HashVerifier.isValidSha256Hex(expectedSha256)

        /**
         * Case-insensitive equality of the computed archive hash with the
         * source-provided checksum. A mismatch is [NovaError.ChecksumMismatch].
         */
        internal fun checksumStillMatches(expectedSha256: String?, actual: String): Boolean =
            expectedSha256 != null && expectedSha256.equals(actual, ignoreCase = true)
    }
}
