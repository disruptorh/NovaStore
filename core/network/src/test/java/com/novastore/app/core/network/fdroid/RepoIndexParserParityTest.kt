package com.novastore.app.core.network.fdroid

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Differential test for the two index parsers.
 *
 * The native parser cannot run in a JVM unit test, so [FdroidIndexBufferTest]
 * covers the wire format and this test pins the Gson parser to the text the
 * native parser produced for the same fixture. Any behaviour change on either
 * side shows up here as a diff.
 *
 * The expected files are regenerated with the host build:
 *
 * ```
 * cmake -S core/network/src/main/cpp -B build/native-test
 * cmake --build build/native-test
 * ./build/native-test/fdroid_index_test \
 *   core/network/src/test/resources/fdroid-parity/index-v2.json \
 *   https://repo.example.org/repo v2 de-DE,en-US
 * ```
 */
class RepoIndexParserParityTest {

    @Test
    fun `index v2 matches the native parser`() {
        assertMatches("index-v2", v2 = true)
    }

    @Test
    fun `index v1 matches the native parser`() {
        assertMatches("index-v1", v2 = false)
    }

    private fun assertMatches(name: String, v2: Boolean) {
        val json = resource("$name.json")
        val expected = resource("$name.expected.txt")
        val parser = RepoIndexParser(BASE_URL, listOf("de-DE", "en-US"))
        val index = if (v2) {
            parser.parseV2(ByteArrayInputStream(json.toByteArray()))
        } else {
            parser.parseV1(ByteArrayInputStream(json.toByteArray()))
        }
        assertEquals(expected, dump(index))
    }

    private fun resource(path: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream("fdroid-parity/$path")) {
            "missing test resource fdroid-parity/$path"
        }.bufferedReader().use { it.readText() }

    /** Same shape the native test binary prints: tab separated, `-` when absent. */
    private fun dump(index: ParsedIndex): String = buildString {
        append("repo\t").append(index.repoName ?: "-").append('\n')
        for (app in index.apps) {
            append("app")
            append('\t').append(app.packageName)
            append('\t').append(app.name)
            append('\t').append(app.summary ?: "-")
            append('\t').append(app.description ?: "-")
            append('\t').append(app.developer ?: "-")
            append('\t').append(app.iconUrl ?: "-")
            append('\t').append(app.license ?: "-")
            append('\t').append(app.categories.list())
            append('\t').append(app.website ?: "-")
            append('\t').append(app.sourceCode ?: "-")
            append('\t').append(app.changelog ?: "-")
            append('\t').append(app.added?.toString() ?: "-")
            append('\t').append(app.lastUpdated?.toString() ?: "-")
            append('\n')
        }
        for (version in index.versions) {
            append("version")
            append('\t').append(version.packageName)
            append('\t').append(version.versionCode.toString())
            append('\t').append(version.versionName ?: "-")
            append('\t').append(version.downloadUrl)
            append('\t').append(version.sha256 ?: "-")
            append('\t').append(version.size?.toString() ?: "-")
            append('\t').append(version.minSdk?.toString() ?: "-")
            append('\t').append(version.targetSdk?.toString() ?: "-")
            append('\t').append(version.added?.toString() ?: "-")
            append('\t').append(version.signer ?: "-")
            append('\t').append(version.nativeCode.list())
            append('\n')
        }
    }

    private fun List<String>.list(): String = if (isEmpty()) "-" else joinToString(",")

    private companion object {
        const val BASE_URL = "https://repo.example.org/repo"
    }
}
