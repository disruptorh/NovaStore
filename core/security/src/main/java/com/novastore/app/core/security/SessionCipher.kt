package com.novastore.app.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.novastore.app.core.common.DispatcherProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Encrypts small credential blobs (Play auth / anonymous sessions) at rest
 * with an AES-256-GCM key held by the hardware-backed Android Keystore.
 *
 * The serialized document is prefixed with [PREFIX] so a value written
 * before this cipher existed (plaintext) is still readable — [decrypt]
 * returns it unchanged, and [migrate] re-encrypts it during the one-pass
 * migration on app start.
 * A value that carries the prefix but fails authentication (tampered or
 * key lost) returns null, and callers drop the session rather than use it.
 */
@Singleton
open class SessionCipher @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
) {

    suspend fun encrypt(plaintext: String): String? = withContext(dispatcherProvider.io) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val iv = cipher.iv
            val body = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

            val payload = ByteArray(1 + iv.size + body.size)
            payload[0] = VERSION
            System.arraycopy(iv, 0, payload, 1, iv.size)
            System.arraycopy(body, 0, payload, 1 + iv.size, body.size)

            PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
        }.getOrNull()
    }

    suspend fun decrypt(stored: String): String? = withContext(dispatcherProvider.io) {
        // Legacy plaintext value — accepted as-is, re-encrypted on next write.
        if (!stored.startsWith(PREFIX)) return@withContext stored

        runCatching {
            val payload = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            if (payload.size <= 1 + GCM_IV_LENGTH || payload[0] != VERSION) {
                return@runCatching null
            }
            val iv = payload.copyOfRange(1, 1 + GCM_IV_LENGTH)
            val body = payload.copyOfRange(1 + GCM_IV_LENGTH, payload.size)

            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            }
            String(cipher.doFinal(body), Charsets.UTF_8)
        }.getOrNull()
    }
    /**
     * P11-T04: re-encrypts a legacy plaintext value. Returns the ciphertext
     * to persist, or null when the value is already encrypted or absent —
     * callers write back only on a non-null result, so an already-encrypted
     * blob is never double-wrapped.
     */
    suspend fun migrate(stored: String?): String? {
        if (stored == null || stored.startsWith(PREFIX)) return null
        return encrypt(stored)
    }

    /** Overridable so the pipeline is unit-testable without the Android Keystore. */
    protected open fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "nova_session_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val VERSION: Byte = 1
        const val PREFIX = "enc:v1:"
    }
}
