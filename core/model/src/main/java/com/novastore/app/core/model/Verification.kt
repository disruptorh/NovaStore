package com.novastore.app.core.model

/**
 * Outcome of artifact verification. Verification is always mandatory
 * before installation, regardless of source trust or root availability.
 */
sealed class VerificationResult {
    /** All executed checks passed. */
    data class Valid(
        val packageName: String,
        val versionCode: Long,
        val sha256: String?,
        val certificate: CertificateInfo?,
        /**
         * True when [sha256] is the checksum the repository offered for this
         * release. False when it had to be computed locally; such artifacts
         * may still be installed manually, but never by the automatic
         * update-all pipeline (P11-T01).
         */
        val checksumFromSource: Boolean = false,
    ) : VerificationResult()

    data class Invalid(
        val error: NovaError,
    ) : VerificationResult()
}

data class CertificateInfo(
    /** Hex encoded SHA-256 digest of the signing certificate. */
    val sha256Digest: String,
)

data class UpdateHistoryRecord(
    val packageName: String,
    val appName: String,
    val oldVersion: String?,
    val newVersion: String?,
    val timestamp: Long,
    val source: String,
    val result: UpdateHistoryResult,
    val error: String?,
)

enum class UpdateHistoryResult {
    SUCCESS,
    FAILED,
    CANCELLED,
}
