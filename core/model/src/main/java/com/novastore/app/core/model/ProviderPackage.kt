package com.novastore.app.core.model

/**
 * Deterministic synthetic package name for apps materialized from a
 * single-repository source (GitHub / GitLab / Gitea / HTML). Pattern:
 * "novasrc.<type>.<sanitized canonical url>", e.g.
 * "novasrc.github.github.com.owner.repo".
 *
 * These are identity keys only: they are never installed as-is (the real APK
 * carries its own package id), but they let the UI tell provider apps apart
 * from Play / F-Droid / builtin catalog entries and back them with the
 * catalog row's own release data.
 */
object ProviderPackage {

    const val PREFIX = "novasrc."

    fun of(providerType: ProviderType, canonicalRef: String): String {
        val slug = canonicalRef
            .removePrefix("https://")
            .removePrefix("http://")
            .lowercase()
            .map { if (it.isLetterOrDigit() || it == '.' || it == '-' || it == '_') it else '.' }
            .joinToString("")
            .replace(Regex("\\.{2,}"), ".")
            .trim('.')
            .takeIf { it.isNotBlank() } ?: "unknown"
        return PREFIX + typeTag(providerType) + "." + slug
    }

    /** Stable short tag per provider type. */
    fun typeTag(providerType: ProviderType): String = when (providerType) {
        ProviderType.FDROID_INDEX -> "fdroid"
        ProviderType.GITHUB -> "github"
        ProviderType.GITLAB -> "gitlab"
        ProviderType.GITEA -> "gitea"
        ProviderType.HTML_REGEX -> "html"
    }

    fun isProviderPackage(packageName: String): Boolean = packageName.startsWith(PREFIX)
}