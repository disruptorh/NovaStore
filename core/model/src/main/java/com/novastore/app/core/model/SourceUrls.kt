package com.novastore.app.core.model

import java.net.URI

/**
 * Sanitizing and normalization for user-supplied source URLs and repository
 * refs. Pure and JVM-testable so every provider's validate() step and the
 * P06 add-source flow share ONE acceptance of what a usable URL is.
 *
 * Security rules enforced here:
 *  - only https:// is acceptable (never javascript:, file:, data:, ftp:…);
 *  - length is capped against abusive input;
 *  - a trailing slash / whitespace is irrelevant and stripped.
 */
object SourceUrls {

    /** Guard against pathological URL inputs in validate() and the add flow. */
    const val MAX_URL_LENGTH = 2048

    /**
     * Normalizes a source URL/ref. Returns the canonical form
     * ("https://host/path" without a trailing slash, host lowercased) or
     * null when the input is not a usable HTTPS URL.
     */
    fun normalizeSourceUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_URL_LENGTH) return null
        val parsed = runCatching { URI(trimmed) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase() ?: return null
        if (scheme != "https") return null
        val host = parsed.host?.lowercase() ?: return null
        val path = parsed.path.takeUnless { it.isNullOrBlank() } ?: ""
        return buildString {
            append("https://")
            append(host)
            if (parsed.port > 0 && parsed.port != 443) append(':').append(parsed.port)
            append(path.trimEnd('/'))
        }
    }

    /**
     * Extracts the canonical "owner/repo" full name from a GitHub ref: the
     * bare "owner/repo", or a repo URL hosted on github.com. Null for hosts
     * that are not GitHub (a Codeberg URL must never be treated as GitHub).
     */
    fun asGitHubFullName(ref: String): String? {
        val trimmed = ref.trim()
        val path = when {
            trimmed.startsWith("https://github.com/") -> trimmed.removePrefix("https://github.com/")
            trimmed.startsWith("http://github.com/") -> trimmed.removePrefix("http://github.com/")
            trimmed.startsWith("git://github.com/") -> trimmed.removePrefix("git://github.com/")
            trimmed.startsWith("github.com/") -> trimmed.removePrefix("github.com/")
            !trimmed.contains("://") && !trimmed.startsWith("/") -> trimmed
            else -> return null
        }
        val segments = path.trim('/').split('/')
        if (segments.size < 2 || segments[0].isBlank() || segments[1].isBlank()) return null
        if (segments.size > 2) {
            // a deeper path (…/releases/tag/v1) is still the same repo
            return "${segments[0]}/${segments[1]}"
        }
        return "${segments[0]}/${segments[1]}"
    }

    /**
     * Repository host → API base for release-list probes. GitHub maps to its
     * dedicated API host; gitlab.com to the v4 API; any other https host is
     * treated as a Gitea-compatible instance (v1 API). Null for non-HTTPS.
     */
    /**
     * Maps a URL to a single-repository provider type when the host is a
     * well-known one, or null otherwise. Only explicit hosts are ever guessed
     * (http(s) only); an F-Droid repo is never detected from a URL.
     */
    fun detectProviderType(ref: String): ProviderType? {
        val url = normalizeSourceUrl(ref) ?: return null
        val host = url.removePrefix("https://").substringBefore('/').substringBefore(':')
        return when (host) {
            "github.com" -> ProviderType.GITHUB
            "gitlab.com" -> ProviderType.GITLAB
            "codeberg.org" -> ProviderType.GITEA
            else -> null
        }
    }

    fun apiBaseOf(ref: String): String? {
        val url = normalizeSourceUrl(ref) ?: return null
        val hostPort = url.removePrefix("https://").substringBefore('/')
        val host = hostPort.substringBefore(':')
        val port = hostPort.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { ":$it" } ?: ""
        return when (host) {
            "github.com" -> "https://api.github.com"
            "gitlab.com" -> "https://gitlab.com/api/v4"
            else -> "https://$host$port/api/v1"
        }
    }
}