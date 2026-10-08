package com.novastore.app.core.security

import com.novastore.app.core.common.DispatcherProvider
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * P11-T04: the one-pass migration must turn a legacy plaintext session into
 * ciphertext (and leave already-encrypted values alone). The Android Keystore
 * is not available under Robolectric, so the key is supplied in memory.
 */
@RunWith(RobolectricTestRunner::class)
class SessionCipherTest {

    private val plaintext = "{\"email\":\"a@b.c\",\"aasToken\":\"secret\"}"

    private fun cipher() = InMemoryKeySessionCipher(DirectDispatchers)

    @Test
    fun `migrate re-encrypts a plaintext value`() = runTest {
        val cipher = cipher()
        val stored = cipher.migrate(plaintext)
        assertNotNull(stored)
        assertTrue(stored!!.startsWith("enc:v1:"))
        assertTrue(stored != plaintext)
        assertEquals(plaintext, cipher.decrypt(stored))
    }

    @Test
    fun `migrate leaves already-encrypted and absent values untouched`() = runTest {
        val cipher = cipher()
        val encrypted = cipher.encrypt(plaintext)
        assertNotNull(encrypted)
        assertNull(cipher.migrate(encrypted))
        assertNull(cipher.migrate(null))
    }

    @Test
    fun `decrypt still reads a legacy plaintext value`() = runTest {
        assertEquals(plaintext, cipher().decrypt(plaintext))
    }

    private class InMemoryKeySessionCipher(dispatchers: DispatcherProvider) : SessionCipher(dispatchers) {
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun secretKey(): SecretKey = key
    }

    private object DirectDispatchers : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.Unconfined
        override val default: CoroutineDispatcher = Dispatchers.Unconfined
        override val main: CoroutineDispatcher = Dispatchers.Unconfined
    }
}