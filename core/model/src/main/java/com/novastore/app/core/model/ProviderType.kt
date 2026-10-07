package com.novastore.app.core.model

/** How a repository is fetched and materialized into the local catalog. */
enum class ProviderType {
    FDROID_INDEX,
    GITHUB,
    GITEA,
    GITLAB,
    HTML_REGEX,
}