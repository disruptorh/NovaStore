package com.novastore.app.core.network.fdroid

import java.io.File

/**
 * Native index parser.
 *
 * The C++ side memory maps the downloaded file, parses it with the same rules as
 * [RepoIndexParser] and returns one flat buffer that [FdroidIndexBuffer] reads.
 * Every reason to distrust the native result — no shared library on this host,
 * an unreadable file, malformed JSON, a buffer that does not match the format —
 * comes back as `null` so the caller can run the Gson parser instead. The native
 * path is therefore never the only path.
 */
internal object NativeFdroidIndex {

    private const val LIBRARY_NAME = "novastore_native"

    /** False wherever the shared library is missing, JVM unit tests included. */
    val isAvailable: Boolean = try {
        System.loadLibrary(LIBRARY_NAME)
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    fun parse(
        file: File,
        baseUrl: String,
        v2Format: Boolean,
        preferredLocales: List<String>,
    ): ParsedIndex? {
        if (!isAvailable) return null
        val buffer = nativeParse(file.absolutePath, baseUrl, v2Format, preferredLocales.toTypedArray())
            ?: return null
        return FdroidIndexBuffer.decode(buffer)
    }

    private external fun nativeParse(
        path: String,
        baseUrl: String,
        v2Format: Boolean,
        locales: Array<String>,
    ): ByteArray?
}
