# 00 — Auditoría NovaStore

Fecha: 2026-10-05. Lectura de código + artefactos en `auditoria incompleta/`. No se ejecutó Gradle en esta fase. Tamaño APK verificado en disco.

## 1. Stack

| Pieza | Valor | Evidencia |
|---|---|---|
| Lenguaje | Kotlin 2.0.21, JVM 17 | `gradle/libs.versions.toml:3`, `app/build.gradle.kts:68-75` |
| UI | Jetpack Compose + Material 3, BOM `2024.12.01` | `gradle/libs.versions.toml:8,40` |
| App | `namespace` `com.novastore.app`, `applicationId` `com.novastore.fork` | `app/build.gradle.kts:13,17` |
| SDK | `compileSdk`/`targetSdk` 35, `minSdk` 26 | `app/build.gradle.kts:14-19` |
| Versión | `versionCode` 14, `versionName` `7.2.3` | `app/build.gradle.kts:20-21` |
| DI | Hilt 2.52 | `gradle/libs.versions.toml:5` |
| Persistencia | Room 2.6.1 (`nova-store.db` v4), DataStore 1.1.1 | `NovaDatabase.kt:23-50`, `libs.versions.toml:7,17` |
| Red | OkHttp 4.12.0 (sin Retrofit en código Kotlin) | `NetworkModule.kt:31-48`, `libs.versions.toml:15-16` |
| Imágenes | Coil 2.7.0, caché disco 256 MB | `NovaStoreApp.kt:72-88` |
| Fondo | WorkManager 2.10.0 | `UpdateScanWorker.kt:38`, `WorkScheduler` L73 |
| Nativo | CMake parser F-Droid en `core/network` | `core/network/build.gradle.kts:23-27` |
| Módulos | 22 `include(...)` | `settings.gradle.kts:25-50` |
| Release minify | **desactivado** | `app/build.gradle.kts:46,53` (`isMinifyEnabled = false`) |
| ABI splits | **no** | grep `splits`/`abiFilters` vacío |
| APK release actual | **18 MB**, un solo APK `NovaStore-v7.2.3.apk` | `app/build/outputs/apk/release/` |

Arquitectura declarada: app → features → domain; data implementa domain; cores. Inversión real: `core:updater` importa `domain` (`UpdateEngine.kt:12-18`).

## 2. Mapa de módulos y pantallas

Rutas de navegación (`NovaStoreRoot.kt:64-74` y NavHost en el mismo archivo): `home`, `updates`, `installed`, `settings`, más rutas `search`, `downloads`, `account`, `details/{packageName}`, `ignored`, `category/{key}`.

| Módulo | Archivos principales |
|---|---|
| `app` | `MainActivity.kt`, `NovaStoreApp.kt`, `ui/NovaStoreRoot.kt` (331 L), `work/UpdateScanWorker.kt`, `work/UpdateNotifier.kt`, `work/ScanProgressNotifier.kt`, `receiver/BootReceiver.kt`, `receiver/PackageChangeReceiver.kt` |
| `feature/home` | `HomeScreen.kt` (1270 L), `HomeViewModel.kt`, `CategoryScreen.kt`, `PlayShelves.kt` |
| `feature/search` | `SearchScreen.kt` (592 L), `SearchViewModel.kt` |
| `feature/details` | `AppDetailsScreen.kt` (1452 L), `AppDetailsViewModel.kt`, `DetailsExtraSections.kt` |
| `feature/updates` | `UpdatesScreen.kt` (735 L), `UpdatesViewModel.kt`, `IgnoredScreen.kt` |
| `feature/installed` | `InstalledScreen.kt`, `InstalledViewModel.kt` |
| `feature/downloads` | `DownloadsScreen.kt`, `DownloadsViewModel.kt` |
| `feature/settings` | `SettingsScreen.kt` (1608 L), `SettingsViewModel.kt` |
| `feature/account` | `AccountScreen.kt`, `AccountViewModel.kt`, `GoogleWebLogin.kt` |
| `domain` | 13 use cases en `domain/.../usecase/`; contratos en `domain/.../repository/` |
| `data` | `RepositoriesRepositoryImpl.kt` (345 L), `CatalogRepositoryImpl.kt` (443 L), `PlayStoreRepositoryImpl.kt`, `BuiltInRepositories.kt`, `data/websource/*` |
| `core/updater` | `UpdateEngine.kt`, `SourceResolutionPolicy.kt` |
| `core/network` | `FdroidIndexClient.kt`, `RepoIndexParser.kt`, `NativeFdroidIndex.kt` |
| `core/database` | `NovaDatabase.kt`, `entity/Entities.kt`, `dao/CatalogDao.kt` |
| `core/ui` | `theme/{Color,Theme,Typography}.kt`, `components/*` |

`docs/source-providers.md` documenta `AppSourceProvider`, `FdroidSourceProvider`, `SourceRegistry`. **No existen** (glob `data/source/` vacío; implementación real = `RepositoriesRepositoryImpl` + clientes web).

## 3. Agregación de repositorios (hoy)

1. Built-ins: 29 URLs HTTPS en `BuiltInRepositories.kt:14-45`. Archive y NetHunter `enabledByDefault = false` (L43-44).
2. Persistencia Room `RepositoryEntity` (`Entities.kt:142-158`): `priority`, ETag/Last-Modified, `enabled`, `isBuiltIn`. El modelo `RepositoryConfig.kt:13-23` **no** expone `priority`.
3. Alta custom: `RepositoriesRepository.add(name, url)` (`RepositoriesRepository.kt:20`). Impl normaliza con `FdroidIndexClient.normalizeRepoUrl` y exige `https://` (`RepositoriesRepositoryImpl`, región add ~L85-88 según auditoría de código).
4. Refresh: GET condicional índice F-Droid (`FdroidIndexClient.kt:63-74`) → parse nativo/Kotlin → `CatalogDao.replaceSource` (`CatalogDao.kt:31-36`). `refreshAll` con concurrencia 4.
5. Catálogo unificado: `remote_apps` PK `(packageName, source)` (`Entities.kt:22`). Dedup de listado: SQL `ORDER BY COALESCE(r.priority, 1000) LIMIT 1` (`CatalogDao.kt:60-70`). Categorías: `GROUP BY packageName` + `MIN(priority)` (`CatalogDao.kt:103-113`). Recientes **sin** prioridad (`CatalogDao.kt:79-87`).
6. Preferencia por app: DataStore `preferred_update_sources` (mapa `package=source`).
7. GitHub/GitLab **no** son repos Room: toggles `github_catalog_enabled` / `gitlab_catalog_enabled` y clientes `GitHubClient.kt`, `GitLabClient.kt` inyectados en `CatalogRepositoryImpl` (búsqueda/detalles, paquetes sintéticos `github.*` / `gitlab.*`).
8. Play nativo + Play web (`SOURCE_PLAY_WEB = "play"` igual que `SOURCE_PLAY`, `Sources.kt:9,21`).
9. No hay import/export JSON, ni API de reordenar, ni tipo de proveedor distinto de índice F-Droid para custom.

`ensureBuiltIns()` resetea prioridad de built-ins al índice de declaración en cada arranque (impl ~L299). Un reorder no sobreviviría.

## 4. Actualizaciones y mirrors

Flujo: `UpdateScanWorker.doWork` (`UpdateScanWorker.kt:46-58`) → `CheckForUpdatesUseCase` → `repositoriesRepository.refreshAll` → `UpdateCheckService`/`UpdateEngine` (repos F-Droid) → `addPlayCandidates` (`CheckForUpdatesUseCase.kt:82-183`).

Mirrors:

- Clientes: `data/websource/ApkPureClient.kt`, `ApkComboClient.kt`.
- Bulk: `PlayStoreRepository.mirrorVersionsFor` (`PlayStoreRepository.kt:72`, impl `PlayStoreRepositoryImpl.kt:547-576`). Race APKPure luego huecos APKCombo.
- Scan: etapa `ScanProgress.Stage.MIRRORS` (`ScanProgressTracker.kt:19`, `CheckForUpdatesUseCase.kt:154-178`). Candidatos se guardan con `source = SOURCE_PLAY` (L172-176), no `apkpure`/`apkcombo`.
- Detalles: `CatalogRepositoryImpl.mergeMirrorVersions` L332-347 si `versions` vacío.
- Settings: toggles L284/L291 `SettingsScreen.kt`; keys DataStore L101-103; default **true** (L300, L315).
- Instalación bloqueada: `isInstallSourceAllowed` (`Sources.kt:58`).
- WorkManager: `WorkScheduler.schedulePeriodicScan` L78-112. Intervalos IMMEDIATELY=1h, DAILY, WEEKLY. No pide `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. `BootReceiver` no reprograma el scan.

## 5. Código muerto, peso, blob

**Código muerto (0 referencias fuera del archivo):**

- `UninstallAppUseCase` — `domain/.../UninstallAppUseCase.kt`
- `RefreshRepositoriesUseCase` — `domain/.../RefreshRepositoriesUseCase.kt`
- Preferencia `try_community_dispensers` — `SettingsDataStore.kt:95` (solo read/write en el mismo archivo)

**Dependencias:** Retrofit declarado en `app/build.gradle.kts:150-151` y `core/network/build.gradle.kts:37-38`; **cero** usos `import retrofit` en `.kt`. `okhttp.logging` en network gradle; logging interceptor no aparece en `NetworkModule.kt`.

**Docs duplicados byte a byte:** `build.md`, `source-providers.md`, `update-engine.md`, `root-installation.md` en raíz y en `docs/`.

**Canales de notificación creados y sin `NotificationCompat.Builder`:** `CHANNEL_INSTALLATION`, `CHANNEL_ERRORS` (`NovaStoreApp.kt` región 120-152).

**Composable con 0 usos externos (verificar antes de borrar):** `AppCardGrid`, `SeeAllLink` (`AppCards.kt`); color `Teal` en `Color.kt`.

**Pantallas blob:** `SettingsScreen.kt` 1608 L, `AppDetailsScreen.kt` 1452 L, `HomeScreen.kt` 1270 L.

**Íconos:** `docs/branding/icon.png` 1254×1254; launcher `ic_launcher_art` PNG nodpi. Sin capa monochrome.

**Minify/R8/shrinkResources/ABI split:** ausentes. APK 18 MB unificado.

## 6. Rendimiento

| Problema | Evidencia |
|---|---|
| Release sin R8 | `app/build.gradle.kts:53` |
| Listas Lazy sin Paging 3 | grep `androidx.paging` vacío; `LazyColumn` en Home/Search/Updates/Installed/Downloads |
| Home carga catálogo completo en memoria vía ViewModel (no page-on-scroll del listado principal) | `HomeScreen.kt:239` LazyColumn de shelves; `CatalogDao.search` LIMIT 200 (`CatalogDao.kt:58`) |
| Categoría sí pagina SQL | `listByCategory` OFFSET (`CatalogDao.kt:110`) |
| Índices Room | solo `index_downloads_state` en `Migrations.kt:12`. Entidades catálogo **sin** `@Index` (`Entities.kt:22-56`) |
| LIKE `'%' \|\| query` | `CatalogDao.kt:48-50` — no usa índice |
| Coil sí cachea | `NovaStoreApp.kt:72-88` (20% RAM, 256 MB disco) |
| `InstalledAppEntity.icon: ByteArray?` | `Entities.kt:18` — iconos PNG en SQLite |
| OkHttp sin `Cache` | `NetworkModule.kt:31-48` |
| 682 literales `.dp`; 112 `RoundedCornerShape` | `uiux_audit.md` sección A |
| `HomeScreen` 1270 L, hex/gradientes inline | `HomeScreen.kt:536-546` aprox. |
| Arranque | `NovaStoreApp.onCreate` registra notifiers + `schedulePeriodicScan` (`NovaStoreApp.kt:90-100`); `ensureBuiltIns` en primer `observe()` |

Métricas actuales **no medidas** (no Macrobenchmark en repo). Proxy: APK 18 MB; minify off.

## 7. UI/UX

- Tema: `Theme.kt` light/dark/AMOLED + color dinámico API 31+ (`Theme.kt:1-173`). 16 acentos `AccentPalette`.
- Tipografía: `Typography.kt`, `FontFamily.Default`, sin `res/font`.
- Tokens: no hay `Spacing`/`Dimens`/`Shapes` centralizados.
- Settings: orden Play, Apariencia, Fuentes (incl. mirrors), Updates, Downloads, Storage, Advanced, About. Sin búsqueda. Borrado de repo sin diálogo de confirmación (`SettingsScreen.kt` ~1267).
- Estados: `ScreenStates.kt` Loading/Empty/Error; Home usa `ShimmerBox`; muchas pantallas aún `CircularProgressIndicator`.
- XML: `Theme.AppCompat.DayNight.NoActionBar`, sin splash Compose (`app/src/main/res/values/themes.xml`).
- A11y: 6 targets <48 dp; 103 iconos no AutoMirrored vs 20 sí; `fontSize` 8.sp/10.sp (`SearchScreen.kt:406`, `NovaStoreRoot.kt:329`).
- i18n: `core/ui/res/values` + ru/es/fr.

## 8. Deuda y seguridad

1. SHA-256 **opcional** si `expectedSha256` nulo o no hex (`DefaultArtifactVerifier.kt:42-47`). README afirma mandatory.
2. Firma: primer signer; skip si `installedCertDigest == null` (`DefaultArtifactVerifier.kt:73-77`).
3. `SOURCE_PLAY_WEB` colisiona con `SOURCE_PLAY` (`Sources.kt:9,21`).
4. Dedup catálogo (prioridad) ≠ updates (`SourceResolutionPolicy.kt:22-26` versionCode primero).
5. Prioridad no persistible de forma útil (`ensureBuiltIns`).
6. `SessionCipher.decrypt` acepta plaintext legacy (auditoría arch §5).
7. Sin `network_security_config`. Cleartext bloqueado por targetSdk 35. Deep links `http://` Play en `MainActivity` (`AndroidManifest.xml`).
8. `DownloadEngine` no rechaza `http://` en URL de artefacto.
9. Permisos: `MANAGE_EXTERNAL_STORAGE`, `QUERY_ALL_PACKAGES`, storage legado maxSdk 28/32 (`AndroidManifest.xml`).
10. Root `su -c` con regex (`RootCommandExecutor.kt`) **sin tests**.
11. 17/22 módulos sin tests. CI `.github/workflows/build.yml` solo `testReleaseUnitTest`. `run-tests.sh` hace `cd ..` fuera del repo.
12. `core:updater` → `domain`.

## 9. Lo que ya existe y no hay que reinventar

- Repos F-Droid custom add/enable/disable/refresh en Settings.
- Coil cacheado, WorkManager de scans, verificación de paquete (aunque incompleta).
- Preferencia de fuente por app + `chooseSource` en Updates.
- Paginación SQL de categorías.
- Tema AMOLED y acentos.

## 10. Implicaciones para los planes

- P05 debe **borrar** mirrors (clientes, merge, etapa MIRRORS, toggles, constants, tests de StoreLinks apkpure si se deja de parsear).
- P04 debe **crear** la abstracción que la doc ya describe y no está en código.
- P03 debe índices + paging + R8 no espera a identidad.
- P10: `applicationId` ya es `com.novastore.fork` ≠ namespace; cambiar package otra vez rompe datos salvo migración explícita.
- Identidad recomendada: **Arkiv** (ver plan 10).
