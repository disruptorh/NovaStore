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

- **FdroidIndexSourceProvider** — wraps `FdroidIndexClient` (core/network) and the existing F-Droid index parsing. Validation fetches a small cache copy and parses it to produce `SourcePreview`. Skeleton methods (`search`, `getAppDetails`, `getVersions`, `refresh`) are minimal stubs for now; the existing catalog flow remains responsible for F-Droid catalog materialization.
- **GitHubReleaseSourceProvider** — `providerType = GITHUB`. Skeleton implementation; validation returns an empty preview. Intended to wrap `GitHubClient` for release-based discovery.
- **GiteaCompatibleSourceProvider** — `providerType = GITEA`. Skeleton implementation (covers Gitea/GitLab-compatible release layouts).
- **HtmlRegexSourceProvider** — `providerType = HTML_REGEX`. Skeleton implementation for direct/HTML+regex sources (constraints: HTTPS-only, timeouts, regex caps are the responsibility of concrete use cases).

## SourceRegistry

`SourceRegistry` (domain) and `SourceRegistryImpl` (data) aggregate enabled providers by repository configuration. The registry can return enabled providers and look up a provider for a given config.

## RepositoryConfig/Entity

- `RepositoryConfig` (core-model): adds `priority`, `providerType`, `extraJson`.
- `RepositoryEntity` (core-database): adds `providerType`, `extraJson`; migration `5→6` adds columns with defaults.
- Mappers translate between entity and model; Room schema `6.json` is exported.

## Notes

- HTTPS-only and conservative timeouts are expected for remote HTML/regex sources.
- Provider implementations are currently skeletal (per P04). They compile against existing clients and can be expanded incrementally without breaking the build.