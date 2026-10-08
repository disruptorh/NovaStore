# P05 — Motor de actualizaciones (eliminar mirrors)

## Objetivo

Cero APKPure/APKCombo. El scan compara instaladas **solo** con providers enabled + Play si el usuario lo tiene activo. WorkManager eficiente, notificaciones reales, migración de prefs.

## Prerrequisitos

P04-T03, P04-T04 (registry + F-Droid). P04-T05/T06 si GitHub/Gitea ya están; si no, el scan de esas fuentes espera a que existan pero **no** se usa mirror como tapujo.

## Archivos afectados (existen hoy)

- `data/src/main/java/com/novastore/app/data/websource/ApkPureClient.kt` **borrar**
- `data/src/main/java/com/novastore/app/data/websource/ApkComboClient.kt` **borrar**
- `data/.../PlayStoreRepositoryImpl.kt` L547-576 `mirrorVersionsFor`
- `domain/.../PlayStoreRepository.kt` L67-72
- `data/.../CatalogRepositoryImpl.kt` L300, L332-347 `mergeMirrorVersions`; campos L54-55
- `domain/.../CheckForUpdatesUseCase.kt` imports L6-7, bloque L154-178, stage MIRRORS
- `domain/.../ScanProgressTracker.kt` enum `MIRRORS`
- `domain/.../DownloadUpdateUseCase.kt` L71
- `core/model/Sources.kt` L23-34, L58
- `core/model/UpdateRules.kt` L18
- `core/model/SyntheticVersionCodes.kt`
- `core/datastore/SettingsDataStore.kt` L101-103, L300-320
- `feature/settings/SettingsScreen.kt` L279-292
- `feature/settings/SettingsViewModel.kt` L228-237
- `feature/details/AppDetailsViewModel.kt` L347-348
- `feature/updates/UpdatesScreen.kt` L730-731
- `core/ui/.../SourceBadge.kt` L52-57
- `core/ui/.../AppCards.kt` L165
- `core/model/StoreLinks.kt` L83
- tests: `SourcesTest.kt`, `UpdateRulesTest.kt`, `StoreLinksTest.kt`
- `app/.../UpdateScanWorker.kt`, `WorkScheduler`
- `app/.../receiver/BootReceiver.kt`
- `app/.../NovaStoreApp.kt` canales
- `app/.../work/UpdateNotifier.kt`
- `core/updater` o `data/updater` `SourceResolutionPolicy.kt`

## Tareas

### P05-T01 Borrar clientes mirror

**Qué:** Delete `ApkPureClient.kt`, `ApkComboClient.kt`. Quitar `@Inject` constructors consumidores.

**Aceptación:** `rg -i 'apkpure|apkcombo|ApkPure|ApkCombo' --glob '*.kt'` solo puede quedar parseo de **links** en `StoreLinks` si se decide mantener (P05-T05). Compile.

**Verificación:** gradle compile app.

### P05-T02 Quitar mirrorVersionsFor y mergeMirrorVersions

**Qué:** Eliminar método interfaz+impl. Eliminar `mergeMirrorVersions`. `getAppDetails` no rellena versions desde mirrors.

**Aceptación:** 0 llamadas. Detalles de app Play/F-Droid/GitHub siguen funcionando.

**Verificación:** `rg mergeMirrorVersions`; `rg mirrorVersionsFor`

### P05-T03 CheckForUpdatesUseCase sin MIRRORS

**Qué:** Borrar bloque leftovers L154-178. Quitar `Stage.MIRRORS` del enum (actualizar UI `ScanProgressBanner` en Home). Tras repos+installed, Play **solo si** `playUpdatesEnabled`. Luego providers enabled no-F-Droid (GitHub etc.) vía registry `getVersions`. `unconfirmed` se llena si Play toggle on pero `hasPlayAccess()` false, **no** por fallo de mirror.

**Aceptación:** scan con mirrors prefs residuales no hace HTTP a apkpure.com (Charles/log OkHttp). Candidatos `source` nunca `apkpure`/`apkcombo`.

**Verificación:** unit test use case con fake PlayStoreRepository sin método mirror; `rg Stage.MIRRORS` vacío.

### P05-T04 DataStore + Settings UI

**Qué:** Eliminar keys `apkpure_mirror_enabled`, `apkcombo_mirror_enabled` y setters. Quitar SwitchRows Settings. Migración: al leer DataStore, ignorar keys huérfanas (Preferences DataStore no requiere migración schema).

**Aceptación:** Settings no muestra APKPure/APKCombo. grep XML strings relacionadas: eliminar o dejar unused → quitar strings.

**Verificación:** `rg apkpure_mirror`; UI compile.

### P05-T05 Constants, badges, tests

**Qué:** Quitar `SOURCE_APKPURE`, `SOURCE_APKCOMBO` de `Sources.kt` y `isInstallSourceAllowed`. Ajustar `UpdateRules` (sin rama isMirror). `SyntheticVersionCodes` de combo: borrar si solo mirrors. SourceBadge ramas. AppCards setOf play/apkpure. StoreLinks: **mantener** parse de URL apkpure como deep link a package (útil) **o** borrar; prescrito: **mantener parse** (no es check de updates). Tests UpdateRules de mirrors: reemplazar por tests de providers.

**Aceptación:** `isInstallSourceAllowed` no menciona mirrors. Tests verdes.

**Verificación:** `./gradlew testReleaseUnitTest`

### P05-T06 Scan fuentes activas

**Qué:** Pseudocódigo obligatorio:

```
refresh enabled F-Droid/index providers
engine.check(installed, versions from CatalogDao for enabled sources only)
if playUpdatesEnabled && hasPlayAccess: play versions for apps not signer-matched to a repo
for other enabled providers (github/gitea/html): getVersions(pkg)
resolve with SourceResolutionPolicy + preferredSources
retainOnly
```

No consultar provider `enabled=false`. No GitHub global search.

**Aceptación:** test: 2 repos, uno disabled, versions del disabled no generan candidato.

**Verificación:** `UpdateEngine` test.

### P05-T07 Dedup unificada

**Qué:** `SourceResolutionPolicy.select`: 1) preferred source del usuario si ofrece update compatible; 2) menor `priority`; 3) mayor versionCode; 4) source id. Alinear comentario DAO “lower wins”. Catalog listing ya es prioridad; documentar en `docs/update-engine.md`.

**Aceptación:** test: repo priority 10 v100 vs priority 20 v200 → gana v100 **si** se documenta prioridad-first. **Decisión prescrita:** prioridad de fuente gana; version más nueva de fuente peor solo si el usuario eligió esa fuente o si prioridades empatan. Evita que un repo random “gane” el catálogo.

**Verificación:** tests policy ≥4 casos.

### P05-T08 SOURCE_PLAY_WEB

**Qué:** Cambiar `SOURCE_PLAY_WEB` a `"play-web"` (`Sources.kt:21`). Actualizar badges, allow-install, Catalog merge, `isInstallSourceAllowed`. Play web **no** entrega APK (igual que ahora); solo metadata. Updates no usan play-web como artefacto.

**Aceptación:** grep `"play"` en source ids: play vs play-web distintos. Tests Sources.

**Verificación:** `rg 'SOURCE_PLAY_WEB' -A2`

### P05-T09 WorkManager + boot

**Qué:** `WorkScheduler`: `setRequiresBatteryNotLow(true)` cuando threshold settings >0. Mapear `battery_threshold`. Periodic min interval 15 min (API WorkManager). `IMMEDIATELY` → 15 min no 1h si se quiere agresivo, **o** dejar 1h (batería). Prescrito: IMMEDIATELY = 15 min + network connected. `BootReceiver`: además de downloads, `workScheduler.schedulePeriodicScan()`. Opcional: settings toggle “ignorar optimización batería” que abre intent; no forzar el permiso.

**Aceptación:** reboot (doc manual) re-registra unique work `nova_update_scan`. Unique name se mantiene (P10 omitido).

**Verificación:** unit con WorkManager test artifact si se añade `work-testing`; o inspección código + compile.

### P05-T10 Notificaciones

**Qué:** Usar `CHANNEL_INSTALLATION` al éxito de install; `CHANNEL_ERRORS` en fallo verify/download. Constantes, no literal `"downloads"` en `DownloadWorker.kt:67` — usar `NovaStoreApp.CHANNEL_DOWNLOADS` (si circularidad de módulos: extraer ids a `core/common/NotificationIds.kt`).

**Aceptación:** 5 canales, 5 usados o borrar los no usados. Prescrito: usar los dos muertos.

**Verificación:** `rg NotificationCompat.Builder` ≥5 sitios o 3 sitios + 2 nuevos.

### P05-T11 Tests motor

**Qué:** Tests: no mirror; disabled source ignored; preferred source; play disabled skip; retainOnly keeps in-flight.

**Aceptación:** ≥6 tests nuevos green.

**Verificación:** gradle unit test.

## Definición de hecho

`rg -i apkpure --glob '*.kt'` solo StoreLinks/tests de parse URL. Scan no etapa MIRRORS. Policy alineada. Play-web id distinto. Boot re-arma. Canales vivos.

## Notas de decisión

Descartado “dejar mirrors metadata-only”: el requisito es eliminarlos por completo. Play anónimo nativo **se conserva** (no es mirror APKPure).
