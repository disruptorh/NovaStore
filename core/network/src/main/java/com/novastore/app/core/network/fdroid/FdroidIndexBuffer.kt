package com.novastore.app.core.network.fdroid

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for the flat buffer the native index parser returns.
 *
 * The layout is written by `fdroid_flat_buffer.cpp`: a 40 byte header, one
 * UTF-8 string blob, then fixed size app and version records that only hold
 * offsets into that blob. Every offset is checked against the buffer before it
 * is used, so a truncated or mismatched buffer is rejected instead of read.
 */
internal object FdroidIndexBuffer {

    private const val MAGIC = 0x3149534E
    private const val FORMAT_VERSION = 1
    private const val HEADER_BYTES = 40
    private const val APP_RECORD_BYTES = 104
    private const val VERSION_RECORD_BYTES = 80
    private const val ABSENT = -1

    fun decode(bytes: ByteArray): ParsedIndex? {
        if (bytes.size < HEADER_BYTES) return null
        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (header.int != MAGIC || header.int != FORMAT_VERSION) return null
        val blobBytes = header.int
        val appCount = header.int
        val versionCount = header.int
        val blobOffset = header.int
        val appsOffset = header.int
        val versionsOffset = header.int
        val repoNameOffset = header.int
        val repoNameLength = header.int

        if (blobOffset != HEADER_BYTES || blobBytes < 0) return null
        if (appCount < 0 || versionCount < 0) return null
        if (appCount > bytes.size / APP_RECORD_BYTES) return null
        if (versionCount > bytes.size / VERSION_RECORD_BYTES) return null
        if (appsOffset != align4(HEADER_BYTES + blobBytes)) return null
        if (versionsOffset != appsOffset + appCount * APP_RECORD_BYTES) return null
        if (versionsOffset + versionCount * VERSION_RECORD_BYTES != bytes.size) return null

        val blob = Blob(bytes, blobOffset, blobBytes)
        val apps = ArrayList<ParsedApp>(appCount)
        for (i in 0 until appCount) {
            val record = ByteBuffer.wrap(bytes, appsOffset + i * APP_RECORD_BYTES, APP_RECORD_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
            val packageName = blob.string(record.int, record.int) ?: return null
            val name = blob.string(record.int, record.int) ?: return null
            apps += ParsedApp(
                packageName = packageName,
                name = name,
                summary = blob.string(record.int, record.int),
                description = blob.string(record.int, record.int),
                developer = blob.string(record.int, record.int),
                iconUrl = blob.string(record.int, record.int),
                license = blob.string(record.int, record.int),
                categories = blob.stringList(record.int, record.int) ?: return null,
                website = blob.string(record.int, record.int),
                sourceCode = blob.string(record.int, record.int),
                changelog = blob.string(record.int, record.int),
                added = record.longOrNull(),
                lastUpdated = record.longOrNull(),
            )
        }

        val versions = ArrayList<ParsedVersion>(versionCount)
        for (i in 0 until versionCount) {
            val record = ByteBuffer.wrap(bytes, versionsOffset + i * VERSION_RECORD_BYTES, VERSION_RECORD_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN)
            val packageName = blob.string(record.int, record.int) ?: return null
            val versionCode = record.long
            val versionName = blob.string(record.int, record.int)
            val downloadUrl = blob.string(record.int, record.int) ?: return null
            val sha256 = blob.string(record.int, record.int)
            val size = record.longOrNull()
            val minSdk = record.intOrNull()
            val targetSdk = record.intOrNull()
            val added = record.longOrNull()
            val signer = blob.string(record.int, record.int)
            val nativeCode = blob.stringList(record.int, record.int) ?: return null
            versions += ParsedVersion(
                packageName = packageName,
                versionCode = versionCode,
                versionName = versionName,
                downloadUrl = downloadUrl,
                sha256 = sha256,
                size = size,
                minSdk = minSdk,
                targetSdk = targetSdk,
                added = added,
                signer = signer,
                nativeCode = nativeCode,
            )
        }

        return ParsedIndex(repoName = blob.string(repoNameOffset, repoNameLength), apps = apps, versions = versions)
    }

    private fun align4(value: Int): Int = (value + 3) and 3.inv()

    /** A string reference is (offset, length) into the blob; offset -1 means absent. */
    private class Blob(private val bytes: ByteArray, private val start: Int, private val size: Int) {
        fun string(offset: Int, length: Int): String? {
            if (offset == ABSENT && length == 0) return null
            if (offset < 0 || length < 0) return null
            if (offset + length > size) return null
            return String(bytes, start + offset, length, Charsets.UTF_8)
        }

        /**
         * A list reference is (offset, total byte length) at u32 count then
         * count × (u32 length, bytes). Read through absolute offsets so the
         * bounds are the ones the reference itself declared.
         */
        fun stringList(offset: Int, totalLength: Int): List<String>? {
            if (offset == ABSENT) return emptyList()
            if (offset < 0 || totalLength < 4 || offset + totalLength > size) return null
            val reader = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val base = start + offset
            val end = base + totalLength
            val count = reader.getInt(base)
            if (count < 0 || count > totalLength) return null
            val items = ArrayList<String>(count)
            var cursor = base + 4
            repeat(count) {
                if (cursor + 4 > end) return null
                val length = reader.getInt(cursor)
                cursor += 4
                if (length < 0 || cursor + length > end) return null
                items += String(bytes, cursor, length, Charsets.UTF_8)
                cursor += length
            }
            return items
        }
    }

    /** Optional numbers are sent as -1 when the index does not declare them. */
    private fun ByteBuffer.longOrNull(): Long? = long.takeIf { it != -1L }

    private fun ByteBuffer.intOrNull(): Int? = int.takeIf { it != -1 }
}
