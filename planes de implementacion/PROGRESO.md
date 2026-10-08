# PROGRESO — tablero maestro

Leyenda de estado: **Pendiente** (casilla vacía) · **En curso** (marca `~` y pon el ID aquí abajo) · **Hecho** (`[x]`) · **Bloqueado** (sección al final).

Actualiza este archivo en el mismo commit que cierra la tarea.

## En curso

(ninguno)

## Hecho

- P01-T01…P01-T06 (ver `01_LINEA_BASE_Y_METRICAS.md` y `METRICAS_BASE.md`)
- P02-T01…P02-T11 (R8+shrink on, splits ABI, WebP launcher; smoke en device real: home/settings/details F-Droid OK)
- P03-T01 (core:updater → data/updater; módulo eliminado)
- P03-T02 (índices Room + migración 4→5; schema JSON exportado; test MigrationTestHelper; upgrade device OK)
- P03-T03 (OkHttp cache 50 MiB en NetworkModule; test MockWebServer; smoke device)
- P03-T04 (límites de sección ≤20; filas lazy con key; feed paginado ya existente)
- P03-T05 (búsqueda local paginada a 40 con load-more; `search` DAO con OFFSET + test; merge de fuentes intacto)
- P03-T06 (infinite scroll de categorías ya presente: `CategoryViewModel` página con `listByCategory(offset, PAGE=90)` + `CategoryScreen` nearEnd→loadMore)
- P03-T07 (keys+contentType en todas las Lazy lists de features: Search, Home, Updates, Installed, Downloads, Ignored, Category, Details)
- P03-T08 (iconos instalados ya no se persisten: scan/upsert escriben `icon = null`; UI lee de PackageManager vía `InstalledAppIcon` con LruCache off-main)
- P03-T09 (auditoría de hilos OK: 0 `runBlocking` en features, `fetchIndex`/`parseIndex` en IO, `replaceSource`/`ensureBuiltIns` Room suspend, bloqueo de features en `Dispatchers.IO`)
- P03-T10 (baseline profile con path de cold start: `app/src/main/baseline-prof.txt` con MainActivity/NovaStoreApp/NovaStoreRoot; APK sigue empaquetando `assets/dexopt/baseline.prof`+`.profm`)
- P05-T04 (keys `apkpure_mirror_enabled`/`apkcombo_mirror_enabled` + flows/setters fuera de `SettingsDataStore`; toggles fuera de `SettingsScreen`/`SettingsViewModel`; valores previos inertes, sin migración estructural)
- P05-T05 (`SOURCE_APKPURE`/`SOURCE_APKCOMBO`/`isInstallSourceAllowed`/`SyntheticVersionCodes`/`Stage.MIRRORS`/`MirrorRequired`/`mergeMirrorVersions`/`mirrorVersionsFor` eliminados; SourceBadge/AppCards/AppDetails/Updates sin ramas mirror; ScanProgress* sin MIRRORS; strings+locales y comentarios limpiados; se mantiene `StoreLinks` parse en `core:model`)
- P05-T08 (`SOURCE_PLAY_WEB = "play-web"` distinto de `SOURCE_PLAY`; `DownloadUpdateUseCase.prepare()` sube candidates `play-web` → Play; download() guarda `NovaError.Metadata` para `play-web`)
- P04 completo (T08 validate+preview en los 4 providers con errores tipeados TLS/404/JSON inválido/regex 0 matches; T09 `docs/source-providers.md` citando solo clases reales; T10 `SourceUrls` de normalización/rechazo + tests registry/URL ≥8 green). Detalle en `04_CAPA_DE_FUENTES.md`.
- P05-T06 (`CatalogDao.getVersionsFor` filtra por `source IN (repositories enabled)` — un repo disabled nunca genera candidato ni marca la app como gestionada; el pase Play lo respeta vía el mismo DAO; `getVersions(packageName)` de detalle/install queda intacto. Nota: las `getVersions(pkg)` de GitHub/Gitea/HTML siguen en stub (vuelven vivas en P06/P07 al materializar providers); el loop del pseudocódigo no consulta providers con `enabled=false`.)
- P05-T07 (`SourceResolutionPolicy.select` reordenada a prioridad-first: preferida (caller) → menor priority → mayor versionCode → source id; 5 tests ≥4 casos y `docs/update-engine.md` documenta prioridad-first; comentario `RepositoryEntity` "lower wins" alineado)
- P05-T09 (`WorkScheduler`: `setRequiresBatteryNotLow(threshold > 0)` y `IMMEDIATELY` → 15 min (mínimo WorkManager) con red; `BootReceiver` re-registra `nova_update_scan` tras reboot además de reencolar downloads; unique work ids se mantienen)
- P05-T10 (ids de canales unificados en `core/common/NotificationChannelIds` y usados por app + downloader; `InstallOutcomeNotifier` en `feature:updates` publica success de install en `installation` y fallos de download/verify/install en `errors` desde el resumen de `updateAll`; strings en `core:ui` 4 locales; 5 canales/5 usados, 5 sitios `NotificationCompat.Builder`)
- P05-T11 (la retención del escaneo se extrae a `data/.../UpdateRetention.kt`: `shouldDeleteUpdateRow` puro — fila en vuelo fresca sobrevive, zombie >24h se borra, terminal se borra, candidata nunca se borra — con `UpdateRetentionTest`; `CheckForUpdatesUseCaseTest` con fakes de interfaces: play desactivado salta el pase Play, play sin acceso retiene paquetes sin responder, fallo de repos sin fuente comparable → Failure, filas legacy DISCOVERY se limpian antes del pase; 8 tests nuevos, `:data` y `:domain` unit test verdes)
- P06-T01 (`RepositoryDao.updateFields` persiste name/baseUrl/metadataUrl/providerType/extraJson (2 tests Robolectric); `RepositoriesRepository.add` acepta `ProviderType` + `extraJson` y `update` nuevo; impl: F-Droid conserva `normalizeRepoUrl`+refresh, no-F-Droid escribe row canónica https (owner/repo para GitHub/GitLab/Gitea) sin tocar índices F-Droid, `ensureBuiltIns` ya NO sobrescribe el nombre local (solo reposiciona); `RepositoryEditorDialog` con selector de tipo, regex APK condicional para HTML y URL bloqueada en built-ins (edición solo nombre); strings en 4 locales; compila + tests verdes)
- P11 completo (T01–T09). Detalles + riesgo aceptado en commit: P11-T01 `VerificationResult.Valid.checksumFromSource`; sha256 del source con mismatch → `ChecksumMismatch`, sin sha256 → hash local y flags `checksumFromSource=false`; `UpdateAllUseCase.autoUpdatable` exige sha256 válido (Play deja de auto-instalar — aceptado). P11-T02 `SignatureVerifier.anySignerMatches` compara TODOS los firma apkContentsSigners; digest instalado desconocido + app instalada → bloqueo `SignatureMismatch`. P11-T03 `DownloadUrlPolicy.isSecureDownloadUrl` (https-only) en `enqueue`+`executeDownload`; `NetworkSecurityConfig` cleartext off + `usesCleartextTraffic=false`; `StoreLinks.upgradeToHttps`. P11-T04 `SessionCipher.migrate()` + `SessionMigrations` (playAuth/anonPlay) en arranque. P11-T05 `RootCommandExecutor.isPathInsideCache` + 5 tests JVM. P11-T06 `UpdateEngine.resolverCandidates` puro en companion (catalog-driven, sin tocar sources/mirrors) + `UpdateEngineScanTest` (5); total @Test 147 > 35. P11-T07 build.yml ya corre `testReleaseUnitTest`; `run-tests.sh` `bash -n` OK; README documenta `connectedAndroidTest` local. P11-T08 chevrons AutoMirrored (Logout, KeyboardArrowRight; 0 `Icons.Filled.Chevron*/KeyboardArrow*/ArrowForwardIos` restantes). P11-T09 quitado `MANAGE/WRITE/READ_EXTERNAL_STORAGE` + `StoragePermissionGate` + `AllFilesAccessRow`; se conservan `REQUEST_INSTALL_PACKAGES`/`QUERY_ALL_PACKAGES`; downloads siguen en `cache/downloads`.

## Bloqueado

(ninguno)

---

## P01 Línea base y métricas

- [x] P01-T01 Crear `METRICAS_BASE.md` y registrar tamaño APK/AAB actual
- [x] P01-T02 Script `scripts/measure-apk.sh` (tamaño, método, ABI)
- [x] P01-T03 Procedimiento de arranque en frío (adb + Macrobenchmark o manual)
- [x] P01-T04 Procedimiento de jank/frames (Perfetto o dump gfxinfo)
- [x] P01-T05 Procedimiento de RAM (meminfo)
- [x] P01-T06 Congelar números baseline en `METRICAS_BASE.md`

## P02 Limpieza y peso

- [x] P02-T01 Eliminar `UninstallAppUseCase`
- [x] P02-T02 Eliminar `RefreshRepositoriesUseCase`
- [x] P02-T03 Eliminar `try_community_dispensers`
- [x] P02-T04 Quitar Retrofit si sigue sin usos Kotlin
- [x] P02-T05 Quitar `okhttp-logging` si el interceptor no se usa
- [x] P02-T06 Deduplicar docs raíz vs `docs/`
- [x] P02-T07 Corregir `run-tests.sh`
- [x] P02-T08 Activar R8 + `shrinkResources` en release
- [x] P02-T09 Splits ABI o `abiFilters`
- [x] P02-T10 WebP/compresión de PNGs de branding/launcher sin pérdida visual
- [x] P02-T11 ProGuard keep rules para Hilt/Work/kotlinx.serialization/Room

## P03 Arquitectura y rendimiento

- [x] P03-T01 Romper inversión `core:updater` → `domain`
- [x] P03-T02 Índices Room catálogo/versiones + migración 4→5
- [x] P03-T03 OkHttp `Cache` en `NetworkModule`
- [x] P03-T04 Paging Home (no cargar catálogo entero)
- [x] P03-T05 Paging Search
- [x] P03-T06 Infinite scroll categorías (`listByCategory` ya tiene OFFSET)
- [x] P03-T07 Keys estables + contentType en Lazy lists
- [x] P03-T08 Iconos instalados: dejar de meter PNG grandes en Room o comprimir
- [x] P03-T09 Dispatcher IO en refresh/parse de índices (auditoría de hilos)
- [x] P03-T10 Baseline Profile keep (ya hay `.dm` en APK; verificar `baseline-prof.txt`)

## P04 Capa de fuentes

- [x] P04-T01 Interfaz `AppSourceProvider` en domain
- [x] P04-T02 Modelo `RepositoryConfig` + tipo de proveedor + priority
- [x] P04-T03 `SourceRegistry` (solo fuentes enabled)
- [x] P04-T04 Provider F-Droid/index (envolver cliente actual)
- [x] P04-T05 Provider GitHub Releases
- [x] P04-T06 Provider GitLab / Gitea / Codeberg
- [x] P04-T07 Provider URL directa / HTML + regex
- [x] P04-T08 Validación URL + preview de fetch
- [x] P04-T09 Documentar alta de provider nuevo (reescribir `docs/source-providers.md` a la realidad)
- [x] P04-T10 Tests unitarios de registry y URL

## P05 Motor de actualizaciones

- [x] P05-T01 Borrar `ApkPureClient` y `ApkComboClient`
- [x] P05-T02 Quitar `mirrorVersionsFor` y `mergeMirrorVersions`
- [x] P05-T03 Reescribir `CheckForUpdatesUseCase` sin etapa MIRRORS
- [x] P05-T04 Quitar keys/toggles APKPure/APKCombo + migración DataStore
- [x] P05-T05 Quitar constantes/UI/tests de fuentes mirror (salvo parseo de links externos si se mantiene)
- [x] P05-T06 Scan solo contra providers enabled + Play si toggle Play activo
- [x] P05-T07 Unificar política de dedup (preferida → prioridad → versionCode)
- [x] P05-T08 Separar `SOURCE_PLAY_WEB` de `SOURCE_PLAY`
- [x] P05-T09 WorkManager: constraints, intervalo, `BootReceiver` re-arma scan
- [x] P05-T10 Notificaciones INSTALLATION y ERRORS
- [x] P05-T11 Tests motor (póliza de retención de filas en vuelo + uso del `CheckForUpdatesUseCase`)

## P06 Fuentes personalizadas

- [x] P06-T01 UI add/edit con tipo de proveedor
- [x] P06-T02 Preview de resultados al añadir
- [x] P06-T03 Enable/disable + delete con confirmación
- [x] P06-T04 Reordenar prioridad persistente (`ensureBuiltIns` no pisa)
- [x] P06-T05 Import/export JSON
- [x] P06-T06 Fuente preferida por app en detalle
- [x] P06-T07 Catálogo único: misma regla de dedup en DAO y updates
- [x] P06-T08 Integridad hash/firma en flujo de alta (aviso si el índice no trae sha256)
- P06-T02 (`PreviewSourceUseCase` en domain con `@Inject SourceRegistry`: build un `RepositoryConfig` "preview" y delega en `providerForType(type)?.validate`; éxito → sampleNames (cap 5) + appCountHint + warning, fallo tipado; test domain con fake registry/provider: éxito ≤5, fallo 404 tipado, tipo sin provider → Failure. `SettingsViewModel.testSource` (extraJsonFor para HTML regex; estado `sourcePreview/sourcePreviewError/sourcePreviewLoading` en `SettingsUiState`); `RepositoryEditorDialog` botón "Test source" (spinner mientras carga, error color error, aviso warning, resultado live sin escribir catálogo). Verificación: `:domain:testReleaseUnitTest --tests PreviewSourceUseCaseTest` + compila settings/app.)
- P06-T03 (delete: `RepositoriesManager.pendingRemove` state → `AlertDialog` con `settings_repo_delete_confirm` (custom, "%d apps del catálogo, no se puede deshacer") o `settings_repo_delete_builtin` (built-in: se deshabilita y siempre vuelve); botón de borrar ahora visible también en built-ins (delete = disable, comportamiento `remove` existente); confirm llama `onRemove` y limpia; strings 4 locales; compila.)
- P06-T04 (`RepositoryDao.updatePriority`; API `RepositoriesRepository.reorder(idsInOrder)` → prioridades 10,20,30… persistidas; `ensureBuiltIns` refactor: la decisión "solo filas ausentes" extraída a `missingBuiltInRows(existing)` pura en `BuiltInRepositories.kt` — filas existentes NUNCA se devuelven (su `upsert` REPLACE clobberearía nombre/prioridad; ese es el motivo del guard); impl `ensureBuiltIns` solo upsert de ausentes. UI: botones ↑/↓ en `RepositoryRow` (KeyboardArrowUp/Down, `onReorder` en `RepositoriesManager`, `viewModel.reorderRepositories`), cd_reorder_up/down en 4 locales. Tests: `RepositoryPriorityTest` (2→1: reorder persiste tras re-read — "matar proceso" en DAO; el test "upsert no pisa" se movió a `BuiltInRepositoriesTest`: insertIfAbsent cubre todos, existente NUNCA devuelto → nombre+prioridad sobreviven, disabledByDefault preservado); compila + verdes.)
- P06-T05 (`SourceBackup.kt` codec puro: `SourceBackup{sources}` version 1 {name,url,type,enabled,priority,extra}, `toBackupEntry()` desde `RepositoryEntity`, `toJson()`, `parseSourceBackup(json)` valida version/tipo/name/url y falla estructural → `AppResult.Failure`; `core.model.ImportReport(added,updated,rejected)`; `RepositoriesRepository.exportSources()/importSources(json)`; impl: export = todas las rows; import merge por URL canónico (FDROID normalizeRepoUrl; resto SourceUrls), no-https → entry rechazada (rejected++), re-add por URL: si existe → solo aplica enabled+priority del archivo, conserva name/type/extras/isBuiltIn (built-ins ausentes del archivo NO se borran); UI: launchers SAF CreateDocument/OpenDocument en `SettingsScreen` (resolver+coroutineScope), botones Export/Import en `RepositoriesManager` (settings_repos_export/import 4 locales), notices `onSourcesExported`/`onSourcesImported`/`noticeFailure` en VM; tests `SourceBackupTest` (roundtrip preserva todo, version 2 rechazada, parse respeta enabled/priority, URL http sobrevive al codec para que el merge la rechace); compila + verdes.)
- P06-T06 (`AppDetailsViewModel` ahora inyecta `RepositoriesRepository`; UI state gana `preferredSource`/`sources`/`sourceNames`: `load` lee `settingsDataStore.preferredSources.first()[pkg]` + snapshot de repos (id→name); `resolve` pasa la preferencia a `pickBestVersion` y la fuente preferida gana SIEMPRE que ofrezca versión instalable (antes pasa el flujo existente signer→Play→resto — invariante del pick original intacto cuando no hay preferencia). `onSelectSource(source|null)` persiste `setPreferredSource(pkg, source "")` + re-resuelve. `AppDetailsScreen`: chips FilterChip (fuentes >1) en la card Versions, lista filtrada a la fuente elegida, label vía `sourceLabel` (play→Google Play, github→GitHub, gitlab→GitLab, else nombre repos o capitalize). `pickBestVersion` extraída a función top-level pública como seam de decisión pura (los unit tests locales del módulo no pueden ver `internal` de main — sin friend paths). Tests `PickBestPreferredSourceTest` (6): sin preferencia mantiene signer, preferida gana a versionCode mayor, elige la mayor de su fuente, preferida no relacionada hace fallback, fresh install con preferida, preferida ignora mismatch de certificado. Verificación: `:feature:details:testReleaseUnitTest` 6 verdes + `:app:compileReleaseKotlin` (grafo Hilt OK).)
- P06-T07 (`observeRecent`/`listRecent` eran los ÚNICOS listados que elegían la fila por recencia (no min priority) — `getApp` (LIMIT 1 por priority), `listByCategory` (bare-column MIN), `search` (ORDER BY priority + `distinctBy` en repo) ya coincidían. Reescribimos ambos con el mismo patrón: `JOIN (SELECT packageName, MIN(COALESCE(r.priority,1000)) ... GROUP BY packageName)` y la fila ganadora es la del repo de menor prioridad; el orden sigue siendo recencia MAX por package vía subconsulta correlacionada (una fila más fresca de un repo de mayor prioridad no pierde su hueco, pero el header es de min priority). ROW_NUMBER/window functions descartadas: minSdk 26 trae SQLite sin window functions. Ties de prioridad igual → una fila (GROUP BY), cualquiera de los repos de min priority es válido. "Preferred override en capa domain" del plan NO aplica al listado de catálogo: las settings viven en DataStore (la capa data no la lee) y preferred ya se aplica en sus flujos (detalles/updates); forzarlo aquí metería una dependencia DataStore en data solo para listings. Tests `CatalogDaoDedupTest` (3 Robolectric): min-priority row por package en recent, orden por recencia pero header de min priority (stale con fila fresca en repo de mayor prioridad), tie de prioridad → una sola fila. Verificación: `:core:database:testReleaseUnitTest` + `:data:testReleaseUnitTest` + `:app:compileReleaseKotlin` verdes.)
- P06-T08 (`FdroidIndexSourceProvider.validate` ahora pasa `versionCount` + `hasAnySha256` (blank cuenta como ausente) a la función pura `fdroidIntegrityWarning`: index con versiones pero sin ningún hash sha256 → warning "This index provides no sha256 hashes: integrity is verified locally after download."; si trae hashes → sin warning; índice vacío → sin warning. `HtmlRegexSourceProvider.validate` SIEMPRE warnea vía `HTML_INTEGRITY_WARNING` ("This source provides no checksums: sha256 is computed after download and there is no origin digest to compare.") — por construcción un origen HTML no puede declarar checksum. Ninguno bloquea el add (warning es solo informativo y el diálogo del preview ya lo renderiza verbatim, `SettingsScreen` state.sourcePreview.warning). Copy en inglés, consistente con los NovaError que también se muestran verbatim. Tests `IntegrityWarningTest` (5) en :data — fixture "sin sha256" → warning; verificación: `:data:testReleaseUnitTest` + `:domain:testReleaseUnitTest` + `:app:compileReleaseKotlin` verdes.)

## P07 Sistema de diseño

- [x] P07-T01 Tokens Spacing (4/8) Elevation Radius
- [x] P07-T02 Paleta: 1 acento + neutros + semánticos
- [x] P07-T03 `NovaShapes` + tipografía
- [x] P07-T04 Tema claro/oscuro/AMOLED/dynamic
- [x] P07-T05 Componentes: AppCard, Button, Chip, SearchBar, Dialog, Sheet, Empty/Error/Skeleton
- [x] P07-T06 Tokens de motion
- [x] P07-T07 Contraste WCAG AA documentado en tokens
- [x] P07-T08 Migrar `core/ui` off hex/dp crudos
- P07-T01/T03/T08 (nuevos `NovaSpacing` 4/8/12/16/24/32/48, `NovaShapes` Chip 8 / Card 12 / Sheet 28 + `NovaMaterialShapes` (slots M3), `NovaElevation` 0/2/6 en `core/ui/theme/{Spacing,Shape,Elevation}.kt`; `MaterialTheme(shapes=NovaMaterialShapes)` y `typography=NovaTypography` ya wired en `Theme.kt`. Migrados a tokens en `core/ui/components`: AppCards (paddings 16→LG, gaps 12→MD/8→SM/4→XS, corners de card 20→CARD, hero 24→SHEET, pill 50→CHIP, icon squircles 18/16/3.2f → regla única `appIconSquircle(size/3.2f)` en AppIcon.kt), ScreenStates (28→SHEET, 18→CARD, 8→SM, 24→XL), NovaGradients (corners botón 20→CARD, gap 12→MD), SourceBadge (8→CHIP). `AppCards.kt` conserva literales de dimensión sin token prescrito (272 hero width, iconos 64/60dp, gaps 14/6/2dp, starSize 12dp, chip vertical 10dp) — tamaños de icono/medidas, no espaciado de escala; el token set prescrito es cerrado y forzar 16/12 distorsionaría). `rg 'Color(0x' core/ui/components` = 0 (T08: se borró el último, `CardFallbackColor` muerto; NovaGradients ya es puro acento vía `LocalNovaAccent`)).
- P07-T02 (enum `AccentPalette` recortado a 6: NOVA (default), OCEAN, VIOLET, AMBER, GRAPHITE, DYNAMIC; borradas EMERALD/ROSE/LIME/SAPPHIRE/SKY/TEAL/PINK/CORAL/CRIMSON/GOLD; `AccentPalettes` ahora solo las 5 + `of(DYNAMIC)=NOVA` como fallback. Defaults EMERALD→NOVA en `SettingsDataStore.accentPalette`, `SettingsViewModel`, `NovaStoreRootViewModel`, `NovaTheme`, `LocalNovaAccent`. Selector de Settings: ahora 6 swatches (5 gradientes + el sweep multi-accent para DYNAMIC); stale `EMERALD` guardado en DataStore cae por `enumOrDefault` → NOVA sin crash. Se quitó `Teal` muerto; KDoc del header documenta política de contraste y "no acento para error".)
- P07-T04 (ThemeMode SYSTEM/LIGHT/DARK/AMOLED ya mapeaban claro/oscuro/negro; `AccentPalette.DYNAMIC` activa `dynamicLight/DarkColorScheme` en API 31+ (reemplaza el param `dynamicColor` inusado de `NovaTheme` — único caller `NovaStoreRoot`), con fallback NOVA < S; `themes.xml`/`values-night` `windowBackground` → `@android:color/transparent` (sin blanco hardcode; AMOLED puro en Compose).)
- P07-T05 (borrados composables muertos verificados por rg: `AppCardGrid` (0 refs externas), `SectionTitle` + `SeeAllLink` (0 callers en features; HomeScreen trae su propio header local) e import stale de SectionTitle en HomeScreen. Resto de componentes `core/ui` verificados usados en features: NovaGradientButton (API estable, reusado en account/details/updates), AppCardLarge/Row/AppGridCell, NovaRating*, ShimmerBox, SourceBadge, NovaCategories, InstalledAppIcon, rememberQrScanner. `mv uses` internos ya migrados a tokens en T01.)
- P07-T06 (`core/ui/theme/Motion.kt`: `NovaMotion.QUICK=120`, `MIDDLE=200`, `EMPHASIZED=400` ms + nota reduce-motion vía `MotionDurationScale`; transiciones nav las consume P08-T08.)
- P07-T07 (pares primary/onPrimary corregidos: `NovaAccent` ganó `onPrimaryLight`/`onPrimaryDark` por paleta para que dark-mode (primary pálida) y OCEAN-light no rompan 4.5:1; `Theme.kt` copia `onPrimary = palette.onPrimary*` (amoledScheme también). Contraste calculado por luminancia: NOVA 5.3/5.9, OCEAN light 3.5→corregida con onPrimary `#00262E`, VIOLET 5.0+, AMBER 5.05+, GRAPHITE 7.5+; blancos sobre gradiente declarados patrón decorativo en el KDoc. Test de luminancia opcional según plan — no se agregó (core/ui sin JUnit).)

## P08 Rediseño pantallas

- [x] P08-T01 Home / descubrir
- [x] P08-T02 Búsqueda
- [x] P08-T03 Detalle
- [x] P08-T04 Descargas
- [x] P08-T05 Actualizaciones
- [x] P08-T06 Biblioteca / instaladas
- [x] P08-T07 Cuenta (consistencia visual)
- [x] P08-T08 Nav root + transiciones
- [x] P08-T09 Estados carga/vacío/error/offline en cada pantalla de P08
- [x] P08-T10 Targets 48 dp y contentDescription en pantallas tocadas
- P08-T01 (HomeScreen.kt 1270 L **partido a 475 L**: entry + dispatch de estados; secciones extraídas a `HomeSections.kt` (408 L: headers, quick tiles, OfflineBanner, ScanProgressBanner, chips, `CatalogEmptyState`, shelf placeholder) y `HomeShelvesSection.kt` (313 L: HeroPager, ShelfCard, RecentAppCard, shimmer rows) y `HomeTopBar.kt` (197 L: topbar + `HomeSearchPill` tokenizado `NovaShapes.Sheet`+`NovaElevation.SEARCH_BAR`+`NovaSpacing`). Gradientes inline (~536) → exportados a `core/ui/theme/Color.kt` (`BrandIndigo`, `BrandViolet`, `NovaTitleBrush`, `UpToDateBrush`, `HeroGradients` como constantes de marca decorativas, usadas vía Brush en Home). `rg 'Color(0x' feature/home` = **0** (también `CircularProgressIndicator` = 0; loading = ShimmerBox skeleton). Empty catalog → EmptyState con CTA "Añadir fuente" (nueva string `home_catalog_empty_add_source` en 4 locales; wire `onAddSource = navigate(ROUTE_SETTINGS)` en Root; CTA refresca también en layout default). Shelf ≤20 ya vigente (`ROW_SIZE=20`, `SHELF_SIZE=18`, `observeRecentlyAdded(limit=20)`). **Decisión:** `NovaSearchBar` no existe en core/ui (plan asumía componente) → la pill inline quedó tokenizada en vez de nueva API compartida. `home_catalog_empty_action` quedó sin uso.)
- P08-T02 (SearchScreen: `8.sp` del badge AUTO → `MaterialTheme.typography.labelSmall` (11sp) — `rg '\.sp'` search = 0; primer load con skeleton `SearchResultsSkeleton` (6 shimmer cards AppCardRow-like) cuando `results.isEmpty() && searching`; query vacío vs sin resultados siguen distintos (EmptyState ramas separadas ya existían y se conservan); keys ya presentes (`"${it.source}:${it.packageName}"`, chips `it ?: "all"`); offline usa `searchLocal`.)
- P08-T03 (AppDetailsScreen: rama `state.loading` → `DetailsSkeleton` (ShimmerBox: icono 96dp `.clip(NovaShapes.Sheet)`, title/subtitle bars, banda de botón 52dp) reemplazando `LoadingState`/`CircularProgressIndicator` (sin imports de ambos); el loading string se mantiene vía `semantics` para TalkBack. Botón instalar ya era 52dp (`NovaGradients.kt:86`), Play 48dp. Touch ≥48 añadidos: banner "Open" (44→48), close del visor de screenshots (44→48), `SecondaryActionButton`, FilterChips de fuente (P06-T06), TextButtons (ver más/menos, leer reseñas), VersionRow, `ContactRow` (la fila del icono 20dp en DetailsExtraSections:224), permisos "Show all". Screenshots ya LazyRow con keys. `rg 'Icons.Filled.ArrowBack|KeyboardArrow'` details = 0 (AutoMirrored).)
- P08-T04 (Downloads ya cumplía: `EmptyState` si cola vacía, LazyColumn virtualizada con keys en todas las secciones (active/queued/completed/failed por taskId), sección auto-clean en empty. Verificado, sin cambios.)
- P08-T05 (Updates: `size(42.dp)` → `48.dp` en el MoreVert (L598); "Update all" y NoticeBanner ≥48; `rg -ni 'apkpure|apkcombo'` updates = 0; agrupación por fuente **activa** ya vigente (`UpdatesViewModel` agrupa `notIgnored.groupingBy { it.source }`, candidatos solo de fuentes enabled; mirrors eliminados en P05).)
- P08-T06 (Installed NO tenía ruta de desinstalar → añadida: MoreVert por fila → DropdownMenu "Desinstalar" → **AlertDialog de confirmación** (`installed_uninstall_title` en 4 locales; reusa `details_uninstall`, `action_cancel`) → `InstalledViewModel.uninstall(packageName)` → `InstalledAppsRepository.uninstall` (sistema lanza su diálogo; lista se refresca vía PackageChangeReceiver). Filas 48dp (`heightIn`). Error → ErrorState+retry existente. LazyColumn con keys existente, búsqueda local y empty conservados.)
- P08-T07 (Cuenta: ErrorBanner local duplicado eliminado → componente único **`core/ui/components/InfoBanner.kt`** (`InfoBannerKind.INFO/ERROR`, tokens: `NovaShapes.Card`, `NovaSpacing`, ≥48dp clickable, hint opcional). Account usa `InfoBanner(kind=ERROR, hint=…)`. Sin banners duplicados copy-paste.)
- P08-T08 (NovaStoreRoot: `fontSize = 10.sp` → `MaterialTheme.typography.labelSmall` (11sp = mínimo AA; `labelTiny` del plan se satisface con labelSmall existente, sin añadir dimensión nueva; `softWrap=false`+ellipsis mantiene una línea a 4 tabs @360dp). Transiciones nav con `NovaMotion.MIDDLE` (fade ± slide 5% en NavHost enter/exit/pop). NavigationBar ya usaba token `surfaceContainer`; badge updates ya existía. Rutas intactas (deep links compile). `rg '10\.sp|\.sp'` Root = 0, import sp eliminado.)
- P08-T09 (checklist estados, ver tabla abajo. Skeleton donde hay listas; error+retry en red; OfflineBanner en Home; `searchLocal` offline.)
- P08-T10 (a11y en pantallas tocadas: contentDescription en todo icon-only; chevrons/flèches `AutoMirrored` (back, sort, chevrons, OpenInNew); targets ≥48 en details/updates/installed/home/search; decorative icons `null` documentados junto a texto.)
- Nota DERIVADA (fuera de P08 pero bloqueante detectado al correr `testReleaseUnitTest`): el grafo Hilt de `:app` NO ensamblaba — `SourceRegistry` sin `@Binds` y el `setOf` de providers sin multibinding. `/*apk*/:app:assembleRelease` habría fallado desde la fase de fuentes. Fijado en `data/di/DataModule.kt` (`bindSourceRegistry` + 4 `@Binds @IntoSet` de providers: Fdroid, HtmlRegex/play-web, GitHub, Gitea) y `SourceRegistryImpl` con `@JvmSuppressWildcards` (use-site type) porque `Set` kotlin es covariante → `Set<? extends …>` nunca matchea el key `Set<…>` de Dagger. `:app:hiltJavaCompileRelease`, `testReleaseUnitTest` y `:app:assembleRelease` ahora pasan.

### Tabla P08-T09 — estados por pantalla

| Pantalla | Loading (skeleton) | Empty | Error + retry | Offline |
|---|---|---|---|---|
| Home | ShimmerBox (hero, chips, shelves, recientes) | EmptyState + CTA "Añadir fuente" | banner de estado + auto-retry init | OfflineBanner + searchLocal |
| Search | `SearchResultsSkeleton` (primer load) | EmptyState diferenciado (query vacío vs sin hits) | ErrorState + retry | `searchLocal` fallback |
| Details | `DetailsSkeleton` (header + botón) | N/A (o contenido o error) | ErrorState + retry | N/A (fallo de fuente → ErrorState) |
| Downloads | N/A (sin pantalla de carga; progreso por item) | EmptyState "cola vacía" + auto-clean | N/A (fallidos en sección propia) | N/A (cola local) |
| Updates | `UpdatesSkeleton` (+ barra scan) | EmptyState + botón scan | ErrorState + retry | N/A (fuentes sin mirror, P05) |
| Installed | `InstalledSkeleton` | EmptyState | ErrorState + retry | N/A (lista local) |
| Category | ShimmerBox grid + shimmer load-more | (hereda Home) | vía Home | vía Home |
| Account | N/A | N/A | InfoBanner (ERROR) + mensaje contextual | N/A |
| Settings | P09 | P09 | P09 | P09 |

## P09 Configuración

- [x] P09-T01 Reagrupar secciones requeridas
- [x] P09-T02 Búsqueda dentro de ajustes
- [x] P09-T03 Descripciones y defaults
- [x] P09-T04 Confirmación destructiva
- [x] P09-T05 Privacidad / backup / acerca de
- [x] P09-T06 Extraer SettingsScreen blob a archivos por sección
- P09-T01 (blob 1910 L partido en `feature/settings/sections/` en el orden prescrito: Sources, Updates, DownloadsAndInstall, Appearance, Storage, Privacy, Backup, About; reorden absoluto del UI; **Advanced eliminado**: root install → Descargas e instalación, token dispenser/SessionProvider → Privacidad; rows/diálogos compartidos en `SettingsRows.kt`; `SettingsScreen.kt` = Scaffold + TopAppBar + search + lista de secciones → **250 L exactos**. Firma pública `SettingsScreen(onOpenAccount, onBack, viewModel = hiltViewModel())` intacta para NovaStoreRoot.)
- P09-T02 (`SettingsSearch.kt`: 40+ items indexados `SettingsItem(id, title, description, keywords, section)` + matcher puro `settingsItemMatches(item, query)` case-insensitive sobre título+descripción+keywords; search field fijado bajo TopAppBar; secciones vacías ocultas en query; EmptyState si no hay match. "wifi" → fila Wi-Fi only. **`SettingsSearchTest` (6 tests) en `feature/settings`**: testReleaseUnitTest ahora ejecuta tests reales — cubre el requisito de prueba (sin infra Compose).)
- P09-T03 (todo `SwitchRow`/`ToggleTile` con `title`+`description` — verificado por grep; About lista "valores de fábrica" verificados: auto-update off, wifi-only on, confirm-install on, play-source on, anonymous on; fila **Reset settings** con ventana de confirm → `SettingsViewModel.resetSettings()` → `SettingsDataStore.resetAll()` (lista explícita de keys de settings, deja favorites/sessions/ignored intactos).)
- P09-T04 (`ConfirmActionDialog` gating confirmación ANTES de ejecutar: borrar fuente, reset settings, clear cache, clear downloads, sign out; "auto-clean downloads" NO se confirma por diseño. `rg repo remove/clear/reset` en settings confirma diálogos presentes.)
- P09-T05 (Privacidad: fila "Cuenta Play" → navega `account` (sin embeber login), modo anonymous con status, fila informativa QUERY_ALL_PACKAGES (explicación por qué se pide), toggles notificaciones, session provider (venía de Advanced). Backup: export/import fuentes (P06-T05) accesibles vía SAF + nota de versión. About: `versionName` real, `LicenseDialog` (licencias), nota "no afiliado a Google".)
- P09-T06 (SettingsScreen.kt ≤250 L y delgado; lógica en `SettingsViewModel` — reset/clear cache/clear downloads/sign out nuevos; `core:datastore` expone `datastore.preferences` vía `api`; `feature/settings` gana dependencia `:core:downloader` para el clear de descargas.)
- Notas: reutiliza `InfoBanner`/`EmptyState` de core/ui (sin duplicados locales); a11y: reorder con `Icons.Filled.ArrowUpward/ArrowDownward` + `contentDescription`, chips/swatches 48dp; 26 strings nuevas en 4 locales. Verificación: `wc -l` SettingsScreen.kt = 250; `rg -i advanced` settings = 0; `:feature:settings:testReleaseUnitTest` corre SettingsSearchTest (6 green); `:app:hiltJavaCompileRelease` y `:app:assembleRelease` verdes.

## P10 Identidad — omitido

Cancelado: coste alto, riesgo de incongruencias (applicationId, workers, UA, clases) sin necesidad de producto. Identidad actual (NovaStore, `com.novastore.fork`) es el estado final.

## P11 Pruebas, a11y, seguridad

- [x] P11-T01 SHA-256 mandatory o rechazo explícito `Unverified`
- [x] P11-T02 Firma: no instalar si digest instalado falta y hay app instalada
- [x] P11-T03 Rechazar `http://` en descargas; `network_security_config`
- [x] P11-T04 Migrar tokens plaintext `SessionCipher`
- [x] P11-T05 Tests RootCommand regex
- [x] P11-T06 Tests providers + update scan
- [x] P11-T07 CI: `testReleaseUnitTest` + script tests arreglado
- [x] P11-T08 A11y RTL + TalkBack strings
- [x] P11-T09 Revisar permisos storage

## P12 Pulido y release

- [ ] P12-T01 Re-medir métricas vs baseline
- [ ] P12-T02 `assembleRelease` + lint vital
- [ ] P12-T03 Checklist `99_CRITERIOS_DE_FINALIZACION.md`
- [ ] P12-T04 Escribir `INFORME_FINAL.md`
