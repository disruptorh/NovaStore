# Source Providers

## AppSourceProvider

The `AppSourceProvider` interface (domain) defines the contract for any catalog source:

```kotlin
interface AppSourceProvider {
    val providerId: String
    val displayName: String
    val type: ProviderType

    suspend fun isEnabled(): Boolean
    suspend fun validate(config: RepositoryConfig): AppResult<SourcePreview>
    suspend fun fetch(config: RepositoryConfig): AppResult<SourceCatalog>
    suspend fun search(query: String): List<RemoteApp>
    suspend fun getAppDetails(packageName: String): RemoteAppDetails?
    suspend fun getVersions(packageName: String): List<AppVersion>
    suspend fun refresh(): AppResult<Unit>
}
```

Single-repository providers implement `fetch()`, which materializes the whole catalog in one call: an `AppVersion` list plus the `RemoteApp` row. The F-Droid provider inherits the default failure — its catalog comes from the index-persistence flow instead. Providers return what data exists and never fabricate missing fields; the only synthetic value is the identity key (`ProviderPackage`, see below). `SourcePreview` (domain) carries a sample and optional warning after validation.

## Provider implementations (data/source)

- **FdroidIndexSourceProvider** — wraps `FdroidIndexClient` (core/network) and the existing F-Droid index parsing. Validation downloads and parses a scratch copy of the index and produces a `SourcePreview` of up to 5 app names; a failed download (TLS/404) or an unparseable index is surfaced as `NovaError.Repository`, never as an empty success. Catalog materialization stays in the existing index-persistence flow (`RepositoriesRepositoryImpl.storeCatalog`).
- **GitHubReleaseSourceProvider** — `providerType = GITHUB`; display name "GitHub". Validation accepts `owner/repo` or a `github.com` URL (rejects any other host), probes the GitHub releases API and previews up to 5 release names; 404, invalid JSON and network failures are typed errors. `fetch()` reuses `GitHubClient`, so UA, caching and error handling stay in one place: repo metadata feeds the app row, the releases feed the versions, and each version is keyed by `ProviderPackage.of(GITHUB, canonicalUrl)`.
- **GiteaCompatibleSourceProvider** — `providerType = GITEA`; display name "GitLab / Gitea". The host picks the dialect: `gitlab.com` → v4 releases API (`assets.links[]`, no size or digest, type tag `GITLAB`), any other https host → Gitea v1 (`assets[]` with `browser_download_url` and a `digest.sha256` object, type tag `GITEA`). `fetch()` parses releases with `ReleaseCatalog` and stores the result under the matching `ProviderPackage`.
- **HtmlRegexSourceProvider** — `providerType = HTML_REGEX`; display name "HTML". Validation fetches the base page (https:// only — http:, javascript:, file: are rejected), applies `apkUrlRegex` from `extraJson` (`{"apkUrlRegex": "…"}`) under a pattern length cap and a hard 2 s extraction timeout, and previews up to 5 matched URLs. Zero matches and over-long patterns fail validation. `fetch()` turns each matched URL into one version and always attaches the integrity warning (no origin checksums exist by construction).

## Shared parsing and options

`ReleaseCatalog` (data/source) is the single, pure implementation of release/asset rules shared by the providers (and by `GitHubClient.fetchReleases`): what counts as an APK asset, the optional APK-name filter regex, the pre-release policy, the digest/date extraction and the newest-first ordering. Per-source options travel in `RepositoryConfig.extraJson` and are read through `ReleaseCatalog.parseOptions`:

- `includePrereleases` (boolean) — show betas/release candidates (GitHub, GitLab, Gitea).
- `apkFilterRegex` (regex) — keep only APK assets whose file name matches (GitHub, GitLab, Gitea).
- `apkUrlRegex` (regex, group 1 = download URL) — HTML sources only.

Sources with no digest information attach `NO_CHECKSUM_WARNING` — it explains sha256 is only known after download, and is shown, never blocking.

## Identity of provider apps

Apps materialized from a single-repository source get a deterministic synthetic package name `novasrc.<type>.<sanitized canonical url>` built by `ProviderPackage` (core-model) — e.g. `novasrc.github.github.com.owner.repo`. These are identity keys only and are never installed; `ProviderPackage.isProviderPackage` lets the UI tell provider apps apart from Play / F-Droid / builtin entries and skip Play probes or reviews for them.

## Materialization flow

`RepositoriesRepositoryImpl` branches on `providerType` in `refreshLocked`. Provider-backed repositories never touch the F-Droid index path: the provider is looked up through `SourceRegistry.providerForType`, its `fetch()` result (an `AppResult<SourceCatalog>` with a `warning`) is persisted via `catalogDao.replaceSource`, and the repository's refresh state columns are updated exactly like a parsed F-Droid index so Settings shows the same success/error states. `add()` and `setEnabled(true)` call `refresh()` after persisting the row, so apps appear right after adding/enabling a source.

Settings detects the provider type from the URL (`SourceUrls.detectProviderType`: `github.com`, `gitlab.com`, `codeberg.org` only — an F-Droid repo is never guessed) and passes the extra options above through the add/edit dialog.

## SourceRegistry

`SourceRegistry` (domain) and `SourceRegistryImpl` (data) aggregate enabled providers by repository configuration. The registry can return enabled providers and look up a provider for a given config; `GITLAB` type falls back to the Gitea-compatible provider.

## RepositoryConfig/Entity

- `RepositoryConfig` (core-model): adds `priority`, `providerType`, `extraJson`.
- `RepositoryEntity` (core-database): adds `providerType`, `extraJson`; migration `5→6` adds columns with defaults.
- Mappers translate between entity and model; Room schema `6.json` is exported.

## Notes

- HTTPS-only and conservative timeouts are expected for remote HTML/regex sources; `SourceUrls` (core-model) is the single sanitizer for user-supplied URLs.
- `search`, `getAppDetails`, `getVersions` and `refresh` remain minimal stubs on the single-repository providers; their catalog materialization is fully served by `validate()` + `fetch()`.