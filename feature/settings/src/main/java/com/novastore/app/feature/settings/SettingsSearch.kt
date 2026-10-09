package com.novastore.app.feature.settings

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.novastore.app.core.ui.R as UiR

/** The eight Settings sections, in the prescribed display order (P09). */
enum class SettingsSectionKey {
    SOURCES,
    UPDATES,
    DOWNLOADS_AND_INSTALL,
    APPEARANCE,
    STORAGE,
    PRIVACY,
    BACKUP,
    ABOUT,
}

/**
 * One searchable settings row. [keywords] lets a row match words that do not
 * appear in its visible title or description (e.g. "wifi" for "Wi-Fi only").
 */
data class SettingsItem(
    val id: String,
    val title: String,
    val description: String? = null,
    val keywords: List<String> = emptyList(),
    val section: SettingsSectionKey,
)

/** Case-insensitive substring match on title, description and keywords. */
fun settingsItemMatches(item: SettingsItem, query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    if (item.title.lowercase().contains(q)) return true
    if (item.description?.lowercase()?.contains(q) == true) return true
    return item.keywords.any { it.lowercase().contains(q) }
}

/**
 * Source of truth for every row the Settings search can find. Titles and
 * descriptions come from resources so the match mirrors what the user sees.
 */
@Composable
fun settingsItems(): List<SettingsItem> = listOf(
    // Sources
    SettingsItem(
        "add_repository",
        stringResource(UiR.string.settings_add_repository),
        stringResource(UiR.string.settings_add_repository_desc),
        listOf("add", "new", "github", "gitlab", "gitea", "codeberg", "html", "f-droid", "source"),
        SettingsSectionKey.SOURCES,
    ),
    SettingsItem(
        "anonymous_play",
        stringResource(UiR.string.sources_play_anonymous),
        stringResource(UiR.string.sources_play_anonymous_desc),
        listOf("google play", "anonymous", "account"),
        SettingsSectionKey.SOURCES,
    ),
    SettingsItem(
        "web_catalog",
        stringResource(UiR.string.sources_web_catalog),
        stringResource(UiR.string.sources_web_catalog_desc),
        listOf("web", "catalog"),
        SettingsSectionKey.SOURCES,
    ),
    SettingsItem(
        "github",
        stringResource(UiR.string.sources_github),
        stringResource(UiR.string.sources_github_desc),
        listOf("github", "releases"),
        SettingsSectionKey.SOURCES,
    ),
    SettingsItem(
        "gitlab",
        stringResource(UiR.string.sources_gitlab),
        stringResource(UiR.string.sources_gitlab_desc),
        listOf("gitlab", "releases"),
        SettingsSectionKey.SOURCES,
    ),
    SettingsItem(
        "fdroid_repos",
        stringResource(UiR.string.sources_fdroid_repos),
        stringResource(UiR.string.sources_fdroid_repos_desc),
        listOf("repositories", "f-droid", "repo", "add"),
        SettingsSectionKey.SOURCES,
    ),
    SettingsItem(
        "open_links",
        stringResource(UiR.string.settings_open_links),
        stringResource(UiR.string.settings_open_links_desc),
        listOf("links", "intent", "market", "default"),
        SettingsSectionKey.SOURCES,
    ),
    // Updates
    SettingsItem(
        "schedule",
        stringResource(UiR.string.settings_check_updates),
        null,
        listOf("schedule", "daily", "weekly", "immediately", "cadence"),
        SettingsSectionKey.UPDATES,
    ),
    SettingsItem(
        "auto_updates",
        stringResource(UiR.string.settings_auto_updates),
        stringResource(UiR.string.settings_auto_updates_desc),
        listOf("automatic", "background"),
        SettingsSectionKey.UPDATES,
    ),
    SettingsItem(
        "wifi_only",
        stringResource(UiR.string.settings_wifi_only),
        stringResource(UiR.string.settings_wifi_only_desc),
        listOf("wifi", "wi-fi", "network", "metered"),
        SettingsSectionKey.UPDATES,
    ),
    SettingsItem(
        "charging_only",
        stringResource(UiR.string.settings_charging_only),
        stringResource(UiR.string.settings_charging_only_desc),
        listOf("charging", "battery"),
        SettingsSectionKey.UPDATES,
    ),
    // Downloads & installation
    SettingsItem(
        "installation_mode",
        stringResource(UiR.string.settings_installation),
        null,
        listOf("install", "mode", "root", "managed", "session"),
        SettingsSectionKey.DOWNLOADS_AND_INSTALL,
    ),
    SettingsItem(
        "root_status",
        stringResource(UiR.string.settings_root_installation),
        null,
        listOf("root", "su", "superuser", "permission"),
        SettingsSectionKey.DOWNLOADS_AND_INSTALL,
    ),
    SettingsItem(
        "confirm_install",
        stringResource(UiR.string.settings_confirm),
        stringResource(UiR.string.settings_confirm_desc),
        listOf("confirm", "prompt", "installer"),
        SettingsSectionKey.DOWNLOADS_AND_INSTALL,
    ),
    SettingsItem(
        "mobile_data",
        stringResource(UiR.string.settings_mobile_data),
        stringResource(UiR.string.settings_mobile_data_desc),
        listOf("mobile", "cellular", "data"),
        SettingsSectionKey.DOWNLOADS_AND_INSTALL,
    ),
    SettingsItem(
        "download_charging",
        stringResource(UiR.string.settings_download_charging),
        stringResource(UiR.string.settings_download_charging_desc),
        listOf("charging", "battery"),
        SettingsSectionKey.DOWNLOADS_AND_INSTALL,
    ),
    SettingsItem(
        "simultaneous",
        stringResource(UiR.string.settings_simultaneous, 2),
        null,
        listOf("concurrent", "parallel", "simultaneous"),
        SettingsSectionKey.DOWNLOADS_AND_INSTALL,
    ),
    // Appearance
    SettingsItem(
        "search_pinned",
        stringResource(UiR.string.settings_search_pinned),
        stringResource(UiR.string.settings_search_pinned_desc),
        listOf("home", "bar", "pin"),
        SettingsSectionKey.APPEARANCE,
    ),
    SettingsItem(
        "theme",
        stringResource(UiR.string.settings_theme),
        null,
        listOf("dark", "light", "amoled", "mode"),
        SettingsSectionKey.APPEARANCE,
    ),
    SettingsItem(
        "accent",
        stringResource(UiR.string.settings_accent),
        null,
        listOf("color", "palette", "tint"),
        SettingsSectionKey.APPEARANCE,
    ),
    SettingsItem(
        "language",
        stringResource(UiR.string.settings_language),
        null,
        listOf("language", "locale", "idioma"),
        SettingsSectionKey.APPEARANCE,
    ),
    // Storage & cache
    SettingsItem(
        "autoclean",
        stringResource(UiR.string.settings_autoclean),
        stringResource(UiR.string.settings_autoclean_desc),
        listOf("auto", "clean", "days", "delete"),
        SettingsSectionKey.STORAGE,
    ),
    SettingsItem(
        "clear_cache",
        stringResource(UiR.string.settings_clear_cache),
        stringResource(UiR.string.settings_clear_cache_desc),
        listOf("cache", "temporary", "tmp"),
        SettingsSectionKey.STORAGE,
    ),
    SettingsItem(
        "clear_downloads",
        stringResource(UiR.string.settings_clear_downloads),
        stringResource(UiR.string.settings_clear_downloads_desc),
        listOf("apk", "remove", "delete", "files"),
        SettingsSectionKey.STORAGE,
    ),
    // Privacy
    SettingsItem(
        "play_account",
        stringResource(UiR.string.settings_play_account),
        null,
        listOf("account", "sign in", "login", "google", "anonymous"),
        SettingsSectionKey.PRIVACY,
    ),
    SettingsItem(
        "play_updates",
        stringResource(UiR.string.settings_play_updates),
        stringResource(UiR.string.settings_play_updates_desc),
        listOf("google play", "automatic", "updates"),
        SettingsSectionKey.PRIVACY,
    ),
    SettingsItem(
        "device_profile",
        stringResource(UiR.string.settings_device_profile),
        stringResource(UiR.string.settings_device_profile_hint),
        listOf("device", "spoof", "model", "identity"),
        SettingsSectionKey.PRIVACY,
    ),
    SettingsItem(
        "session_provider",
        stringResource(UiR.string.sources_session_provider),
        null,
        listOf("session", "token", "dispenser", "provider"),
        SettingsSectionKey.PRIVACY,
    ),
    SettingsItem(
        "query_all_packages",
        stringResource(UiR.string.settings_query_all_packages),
        stringResource(UiR.string.settings_query_all_packages_desc),
        listOf("permission", "installed", "scan", "list"),
        SettingsSectionKey.PRIVACY,
    ),
    SettingsItem(
        "notifications",
        stringResource(UiR.string.settings_notifications),
        stringResource(UiR.string.settings_notifications_desc),
        listOf("alert", "update", "result"),
        SettingsSectionKey.PRIVACY,
    ),
    SettingsItem(
        "sign_out",
        stringResource(UiR.string.account_sign_out),
        stringResource(UiR.string.settings_sign_out_desc),
        listOf("logout", "session", "account"),
        SettingsSectionKey.PRIVACY,
    ),
    // Backup
    SettingsItem(
        "export_sources",
        stringResource(UiR.string.settings_repos_export),
        stringResource(UiR.string.settings_export_desc),
        listOf("backup", "json", "save"),
        SettingsSectionKey.BACKUP,
    ),
    SettingsItem(
        "import_sources",
        stringResource(UiR.string.settings_repos_import),
        stringResource(UiR.string.settings_import_desc),
        listOf("restore", "json", "load"),
        SettingsSectionKey.BACKUP,
    ),
    // About
    SettingsItem(
        "about_version",
        stringResource(UiR.string.settings_about_version_label),
        null,
        listOf("version", "build"),
        SettingsSectionKey.ABOUT,
    ),
    SettingsItem(
        "about_sources",
        stringResource(UiR.string.settings_about_sources),
        null,
        listOf("google play", "f-droid", "github", "gitlab"),
        SettingsSectionKey.ABOUT,
    ),
    SettingsItem(
        "about_device",
        stringResource(UiR.string.settings_about_device),
        null,
        listOf("device", "model", "android"),
        SettingsSectionKey.ABOUT,
    ),
    SettingsItem(
        "about_privacy",
        stringResource(UiR.string.settings_about_privacy),
        stringResource(UiR.string.settings_privacy_note),
        listOf("telemetry", "data"),
        SettingsSectionKey.ABOUT,
    ),
    SettingsItem(
        "about_license",
        stringResource(UiR.string.settings_about_license),
        null,
        listOf("license", "gpl", "gnu"),
        SettingsSectionKey.ABOUT,
    ),
    SettingsItem(
        "about_disclaimer",
        stringResource(UiR.string.settings_about_disclaimer),
        stringResource(UiR.string.settings_about_disclaimer_value),
        listOf("google", "f-droid", "affiliation", "trademark"),
        SettingsSectionKey.ABOUT,
    ),
    SettingsItem(
        "factory_defaults",
        stringResource(UiR.string.settings_factory_defaults),
        null,
        listOf("default", "factory", "values"),
        SettingsSectionKey.ABOUT,
    ),
    SettingsItem(
        "reset",
        stringResource(UiR.string.settings_reset_title),
        stringResource(UiR.string.settings_reset_desc),
        listOf("factory", "default", "reset", "clear"),
        SettingsSectionKey.ABOUT,
    ),
)

/** Pinned search field that filters every section (P09-T02). */
@Composable
fun SettingsSearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(stringResource(UiR.string.settings_search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(UiR.string.settings_search_clear),
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
    )
}