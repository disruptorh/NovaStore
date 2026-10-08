package com.novastore.app.core.downloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P11-T03: the download engine must never follow a cleartext URL. */
class DownloadUrlPolicyTest {

    @Test
    fun `https is allowed`() {
        assertTrue(isSecureDownloadUrl("https://f-droid.org/repo/app.apk"))
        assertTrue(isSecureDownloadUrl("HTTPS://EXAMPLE.COM/app.apk"))
    }

    @Test
    fun `cleartext http is refused`() {
        assertFalse(isSecureDownloadUrl("http://f-droid.org/repo/app.apk"))
    }

    @Test
    fun `non-http schemes and blanks are refused`() {
        assertFalse(isSecureDownloadUrl("file:///data/local/tmp/app.apk"))
        assertFalse(isSecureDownloadUrl("ftp://example.com/app.apk"))
        assertFalse(isSecureDownloadUrl(""))
        assertFalse(isSecureDownloadUrl("//example.com/app.apk"))
    }
}