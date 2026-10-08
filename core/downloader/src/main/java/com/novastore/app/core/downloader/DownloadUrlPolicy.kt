package com.novastore.app.core.downloader

/**
 * P11-T03: artifacts are only ever fetched over HTTPS. Cleartext (`http`),
 * `file://` and any other scheme is refused both when a task is enqueued and
 * again before the network call, so a resumed task or a direct database row
 * can never smuggle a cleartext request past the engine.
 */
internal fun isSecureDownloadUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true)

/** User-visible reason returned when an insecure URL is refused. */
internal const val INSECURE_URL_MESSAGE =
    "Refused: downloads must use HTTPS."
