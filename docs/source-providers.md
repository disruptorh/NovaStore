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
    suspend fun search(query: String): List<RemoteApp>
    suspend fun getAppDetails(packageName: String): RemoteAppDetails?
    suspend fun getVersions(packageName: String): List<AppVersion>
    suspend fun refresh(): AppResult<Unit>
}
```

Providers return what data exists — they never fabricate missing fields. `SourcePreview` (domain) carries a sample and optional warning after validation. `RepositoryConfig` (core-model) adds `providerType`, `priority`, and `extraJson` fields.

## Provider implementations (data/source)

- **FdroidIndexSourceProvider** — wraps `FdroidIndexClient` (core/network) and the existing F-Droid index parsing. Validation downloads and parses a scratch copy of the index and produces a `SourcePreview` of up to 5 app names; a failed download (TLS/404) or an unparseable index is surfaced as `NovaError.Repository`, never as an empty success. `search`, `getAppDetails`, `getVersions`, `refresh` are minimal stubs for now; the existing catalog flow remains responsible for F-Droid catalog materialization.
- **GitHubReleaseSourceProvider** — `providerType = GITHUB`. Validation accepts `owner/repo` or a `github.com` URL (rejects any other host), probes the GitHub releases API and previews up to 5 release names; 404, invalid JSON and network failures are typed errors. `providerType = GITLAB` configs are served by this family via `GiteaCompatibleSourceProvider` (host detection).
- **GiteaCompatibleSourceProvider** — `providerType = GITEA`. Validation probes `gitlab.com` projects (v4 API) or any other host's Gitea-compatible releases API (v1) from the configured base URL, with the same typed error paths and a preview of up to 5 release names.
- **HtmlRegexSourceProvider** — `providerType = HTML_REGEX`. Validation fetches the base page (https:// only — http:, javascript:, file: are rejected), applies `apkUrlRegex` from `extraJson` (`{"apkUrlRegex": "…"}`) under a pattern length cap and a hard 2 s extraction timeout, and previews up to 5 matched URLs. Zero matches and over-long patterns fail validation.

## SourceRegistry

`SourceRegistry` (domain) and `SourceRegistryImpl` (data) aggregate enabled providers by repository configuration. The registry can return enabled providers and look up a provider for a given config.

## RepositoryConfig/Entity

- `RepositoryConfig` (core-model): adds `priority`, `providerType`, `extraJson`.
- `RepositoryEntity` (core-database): adds `providerType`, `extraJson`; migration `5→6` adds columns with defaults.
- Mappers translate between entity and model; Room schema `6.json` is exported.

## Notes

- HTTPS-only and conservative timeouts are expected for remote HTML/regex sources; `SourceUrls` (core-model) is the single sanitizer for user-supplied URLs.
- `search`, `getAppDetails`, `getVersions` and `refresh` are still minimal stubs; P05–P06 wire catalog materialization through the providers.