package com.novastore.app.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private fun item(
        id: String,
        title: String,
        description: String? = null,
        keywords: List<String> = emptyList(),
        section: SettingsSectionKey = SettingsSectionKey.STORAGE,
    ) = SettingsItem(id = id, title = title, description = description, keywords = keywords, section = section)

    @Test
    fun `empty or blank query matches everything`() {
        val item = item("x", "Anything", keywords = listOf("zzz"))
        assertTrue(settingsItemMatches(item, ""))
        assertTrue(settingsItemMatches(item, "   "))
    }

    @Test
    fun `substring match is case insensitive on title and description`() {
        val item = item("x", "Clear cache", "Removes temporary files", section = SettingsSectionKey.STORAGE)
        assertTrue(settingsItemMatches(item, "CACHE"))
        assertTrue(settingsItemMatches(item, "cache"))
        assertTrue(settingsItemMatches(item, "temporary"))
        assertFalse(settingsItemMatches(item, "downloads"))
    }

    @Test
    fun `wifi keyword matches the wifi only row`() {
        val wifi = item("wifi_only", "Wi-Fi only", keywords = listOf("wifi", "wi-fi", "network", "metered"))
        assertTrue(settingsItemMatches(wifi, "wifi"))
        assertFalse(settingsItemMatches(wifi, "clock"))
    }

    @Test
    fun `keywords do not leak into unrelated rows`() {
        val unrelated = item("clear_downloads", "Clear downloads", "Remove finished downloads", keywords = listOf("apk", "files"))
        assertFalse(settingsItemMatches(unrelated, "wifi"))
    }

    @Test
    fun `keywords do affect a row even when the title has no match`() {
        val row = item(
            "query_all_packages",
            "Why Nova Store sees your apps",
            "Nova Store lists every installed app",
            keywords = listOf("permission", "installed", "scan"),
        )
        assertTrue(settingsItemMatches(row, "permission"))
        assertTrue(settingsItemMatches(row, "SCAN"))
        assertFalse(settingsItemMatches(row, "nolikeit"))
    }

    @Test
    fun `matching is purely substring based`() {
        val row = item("about_version", "Version")
        assertTrue(settingsItemMatches(row, "ver"))
        assertTrue(settingsItemMatches(row, "sion"))
    }
}