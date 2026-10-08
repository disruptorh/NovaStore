package com.novastore.app.data.repository

import com.novastore.app.core.datastore.SettingsDataStore
import com.novastore.app.core.security.SessionCipher
import javax.inject.Inject
import javax.inject.Singleton

/**
 * P11-T04: one-pass migration of credential blobs written before
 * [SessionCipher] existed. Any legacy plaintext Play session (Google account
 * or anonymous) is re-encrypted and written back once, on app start.
 *
 * Best-effort and fail-closed: an encrypting failure leaves the original
 * value untouched (it is still readable in the clear) rather than dropping
 * the session; already-encrypted values are skipped.
 */
@Singleton
class SessionMigrations @Inject constructor(
    private val sessionCipher: SessionCipher,
    private val settingsDataStore: SettingsDataStore,
) {
    suspend fun run() {
        settingsDataStore.playAuthSessionSnapshot()?.let { stored ->
            sessionCipher.migrate(stored)?.let { encrypted ->
                runCatching { settingsDataStore.setPlayAuthSession(encrypted) }
            }
        }
        settingsDataStore.anonPlaySessionSnapshot()?.let { stored ->
            sessionCipher.migrate(stored)?.let { encrypted ->
                runCatching { settingsDataStore.setAnonPlaySession(encrypted) }
            }
        }
    }
}
