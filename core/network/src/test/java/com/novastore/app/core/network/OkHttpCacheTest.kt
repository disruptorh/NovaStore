package com.novastore.app.core.network

import java.nio.file.Files
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The OkHttp client used by repository/CDN/Play-web requests carries a disk
 * cache (NetworkModule). F-Droid indexes are ETag/Last-Modified driven; for
 * the rest of the HTTP surface, Cache-Control expiry must make repeat reads
 * serve from disk instead of the network.
 */
class OkHttpCacheTest {

    @Test
    fun repeatGetIsServedFromDiskCache() {
        val cacheDir = Files.createTempDirectory("okhttp-cache").toFile()
        val cache = Cache(cacheDir, 50L * 1024 * 1024)
        val client = OkHttpClient.Builder().cache(cache).build()
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Cache-Control", "max-age=60")
                .setHeader("ETag", "\"v1\"")
                .setBody("payload"),
        )
        server.start()
        try {
            repeat(2) {
                client.newCall(Request.Builder().url(server.url("/index-v2.json")).get().build())
                    .execute()
                    .use { response -> assertEquals("payload", response.body?.string()) }
            }
            assertEquals("second read must not hit the network", 1, server.requestCount)
            assertTrue("content stored on disk", cache.directory.listFiles()?.isNotEmpty() == true)
        } finally {
            server.shutdown()
            cache.delete()
        }
    }
}