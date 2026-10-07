package com.novastore.app.core.common

/**
 * Notification channel ids, shared so that any module (downloader, installer,
 * app) post notifications without depending on [android.app.Application]
 * classes and so the channels registered at startup are exactly the ones used
 * at runtime.
 */
object NotificationChannelIds {
    const val UPDATES = "updates"
    const val DOWNLOADS = "downloads"
    const val INSTALLATION = "installation"
    const val ERRORS = "errors"
    const val SCAN = "scan_progress"
}