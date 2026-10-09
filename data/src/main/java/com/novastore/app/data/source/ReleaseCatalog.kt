package com.novastore.app.data.source

import com.novastore.app.core.model.AppVersion
import com.novastore.app.core.model.ArtifactType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared, pure release/asset plumbing for every single-repository provider so a
 * GitHub release, a GitLab v4 release and a Gitea v1 release all become the
 * same [AppVersion] list with identical rules for what counts as an installable
 * artifact (APK-ness, an optional APK-name filter regex, the pre-release
 * policy and the newest-first ordering) — and the same label plumbing
 * (version names from tags, synthetic version codes, ISO dates).
 */
internal object ReleaseCatalog {

    /** User-facing per-source extras carried in RepositoryConfig.extraJson. */
    data class CatalogOptions(
        /** Include pre-release / beta / upcoming releases as installable versions. */
        val includePrereleases: Boolean = false,
        /** Only keep APK assets whose file name matches this regex (null = all). */
        val apkFilterRegex: String? = null,
        /** HTML sources only: regex whose group 1 is a download URL. */
        val apkUrlRegex: String? = null,
    )

    fun parseOptions(extraJson: String?): CatalogOptions {
        if (extraJson.isNullOrBlank()) return CatalogOptions()
        return runCatching {
            val json = JSONObject(extraJson)
            CatalogOptions(
                includePrereleases = json.optBoolean("includePrereleases", false),
                apkFilterRegex = json.optString("apkFilterRegex").ifBlank { null },
                apkUrlRegex = json.optString("apkUrlRegex").ifBlank { null },
            )
        }.getOrNull() ?: CatalogOptions()
    }

    /** Shown when a release source carries no digests (not blocking). */
    const val NO_CHECKSUM_WARNING =
        "This source publishes no checksums: sha256 is only known after download."

    fun isApkAsset(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".apk") ||
            lower.endsWith(".apkm") ||
            lower.endsWith(".apks") ||
            lower.endsWith(".xapk")
    }

    fun artifactType(name: String): ArtifactType =
        if (name.lowercase().endsWith(".apk")) ArtifactType.APK else ArtifactType.APK_SET

    fun versionNameOf(tag: String?): String? =
        tag?.trim()?.removePrefix("v")?.removePrefix("V")?.takeIf { it.isNotBlank() }

    fun isPreRelease(json: JSONObject): Boolean = json.optBoolean("prerelease", false)

    fun shouldInclude(prerelease: Boolean, includePrereleases: Boolean): Boolean =
        includePrereleases || !prerelease

    fun matchesNameFilter(name: String, apkFilterRegex: String?): Boolean {
        if (apkFilterRegex.isNullOrBlank()) return true
        val regex = runCatching { Regex(apkFilterRegex) }.getOrNull() ?: return true
        return regex.containsMatchIn(name)
    }

    fun parseIsoDate(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return runCatching { java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
    }

    /**
     * A stable, mostly-monotonic synthetic version code from a git tag: the
     * first three numeric groups are packed as major·1e3·1e3 + minor·1e3 +
     * patch, plus a recency tiebreak so the newest release always wins. Tags
     * that carry no version numbers fall back to a recency-only code.
     */
    fun versionCodeFromTag(tag: String?, index: Int): Long {
        val runs = tag
            ?.split(Regex("[^0-9]+"))
            ?.filter { it.isNotEmpty() }
            ?.map { it.take(3).toLongOrNull() ?: 0L }
        val major = runs?.getOrNull(0) ?: 0L
        val minor = runs?.getOrNull(1) ?: 0L
        val patch = runs?.getOrNull(2) ?: 0L
        if (major == 0L && minor == 0L && patch == 0L) return (999 - index.coerceAtMost(999)).toLong()
        val base = major * 1_000_000L + minor * 1_000L + patch
        return base * 1000L + (999 - index.coerceAtMost(999))
    }

    // ------------------------------------------------------------------
    // Gitea v1 >=/releases
    // ------------------------------------------------------------------

    fun parseGiteaReleases(
        releases: JSONArray,
        packageName: String,
        source: String,
        includePrereleases: Boolean = true,
        apkFilterRegex: String? = null,
        limit: Int = Int.MAX_VALUE,
    ): List<AppVersion> {
        val versions = mutableListOf<AppVersion>()
        var index = 0
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (release.optBoolean("draft", false)) continue
            if (!shouldInclude(isPreRelease(release), includePrereleases)) continue
            val tag = release.optString("tag_name")
            val assets = release.optJSONArray("assets") ?: continue
            for (a in 0 until assets.length()) {
                val asset = assets.optJSONObject(a) ?: continue
                val name = asset.optString("name", "")
                if (!isApkAsset(name)) continue
                if (!matchesNameFilter(name, apkFilterRegex)) continue
                versions += AppVersion(
                    packageName = packageName,
                    versionCode = versionCodeFromTag(tag, index),
                    versionName = versionNameOf(tag),
                    source = source,
                    size = asset.optLong("size", 0).takeIf { it > 0 },
                    downloadUrl = asset.optString("browser_download_url"),
                    sha256 = asset.optJSONObject("digest")?.optString("sha256")?.takeIf { it.isNotBlank() },
                    minSdk = null,
                    targetSdk = null,
                    addedAt = parseIsoDate(release.optString("published_at").ifBlank { release.optString("created_at") }),
                    artifactType = artifactType(name),
                    signer = null,
                    nativeCode = emptyList(),
                )
            }
            index++
        }
        return versions.sortedByDescending { it.versionCode }.take(limit)
    }

    // ------------------------------------------------------------------
    // GitLab v4 >=/releases (assets are release links)
    // ------------------------------------------------------------------

    fun parseGitLabReleases(
        releases: JSONArray,
        packageName: String,
        source: String,
        includePrereleases: Boolean = true,
        apkFilterRegex: String? = null,
        limit: Int = Int.MAX_VALUE,
    ): List<AppVersion> {
        val versions = mutableListOf<AppVersion>()
        var index = 0
        for (i in 0 until releases.length()) {
            val release = releases.optJSONObject(i) ?: continue
            if (!includePrereleases && release.optBoolean("upcoming_release", false)) continue
            val tag = release.optString("tag_name")
            val links = release.optJSONObject("assets")?.optJSONArray("links") ?: continue
            for (a in 0 until links.length()) {
                val link = links.optJSONObject(a) ?: continue
                val name = link.optString("name", "").ifBlank { link.optString("url").substringAfterLast('/') }
                if (!isApkAsset(name)) continue
                if (!matchesNameFilter(name, apkFilterRegex)) continue
                versions += AppVersion(
                    packageName = packageName,
                    versionCode = versionCodeFromTag(tag, index),
                    versionName = versionNameOf(tag),
                    source = source,
                    size = null,
                    downloadUrl = link.optString("url"),
                    sha256 = null,
                    minSdk = null,
                    targetSdk = null,
                    addedAt = parseIsoDate(release.optString("released_at")),
                    artifactType = artifactType(name),
                    signer = null,
                    nativeCode = emptyList(),
                )
            }
            index++
        }
        return versions.sortedByDescending { it.versionCode }.take(limit)
    }
}