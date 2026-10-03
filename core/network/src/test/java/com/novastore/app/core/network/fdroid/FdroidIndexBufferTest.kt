package com.novastore.app.core.network.fdroid

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The buffer under test is produced by `fdroid_flat_buffer.cpp`, which cannot
 * run in a JVM unit test, so [BufferBuilder] writes the same layout by hand.
 */
class FdroidIndexBufferTest {

    @Test
    fun `decodes apps and versions`() {
        val buffer = BufferBuilder()
            .repoName("F-Droid")
            .app(
                packageName = "com.example.one",
                name = "One",
                summary = "First",
                categories = listOf("System", "Tools"),
                added = 1_500_000_000L,
            )
            .app(packageName = "com.example.two", name = "Two")
            .version(
                packageName = "com.example.one",
                versionCode = 42,
                downloadUrl = "https://x/repo/one_42.apk",
                sha256 = "aabbcc",
                size = 4096,
                minSdk = 21,
                targetSdk = 34,
                nativeCode = listOf("x86_64"),
            )
            .build()

        val parsed = FdroidIndexBuffer.decode(buffer)!!

        assertEquals("F-Droid", parsed.repoName)
        assertEquals(2, parsed.apps.size)
        assertEquals("com.example.one", parsed.apps[0].packageName)
        assertEquals("One", parsed.apps[0].name)
        assertEquals("First", parsed.apps[0].summary)
        assertEquals(listOf("System", "Tools"), parsed.apps[0].categories)
        assertEquals(1_500_000_000L, parsed.apps[0].added)
        assertNull(parsed.apps[1].summary)
        assertNull(parsed.apps[1].added)

        assertEquals(1, parsed.versions.size)
        val version = parsed.versions[0]
        assertEquals("com.example.one", version.packageName)
        assertEquals(42L, version.versionCode)
        assertEquals("https://x/repo/one_42.apk", version.downloadUrl)
        assertEquals("aabbcc", version.sha256)
        assertEquals(4096L, version.size)
        assertEquals(21, version.minSdk)
        assertEquals(34, version.targetSdk)
        assertEquals(listOf("x86_64"), version.nativeCode)
        assertNull(version.added)
    }

    @Test
    fun `rejects a foreign magic`() {
        val buffer = BufferBuilder().app("com.example.one", "One").build()
        buffer[0] = 0
        assertNull(FdroidIndexBuffer.decode(buffer))
    }

    @Test
    fun `rejects a truncated buffer`() {
        val buffer = BufferBuilder().app("com.example.one", "One").build()
        assertNull(FdroidIndexBuffer.decode(buffer.copyOf(buffer.size - 1)))
    }

    @Test
    fun `rejects an empty buffer`() {
        assertNull(FdroidIndexBuffer.decode(ByteArray(0)))
        assertNull(FdroidIndexBuffer.decode(ByteArray(8)))
    }

    @Test
    fun `rejects a string that reaches past the blob`() {
        val builder = BufferBuilder().app("com.example.one", "One")
        val buffer = builder.build()
        // The first app record starts right after the blob; its package name
        // offset lives there, so a huge one must not be followed.
        val appsOffset = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).getInt(24)
        ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).putInt(appsOffset, 1 shl 20)
        assertNull(FdroidIndexBuffer.decode(buffer))
    }

    private class BufferBuilder {
        private val blob = ByteArrayOutputStream()
        private val appRecords = ByteArrayOutputStream()
        private val versionRecords = ByteArrayOutputStream()
        private var appCount = 0
        private var versionCount = 0
        private var repoName = intArrayOf(-1, 0)

        fun repoName(value: String): BufferBuilder = apply { repoName = intern(value) }

        fun app(
            packageName: String,
            name: String,
            summary: String? = null,
            categories: List<String> = emptyList(),
            added: Long? = null,
        ): BufferBuilder = apply {
            appCount++
            putRef(appRecords, intern(packageName))
            putRef(appRecords, intern(name))
            putRef(appRecords, intern(summary))
            repeat(4) { putRef(appRecords, intArrayOf(-1, 0)) }
            putRef(appRecords, internList(categories))
            repeat(3) { putRef(appRecords, intArrayOf(-1, 0)) }
            putLong(appRecords, added)
            putLong(appRecords, null)
        }

        fun version(
            packageName: String,
            versionCode: Long,
            downloadUrl: String,
            sha256: String? = null,
            size: Long? = null,
            minSdk: Int? = null,
            targetSdk: Int? = null,
            nativeCode: List<String> = emptyList(),
        ): BufferBuilder = apply {
            versionCount++
            putRef(versionRecords, intern(packageName))
            putLong(versionRecords, versionCode)
            putRef(versionRecords, intArrayOf(-1, 0))
            putRef(versionRecords, intern(downloadUrl))
            putRef(versionRecords, intern(sha256))
            putLong(versionRecords, size)
            putInt(versionRecords, minSdk)
            putInt(versionRecords, targetSdk)
            putLong(versionRecords, null)
            putRef(versionRecords, intArrayOf(-1, 0))
            putRef(versionRecords, internList(nativeCode))
        }

        fun build(): ByteArray {
            val blobBytes = blob.toByteArray()
            val appsOffset = align4(HEADER_BYTES + blobBytes.size)
            val versionsOffset = appsOffset + appCount * APP_RECORD_BYTES
            val out = ByteArray(versionsOffset + versionCount * VERSION_RECORD_BYTES)
            val header = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
            header.putInt(MAGIC)
            header.putInt(FORMAT_VERSION)
            header.putInt(blobBytes.size)
            header.putInt(appCount)
            header.putInt(versionCount)
            header.putInt(HEADER_BYTES)
            header.putInt(appsOffset)
            header.putInt(versionsOffset)
            header.putInt(repoName[0])
            header.putInt(repoName[1])
            blobBytes.copyInto(out, HEADER_BYTES)
            appRecords.toByteArray().copyInto(out, appsOffset)
            versionRecords.toByteArray().copyInto(out, versionsOffset)
            return out
        }

        /** Returns the (offset, length) pair the native serializer would write. */
        private fun intern(value: String?): IntArray {
            if (value == null) return intArrayOf(-1, 0)
            val bytes = value.toByteArray(Charsets.UTF_8)
            val ref = intArrayOf(blob.size(), bytes.size)
            blob.write(bytes)
            return ref
        }

        private fun internList(items: List<String>): IntArray {
            if (items.isEmpty()) return intArrayOf(-1, 0)
            val ref = intArrayOf(blob.size(), 0)
            putU32(blob, items.size)
            for (item in items) {
                val bytes = item.toByteArray(Charsets.UTF_8)
                putU32(blob, bytes.size)
                blob.write(bytes)
            }
            ref[1] = blob.size() - ref[0]
            return ref
        }

        private fun putRef(out: ByteArrayOutputStream, ref: IntArray) {
            putInt(out, ref[0])
            putInt(out, ref[1])
        }

        private fun putLong(out: ByteArrayOutputStream, value: Long?) {
            val buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putLong(value ?: -1L)
            out.write(buffer.array())
        }

        private fun putInt(out: ByteArrayOutputStream, value: Int?) {
            val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(value ?: -1)
            out.write(buffer.array())
        }

        private fun putU32(out: ByteArrayOutputStream, value: Int) {
            val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(value)
            out.write(buffer.array())
        }

        private fun align4(value: Int): Int = (value + 3) and 3.inv()
    }

    private companion object {
        const val MAGIC = 0x3149534E
        const val FORMAT_VERSION = 1
        const val HEADER_BYTES = 40
        const val APP_RECORD_BYTES = 104
        const val VERSION_RECORD_BYTES = 80
    }
}
