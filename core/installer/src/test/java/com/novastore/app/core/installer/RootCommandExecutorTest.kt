package com.novastore.app.core.installer

import com.novastore.app.core.model.RootCommand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P11-T05: the root shell boundary. Illegal arguments never reach the shell,
 * and a local artifact path must resolve inside the app's private cache.
 */
class RootCommandExecutorTest {

    private val root = "/data/data/com.novastore.app/cache/downloads"

    @Test
    fun `shell metacharacters in arguments are rejected`() {
        assertFalse(RootCommand.validate("pm", listOf("install", "/x.apk; rm -rf /")))
        assertFalse(RootCommand.validate("pm", listOf("install", "&&", "sh")))
        assertFalse(RootCommand.validate("pm;reboot", listOf("install")))
    }

    @Test
    fun `a path outside the cache is refused`() {
        assertFalse(RootCommandExecutor.isPathInsideCache("/sdcard/evil.apk", root))
        assertFalse(RootCommandExecutor.isPathInsideCache("/data/data/com.novastore.app/cache/other/x.apk", root))
        assertFalse(RootCommandExecutor.isPathInsideCache("$root-evil/x.apk", root))
    }

    @Test
    fun `traversal that escapes the cache is refused`() {
        assertFalse(RootCommandExecutor.isPathInsideCache("$root/../../databases/x", root))
    }

    @Test
    fun `a path inside the cache is accepted`() {
        assertTrue(RootCommandExecutor.isPathInsideCache("$root/app.apk", root))
        assertTrue(RootCommandExecutor.isPathInsideCache("$root/sub/app.apk", root))
    }

    @Test
    fun `safe commands validate`() {
        assertTrue(RootCommand.validate("pm", listOf("install", "-r", "$root/app.apk")))
    }
}