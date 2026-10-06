# P04 — Capa de fuentes y proveedores extensibles

## Objetivo

Una interfaz real de proveedor (hoy solo documentada y **inexistente**) para F-Droid, GitHub, GitLab/Gitea/Codeberg y URL/HTML+regex. El catálogo unificado solo consulta providers `enabled`.

## Prerrequisitos

P03-T01 (sitio de UpdateEngine). P02 limpio.

## Archivos afectados

Crear (rutas nuevas bajo paquetes existentes):

- `domain/src/main/java/com/novastore/app/domain/source/AppSourceProvider.kt`
- `domain/src/main/java/com/novastore/app/domain/source/SourceRegistry.kt` (interfaz)
- `data/src/main/java/com/novastore/app/data/source/SourceRegistryImpl.kt`
- `data/src/main/java/com/novastore/app/data/source/FdroidIndexSourceProvider.kt`
- `data/src/main/java/com/novastore/app/data/source/GitHubReleaseSourceProvider.kt`
- `data/src/main/java/com/novastore/app/data/source/GiteaCompatibleSourceProvider.kt`
- `data/src/main/java/com/novastore/app/data/source/HtmlRegexSourceProvider.kt`
- tests en `data/src/test/...`

Modificar:

- `core/model/.../RepositoryConfig.kt` (añadir `priority`, `providerType`, `extraJson` para regex)
- `core/database/.../entity/Entities.kt` `RepositoryEntity`
- `core/database` migración 5 o 6 (si P03 ya usó 5, esta es 6)
- `domain/.../RepositoriesRepository.kt`
- `data/.../RepositoriesRepositoryImpl.kt`
- `data/.../CatalogRepositoryImpl.kt` (dejar de llamar GitHub/GitLab/mirrors ad hoc; pasar por registry)
- `data/websource/GitHubClient.kt`, `GitLabClient.kt` (reutilizar)
- `docs/source-providers.md` (alinear con código)

No crear `data/source` paralelo mortal: un solo paquete `data/source`.

## Contrato (normativo)

```kotlin
enum class ProviderType { FDROID_INDEX, GITHUB, GITEA, GITLAB, HTML_REGEX }

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

data class SourcePreview(val appCountHint: Int?, val sampleNames: List<String>, val warning: String?)
```

`SourceRegistry.enabledProviders(): List<AppSourceProvider>` filtra `enabled==true`. UpdateEngine y search **solo** iteran eso + Play si `playUpdatesEnabled` / web toggle.

Alta de proveedor nuevo (documentar en docs):

1. Implementar `AppSourceProvider`.
2. Añadir valor a `ProviderType`.
3. Registrar en DI de `SourceRegistryImpl`.
4. Añadir rama en UI de add-source (P06).
5. Test de `validate()` con MockWebServer.

## Tareas

### P04-T01 Interfaz AppSourceProvider

**Qué:** Crear archivo domain exacto al contrato. Sin lógica.

**Aceptación:** `:domain:compileReleaseKotlin`. Ningún feature importa data.

**Verificación:** compile + archivo existe.

### P04-T02 RepositoryConfig + entity

**Qué:** Campos `priority: Int = 500`, `providerType: ProviderType = FDROID_INDEX`, `extraJson: String? = null` (regex, owner/repo, gitea base). Room columnas + migración. Mapper `toModel`.

**Aceptación:** DB upgrade conserva repos built-in; custom siguen FDROID_INDEX.

**Verificación:** schema export; compile database.

### P04-T03 SourceRegistry

**Qué:** Interfaz domain + impl data. Métodos: `providers()`, `enabledProviders()`, `byId(id)`. Built-ins F-Droid se materializan como N instances (una por row Room), no un provider global “fdroid” único que ignore disable.

**Aceptación:** disable IzzyOnDroid → no aparece en `enabledProviders`. Unit test fake.

**Verificación:** test `SourceRegistryImplTest`.

### P04-T04 Provider F-Droid

**Qué:** Envolver `FdroidIndexClient` + `CatalogDao.replaceSource`. Una instancia por `repositoryId`. `refresh()` = `RepositoriesRepositoryImpl.refreshLocked` extraído a este provider.

**Aceptación:** 29 built-ins siguen refrescando; custom HTTPS index igual.

**Verificación:** `./gradlew :core:network:testReleaseUnitTest`; smoke refresh fdroid.

### P04-T05 GitHub Releases

**Qué:** Config: URL `https://github.com/owner/repo` o `owner/repo`. Usa `GitHubClient` existente (API releases, APK assets). `providerId` estable `github:owner/repo`. Package: el `applicationId` del APK del release, **no** `github.owner.repo` sintético cuando el APK declare package (el sintético solo como fallback de búsqueda).

**Aceptación:** validate con repo público conocido (p.ej. un repo FOSS) en test MockWebServer, no red viva en CI.

**Verificación:** unit tests JSON fixture.

**Riesgo:** rate limit GitHub. Cache TTL ya en client (10 min).

### P04-T06 Gitea/GitLab/Codeberg

**Qué:** Un `GiteaCompatibleSourceProvider`: base URL + proyecto. GitLab.com API v4 (`GitLabClient`) como especialización. Codeberg: `/api/v1/repos/{owner}/{repo}/releases`. Detectar host: `gitlab.com` → GitLab; resto Gitea-compatible.

**Aceptación:** validate URL Codeberg y GitLab en tests con fixtures.

**Verificación:** tests unitarios.

### P04-T07 HTML + regex

**Qué:** Usuario da `baseUrl`, `apkUrlRegex` (grupo 1 = URL), opcional `versionRegex`. Fetch HTML IO, aplicar regex, listar APKs. **Solo HTTPS**. Rechazar regex catastrófico (timeout 2s, patrón length cap).

**Aceptación:** fixture HTML + regex extrae 1 URL; http:// falla validate.

**Verificación:** `HtmlRegexSourceProviderTest`.

### P04-T08 validate + preview

**Qué:** `validate` no escribe Room. Preview: hasta 5 nombres. Errores: TLS, 404, JSON inválido, regex 0 matches.

**Aceptación:** add flow (P06) puede llamar esto; aquí API lista.

**Verificación:** tests de error paths.

### P04-T09 Docs

**Qué:** Reescribir `docs/source-providers.md` a las clases reales. Borrar menciones a archivos fantasma. Un solo doc (raíz ya borrada en P02-T06).

**Aceptación:** todas las clases citadas existen (`rg` de cada nombre).

**Verificación:** lista de símbolos vs glob.

### P04-T10 Tests registry/URL

**Qué:** Normalización URL (trailing slash, github.com vs api). Rechazo javascript:, file:, http:.

**Aceptación:** ≥8 tests nuevos green en `./gradlew testReleaseUnitTest`.

**Verificación:** gradle test.

## Definición de hecho

Cuatro tipos de provider + registry. CatalogRepositoryImpl no instancia GitHub/GitLab por toggles sueltos: los toggles `github_catalog_enabled` se migran a repos enabled o se eliminan en P06 (no dejar dos interruptores). Documentar migración de esos booleans: si true, no auto-crear repos; el usuario añade GitHub como fuente. El catálogo genérico GitHub search (`GitHubClient.search` MIN_STARS) **se desactiva** (ruido, no Obtainium). Solo repos que el usuario o built-ins activaron.

## Notas de decisión

Descartado mantener search global GitHub/GitLab por estrellas: no es “fuentes activas”. Play search no es un `RepositoryConfig`; sigue siendo fuente sistema con toggles Play/web en Settings (P09), visible como provider interno `PlaySourceProvider` **opcional** en P04 si cabe en una sesión; si no, Play permanece en `PlayStoreRepository` y el registry no lo envuelve hasta P05-T06.

Descartado index-v2 only: el parser v1+v2 actual se conserva dentro del provider F-Droid.
