package com.novastore.app.feature.updates

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.novastore.app.core.common.NotificationChannelIds
import com.novastore.app.core.datastore.SettingsDataStore
import com.novastore.app.core.ui.R as UiR
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome notifications for the "Update all" flow (P05-T10):
 *
 *  - CHANNEL_INSTALLATION: an app was successfully updated;
 *  - CHANNEL_ERRORS: a download/verify/install step failed.
 *
 * Both channels were previously registered but unused. Posted from the UI
 * layer because installs happen in-process; the error message and the app
 * name are the source of truth from the use case summary.
 */
@Singleton
class InstallOutcomeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
) {

    suspend fun notifyInstalled(packageName: String, appName: String) {
        if (!enabled()) return
        val notification = NotificationCompat.Builder(context, NotificationChannelIds.INSTALLATION)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(UiR.string.notification_install_success_title))
            .setContentText(context.getString(UiR.string.notification_install_success_text, appName))
            .setContentIntent(launcherIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        post(idFor(packageName), notification)
    }

    suspend fun notifyFailed(packageName: String, appName: String, message: String) {
        if (!enabled()) return
        val trimmed = message.take(MAX_MESSAGE_CHARS)
        val notification = NotificationCompat.Builder(context, NotificationChannelIds.ERRORS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(UiR.string.notification_update_error_title, appName))
            .setContentText(trimmed)
            .setContentIntent(launcherIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        post(idFor(packageName), notification)
    }

    private suspend fun enabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            settingsDataStore.snapshot().notificationsEnabled

    private fun post(id: Int, notification: Notification) {
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    /** Opens the app (feature modules must not reference the app: activity). */
    private fun launcherIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun idFor(packageName: String): Int = packageName.hashCode() and 0x7fffffff

    private companion object {
        const val MAX_MESSAGE_CHARS = 180
    }
}