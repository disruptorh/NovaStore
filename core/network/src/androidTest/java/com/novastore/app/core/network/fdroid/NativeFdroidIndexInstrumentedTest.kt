package com.novastore.app.core.network.fdroid

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end proof that the native path works on a real Android runtime: the
 * shared library loads, [MappedFile] maps the download, the C++ parser produces
 * a buffer that [FdroidIndexBuffer] decodes, and the result is identical to the
 * Kotlin reference parser. Every untrusted input must return null instead of
 * throwing so the caller can fall back to Gson.
 */
@RunWith(AndroidJUnit4::class)
class NativeFdroidIndexInstrumentedTest {

    private val locales = listOf("de-DE", "en-US")

    @Test
    fun sharedLibraryLoads() {
        assertTrue(NativeFdroidIndex.isAvailable)
    }

    @Test
    fun v2IndexMatchesTheKotlinParser() {
        assertSameAsReference(INDEX_V2, v2 = true)
    }

    @Test
    fun v1IndexMatchesTheKotlinParser() {
        assertSameAsReference(INDEX_V1, v2 = false)
    }

    @Test
    fun untrustedInputReturnsNullInsteadOfThrowing() {
        assertNull(parse(""))
        assertNull(parse("   \n\t "))
        assertNull(parse("not json at all"))
        assertNull(parse("{\"packages\":{\"org.example\":{\"name\":\"x\""))
        assertNull(parse("[]"))
        assertNull(parse("[1,2,3]"))
        assertNull(parse("{\"packages\":42}"))
        assertNull(NativeFdroidIndex.parse(missingFile(), BASE_URL, true, locales))
    }

    @Test
    fun negativeOptionalNumberIsLeftToTheGsonReference() {
        // The flat buffer marks an absent optional number with -1, so the
        // native parser declines the index instead of losing the value, and
        // the reference parser keeps it.
        val file = writeIndex(
            """
            {
              "packages": {
                "org.example.app": {
                  "versions": {
                    "k": {
                      "file": { "name": "app.apk", "size": -1 },
                      "manifest": { "versionCode": 7 }
                    }
                  }
                }
              }
            }
            """.trimIndent(),
        )

        assertNull(NativeFdroidIndex.parse(file, BASE_URL, true, locales))

        val reference = RepoIndexParser(BASE_URL, locales).parseV2(file.inputStream())
        assertEquals(-1L, reference.versions.single().size)
    }

    private fun assertSameAsReference(json: String, v2: Boolean) {
        val file = writeIndex(json)
        val native = NativeFdroidIndex.parse(file, BASE_URL, v2, locales)
        val reference = RepoIndexParser(BASE_URL, locales).let { parser ->
            if (v2) parser.parseV2(file.inputStream()) else parser.parseV1(file.inputStream())
        }

        assertEquals(reference, native)
    }

    private fun parse(json: String): ParsedIndex? =
        NativeFdroidIndex.parse(writeIndex(json), BASE_URL, true, locales)

    private fun writeIndex(json: String): File {
        val file = File.createTempFile("fdroid-index", ".json")
        file.writeText(json)
        file.deleteOnExit()
        return file
    }

    private fun missingFile(): File = File(File("/data/local/tmp"), "novastore-absent-index.json")

    private companion object {
        const val BASE_URL = "https://f-droid.org/repo"

        const val INDEX_V2 = """
            {
              "repo": {
                "name": "F-Droid",
                "name[de-DE]": "F-Droid DE",
                "address": "https://f-droid.org/repo"
              },
              "packages": {
                "org.fdroid.fdroid": {
                  "metadata": {
                    "name": "F-Droid",
                    "name[de-DE]": "F-Droid (DE)",
                    "summary": "Software catalogue",
                    "description": "An offline software catalogue for Android.",
                    "authorName": "F-Droid",
                    "license": "GPL-3.0-only",
                    "categories": ["System", "Tools"],
                    "suggestedVersionName": "1.0.4",
                    "suggestedVersionCode": 104,
                    "added": 1500000000,
                    "lastUpdated": 1700000000
                  },
                  "versions": {
                    "aa11bb22cc33dd44ee55ff6600112233445566778899aabbccddeeff0011223344": {
                      "versionCode": 104,
                      "versionName": "1.0.4",
                      "apkName": "F-Droid_104.apk",
                      "size": 1234567,
                      "sha256": "aa11bb22cc33dd44ee55ff6600112233445566778899aabbccddeeff0011223344",
                      "added": 1500000000,
                      "minSdk": 26,
                      "targetSdk": 35,
                      "nativeCode": ["arm64-v8a", "armeabi-v7a"],
                      "signer": "e1c3a9f2"
                    },
                    "bb22cc33dd44ee55ff6600112233445566778899aabbccddeeff0011223344aa11": {
                      "versionCode": 103,
                      "versionName": "1.0.3",
                      "apkName": "F-Droid_103.apk",
                      "size": 1234000,
                      "sha256": "bb22cc33dd44ee55ff6600112233445566778899aabbccddeeff0011223344aa11",
                      "minSdk": 21,
                      "nativeCode": ["x86_64"],
                      "signer": "e1c3a9f2"
                    }
                  }
                }
              }
            }
        """

        const val INDEX_V1 = """
            {
              "repo": { "name": "F-Droid" },
              "apps": [
                {
                  "packageName": "org.fdroid.fdroid",
                  "name": "F-Droid",
                  "license": "GPL-3.0-only",
                  "categories": ["System"],
                  "description": "An offline software catalogue for Android."
                }
              ],
              "packages": {
                "org.fdroid.fdroid": [
                  {
                    "versionName": "1.0.4",
                    "versionCode": 104,
                    "apkName": "F-Droid_104.apk",
                    "size": 1234567,
                    "hashType": "sha256",
                    "hash": "aa11bb22cc33dd44ee55ff6600112233445566778899aabbccddeeff0011223344",
                    "minSdkVersion": 26,
                    "maxSdkVersion": 35,
                    "icon": "favicon-640.png"
                  }
                ]
              }
            }
        """
    }
}