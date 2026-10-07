package com.novastore.app.core.model

/**
 * The single "is this a real update?" rule shared by the update scan and
 * the UI. A real update is a strictly higher versionCode AND not the same
 * version name (a different code for the same name is another device
 * variant of the same release — the "11.0.3 → 11.0.3" false update).
 * Missing metadata is never treated as "newer".
 */
object UpdateRules {

    fun isRealUpdate(installed: InstalledApp, available: AppVersion): Boolean {
        val availableName = normalizeVersionName(available.versionName)
        val installedName = normalizeVersionName(installed.versionName)
        if (available.versionCode <= installed.versionCode) return false
        if (availableName != null && installedName != null &&
            VersionComparator.compareVersionNames(installedName, availableName) == 0
        ) {
            return false
        }
        return true
    }

    /** `"v4.0.2 "` and `"4.0.2"` are the same release. */
    fun normalizeVersionName(name: String?): String? =
        name?.trim()?.removePrefix("v")?.removePrefix("V")?.lowercase()?.takeIf { it.isNotEmpty() }
}